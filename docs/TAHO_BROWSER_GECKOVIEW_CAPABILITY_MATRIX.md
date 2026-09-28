# TAHO_BROWSER_GECKOVIEW_CAPABILITY_MATRIX.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Purpose:** Establish, from official sources, what GeckoView actually provides — so the architecture
rests on verified ground rather than on the PRD's assumptions.

---

## 1. How to read this document

| Grade | Meaning |
|---|---|
| **SUPPORTED** | API exists in GeckoView (mozilla-central) and is documented for embedders. Usable now. |
| **PARTIAL** | Exists, but with a documented limitation that constrains product design. |
| **UNSUPPORTED** | No API exists. Must not be designed for. |
| **VERIFY** | Existence plausible but **not confirmed for GeckoView built-in extensions**. Requires a physical-device spike before any dependent work. |

Anything marked **VERIFY** is a load-bearing unknown. The PRD (§9.2, §107, §131) is explicit that
product copy must match verified behaviour, and that where a capability is unavailable the honest
state — `LIMITED` / "unavailable" — is preferable to a false claim. This matrix is the mechanism
that enforces that.

**Sources** are listed per row. `gecko/src` = `mozilla-firefox/firefox@main`.

---

## 2. Runtime and session

| Capability | Grade | Source / evidence |
|---|---|---|
| `GeckoRuntime.create(Context)` — one per process, main thread | **SUPPORTED** | `GeckoRuntime` javadoc; quick-start guide: *"GeckoRuntime can only be initialized once per process"* |
| Recreate / destroy runtime | **UNSUPPORTED** | no such API; singleton for process lifetime |
| `GeckoRuntimeSettings` / `RuntimeSettings` (about:config-style prefs) | **SUPPORTED** | `GeckoRuntimeSettings.java` |
| `GeckoRuntime.getWebExtensionController()` | **SUPPORTED** | official web-extensions guide |
| `GeckoSession.open(runtime)` | **SUPPORTED** | javadoc + quick-start |
| `GeckoSession` is `Parcelable` (`writeToParcel`/`readFromParcel`) | **SUPPORTED** | `GeckoSession.java:3186,3195` |
| `GeckoSessionSettings.Builder.usePrivateMode(boolean)` (init-only) | **SUPPORTED** | `GeckoSessionSettings.java:79` — key `USE_PRIVATE_MODE`, `initOnly=true` |
| `USE_TRACKING_PROTECTION` session setting | **SUPPORTED** | `GeckoSessionSettings.java:293` |
| `GeckoSession.transferFrom(session)` seamless handoff | **PARTIAL** | exists, but transfers an *active* session only; does not survive process death (use Parcel for that) |
| `GeckoView.setSession(session)` — one view per session | **SUPPORTED** | javadoc |
| Multiple concurrent `GeckoView` instances | **SUPPORTED** | standard GeckoView usage (one view per session) |
| `session.isReady()` | **SUPPORTED** | `GeckoSession.java:326` |

**Global vs per-session — the rule:**

| Scope | Owned by |
|---|---|
| Runtime, extension installation, content blocking rules, tracking protection *defaults*, about:config | `GeckoRuntime` (process-global, once) |
| Private mode, user agent, viewport/display mode, JS enable, full a11y tree, tracking protection override, `suspendMediaWhenInactive` | `GeckoSessionSettings` (**must be set before `open()`** — several are init-only) |
| Navigation state, history entries, progress, find-in-page, session restore bundle | `GeckoSession` |
| Delegates (content, navigation, permission, content blocking, download, prompt) | per `GeckoSession` |

**Consequence for the tab model:** private mode and tracking-protection overrides are *construction-time*
properties. A tab cannot be flipped into private mode after creation. The tab model must therefore
create a new `GeckoSession` for a private tab rather than mutate one. See `..._COMPONENT_ARCHITECTURE.md` §2.

---

## 3. Delegates and recovery

| Capability | Grade | Evidence |
|---|---|---|
| `ContentDelegate` — title, preview image, focus, close request, fullscreen, context menu, external response, crash, kill, paint metrics | **SUPPORTED** | `GeckoSession.java:4037+` |
| `ContentDelegate.onCrash(session)` | **SUPPORTED** | `GeckoSession.java` (offset 179 in delegate body) |
| `ContentDelegate.onKill(session)` | **SUPPORTED** | same |
| `NavigationDelegate` | **SUPPORTED** | `GeckoSession.java:4723` |
| `PermissionDelegate` with `ContentPermission` | **SUPPORTED** | `GeckoSession.java:7273` |
| `setContentBlockingDelegate(ContentBlocking.Delegate)` | **SUPPORTED** | `GeckoSession.java:3663` |
| `GeckoSessionSettings.ALLOW_JAVASCRIPT` | **SUPPORTED** | `GeckoSessionSettings.java:335` |
| Content-process crash → automatic session recovery | **UNSUPPORTED** | no API; PRD §90/§91 recovery is **app-implemented** |
| Read out-of-process crash dump / minidump | **UNSUPPORTED** | no API |
| Per-tab OS process isolation control | **PARTIAL** | process management is internal to Gecko; embedder cannot pin or isolate individual tabs |

**Design consequence:** PRD §90/§91 ("Tab unavailable / [Reload] / [View Captured Requests]") must be
built by the app on top of `onCrash`/`onKill`. It is not free. The UI for it is mandatory, not
optional, and capture history must be persisted independently of session liveness — which is exactly
why capture persistence is a separate store from tab state.

---

## 4. WebExtension and native messaging

| Capability | Grade | Evidence |
|---|---|---|
| Install built-in privileged extension from APK assets | **SUPPORTED** | `installBuiltIn(uri)` / `ensureBuiltIn(uri, id)`; uri must be `resource://` |
| Built-in extensions: no signature, native messaging allowed, may use experiments | **SUPPORTED** | official guide + `WebExtensionController` javadoc |
| `resource://android/` maps to APK root; `resource://android/assets/<ext>/` | **SUPPORTED** | official guide |
| Extension persists across app restarts; re-install each start is acceptable | **SUPPORTED** | official guide, verbatim |
| `WebExtension.isBuiltIn` | **SUPPORTED** | `WebExtension.java:58` |
| `runtime.sendNativeMessage` (one-off) | **SUPPORTED** | official guide |
| `runtime.connectNative` → `MessageDelegate#onConnect(WebExtension.Port)` | **SUPPORTED** | official guide, Connection-based messaging section |
| `WebExtension.Port` + `PortDelegate#onPortMessage(Object, Port)` / `#onDisconnect` | **SUPPORTED** | official guide example |
| Native app identifier = the `nativeApp` string in `setMessageDelegate`; no native manifest needed | **SUPPORTED** | official guide, explicit |
| `nativeMessaging` + `geckoViewAddons` permissions required | **SUPPORTED** | official guide |
| Content-script native messaging additionally needs `nativeMessagingFromContent` (undocumented, privileged-only) | **SUPPORTED** | official guide, explicit |
| `sender.session` = originating `GeckoSession`, **content scripts only** | **SUPPORTED** | official guide, verbatim |
| `sender.session == null` for background-script messages | **SUPPORTED** | official guide, verbatim |
| Session-scoped delivery: `session.getWebExtensionController().setMessageDelegate(ext, delegate, app)` | **SUPPORTED** | official guide |
| Messages sent before `setMessageDelegate` are queued and delivered on attach | **SUPPORTED** | official guide |
| Returning `GeckoResult` from `onMessage` to reply asynchronously | **SUPPORTED** | javadoc signature |
| Content scripts | **SUPPORTED** | official guide example ships one |
| `runtime.connectNative` from a **content script** | **PARTIAL** | requires `nativeMessagingFromContent`; official guide advises omitting it unless needed, *"to avoid unnecessary API exposure in content scripts (running in web content processes)"* — i.e. Mozilla treats it as an attack-surface increase |
| Extension survives GeckoRuntime teardown | **SUPPORTED** | official guide: lifetime not tied to runtime |

**Design consequence:** the extension talks to the app over **one long-lived port** for bulk events
(`PortRegistry`), while **per-session one-off messages** are used for the attribution handshake
(§5 of the system architecture). This split is deliberate: bulk traffic must not depend on a
per-session delegate, and identity must never depend on the bulk path.

---

## 5. The attribution gap — highest-priority unknown

| Question | Grade | Why it matters |
|---|---|---|
| Does `GeckoView` expose the WebExtension tab ID for a `GeckoSession`? | **UNSUPPORTED** | Full text search of `WebExtension.java` (3198 lines) finds **no `tabId` field, parameter, or accessor** on `WebExtension`, `SessionController`, or `GeckoSession`. `GeckoSession` exposes only message / action / tab / browsing-data / download delegates. There is no `getWebExtensionTabId()`. |
| Does `sender.tab.id` (background script receiving a content-script message) equal `webRequest.details.tabId`? | **VERIFY** | The whole attribution join depends on one shared ID space. True in Firefox desktop; **not confirmed for GeckoView built-in extensions**. |
| Is `webRequest` exposed at all to a GeckoView built-in extension? | **VERIFY** | Firefox for Android runs on GeckoView and supports `webRequest` (uBlock Origin works), so the engine supports it. But the GeckoView consumer docs document **only** messaging — they never mention `webRequest`. A 2019 third-party note lists `webRequest` ✔ for a *Nightly* build; a 2020 GeckoView issue reports `browser.webRequest` `undefined`. These conflict and neither is authoritative. |
| Does `webRequest` behave identically for built-in vs sideloaded extensions in GeckoView? | **VERIFY** | built-ins get privileged permissions; some `webRequest` sub-behaviours are permission-gated differently for privileged extensions |

**Until SPIKE-01 resolves these, the observation layer is provisional.** The architecture is designed
so that the *shape* of the capture domain (L4) is independent of how events arrive (L3): a
`CaptureEventSource` interface has one GeckoView-WebExtension implementation and would have a
page-instrumentation implementation if the spike fails. That is why the fallback is a swap, not a
rewrite.

---

## 6. Network observation detail

| Capability | Grade | Notes |
|---|---|---|
| `webRequest.onBeforeRequest` | **VERIFY** | per §5 |
| `onBeforeSendHeaders` / `onSendHeaders` | **VERIFY** | |
| `onHeadersReceived` | **VERIFY** | |
| `onResponseStarted` | **VERIFY** | |
| `onBeforeRedirect` | **VERIFY** | |
| `onCompleted` / `onErrorOccurred` | **VERIFY** | |
| `details.tabId`, `details.frameId`, `details.requestId`, `details.documentId`, `details.originUrl`, `details.documentUrl`, `details.type` | **SUPPORTED** (WebExtension semantics) | `tabId == -1` means "not associated with a tab" |
| `details.requestBody` (raw form data / parsed body) | **VERIFY** | present in WebExtension API; GeckoView exposure unconfirmed |
| `details.responseBody` (passive) | **UNSUPPORTED** | not a passive field. Requires an explicit filter. See §7. |
| `webRequest.filterResponseData(requestId)` → `StreamFilter` | **PARTIAL** | Exists in the WebExtension API, **not documented for GeckoView**. Three hard constraints: (a) requires `webRequest` **+** `webRequestBlocking` + host permission — MV3 additionally needs `webRequestFilterResponse`; (b) **script requests served from Gecko's optimized byte cache are not available in a useful form**, and the filter must be created in `onBeforeRequest` to intercept before cache lookup; (c) **the extension must `close()` or `disconnect()` the filter or the response is held open forever.** |
| Raw HTTP/2 frames, QUIC packets, arbitrary UDP | **UNSUPPORTED** | not exposed by any WebExtension API. PRD §9.2 correctly forbids implying otherwise. |
| TLS / security metadata per request | **UNSUPPORTED** via webRequest | not in `details`. TLS facts are only reachable at a *navigation/security* level via `GeckoSession` security info — **VERIFY** what is exposed. PRD §55 lists "TLS information" as a completeness row; that row must degrade to `UNAVAILABLE` until verified. |
| Post-handshake WebSocket frames via webRequest | **UNSUPPORTED** | only the handshake (a 101 upgrade) is visible. PRD §32 is correct. |
| WebSocket frames via page instrumentation | **PARTIAL** | `WebSocket` wrapper in a content script observes JS-level send/receive, not wire frames. Must be labelled as page-level (§9.2, §131). |
| SSE frames via page instrumentation | **PARTIAL** | `EventSource` wrapper; JS-level only. |
| Console messages | **PARTIAL** | page-level `console` interception only. |

**Design consequence — response bodies are not a default capability.** Given constraint (c) alone, an
unclosed stream filter hangs a page load. Therefore:

- Response-body capture is **off by default**.
- If enabled at all, it is scoped to an explicit host allowlist, hard-capped in bytes and duration,
  and wrapped in a watchdog that force-`disconnect()`s.
- If SPIKE-03 cannot demonstrate safe operation, response-body capture is **postponed entirely** and
  the completeness model reports `UNAVAILABLE`. PRD §31 and §107 explicitly permit this.

---

## 7. Storage, blocking, permissions

| Capability | Grade | Notes |
|---|---|---|
| `ContentBlocking.Delegate` — observe/allow block decisions | **SUPPORTED** | `setContentBlockingDelegate` |
| GeckoView **tracking protection** (TP) | **SUPPORTED** (as TP only) | `USE_TRACKING_PROTECTION` |
| uBlock-style arbitrary filter-list engine | **UNSUPPORTED** | GeckoView TP is a curated list + heuristics, not a general filter engine. PRD §36 forbids claiming otherwise. Product copy must say "tracking protection", never "ad blocker with filter lists". |
| Per-site TP exception (allowlisting) | **PARTIAL** | must account for every layer applied; PRD §36 warns about this |
| `PermissionDelegate` for camera / mic / geolocation / notifications / clipboard / autoplay / fullscreen / downloads / persistent-storage | **SUPPORTED** | `PermissionDelegate` + `ContentPermission` (`GeckoSession.java:7273`) |
| Runtime Android permission request (`POST_NOTIFICATIONS`, etc.) | **SUPPORTED** | standard AndroidX; the *mapping* from Gecko permission → Android runtime permission is app work and must be per-capability, never blanket (PRD §88) |
| `GeckoSessionSettings.FULL_ACCESSIBILITY_TREE` | **SUPPORTED** | useful for accessibility verification |
| Cookie / site storage access from app code | **PARTIAL** | no direct GeckoView cookie API for arbitrary reads; not needed by design — capture is observation-only |
| Session state persistence across process death | **SUPPORTED** (via Parcel) | `GeckoSession` Parcelable; app must persist the bundle itself |

---

## 8. Mandatory device verification spikes

These are the PRD's §17 "must be verified experimentally on a real Android device". Each is a
**throwaway** probe, not product code. **None of them may be skipped**, and Phase 3+ is blocked on
SPIKE-01.

| ID | Question | Method | Blocks |
|---|---|---|---|
| **SPIKE-01** | Does `webRequest` exist for a GeckoView built-in extension? Does `sender.tab.id` == `webRequest.details.tabId`? Does session-scoped `sender.session` work as documented? | Minimal extension: register all seven `webRequest` listeners; content script announces itself and echoes the `tabId` the background script reports; log the triple (`sender.session` identity, `sender.tab.id`, `details.tabId`) for two tabs with interleaved traffic | Phase 3, and the entire attribution design |
| **SPIKE-02** | With two live tabs, does the join hold under rapid switching, background tabs, and redirect chains? | Multi-tab scenario generating distinguishable traffic per tab; assert zero cross-tab attribution | Phase 2→3 boundary, Release Gate A |
| **SPIKE-03** | Can `filterResponseData` capture a response body without stalling or corrupting page load? | Filter on `onBeforeRequest`, `ondata` accumulate up to a cap, `onstop` close; measure load latency, assert no hangs across 500 iterations incl. chunked/SSE/large-binary | Any response-body feature |
| **SPIKE-04** | Which `webRequest` detail fields are populated in practice? | Enumerate every field of `details` for: GET, POST JSON, multipart, GraphQL, 302 chain, WebSocket upgrade, SSE, failed request, offline | Capture model field mapping |
| **SPIKE-05** | Is `documentId` available and stable per document? Can it be used as a second join key? | Compare `details.documentId` across the lifecycle of one document incl. same-tab navigation | Attribution robustness |
| **SPIKE-06** | Does `tabId == -1` occur in practice? For which request types? | Load a page with service worker, beacon, prefetch, and CSS preload; log every `tabId` seen | Unattributed-bucket design |
| **SPIKE-07** | What TLS/security information is reachable per request or per navigation? | Enumerate reachable security signals; confirm what must be reported `UNAVAILABLE` | PRD §55 completeness row |
| **SPIKE-08** | Gecko content-process crash behaviour: which callbacks fire, in what order, and what survives? | Force OOM/crash of the content process; record `onCrash`/`onKill`, whether the session is reusable, whether the Parcel bundle restores | Phase 2, Lifecycle doc |
| **SPIKE-09** | Session restore fidelity via `GeckoSession` Parcel across real process death | Write bundle, `kill -9` the app, restore, compare history/progress | Lifecycle doc |
| **SPIKE-10** | 16 KB page-size + low-memory device behaviour; GeckoView release requirements | Run on a 16 KB-page device; measure memory under 8 tabs + capture | Release gate |
| **SPIKE-11** | Private-mode isolation: are `details` from a private session separable, and is `usePrivateMode` honoured per session? | Two sessions, one private; verify no cross-contamination of cookies or capture attribution | Phase 2 |
| **SPIKE-12** | Extension lifecycle: reconnect behaviour after port disconnect, runtime teardown, app restart | Kill port, restart app, verify `ensureBuiltIn` idempotence and event resumption | Phase 3 |

**Rule (PRD §130):** do not upgrade the GeckoView release to obtain an unverified capability. Pin the
engine, verify against the pin, and treat the capability matrix as version-scoped — re-run SPIKE-01
through SPIKE-12 on every engine bump.

---

## 9. Product-copy honesty map

Derived from this matrix. Copy is a release artefact and is reviewed at Gate H.

| Do not claim | Claim instead |
|---|---|
| "Capture every packet" | "Observes supported browser network requests" |
| "Full WebSocket packet capture" | "WebSocket handshake, plus page-level frame inspection" |
| "Full response body capture" | "Response bodies where supported by the capture path" |
| "uBlock-style ad blocking" | "GeckoView tracking protection" |
| "Desktop DevTools on mobile" | "Mobile workflow from observed request to editable test" |
| "TLS details per request" | pending SPIKE-07; until then `UNAVAILABLE` |

---

## 10. Summary of load-bearing unknowns

| # | Unknown | If it fails |
|---|---|---|
| 1 | `webRequest` unavailable to GeckoView built-ins | Observation layer becomes page-level instrumentation (`fetch`/`XHR`/`WebSocket`/`EventSource` wrappers). Weaker: misses non-JS traffic, cached responses, and anything outside the page's own APIs. Copy must change. |
| 2 | `sender.tab.id` ≠ `details.tabId` | Attribution join breaks. Fallback: `documentId`/`originUrl` heuristics — materially weaker, raises false-association risk, and would put Release Gate A at risk. |
| 3 | `filterResponseData` unsafe | Response bodies postponed. Completeness reports `UNAVAILABLE`. Acceptable per PRD §31/§107. |
| 4 | No session-scoped `sender.session` | Attribution becomes unsound. Would require re-architecting the observation layer around a native `SessionFeature`/content-script-per-session model. |
