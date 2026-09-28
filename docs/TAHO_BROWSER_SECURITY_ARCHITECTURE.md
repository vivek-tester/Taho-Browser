# TAHO_BROWSER_SECURITY_ARCHITECTURE.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** Credential defence inside Taho Browser, the Android security boundary, and the
Browser→Taho transfer as a hostile interface.

**Threat model posture (PRD §96):** the web page is untrusted; the extension is a conduit, not a
trust boundary; a co-installed application is untrusted; the device owner is not an adversary but may
be socially engineered. Defence assumes the *content* side is compromised.

---

## 1. Trust boundaries

```
  ┌─ UNTRUSTED ──────────────────────────────────────────────┐
  │  Web content (arbitrary JS, hostile pages, hostile iframes)│
  └───────────────────────┬───────────────────────────────────┘
                          │ content script (sandboxed world)
  ┌─ SEMI-TRUSTED ────────▼───────────────────────────────────┐
  │  Extension background process                             │
  │  • our code, but reachable by page influence              │
  │  • messages are validated, never authorised                │
  └───────────────────────┬───────────────────────────────────┘
                          │ native messaging (System B IPC)
  ┌─ TRUSTED APP ──────────▼───────────────────────────────────┐
  │  Ingress → SchemaGate → Capture domain                    │
  │  ┌──────────────────────────────────────────────────────┐ │
  │  │ SECRET BOUNDARY — SecretValue may exist ONLY here     │ │
  │  │ and in PersistenceRepresentation (encrypted at rest)  │ │
  │  └──────────────────────────────────────────────────────┘ │
  │  Representations → Display / Export / Transfer / Replay    │
  └───────────────────────┬───────────────────────────────────┘
                          │ explicit Intent (System C IPC)
  ┌─ UNTRUSTED PEER ──────▼───────────────────────────────────┐
  │  Taho API Testing — validates everything; may be old,     │
  │  forged, or malicious. Never trusted because it responded.│
  └───────────────────────────────────────────────────────────┘
```

**Rule:** trust decreases downward. No layer may assume the layer above it behaved correctly. A
compromised page can therefore cause the app to *misreport*, but never to *exfiltrate silently* —
because exfiltration requires either (a) a user action at a confirmation surface, or (b) a
vulnerability in one of the four explicit gates below.

---

## 2. The four gates

No capture value can leave the trusted app without passing all four.

| Gate | Where | What it enforces |
|---|---|---|
| **G1 — Ingress** | `EventIngress` | length-before-parse, schema validation, type checks, enum membership, `conn` binding, `seq` monotonicity |
| **G2 — Secret boundary** | `SecretValue` type | plaintext may exist only in `RawCapture`/`PersistenceRepresentation`; cannot be logged, cannot reach a display type |
| **G3 — User confirmation** | `TransferConfirmationActivity` | explicit user action; itemised included/protected lists; no silent transfer |
| **G4 — Receiver validation** | Taho import boundary | schema, version, size, URL, method, header, body, completeness, secret-policy validation before any object is created |

PRD §97: *"No stage should skip the validation boundary."* These four are the enforcement points.

---

## 3. Credential defence

### 3.1 Lifecycle of a secret

```
 capture ──▶ classify ──▶ [ RawCapture: plaintext, in-memory, bounded lifetime ]
                            │
                            ├──▶ Persistence: AES-256-GCM, Keystore-wrapped key
                            │
                            ├──▶ Display:    masked, irreversible in the view layer
                            ├──▶ Export:     masked unless explicit RevealIntent
                            ├──▶ Transfer:   PARAMETERISE (default) | MASK | EXPLICIT
                            └──▶ Replay:     stripped unless explicitly authorised
```

### 3.2 Masking is not deletion

The single most important rule in the system (PRD §19, §7.7). `Authorization: Bearer ••••••••` in the
UI means *the view is masked*, not *the value is gone*. A bug that treats a masked value as a safe
value to persist, export, or transfer is a data-loss bug; a bug that treats a masked value as
*deleted from memory* is a false-assurance bug. Both are serious; the architecture prevents the second
by making `DisplayRepresentation` a **distinct type** that cannot be passed to a persistence or
transfer API (Data Model §5.1, invariants I1/I2).

### 3.3 The three policies (PRD §23)

| Policy | Representation sent | Executable as-is? | Default |
|---|---|---|---|
| `PARAMETERIZE` | `Authorization: {{AUTH_TOKEN}}` | no | **yes** |
| `MASK` | `Authorization: Bearer ••••••••` | no | offered explicitly |
| `EXPLICIT` | real value | **yes** | requires deliberate, itemised consent |

`EXPLICIT` transfer must display, per PRD §23.3: which secrets, their category, the destination app,
the destination host, and the persistence implication. PRD §34 adds the private-session warning. The
consent UI is not dismissible by a single tap on a secret row; it is a distinct confirmation step with
a count of included secrets.

**Cookies are never `PARAMETERIZE`d by default** (PRD §74). A captured cookie is session-specific,
HttpOnly, and domain/path-scoped; turning it into a durable environment variable would misrepresent
it. Default is `MASK` with an explicit `EXPLICIT` opt-in. This is a deliberate divergence from the
"parameterize by default" rule for `Authorization`, and it is the right call: a cookie is not a token.

### 3.4 In-memory hygiene

| Control | Implementation |
|---|---|
| Bounded plaintext lifetime | `RawCapture` buffers are pooled, zeroed on release; no `String` copies of secret values |
| No secret in `String` | secret values held as `ByteArray` inside `SecretValue`, not as `String`. `String` is immutable and cannot be zeroed |
| No `toString` leakage | `SecretValue.toString()` returns `"SecretValue(category=…, redacted)"`; the plaintext is not reachable from any generated string |
| No logs | `SecretValue` is not accepted by any logger overload; only `SecretRef` (an opaque id) is |
| No crash reports | custom `UncaughtExceptionHandler` scrubs; crash payloads carry ids and sizes only |
| No heap inspection surface | not achievable; acknowledged as residual risk (Risk R-09) |
| Debug builds | diagnostics may record *categories and locations*, never values — including in debug (PRD §98) |
| Clipboard | never used for secrets. Copy actions operate on `ExportRepresentation`, which is masked by default |

### 3.5 Detection is advisory, defence is structural

`SecretDetector` is a heuristic classifier and **will miss things**. It is not a security control on
its own. The structural controls are:

1. `Authorization`, `Cookie`, `Proxy-Authorization` are treated as secret **by header name, always** —
   before any heuristic. No confidence threshold to cross.
2. The transfer confirmation surface enumerates secrets explicitly, so a miss is visible to the user
   before they consent.
3. `PARAMETERIZE` as default means a missed detection still produces a non-executable request — the
   failure mode is a broken request, not a leaked one. **Failing closed is the design.**

---

## 4. Android application security

### 4.1 Component exposure

| Component | `exported` | Justification |
|---|---|---|
| Main browser `Activity` | `false` | internal navigation only |
| `TransferConfirmationActivity` | `false` | internal |
| Deep-link handler (`taho://…`) | `true` **with intent filter** | only if deep links are adopted; must validate the URI (§4.3) |
| `FileProvider` | `false` (grant-scoped) | never a public provider (PRD §24.5) |
| `DownloadReceiver` / `BroadcastReceiver` | `false` | internal |
| Any exported service | **none in MVP** | no AIDL (PRD §24.4) |
| `android:allowBackup` | `false` | capture + browser data must not enter cloud backup |
| `android:usesCleartextTraffic` | `false` | no plaintext HTTP from app code |

**No blanket `android:permission` on the main activity** — it is not needed, and adding one creates
false confidence. Access control belongs at the data layer.

### 4.2 Incoming intent validation (Taho side, but specified here as the contract)

Per PRD §64, the receiving side must validate in this order, and must **create no internal object
until all steps pass**:

```
 1  action matches exactly the expected constant
 2  calling package == expected (or signature-verified)
 3  payload present and, if a URI, the URI is a content:// from an expected provider authority
 4  URI permission grant actually held (check grant, do not assume)
 5  envelope byte length ≤ limit (before parse)
 6  JSON schema structurally valid
 7  schema id == expected ("taho.request-transfer")
 8  version within [minSupported, current]
 9  request body declared size == actual size (PRD §119)
10  URL scheme is http/https; host syntax; port; path length
11  method is a known token
12  header names RFC-valid; no CR/LF/control chars; no forbidden transport headers; length caps
13  body content-type consistent with declared encoding
14  completeness metadata present and self-consistent
15  secret policy is one of the three known values
16  only now: construct ApiRequest
```

Steps 9 and 12 are the request-smuggling defences named in PRD §118. Step 4 is the one most often
skipped: a `content://` URI without a *held* grant must fail, not throw.

### 4.3 Deep links and custom schemes

If `taho://` deep links are adopted (not required by the PRD), they are an **unauthenticated
injection surface** and must:
- carry no request payload,
- never construct or execute a request,
- only deep-link into a *display* surface,
- be validated for scheme, host, and parameter bounds.

Recommendation: **do not adopt deep links in MVP.** The transfer path is an explicit Intent, which is
already authenticated by package targeting. Adding an unauthenticated deep link re-opens the boundary
for no MVP benefit (PRD §63/§24.1).

### 4.4 External navigation

`mailto:`, `tel:`, `intent://`, custom schemes (PRD §89) are requested through
`ContentDelegate.onExternalResponse`. Handling:
- show a confirmation naming the target app,
- **never auto-launch**,
- the current session and its capture context remain intact (Gecko keeps the page alive),
- `intent://` URLs are parsed defensively: component, package, and extras are untrusted; refuse
  anything with a `javascript:`, `file:`, or unexpected-scheme payload.

### 4.5 Downloads

- Filenames are sanitised — no path traversal, no leading `/`, control characters stripped.
- MIME type is determined from content, not just the server header.
- Downloaded files land in app-private storage first; the user chooses to export/share.
- A download's filename and URL are treated as untrusted strings and never logged verbatim.

### 4.6 Screenshots and task switching

- `FLAG_SECURE` is **not** set by default — it would break legitimate use and is a UX regression.
  Instead, capture data surfaces (`TransferConfirmation`, inspector with revealed secrets) are
  excluded from the recents screenshot via `onProvideAssistData`/`setRecentsScreenshotEnabled(false)`
  on those specific activities only. This targets the actual risk (a credential on screen) without
  punishing the whole app.
- Clipboard is cleared by the OS; Taho does not write secrets to it in the first place (§3.4).

### 4.7 Screenshots of web content

Web content screenshots are not a Taho feature. `ContentDelegate` exposes no such API in the
capability matrix, so there is nothing to secure.

---

## 5. WebExtension hardening

The extension is semi-trusted. Its manifest is the attack surface.

### 5.1 Manifest posture

```jsonc
{
  "manifest_version": 2,              // see §5.3
  "permissions": [
    "webRequest",
    "webRequestBlocking",
    "geckoViewAddons",
    "nativeMessaging"
    // "nativeMessagingFromContent" — REQUIRED for attribution, see §5.2
  ],
  "host_permissions": ["<all_urls>"],
  "background": { "scripts": ["bg.js"], "persistent": true }
}
```

- **`<all_urls>`** is unavoidable: the product observes arbitrary browsing. This is disclosed in the
  app's permission rationale and store listing. There is no narrower honest alternative.
- `webRequestBlocking` is needed only for `filterResponseData`. If response-body capture is
  postponed (SPIKE-03 failure), **remove it** — a materially smaller permission surface.
- `nativeMessagingFromContent` grants content scripts native messaging. Mozilla explicitly flags this
  as *"unnecessary API exposure in content scripts"*. It is required for the P0 attribution
  handshake, and it is accepted with mitigations (§5.2). If attribution is redesigned to avoid it,
  remove it.

### 5.2 Mitigating `nativeMessagingFromContent`

| Mitigation | Effect |
|---|---|
| Separate native app id for the identity lane (`taho.identity`) vs bulk lane (`taho.capture`) | a compromised content script cannot reach bulk commands |
| Identity messages are validated against the session's own committed origin | a hostile iframe cannot claim a foreign tab binding |
| Only the **top frame** (`frameId == 0`) may register | sub-frame hijacking prevented |
| Identity messages carry no secret material | a spoofed identity message leaks nothing |
| Identity lane has a hard message cap (4 KB) and rate limit | flooding cannot starve the bulk lane |
| Registration is idempotent and rate-limited per session | re-registration storms are rejected |

### 5.3 Manifest V2 vs V3

Mozilla docs note that **MV3 additionally requires** the `webRequestFilterResponse` permission for
`filterResponseData`. MV2 is simpler for a built-in privileged extension and is what Mozilla's own
GeckoView examples ship (`manifest_version: 2` in the official guide). **Decision: ship MV2**, pinned,
and treat the MV3 migration as a scheduled compatibility item to be validated in SPIKE-01 with a
recorded engine version. This is a decision with a shelf life and must be revisited on every engine
bump (PRD §130).

### 5.4 Extension integrity

`ensureBuiltIn` installs from the APK. The extension is therefore as trustworthy as the signed APK —
the same trust level as the app's own code. The residual risk is a *page* influencing the extension,
mitigated above. There is no separate extension-signing concern for a built-in extension.

---

## 6. Logging policy (PRD §98)

### 6.1 Allowed

```
capture session created / closed
tabId, transactionId, captureSessionId, extTabId, frameId, docId, reqId
event type, schema/envelope version
byte counts, chunk indices, truncation flags
queue depth, dropped-by-class counters, seq gaps
relevance category (not values)
secret CATEGORY and LOCATION (never evidence containing a value)
transfer result codes, timings
```

### 6.2 Forbidden

```
Authorization / Proxy-Authorization values
Cookie / Set-Cookie values
API keys, tokens, JWTs, passwords, client secrets
request or response body content
URLs with credentials in userinfo
query-string values for keys classified as secret
```

### 6.3 Mechanism

Logging is allow-list based, not deny-list based. The logger exposes typed methods
(`logTransactionState`, `logQueueMetrics`, `logTransferResult`), and there is deliberately **no
generic `log(tag, message: String)`** reachable from any module holding capture data. A
`String`-based sink exists only in `:capture:domain` for pure-domain diagnostics, where no secret
type is reachable.

A CI check greps for `Log.` calls in modules that can touch capture data and fails the build on any
call whose arguments include a body, header map, or `SecretValue`. This is a mechanical gate, not a
review convention.

---

## 7. Analytics boundary (PRD §99)

No analytics in the Browser→Taho workflow. If product telemetry is later added, it is
**aggregate-event-only** and opt-in:

| Recorded | Never recorded |
|---|---|
| `transfer_started` / `_succeeded` / `_failed` | URLs, hosts, paths |
| `capture_to_inspector_rate` | headers, query values |
| `transfer_success_rate` | bodies of any kind |
| `large_payload_fallback_rate` | credentials, secret categories tied to a host |
| `secret_parameterization_rate` (a count) | any value |

`secret_parameterization_rate` is deliberately a **count with no host or category dimension** —
otherwise a metric becomes a side channel for "which site had a credential".

---

## 8. Transfer as a security boundary

Covered in full in `TAHO_BROWSER_TAHO_INTEGRATION_CONTRACT.md` §6. Security-relevant summary:

| Control | Requirement |
|---|---|
| Direction | Explicit Intent to the **known package** (`setPackage`), never a chooser (PRD §24.1) |
| Payload cap | Configured budget (default 256 KB), not a theoretical Binder limit (PRD §44) |
| Large payload | App-private file, Keystore-encrypted, `FileProvider` content URI, **one-time** grant to the single target package |
| Cleanup | Deleted on success **and** on a bounded sweeper for abandoned transfers (PRD §65) |
| Execution | Taho never auto-executes; import ≠ execute (PRD §123) |
| Destination change | Replay to a different origin requires an explicit high-risk warning (PRD §35) |
| Receipt | Transfer ID echoed; failure never deletes the source (PRD §66) |
| AIDL | Not in MVP (PRD §24.4) |
| Public provider | Not in MVP (PRD §24.5) |

---

## 9. Web-content defence

The browser renders hostile content by definition. Measures that are browser responsibilities, not
Taho's, but which must be configured deliberately:

| Setting | Value | Reason |
|---|---|---|
| `GeckoSessionSettings.ALLOW_JAVASCRIPT` | default (on) | disabling breaks the web |
| Safe browsing | enabled | not overridden |
| TLS version floor | Gecko default; **not** lowered | a debugging browser is exactly the wrong place to weaken TLS |
| Mixed content | Gecko default (block) | not weakened for capture convenience |
| `Fission` / process isolation | Gecko default | not lowered to save memory |
| Remote debugging | **off in release builds** | a debugging browser must not ship a debugger |

**Deliberately not done:** no request interception/rewriting beyond what capture requires, no header
injection, no certificate error bypass, no `about:config` exposure to users. A browser that can MITM
itself for debugging is a browser that can be MITM'd. Deep analysis belongs in Taho, which is a
testing tool (PRD §122).

---

## 10. Security release gates

Mapped to PRD §143. Each is a **mechanical** check where possible.

| Gate | Mechanical check |
|---|---|
| **B — no raw-secret logging path** | CI grep gate (§6.3) + a test that runs a full capture→inspect→transfer cycle with a seeded `Authorization`/`Cookie` and asserts the secret string appears in no log buffer, no crash file, no `ContentObserver` output |
| **G2 — secret type containment** | compile-time: `DisplayRepresentation` is not assignable to any persistence/transfer parameter; test I9/I10 |
| **G4 — receiver validation** | fuzz suite over the import validator; 10 000 malformed payloads, no crash, no partial object |
| **E — execution safety** | integration test asserting no network call occurs during import |
| **F — large payload** | test asserting a 5 MB body never lands in Intent extras |
| **A — attribution** | SPIKE-02 multi-tab suite: zero cross-tab attribution across 1 000 events |
| **H — capability honesty** | every product string reviewed against the capability matrix; CI diff between shipped strings and an allow-list |
