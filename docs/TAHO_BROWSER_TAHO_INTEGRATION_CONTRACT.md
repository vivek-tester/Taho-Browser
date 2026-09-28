# TAHO_BROWSER_TAHO_INTEGRATION_CONTRACT.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** The versioned interchange contract between Taho Browser (sender) and Taho API Testing
(receiver). This is an **independent contract** — it is not an internal serialisation of either app's
model (PRD §25, §41).

---

## 1. Contract principles

| Principle | Consequence |
|---|---|
| Independent of both internals | The envelope is not a mirror of Taho's `ApiRequest` or Gecko's `webRequest.details`. A change to either may not change the envelope |
| Versioned, always | `schema` + `version` + `minimumReaderVersion` on every envelope. Unknown → safe rejection |
| Additive-compatible | New optional fields are backward compatible. Breaking → new major version |
| Explicit | Every transfer is a user action. No automatic, no background, no cloud |
| Honest | Completeness travels with the data. A partial body is labelled partial |
| Non-executable | Import ≠ execute. Guaranteed by contract and by the receiver |
| Local | On-device Intent or content URI. Never a network call |

---

## 2. Contract repository

Per PRD §41, the contract is a standalone artefact both apps consume:

```
taho-request-transfer/
├── schema/
│   └── request-transfer-v1.json          # JSON Schema, the normative definition
├── docs/
│   ├── protocol.md                        # this document's normative content
│   ├── security.md                        # threat model of the transfer itself
│   └── compatibility.md                   # version matrix + deprecation policy
├── fixtures/
│   ├── simple-get.json
│   ├── json-post.json
│   ├── multipart.json
│   ├── secret-bearing.json
│   ├── truncated-body.json
│   ├── large-payload.json
│   ├── graphql.json
│   ├── redirect.json
│   ├── binary.json
│   ├── malformed.json
│   └── unsupported-schema.json
└── CHANGELOG.md
```

Both apps run the **same fixture suite** as a contract test (PRD §128). The Browser encodes fixtures;
the Browser's CI decodes them. Taho's CI does the reverse. A fixture that only one side can handle is
a contract defect, not a side defect.

---

## 3. Envelope

```jsonc
{
  "schema": "taho.request-transfer",
  "version": 1,
  "minimumReaderVersion": 1,

  "transferId": "01J8ZQ4M2K7X9V3B8N0P4R6T8Y",   // ULID, dedup key
  "issuedAt": 1758912345678,                    // epoch millis, DB-assigned receivedAt

  "source": {
    "product": "taho-browser",
    "appVersion": "1.0.0",
    "engine": "geckoview-153.0",               // provenance, not capability-bearing
    "captureSessionId": "01J8ZQ…",
    "tabId": "01J8ZQ…",
    "observation": "ENGINE"                    // ENGINE | PAGE  (PRD §131 honesty)
  },

  "request": {
    "id": "01J8ZQ…",                            // transactionId
    "method": "POST",
    "url": "https://api.example.com/v1/orders",
    "query": [ { "name": "expand", "value": "items", "redacted": false } ],
    "headers": [
      { "name": "Content-Type", "value": "application/json", "redacted": false },
      { "name": "Authorization", "value": "{{AUTH_TOKEN}}", "redacted": true,
        "secretCategory": "BEARER_TOKEN", "policy": "PARAMETERIZE" }
    ],
    "body": {
      "representation": "JSON",                 // TEXT|JSON|FORM|MULTIPART|GRAPHQL|BINARY
      "contentType": "application/json",
      "charset": "utf-8",
      "encoding": "UTF8",                       // UTF8 | BASE64 | FILE_URI
      "size": 83912,
      "declaredSize": 83912,
      "truncated": false,
      "completeness": "COMPLETE",
      "content": "{\"item\":\"123\"}",          // or "uri" when encoding = FILE_URI
      "parts": []                               // MULTIPART only
    }
  },

  "capture": {
    "timestamp": 1758912345678,
    "durationMs": 96,
    "status": 201,
    "statusText": "Created",
    "initiator": "SCRIPT",
    "timing": { "ttfbMs": 71, "totalMs": 96 },
    "completeness": {
      "requestUrl": "COMPLETE",
      "requestHeaders": "COMPLETE",
      "requestBody": "COMPLETE",
      "responseHeaders": "COMPLETE",
      "responseBody": "UNAVAILABLE",
      "timing": "COMPLETE",
      "tlsInfo": "UNAVAILABLE"
    }
  },

  "security": {
    "secretPolicy": "PARAMETERIZE",
    "containsSensitiveData": true,
    "secretCount": 1,
    "fromPrivateSession": false,
    "findings": [
      { "location": "HEADER:authorization", "category": "BEARER_TOKEN", "policy": "PARAMETERIZE" }
    ]
  },

  "provenance": {
    "normalizerVersion": "norm/1.0.0",
    "originalUrl": "https://api.example.com/v1/orders",
    "originalMethod": "POST",
    "capturedAt": 1758912345678,
    "redirectCount": 0
  }
}
```

### 3.1 Rules

| Rule | Detail |
|---|---|
| `schema` | exact string match. Anything else → `TAHO_TRANSFER_INVALID_SCHEMA` |
| `version` | integer major. `> MINOR current` → `TAHO_TRANSFER_UNSUPPORTED_VERSION` |
| `minimumReaderVersion` | lets a sender declare the oldest reader it knows works |
| `transferId` | ULID, the dedup key (PRD §68) |
| `issuedAt` | **DB-assigned** `receivedAt`, never the raw engine timestamp (IPC §7) |
| `source.observation` | distinguishes engine observation from page-level observation. A `PAGE` capture must not be presented as engine truth |
| `redacted: true` | the value shown is not the captured value. **Required** for any `policy: PARAMETERIZE`/`MASK` entry |
| `body.encoding = FILE_URI` | large bodies; `content` holds a `content://` URI instead, granted one-time |
| `completeness` | mandatory, every field, no omissions. A missing field is a validation failure |
| `declaredSize` vs `size` | the engine's claim vs what we actually hold. Both travel so the receiver can detect a mismatch (PRD §119) |
| unknown fields | **ignored, never reinterpreted** (PRD §42) |

---

## 4. Versioning and compatibility (PRD §42)

| Sender | Receiver | Behaviour |
|---|---|---|
| Same supported schema | Supported | Import |
| Older sender | Newer receiver | Import if `version >= minimumReaderVersion` |
| Newer sender | Older receiver | Graceful rejection → `TAHO_TRANSFER_UNSUPPORTED_VERSION` |
| Unknown `schema` | Any | Safe rejection → `TAHO_TRANSFER_INVALID_SCHEMA` |

Policy:

1. **Additive optional fields** do not bump the major version. A v1 reader encountering an unknown
   optional field ignores it.
2. **Any removal, rename, or semantic change** bumps the major version. `v2` and `v1` coexist;
   `taho.workspace-transfer` (PRD §145) is designed to coexist without breaking `v1`.
3. **Enums are append-only.** An unknown enum member in a *new optional* field is ignored. An
   unknown enum member in a *required* field is a validation failure — never a default.
4. The receiver **never** guesses. Missing required → reject with a precise error, not a default.
5. `CHANGELOG.md` is mandatory for every version bump, with a compatibility matrix and a
   deprecation window of at least one minor release.

---

## 5. Body representations

| Representation | Contract shape | Receiver obligation |
|---|---|---|
| `TEXT` | `content` as UTF-8 string | store as text |
| `JSON` | `content` as UTF-8; receiver may pretty-print but must retain raw | keep raw; pretty-print is a view |
| `FORM` | `content` as `application/x-www-form-urlencoded` | parse into fields |
| `GRAPHQL` | `content` JSON with `query`/`variables`/`operationName` preserved | import as a GraphQL-capable request, not flattened text (PRD §77) |
| `MULTIPART` | `parts[]` each `{name, filename, contentType, value|uri, size, truncated}` | reconstruct; binary parts use `FILE_URI` |
| `BINARY` | `encoding: FILE_URI`, `size`, `contentType` | read via URI; **never** decode as text (PRD §79) |

**Multipart part:**

```jsonc
{ "name": "file", "filename": "a.png", "contentType": "image/png",
  "encoding": "FILE_URI", "uri": "content://…", "size": 184320, "truncated": false }
```

Binary multipart content must never be silently coerced to text (PRD §76). If a binary part cannot
be transferred, the transfer is **refused with a clear reason**, not degraded into corruption.

---

## 6. Transfer mechanism

### 6.1 Primary — explicit Intent

```
Taho Browser
  └─ user taps "Send to Taho"
      └─ TransferConfirmationActivity          ← G3, explicit consent
          └─ IntentSender
              └─ Intent(ACTION_IMPORT_TAHO_REQUEST)
                     .setPackage(TAHO_PACKAGE)   ← explicit, no chooser
                     .putExtra(EXTRA_TRANSFER_VERSION, 1)
                     .putExtra(EXTRA_TRANSFER_ID, transferId)
                     .putExtra(EXTRA_PAYLOAD, jsonString)
                  ▼
              Taho import entry Activity        ← G4, validates all 16 steps
```

Constants are product-level contract names (PRD §63). **The concrete package name and action string
must be confirmed against the Taho API Testing codebase** (PRD §50, open question 6) before
implementation. This is recorded as a blocking discovery task, not an assumption.

### 6.2 Budget and automatic fallback

| Route | Condition |
|---|---|
| Direct Intent | envelope ≤ `INTENT_BUDGET_BYTES` (default 256 KB — a *configured* budget, not a theoretical Binder limit; PRD §44) |
| File-backed | envelope > budget, **or** body encoding is `FILE_URI` |

Selection is automatic and invisible to the user (PRD §24.2, §65).

### 6.3 Large payload

```
 1  TransferCoordinator computes the encoded envelope size
 2  size > budget → TempArtifactStore writes an AES-256-GCM encrypted file
      to app-private storage, key wrapped by the Keystore
 3  FileProvider issues a content:// URI
 4  Intent carries EXTRA_CONTENT_URI, with a one-time read grant to TAHO_PACKAGE only
 5  Taho reads, validates, imports
 6  Browser records the receipt
 7  Artifact deleted immediately on receipt
 8  ArtifactSweeper deletes any abandoned artifact after 1 hour   (PRD §65)
```

The grant is narrow: read-only, single package, revocable via `releasePersistableUriPermission`
equivalents, and short-lived. A general public `ContentProvider` is explicitly **not** used
(PRD §24.5). No AIDL in MVP (PRD §24.4).

---

## 7. Secret policy (PRD §23, §73, §74)

| Policy | Envelope result | Executable? |
|---|---|---|
| `PARAMETERIZE` (default) | `{{AUTH_TOKEN}}`, `redacted: true` | no |
| `MASK` | `Bearer ••••••••`, `redacted: true` | no |
| `EXPLICIT` | real value, `redacted: false`, `policy: EXPLICIT` | yes |

`EXPLICIT` requires a **distinct consent step** listing each secret, its category, the destination
package, the destination host, and the persistence implication (PRD §23.3). It is never reachable by
ticking a parent row (PRD §112).

**Cookies** default to `MASK`, not `PARAMETERIZE` — a captured cookie is session-specific, HttpOnly,
and domain/path-scoped, and must not silently become a durable environment variable (PRD §74).

**Private sessions** add an explicit warning that the request is leaving a private context and may
persist in Taho (PRD §34).

### 7.1 Fails closed

If `SecretDetector` misses a credential, the default `PARAMETERIZE` still produces a non-executable
request. The failure mode is a request that needs a token supplied — **not** a leak. This is the
single most important security property of the transfer design, and it is why detection is allowed
to be imperfect.

---

## 8. Normalisation on the sender

`RequestNormalizer` runs before envelope construction (PRD §21). It is versioned, pure, idempotent,
and its version is recorded in `provenance.normalizerVersion`.

**Removed** (transport-generated): `Host`, `Content-Length`, `Connection`, `Keep-Alive`,
`Proxy-Connection`, `TE`, `Trailer`, `Transfer-Encoding`, `Upgrade`, `Sec-Fetch-*`, `Sec-CH-UA*`.

**Retained** (application-meaningful): `Content-Type`, `Accept`, `Accept-Language`, `Authorization`
(subject to policy), `X-API-Key`, `X-CSRF-Token`, `X-XSRF-TOKEN`, `X-Request-ID`, and **any
unrecognised header**.

**Deny-list, never allow-list.** An allow-list would silently delete application headers the app has
never seen — precisely the failure PRD §75 warns about.

---

## 9. Transfer lifecycle

```
NOT_STARTED → PREPARING → AWAITING_CONFIRMATION → TRANSFERRING
                                                    │
                        ┌───────────────────────────┼──────────────────────┐
                        ▼                           ▼                      ▼
                    RECEIVED                    FAILED                 CANCELLED
                        │                           │                      │
                        ▼                           ▼                      ▼
                 artifact deleted          source retained         source retained
```

| State | Meaning |
|---|---|
| `NOT_STARTED` | nothing in flight |
| `PREPARING` | normalising, applying policy, serialising (off the UI thread, PRD §43) |
| `AWAITING_CONFIRMATION` | consent surface shown |
| `TRANSFERRING` | Intent dispatched / URI granted |
| `RECEIVED` | receipt returned with a terminal result |
| `FAILED` | error code from the taxonomy |
| `CANCELLED` | user cancelled, or the flow was superseded |
| `EXPIRED` | artifact TTL elapsed before receipt |

**Invariants:**

- The source capture is **never** deleted because a transfer failed (PRD §66).
- `EXPIRED` never implies the receiver got the data.
- Duplicate dispatch of the same `transferId` is idempotent on the receiver (PRD §68).

---

## 10. Receipt (PRD §67)

```jsonc
{
  "transferId": "01J8ZQ4M2K7X9V3B8N0P4R6T8Y",
  "result": "IMPORTED",        // IMPORTED | REJECTED | DUPLICATE | UNSUPPORTED | ERROR
  "requestId": "…",            // receiver's id for the created ApiRequest
  "importedAt": 1758912349999,
  "errorCode": null            // populated when result != IMPORTED
}
```

A one-shot Intent is not an RPC channel; the receipt exists for reliable user feedback (PRD §67).
Absence of a receipt is a valid `FAILED`/`EXPIRED` outcome, not a hang.

---

## 11. Error taxonomy (PRD §115)

| Code | Meaning | UI |
|---|---|---|
| `TAHO_TRANSFER_UNSUPPORTED_VERSION` | receiver too old | "Taho needs an update to import this request." |
| `TAHO_TRANSFER_INVALID_SCHEMA` | unrecognised schema | "Taho could not import this request." |
| `TAHO_TRANSFER_TOO_LARGE` | beyond hard limit | "This request is too large to import." |
| `TAHO_TRANSFER_ACCESS_DENIED` | URI grant missing/denied | "Taho could not be given access." |
| `TAHO_TRANSFER_TARGET_UNAVAILABLE` | Taho not installed | install prompt + Export fallback (PRD §45) |
| `TAHO_TRANSFER_URI_EXPIRED` | artifact TTL elapsed | "The transfer expired. Try again." |
| `TAHO_TRANSFER_IMPORT_FAILED` | receiver-side failure | "Taho could not import this request. No network request was sent." |
| `TAHO_TRANSFER_CANCELLED` | user cancelled | silent or brief |

The UI maps codes to human messages. Codes are stable and machine-readable; messages are not part of
the contract.

---

## 12. Duplicate detection (PRD §68)

Dedup key, in order: `transferId` → `(source.product, source.captureSessionId, request.id)`.
**Never URL alone** (PRD §68) — the same URL is legitimately requested many times with different
bodies and auth.

On a duplicate: `This request was already imported. [Open Existing] [Import Again]`. The receiver
retains transfer ids for a bounded window (default 7 days) and prunes them.

---

## 13. Receiver obligations (Taho side)

Defined here because the contract is bilateral, and because a contract that only constrains the
sender is not a contract.

1. Validate all 16 steps (Security §4.2) **before** constructing any internal object.
2. Land the user directly in the request editor — not a generic import page (PRD §69).
3. Mark the request `IMPORTED / UNSAVED` (PRD §28).
4. **Never auto-execute** (PRD §123). Guaranteed by contract and covered by a release gate.
5. Never auto-create a collection, environment, or persistent credential (PRD §28, §120, §121).
6. Preserve provenance and display it as a dismissible banner (PRD §70).
7. Keep the original capture available even after the user edits (PRD §71, §72).
8. Enforce import limits: header count, URL length, body size, query count, multipart part count,
   metadata depth, string length (PRD §116).
9. Reject header names/values containing CR, LF, or control characters (PRD §118) — request-smuggling
   defence.
10. Reject a payload whose declared size disagrees with actual size, or stream it safely (PRD §119).
11. On any validation failure, create nothing and report a precise code.

---

## 14. End-to-end contract test (PRD §129)

```
Taho Browser
  → capture a known fixture request
  → normalise
  → apply secret policy
  → build envelope
  → transfer
  → Taho import
  → ApiRequest
  → compare
```

**Must be equal:** method, URL, query, meaningful headers, body, content type, completeness,
provenance, secret policy.

**Must not be equal:** browser-only transport headers, internal capture IDs, UI-specific state.

---

## 15. Blocked discovery items

These must be resolved against the Taho API Testing codebase before Phase 8. They are PRD §50 open
questions, and none may be guessed:

| # | Item | Blocks |
|---|---|---|
| 1 | Actual application ID / package name of Taho API Testing | `setPackage` target; `ACTION` constant |
| 2 | Whether an import entry `Activity` exists or must be added | receiver design |
| 3 | `ApiRequest` construction path | mapping |
| 4 | Request-editor route | navigation |
| 5 | Existing import/export serializers | reuse vs new |
| 6 | Collection-insertion API | "Save to Collection" |
| 7 | Environment-variable creation API | parameterisation |
| 8 | Existing exported components / deep-link handling | manifest changes |
| 9 | Existing request persistence model | provenance storage |
| 10 | Security-analysis invocation path | post-import "analyse" |
| 11 | Whether a shared serialisation contract already exists | avoid duplication |

The sibling `Taho App/` repository is the place to answer these. **Its architecture is not a
precedent for this project** — only its import surface is a contract consumer.
