# TAHO_BROWSER_IPC_CONTRACT.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** All IPC *inside* Taho Browser. The Browser→Taho application boundary is specified
separately in `TAHO_BROWSER_TAHO_INTEGRATION_CONTRACT.md` and is deliberately **not** mixed with this
contract.

Two independent IPC systems exist and must never be conflated:

| System | Path | Carries |
|---|---|---|
| **A — Observation IPC** | WebExtension ⇄ native messaging | capture events |
| **B — Transfer IPC** | explicit Intent / content URI → Taho | the handoff envelope |

System A is specified here. System B is in the integration contract.

---

## 1. Design rules

1. **Every inbound message is untrusted input.** Web content is adversarial. The extension process is
   *not* a trust boundary — a compromised page can influence what a content script sends. Validate
   shape, type, length, and enum membership on every field, natively, every time.
2. **Bound before you parse.** Reject on byte length *before* JSON parsing. A 200 MB "message" must
   never reach an allocator.
3. **Identity comes from the delivery context, never from the payload.** `sender.session` is trusted
   because GeckoView provides it. A `tabId` inside a message body is *data*, not identity.
4. **Two lanes, deliberately.** Bulk events and identity handshakes have different trust and latency
   profiles and use different channels.
5. **Silent, bounded, attributed, versioned.** Every event carries a schema version, an event ID, a
   monotonic sequence number, and an attribution verdict.

---

## 2. Channels

### Lane 1 — Bulk event port (extension-level)

Extension side: background script, opened once at startup.

```js
const port = browser.runtime.connectNative("taho.capture");
port.onMessage.addListener(handleCommand);
port.postMessage(envelope);          // events flow extension → app
```

App side:

```java
// registered on the WebExtension, NOT on a session
extension.setMessageDelegate(messageDelegate, "taho.capture");

// MessageDelegate#onConnect(WebExtension.Port port)
WebExtension.PortDelegate pd = new WebExtension.PortDelegate() {
  @Override public void onPortMessage(Object message, WebExtension.Port port) { ingress.accept(message, port); }
  @Override public void onDisconnect(WebExtension.Port port) { ports.unregister(port); }
};
port.setDelegate(pd);
```

**Properties:** one long-lived port; ordered delivery; `onDisconnect` on teardown. This is the only
channel permitted to carry high-frequency capture events.

**Known risk:** a single port is a single point of failure. `PortRegistry` must support
reconnection: the extension re-opens on `onDisconnect`, and the app rebinds. SPIKE-12 verifies this.

### Lane 2 — Session-scoped one-off messages (identity)

Per `GeckoSession`, registered when the tab's session is created:

```java
session.getWebExtensionController()
       .setMessageDelegate(extension, sessionMessageDelegate, "taho.identity");
```

Extension side (content script):

```js
browser.runtime.sendNativeMessage("taho.identity", msg);   // arrives session-scoped
```

**This is the only channel that yields a trustworthy `GeckoSession`.** It carries the attribution
handshake (§4) and nothing else. It is low-volume by design — a handful of messages per navigation.

**Attack-surface note (from Mozilla's own guidance):** `nativeMessagingFromContent` grants content
scripts native messaging, which Mozilla explicitly flags as *"unnecessary API exposure in content
scripts (running in web content processes)"*. Taho accepts this exposure because attribution
correctness (PRD P0) outranks it, and mitigates by:
- using a **separate native app id** (`taho.identity`) from the bulk lane, so a compromised identity
  path cannot reach the bulk command channel;
- validating the origin of every identity message against the session's own committed origin;
- treating identity messages as *hints that must be corroborated*, never as authorisation.

---

## 3. Envelope

All messages in both lanes share one envelope. `org.json` is what GeckoView actually delivers
(`JSONObject`), so the wire form is JSON, not a binary format.

```jsonc
{
  "v": 1,                       // envelope schema version (int, required)
  "type": "TX_EVENT",           // enum, required
  "id": "01J8Z…",               // event UUIDv7 — sortable by creation time
  "seq": 10427,                 // monotonic per port connection, int64
  "ts": 1758912345678,          // sender wall-clock ms (advisory only — see §7)
  "conn": "c-3",                // port connection id
  "extTabId": 41,               // webRequest details.tabId; -1 = not tab-associated
  "frameId": 0,
  "docId": "d-9f2…",            // details.documentId when available
  "reqId": "1234.5",            // details.requestId — engine-scoped correlation key
  "payload": { /* type-specific */ }
}
```

### 3.1 Field rules

| Field | Type | Rule |
|---|---|---|
| `v` | int | must be ≤ `SUPPORTED_ENVELOPE_VERSION`. Higher → `ENVELOPE_VERSION_UNSUPPORTED`, drop. Never partially process. |
| `type` | string | must be a known enum member. Unknown → drop + diagnostic. |
| `id` | string | ≤ 64 chars, `[A-Za-z0-9_-]` only. Used for idempotent dedup. |
| `seq` | long | must be **strictly increasing** per `conn`. Gap or regression → connection integrity warning; see §6. |
| `ts` | long | **advisory only.** Never used for ordering. See §7. |
| `conn` | string | bound to a `PortRegistry` entry. Unknown `conn` → drop. |
| `extTabId` | int | `-1` is legal and meaningful (§4.3). |
| `frameId` | int | ≥ 0. |
| `reqId` | string | ≤ 128 chars. Engine-scoped; **not** globally unique; only unique within a runtime lifetime. |
| `payload` | object | size-capped per type (§5.2). |

**Why `ts` is advisory:** device wall-clock is user-settable and drifts. Ordering and expiry must never
depend on it. `seq` provides order; the database assigns its own monotonic clock on write.

---

## 4. Attribution protocol

The P0 requirement. Full rationale in `TAHO_BROWSER_SYSTEM_ARCHITECTURE.md` §5.

### 4.1 The gap

GeckoView exposes **no** mapping from `GeckoSession` to WebExtension tab ID. `webRequest` events carry
`details.tabId`; the app cannot look up the session from it. Meanwhile session-scoped delivery gives a
perfect `GeckoSession` but no tab ID. The join must be constructed explicitly.

### 4.2 The handshake

```
TAB OPEN
  TabManager: create GeckoSession S
  TabManager: registry.registerPending(S)              → PendingRegistration(state=INIT)
  TabManager: S.getWebExtensionController()
                   .setMessageDelegate(ext, identityDelegate, "taho.identity")

CONTENT SCRIPT (top frame, injected at document_idle)
  1) const r = await browser.runtime.sendMessage({ type: "taho:hello" })
       background handler reads sender.tab.id  →  returns { extTabId: sender.tab.id }
  2) browser.runtime.sendNativeMessage("taho.identity", {
       type: "TAB_REGISTER", extTabId: r.extTabId, url: location.href, readyState
     })

APP (session-scoped delegate, so sender.session === S)
  3) validate: type known, extTabId ≥ 0, url parses as http/https
  4) validate: url's origin == S's last committed origin (NavigationDelegate state)
       mismatch → registration REJECTED, diagnostic, no registry entry
  5) registry.bind(extTabId, S, tahoTabId)              → Bound
  6) acknowledge { ok: true, tahoTabId }  (content script may stop retrying)
```

### 4.3 Resolving an event

```
resolve(extTabId) →
  Bound(extTabId)          → Attribution.KNOWN(session, tahoTabId)
  extTabId == -1           → Attribution.UNATTRIBUTED            (never guessed)
  registry miss            → Attribution.UNRESOLVED(extTabId)    (never guessed)
  session since closed     → Attribution.DETACHED(sessionId)     (retained, not reassigned)
```

**Four absolute prohibitions:**

1. Never fall back to the currently selected tab. *(PRD §10, §53.3)*
2. Never fall back to the most-recently-active tab.
3. Never create a registry entry from a `webRequest` event alone.
4. Never mutate an existing binding — a tab ID is bound once per session; a rebind is a new session.

`UNRESOLVED` events are still captured and persisted, carrying their `extTabId`, but are excluded
from per-tab views and counted in the `LIMITED` indicator. The user is told, per PRD §95 and §14.

### 4.4 Same-document, cross-frame

Sub-frame requests carry `frameId != 0` and the same `docId`/`tabId`. They resolve through the same
registry entry. A cross-origin iframe's `TAB_REGISTER` is **not** honoured — only the top frame
(`frameId == 0`) registers, and only against its own origin. This prevents a hostile iframe from
claiming a tab binding.

### 4.5 Navigation and rebinding

Same-tab navigation keeps the same `GeckoSession`, hence the same `extTabId` binding — the content
script re-announces with the same `extTabId` and an updated URL, and the app updates the *origin*
context without changing the binding. `PendingRegistration` with a timeout (30 s) guards against a tab
that never loads a content-scriptable page (`about:`, PDF viewer, some app schemes): the tab is marked
`UNOBSERVABLE`, and the UI says capture is unavailable **for that tab**, rather than silently
attributing elsewhere.

---

## 5. Message catalogue

### 5.1 Types

| `type` | Lane | Payload cap | Meaning |
|---|---|---|---|
| `TAB_REGISTER` | identity | 4 KB | handshake (§4.2) |
| `TAB_TEARDOWN` | identity | 1 KB | content script observed unload |
| `TX_START` | bulk | 32 KB | `onBeforeRequest` — url, method, type, tabId, frameId, timeStamp |
| `TX_REQ_HEADERS` | bulk | 32 KB | `onBeforeSendHeaders` / `onSendHeaders` |
| `TX_REQ_BODY` | bulk | 256 KB | request body, chunked, never whole unless under cap |
| `TX_RESP_START` | bulk | 16 KB | `onResponseStarted` — status, headers |
| `TX_RESP_BODY` | bulk | 256 KB per chunk | only if response capture enabled (§ SPIKE-03) |
| `TX_REDIRECT` | bulk | 16 KB | `onBeforeRedirect` — status + Location |
| `TX_COMPLETE` | bulk | 8 KB | `onCompleted` — timing |
| `TX_ERROR` | bulk | 8 KB | `onErrorOccurred` |
| `WS_HANDSHAKE` | bulk | 16 KB | 101 upgrade, treated as an HTTP transaction |
| `WS_FRAME` | page | 32 KB | page-level only, explicitly labelled |
| `SSE_OPEN` | page | 8 KB | page-level only |
| `CONSOLE` | page | 8 KB | page-level only |
| `DOM_SIGNAL` | page | 16 KB | page-level only (SPA route changes, GraphQL detection hints) |
| `HEARTBEAT` | bulk | 1 KB | liveness + counters |
| `DIAG` | bulk | 8 KB | extension-side structured diagnostic |

Payload caps are **per message**, enforced natively. Total port throughput is separately bounded
(§5.3). A message exceeding its cap is rejected whole and counted as `droppedOversize` — never
partially accepted, because a half-parsed body is a corrupted body.

### 5.2 Chunking

Bodies larger than the per-message cap are sent as a sequence:

```jsonc
{ "type":"TX_REQ_BODY", "reqId":"1234.5", "chunkIndex":0, "chunkCount":4,
  "isFinal":false, "bytes": 262144, "data":"<base64 or utf8>" }
```

Reassembly is bounded by a **per-transaction ceiling** (`MAX_BODY_BYTES`, default 8 MiB) as well as
the per-message cap. A transaction that would exceed the ceiling transitions to `TRUNCATED` and stops
accepting chunks. Both numbers are required: a per-message cap alone does not stop a 2 GB body.

### 5.3 Throughput budget

| Bound | Default | Behaviour on breach |
|---|---|---|
| Per-message bytes | per type (§5.1) | reject, `droppedOversize` |
| Per-transaction body bytes | 8 MiB | → `TRUNCATED` |
| Messages in flight (unparsed queue) | 512 | apply §5.4 priority |
| Parsed queue depth | 2 048 | apply §5.4 priority |
| Port read rate | 4 000 msg/s | shed lowest priority first |

---

## 6. Ordering, duplicates, and integrity

**Guarantees provided:**

- **Per-connection FIFO.** One port, ordered delivery.
- **Per-connection monotonic `seq`.** Strictly increasing. A repeat or regression means the extension
  or the transport misbehaved.
- **Idempotent application.** Every event carries `id`. The repository upserts on `id`, so a
  redelivery after reconnect is harmless.
- **Correlation, not global identity.** `reqId` is engine-scoped and reused across runtime
  lifetimes. A `TX_*` event whose `reqId` is unknown to the coordinator starts a *new provisional
  transaction* rather than being dropped — because a missed `TX_START` (port connected mid-flight) is
  expected, not exceptional.

**Guarantees deliberately NOT provided:**

- Global ordering across connections.
- Ordering by `ts`.
- Exactly-once delivery. It is at-least-once + idempotent apply. Simpler and honest.

**Integrity checks on `seq`:**

| Observation | Response |
|---|---|
| `seq == last + 1` | normal |
| `seq > last + 1` | gap → increment `seqGaps`, set capture state `LIMITED` if gaps persist |
| `seq <= last` | duplicate or regression → drop, increment `seqRegressions` |
| new `conn` | reset `last`; require `HELLO` first |

**`HELLO` handshake (both directions).** Before any event, the extension sends:

```jsonc
{ "v":1, "type":"HELLO", "id":"…", "seq":0, "conn":"c-3",
  "extVersion":"1.0.0", "envelopeVersion":1,
  "counters":{ "sent":0, "droppedByExtension":0 } }
```

The app replies with its own `HELLO` carrying `acceptedEnvelopes:[1]` and `captureMode`. The app
rejects the whole connection if `envelopeVersion` is unsupported. This makes a version mismatch a
clean, early, diagnosable failure instead of a stream of dropped events.

---

## 7. Clock skew

`ts` from the extension is the device clock — user-settable, NTP-driftable, and identical across both
sides anyway (same device), so it is *not* a skew problem between app and extension. It is a problem
for **provenance honesty**: a user with a wrong clock produces a request whose "captured at" is wrong.

Handling:

- The **database** assigns `receivedAt` on write (monotonic + wall clock at write time). This is the
  authoritative ordering key.
- `ts` is retained as `originReportedAt` and shown to the user **only** if it diverges from
  `receivedAt` by more than 60 s, in which case the inspector shows a "device clock" note.
- Transfer envelopes carry `receivedAt` as the canonical timestamp, never the raw `ts`.
- Expiry of temporary transfer artifacts uses elapsed-time bookkeeping, never comparing to a
  user-settable clock.

---

## 8. Backpressure

Real backpressure, not post-hoc trimming (PRD §12, §95).

**Producer side (extension).** Before `postMessage`, the extension checks its own queue:

| Depth | Action |
|---|---|
| < 50 % | send |
| ≥ 50 % | send only priority ≤ MEDIUM; **coalesce** `HEARTBEAT`/`CONSOLE`; batch `TX_*` metadata into arrays of ≤ 20 |
| ≥ 85 % | send only priority HIGH (`AUTHENTICATION`, `PRIMARY_API`, `BUSINESS_API`, `WS_HANDSHAKE`); drop STATIC/ANALYTICS/TELEMETRY and count them |
| ≥ 98 % | drop everything except `TX_ERROR`/`TX_COMPLETE` for already-open transactions; set `degraded=true` |

Degradation is **reported, not silent** (PRD §95). The extension increments
`droppedByExtension` per class and reports it in `HEARTBEAT`; the app surfaces
`△ Capture limited — some low-priority resources were dropped` with counts. The user is never told
capture is healthy while data is being discarded.

**Consumer side (app).** Ingress does O(1) work only: length check → envelope parse → stamp →
priority compute → offer to `BoundedEventQueue`. If the queue rejects, the drop is counted by class
and the `LIMITED` state is set. Ingress never blocks on Room, Keystore, or the UI.

**Priority lanes** (PRD §12, refined):

| Priority | Classes |
|---|---|
| P0 CRITICAL | `AUTHENTICATION` |
| P1 HIGH | `PRIMARY_API`, `BUSINESS_API`, `GRAPHQL`, `WS_HANDSHAKE` |
| P2 MEDIUM | `RESPONSE_METADATA`, `PAGE_NAVIGATION` |
| P3 LOW | `STATIC_RESOURCE` |
| P4 LOWEST | `ANALYTICS`, `TELEMETRY` |

Reservation: P0/P1 have a reserved capacity floor (60 % of the queue) that P2–P4 cannot consume.
Under sustained overload, low-priority classes are dropped first, then medium, and P0/P1 are never
dropped — instead their *bodies* are degraded (metadata retained, body marked `TRUNCATED`). Losing
the fact that an auth request happened is worse than losing its body.

---

## 9. Reconnection

| Event | App behaviour | Extension behaviour |
|---|---|---|
| `onDisconnect` | unregister port; set capture `ERROR`; keep last persisted state; start a reconnect timer (1 s → 2 s → 4 s → 8 s → 30 s cap) | — |
| Extension observes disconnect | — | reopen `connectNative`, send `HELLO` with fresh `conn` id |
| `HELLO` received | rebind, reset `seq`, replay nothing, increment `reconnections` | — |
| In-flight transactions at disconnect | driven to a terminal state by the **staleness sweeper** (§10) — never left dangling | — |
| App process restarted | fresh registry; tabs restored per Lifecycle doc; extension re-registers via content script handshake | — |

**Critical:** a reconnect must not silently re-attribute. Because `extTabId` bindings are
reconstructed by the handshake, a reconnect re-establishes them explicitly rather than trusting stale
state.

---

## 10. Staleness and terminal states

A transaction that stops receiving events (tab closed, port died, page navigated away) must reach a
terminal state. A sweeper runs on the capture pipeline:

| Condition | Terminal state |
|---|---|
| no event for `STALE_AFTER` (default 30 s) and page not loading | `PARTIAL` (headers known, outcome unknown) |
| tab/session closed | `CANCELLED` (with reason) |
| body cap exceeded | `TRUNCATED` |
| `onErrorOccurred` | `FAILED` (with engine error string) |
| explicit abort (user cleared capture) | `CANCELLED` |

`PARTIAL` is a first-class, honest outcome (PRD §13, §55, §138). It is never rendered as `COMPLETED`,
and it is never silently upgraded. PRD §13's requirement that "invalid/out-of-order transitions must
not corrupt persisted state" is enforced by making the state machine transition table total and
rejecting unknown transitions as no-ops with a diagnostic.

---

## 11. Backpressure and ordering summary (guarantees table)

| Property | Guarantee |
|---|---|
| Ordering | FIFO within one port connection; `seq`-verified |
| Duplicates | Idempotent by `id`; safe to redeliver |
| Loss | Bounded and *counted* per class; surfaced as `LIMITED` |
| Isolation | Per-connection `seq`; `conn` binding; no cross-connection assumptions |
| Attribution | Four-valued, never guessed, never defaulted to the visible tab |
| Memory | Hard per-message, per-transaction, and per-queue ceilings |
| Versioning | `HELLO` negotiates envelope versions; incompatible → refuse the connection cleanly |

---

## 12. What this contract does **not** cover

- The **extension ↔ content script** internal protocol. Owned by the extension itself; the app does
  not see it, and must not depend on its shape.
- **Page-level instrumentation** messages (`WS_FRAME`, `SSE_OPEN`, `CONSOLE`, `DOM_SIGNAL`). These
  are JS-observation, not engine-observation, and every downstream consumer must be able to tell the
  difference. The `type` prefix is the discriminator, and the persisted model records an
  `observationSource` provenance field of `ENGINE` or `PAGE`.
- The **Browser → Taho** application boundary.
