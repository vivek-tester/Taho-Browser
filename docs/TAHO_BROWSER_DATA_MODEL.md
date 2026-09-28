# TAHO_BROWSER_DATA_MODEL.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** The capture domain's types, the transaction state machine, the six representations, body
completeness, and provenance. The capture domain (`:capture:domain`) is **pure Kotlin/JVM** — no
`android.*`, no Room, no GeckoView.

---

## 1. Layering note

Everything in this document is engine-agnostic. The capture domain does not know what a
`GeckoSession` is. It receives already-attributed `CaptureEvent`s and produces `Transaction`s. This is
what allows the P0 correctness requirements to be tested on the JVM in milliseconds, with no device.

```
GeckoView/WebExtension  ──▶  CaptureEventSource (interface)
                                    │  (GeckoView impl | page-instrumentation impl)
                                    ▼
                            CaptureCoordinator
                                    │
                                    ▼
                     RelevanceEngine · SecretDetector
                        TransactionStateMachine
                                    │
                                    ▼
                    RawCapture ──▶ representations (§5)
                                    │
                            CaptureRepository (interface)
                                    │
                                    ▼
                          RoomCaptureRepository (impl)
```

---

## 2. Identity model

Five distinct identities. Conflating any two of them is the root cause of most capture bugs.

| Identity | Type | Owner | Lifetime | Meaning |
|---|---|---|---|---|
| `GeckoSession` | object ref | GeckoView | session | the live engine session. **Never persisted.** |
| `TahoTabId` | `ULID` | `TabManager` | app | stable browser tab identity, survives process death |
| `ExtTabId` | `Int` | Gecko | runtime | engine tab id in `webRequest.details`. `-1` = none. Not stable across restarts. |
| `CaptureSessionId` | `ULID` | `CaptureCoordinator` | session | a bounded window of observation (see §3) |
| `TransactionId` | `ULID` | `CaptureCoordinator` | transaction | one observed HTTP exchange |
| `RequestId` | `String` | Gecko | runtime | `webRequest.details.requestId` — engine correlation key, **runtime-scoped, reused**. Never a primary key. |

**Rule:** `RequestId` is a *correlation* key, not an identity. It is unique only within one runtime
lifetime. All persistence keys are ULIDs minted by the app. This is why a port reconnect mid-flight
can safely create a new provisional transaction for a previously unseen `RequestId` (IPC §6).

---

## 3. Capture sessions

PRD §109/§110 distinguish ephemeral browsing from promoted workspaces. The model encodes that:

```kotlin
enum class CaptureSessionKind { EPHEMERAL, WORKSPACE, PRIVATE }

data class CaptureSession(
    val id: CaptureSessionId,          // ULID
    val kind: CaptureSessionKind,
    val createdAt: Instant,            // DB-assigned, authoritative
    val label: String?,                // workspace name, if promoted
    val targetHost: String?,           // pinned host for relevance first-party matching
    val retention: RetentionPolicy,    // SESSION_ONLY | KEEP_UNTIL_DELETED | PRIVATE
    val state: CaptureSessionState     // ACTIVE | PAUSED | CLOSED
)
```

**Ephemeral sessions** are created per browsing session and, under `SESSION_ONLY`, are dropped when
the session closes. **Workspace sessions** are explicitly promoted by the user (PRD §111) and persist.
**Private sessions** carry `retention = PRIVATE`, which forbids persistence of secret material
regardless of other settings.

`targetHost` exists for the relevance engine's first-party matching. It is set on promotion to a
workspace, or inferred from the first committed navigation, and it is **never** a substring match
(PRD §15):

```kotlin
fun isFirstParty(host: String, target: String): Boolean =
    host.equals(target, ignoreCase = true) ||
    host.endsWith(".$target", ignoreCase = true)
```

---

## 4. The transaction model

### 4.1 Why transactions, not requests

A request and its response are separated by time, may never complete, and may be interrupted. Storing
"requests" forces either nullable response fields or a fiction that the exchange finished. A
**transaction** is the unit of observation; its parts have independent availability, and the
transaction carries an explicit terminal state.

### 4.2 Parts

```kotlin
data class Transaction(
    val id: TransactionId,
    val captureSessionId: CaptureSessionId,
    val tahoTabId: TahoTabId,
    val attribution: Attribution,          // §4.4 — immutable once set
    val correlation: Correlation,          // engine requestId + extTabId + frameId + docId
    val request: RequestPart,
    val response: ResponsePart?,           // null until response starts
    val redirectChain: List<RedirectHop>,
    val timing: Timing?,
    val state: TransactionState,
    val relevance: Relevance,
    val secrets: SecretAssessment,         // classification only
    val observation: ObservationSource,    // ENGINE | PAGE  (PRD §131 honesty)
    val createdAt: Instant,
    val updatedAt: Instant
)
```

```kotlin
sealed interface RequestPart {
    val url: String
    val method: String
    val headers: HeaderSet
    val body: BodyPart?
    val completeness: Completeness
}

data class ResponsePart(
    val status: Int?, val statusText: String?, val headers: HeaderSet,
    val body: BodyPart?, val timing: Timing?, val completeness: Completeness
) {
    val isRedirect: Boolean get() = status != null && status in 300..399
}
```

### 4.3 State machine

PRD §13/§54. The transition table is **total**: every (from, to) pair not listed is an invalid
transition, which is a no-op plus a diagnostic, never a corruption.

```
                 ┌──────────────► CANCELLED ◄──────────────┐
                 │  (tab closed,  │   (user abort,          │
                 │   user clear)  │    session closed)     │
                 │                ▼                        │
  ┌──────────┐   │        ┌──────────────┐                 │
  │ STARTED  ├───┼────────▶│ HEADERS_     │                 │
  └──────────┘   │        │  CAPTURED    │                 │
                 │        └──────┬───────┘                 │
                 │               ▼                         │
                 │        ┌──────────────┐                 │
                 ├────────▶│ RESPONSE_    │─────────────────┤
                 │        │  STARTED     │                 │
                 │        └──────┬───────┘                 │
                 │               ▼                         │
                 │     ┌─────────┼──────────┐              │
                 │     ▼         ▼          ▼              │
                 │  COMPLETED  TRUNCATED  PARTIAL          │
                 │     │         │          │              │
                 └─────┴─────────┴──────────┴──────────────┘
                                  │
                                  ▼
                               FAILED
```

| From | Permitted to |
|---|---|
| `STARTED` | `HEADERS_CAPTURED`, `CANCELLED`, `FAILED`, `TRUNCATED` |
| `HEADERS_CAPTURED` | `RESPONSE_STARTED`, `CANCELLED`, `FAILED`, `TRUNCATED`, `PARTIAL` |
| `RESPONSE_STARTED` | `COMPLETED`, `TRUNCATED`, `PARTIAL`, `CANCELLED`, `FAILED` |
| `COMPLETED` | terminal (no transitions out) |
| `TRUNCATED` | terminal |
| `PARTIAL` | terminal |
| `FAILED` | terminal |
| `CANCELLED` | terminal |

**Why `PARTIAL` is terminal:** once a transaction is declared partial, more late events could arrive
and make it look complete. Forbidding that keeps the persisted record honest — you cannot retroactively
"complete" a request whose response you never saw. A late event is recorded as an orphan diagnostic.

**`TRUNCATED` vs `PARTIAL`:**
- `TRUNCATED` — we captured a prefix and hit a cap. We know what we have; it is incomplete by size.
- `PARTIAL` — we do not know the outcome. Headers without a response, or a response that never
  completed.

The UI must render these differently (PRD §20, §55, §138): `TRUNCATED` shows captured/available bytes;
`PARTIAL` shows a warning that the outcome is unknown.

### 4.4 Attribution (immutable)

```kotlin
sealed interface Attribution {
    data class Known(val tahoTabId: TahoTabId, val extTabId: Int) : Attribution
    data object Unattributed : Attribution              // extTabId == -1
    data class Unresolved(val extTabId: Int) : Attribution
    data class Detached(val tahoTabId: TahoTabId, val extTabId: Int) : Attribution
}
```

`Attribution` is `val` and set once, at first event. It is never recomputed, never "improved" by a
later event, and never replaced when the tab is closed. `Detached` preserves the original ownership
while marking the session gone — so a transaction from a closed tab remains inspectable and remains
attributable to the tab that produced it (PRD §53.4).

### 4.5 Correlation

```kotlin
data class Correlation(
    val engineRequestId: String?,   // null when engine didn't supply one
    val extTabId: Int,
    val frameId: Int,
    val documentId: String?,
    val initiatedBy: Initiator      // NAVIGATION, SCRIPT, MEDIA, WEBSOCKET, OTHER
)
```

`CorrelationIndex` maps `(extTabId, engineRequestId) → TransactionId`. It is rebuilt on
`CaptureCoordinator` construction and updated on every event. It is an in-memory index only —
persistence always resolves via the stored `attribution`.

---

## 5. Representations — the six-layer separation

PRD §19. The critical property: **a value that is masked in one representation is not thereby
removed from the one below it.** Masking is a *view* operation, not a deletion.

```
 RawCapture            ← exactly what the engine reported. Secret values INTACT.
      │                  Only this layer may hold an unmasked secret.
      │  ┌── secret detection runs HERE, on RawCapture
      ▼
 NormalizedRequest     ← transport headers stripped, canonical URL/query, versioned rules
      │
      ├──▶ DisplayRepresentation   ← secrets MASKED. Used by all UI. Cannot be persisted.
      ├──▶ PersistenceRepresentation← secrets ENCRYPTED at rest. Used by Room.
      ├──▶ TransferRepresentation   ← secret policy APPLIED. Used for Browser→Taho.
      ├──▶ ExportRepresentation     ← secrets MASKED by default. cURL / share.
      └──▶ ReplayRepresentation     ← secrets STRIPPED unless explicitly authorised.
```

### 5.1 Enforced invariants

These are properties the build must make unrepresentable, not merely documented:

| # | Invariant | Enforcement |
|---|---|---|
| I1 | `DisplayRepresentation` has no constructor reachable from persisted/network types | distinct types; no shared mutable carrier; sealed hierarchy prevents upcasting |
| I2 | Only `RawCapture` and `PersistenceRepresentation` may contain an unmasked secret | secret type is distinct (`SecretValue`); only these two layers accept it |
| I3 | A secret's plaintext never reaches a `Log`, a `CrashReport`, an `AnalyticsEvent`, or a `ContentObserver` | `SecretValue` has no `toString()` override returning content; loggers accept only `SecretRef` |
| I4 | `TransferRepresentation` cannot be constructed without a `SecretPolicy` decision | policy is a required constructor parameter, not a defaulted field |
| I5 | Provenance is written once and never mutated | `Provenance` is an immutable data class held by value |
| I6 | `ExportRepresentation` is masked unless the caller passes an explicit `RevealIntent` | required parameter |

### 5.2 Provenance

```kotlin
data class Provenance(
    val sourceProduct: String,        // "taho-browser"
    val sourceVersion: String,
    val captureSessionId: CaptureSessionId,
    val tahoTabId: TahoTabId?,
    val transactionId: TransactionId,
    val capturedAt: Instant,          // DB-assigned receivedAt, never raw engine ts
    val originReportedAt: Instant?,   // engine ts, shown only if it diverges > 60s
    val originalUrl: String,
    val originalMethod: String,
    val normalizerVersion: String,    // so a stored request can explain its own shape
    val secretPolicyApplied: SecretPolicy?,
    val observation: ObservationSource
)
```

`normalizerVersion` is what makes "Immutable Provenance" real: a request normalised six months ago
records which rules shaped it, so a later normaliser change cannot silently reinterpret it.

---

## 6. Body completeness

PRD §20/§55. The single most important honesty mechanism in the system.

```kotlin
data class BodyPart(
    val contentType: String?,
    val charset: String?,
    val encoding: Encoding,           // UTF8 | BASE64 | BINARY_REFERENCE
    val storageRef: String?,          // for BINARY_REFERENCE
    val originalSize: Long?,          // declared by engine, may be null
    val capturedSize: Long,
    val truncated: Boolean,
    val representation: BodyRepresentation,  // TEXT | JSON | FORM | MULTIPART | GRAPHQL | BINARY
    val completeness: Completeness
)

enum class Completeness { COMPLETE, PARTIAL, TRUNCATED, UNAVAILABLE, NOT_APPLICABLE }
```

**`originalSize` is a claim, not a measurement.** It is frequently null and sometimes wrong. It is
stored as `declaredSize` semantics and never presented as fact. The UI shows
`Captured 64 KB of ~82 KB declared` at most, and `Size unknown` when null.

**`Completeness` is derived from capture evidence only** (PRD §55, verbatim requirement). It is never
inferred from the presence of other fields, never from UI state, never from "the page looks fine".

Field-level completeness is a first-class concept, because different parts have different
availability:

```kotlin
data class CaptureCompleteness(
    val requestUrl: Completeness,        // essentially always COMPLETE
    val requestHeaders: Completeness,
    val requestBody: Completeness,       // often UNAVAILABLE — see below
    val responseHeaders: Completeness,
    val responseBody: Completeness,      // usually UNAVAILABLE without a stream filter
    val timing: Completeness,
    val tlsInfo: Completeness            // UNAVAILABLE pending SPIKE-07
)
```

**Request-body honesty.** `webRequest` exposes `details.requestBody` only for certain request types
and forms; for others it is absent, and a naive implementation reports `COMPLETE` with an empty body
— which is a lie. Rule: if the engine did not supply a body, the state is `UNAVAILABLE`, and
`capturedSize = 0` with `originalSize = null`. `NOT_APPLICABLE` is reserved for genuinely bodyless
methods where the absence is *known* rather than merely unobserved.

---

## 7. Relevance

PRD §15. Classification is a pure function of the transaction and the capture session — no I/O, no
clock, no randomness. Pure functions are exhaustively testable.

```kotlin
enum class RelevanceCategory {
    AUTHENTICATION, PRIMARY_API, BUSINESS_API, GRAPHQL, WEBSOCKET,
    PAGE_NAVIGATION, STATIC_RESOURCE, ANALYTICS, TELEMETRY
}

data class Relevance(
    val category: RelevanceCategory,
    val isFirstParty: Boolean,
    val reason: String,                // short, user-facing, e.g. "matches workspace host"
    val score: Int                     // ranking within category; ties broken by capturedAt
)
```

**Hard rules from the PRD, encoded as tests:**

1. `PAGE_NAVIGATION` is assigned by the engine's request `type == "main_frame"`. A document load is
   **never** an API request, regardless of its URL shape. *(PRD §15)*
2. First-party matching uses hostname boundaries only — equality or `.` suffix. Never `contains`.
   `evil-example.com` is not first-party for `example.com`. *(PRD §15)*
3. `STATIC_RESOURCE` is never surfaced in the default `Relevant` filter.
4. Classification never mutates the transaction. It is recomputed deterministically if rules change,
   and the rule version is recorded.

---

## 8. Secret assessment

`SecretDetector` **classifies**; it does not redact. Redaction is a representation concern.

```kotlin
data class SecretAssessment(
    val findings: List<SecretFinding>,
    val highestSeverity: SecretSeverity  // NONE | LOW | MEDIUM | HIGH | CRITICAL
)

data class SecretFinding(
    val id: String,                 // stable within a transaction
    val location: SecretLocation,   // HEADER(name) | QUERY(key) | COOKIE(name) | BODY_JSON_PATH(p) | BODY_FORM_FIELD | URL_USERINFO | JWT
    val category: SecretCategory,   // AUTHORIZATION, BEARER_TOKEN, COOKIE, API_KEY, JWT, BASIC_AUTH,
                                    // CSRF_TOKEN, SESSION_ID, CLIENT_SECRET, PASSWORD, QUERY_TOKEN
    val confidence: Confidence,     // HIGH when the name matches a known rule, LOW when heuristic
    val evidence: String            // WHY it matched — never the value
)
```

**`evidence` must never contain the value.** For `Authorization: Bearer eyJ...` the evidence is
`"header name 'authorization' with bearer scheme"` — not a prefix of the token. A prefix is still a
leak (PRD §46.3, §98).

**Detection inputs, in order of confidence:**

1. **Name rules** — header/query/cookie names: `authorization`, `proxy-authorization`, `cookie`,
   `set-cookie`, `x-api-key`, `api-key`, `x-auth-token`, `x-csrf-token`, `x-xsrf-token`,
   `x-session-id`, `x-access-token`, `client-secret`, `password`, `token`, `secret`.
2. **Shape rules** — value structure: `eyJ…` (JWT), `Bearer …`, `Basic …` (base64 of `user:pass`),
   AWS-style `AKIA…`, long high-entropy hex/base64 in an auth-ish position.
3. **Context rules** — JSON body fields named `password`/`token`/`secret`/`apiKey` at any depth;
   form fields likewise.

**Deliberately *not* classified as secret by default** (PRD §75): `X-Request-ID`, generic `X-*`
headers, `Accept`, `Content-Type`, `User-Agent`. The normaliser must not strip them either — semantic
classification, not blanket `X-*` removal.

---

## 9. WebSocket, SSE, and page-level observation

PRD §32/§131. These are **not** HTTP transactions and are not forced into the request model.

```kotlin
data class ObservedStream(
    val id: String,
    val transactionId: TransactionId,   // the handshake transaction
    val kind: StreamKind,              // WEBSOCKET | SSE
    val observation: ObservationSource, // PAGE for frames
    val frames: List<StreamFrame>,
    val truncated: Boolean
)

data class StreamFrame(
    val direction: Direction,   // INBOUND | OUTBOUND
    val opcode: String?,        // text | binary | close | ping | pong  (WS only)
    val payload: BodyPart?,     // capped
    val at: Instant
)
```

The **handshake** is a real `Transaction` (an HTTP 101 upgrade) and is transferable. The **frames** are
`ObservationSource.PAGE` and are explicitly labelled as page-level JS observation, never as wire
capture. Downstream, `observation` is carried all the way to the inspector so the UI can say
"observed in page context" rather than implying transport-level visibility.

---

## 10. Timing

```kotlin
data class Timing(
    val startedAt: Instant,
    val headersAt: Instant?,
    val bodyAt: Instant?,
    val completedAt: Instant?,
    val dnsMs: Long?, val connectMs: Long?, val tlsMs: Long?, val ttfbMs: Long?, val totalMs: Long?
)
```

Every field nullable. The engine's coarse `details.timeStamp` is a **single** value; fine-grained
phases are frequently unavailable. Absent phase timings render as `—`, not `0 ms`. `totalMs` is only
set when both endpoints are known.

---

## 11. Normalisation rules (versioned)

`RequestNormalizer` is a pure, versioned, exhaustively-tested component.

**Remove** (transport-generated, inappropriate for API testing) — PRD §21:

```
Host, Content-Length, Connection, Keep-Alive, Proxy-Connection, TE, Trailer,
Transfer-Encoding, Upgrade, Sec-Fetch-*, Sec-CH-UA*, Sec-CH-UA-*, DNT (optional)
```

**Retain** (application-meaningful) — PRD §21, §75:

```
Content-Type, Accept, Accept-Language, Authorization (subject to policy),
X-API-Key, X-CSRF-Token, X-XSRF-TOKEN, X-Request-ID, and any unrecognised header
```

**Rule:** an explicit deny-list, never an allow-list. An unrecognised header is *retained* by default.
Rationale: an allow-list silently destroys application headers the app has never seen, which is
exactly the class of bug PRD §75 warns about. The deny-list is small, explicit, reviewable, and
versioned.

`normalizerVersion` is recorded in `Provenance`. Behaviour changes require a version bump; stored
requests keep their original version's meaning.

---

## 12. Invariants the test suite must enforce

| # | Invariant | Test level |
|---|---|---|
| M1 | Every terminal state is reachable and no transition leaves a terminal state | JVM unit, exhaustive table |
| M2 | Invalid transition is a no-op + diagnostic, never a mutation | JVM property test |
| M3 | `Attribution` never changes after first assignment | JVM property test |
| M4 | `COMPLETED` never coexists with `response == null` | JVM invariant test |
| M5 | `capturedSize <= originalSize` when `originalSize` is non-null | JVM property test |
| M6 | A body with `COMPLETE` and null storage and 0 bytes is a **failure** | JVM invariant test |
| M7 | `isFirstParty("evil-example.com", "example.com") == false` | JVM unit (substring regression) |
| M8 | A `main_frame` request is never `PRIMARY_API`/`BUSINESS_API` | JVM unit |
| M9 | `DisplayRepresentation` cannot be persisted (type-level) | JVM compile-time + test |
| M10 | No `SecretValue` plaintext appears in any `toString()` output | JVM test over all types |
| M11 | `TransferRepresentation` always has a `secretPolicy` | JVM invariant test |
| M12 | Normalisation is idempotent: `normalize(normalize(x)) == normalize(x)` | JVM property test |
| M13 | `Provenance` equality holds for equal captures regardless of event arrival order | JVM property test |
| M14 | An `UNRESOLVED` transaction is never returned in a per-tab query | DAO test |
