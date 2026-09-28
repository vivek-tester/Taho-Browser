# TAHO_BROWSER_SYSTEM_ARCHITECTURE.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Authority:** `Taho_Browser_PRD (1).md` (product) + verified GeckoView/Android APIs (platform)
**Scope:** Taho Browser only. Taho API Testing changes are specified as a *contract*, not implemented here.

---

## 0. Evidence base

Every platform claim in this document set is traceable to one of:

| Source | Use |
|---|---|
| `mozilla.github.io/geckoview/javadoc` (mozilla-central) | GeckoView Java API surface |
| `firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html` | Official WebExtension + native messaging guide |
| `geckoview/src/main/java/org/mozilla/geckoview/*.java` (firefox main) | Ground truth for API existence |
| `developer.mozilla.org` WebExtension docs | WebExtension API semantics/permissions |

**Explicit non-source:** no prior Taho Capture code, prototype, or research document was found in
`/home/eternal/Taho`. The sibling `Taho App/` repository is a Flutter API tester and is treated as a
*product contract consumer*, not an architectural precedent. Nothing in this architecture is copied
from it.

Where the PRD asserts a platform capability, that assertion is treated as a **hypothesis to verify**,
not a fact. See `TAHO_BROWSER_GECKOVIEW_CAPABILITY_MATRIX.md`.

---

## 1. What we are building

An Android browser whose differentiating behaviour is: it observes the network activity of its *own*
GeckoView sessions, surfaces *relevant* requests ambiently, lets the user inspect one, and hands it to
Taho API Testing as a normalised, provenance-carrying, credential-policy-processed request.

```
Browse → observe → recognise → inspect → protect → send → open in Taho → test
```

Everything else is subordinate. The browser must be usable and pleasant with the entire capture
subsystem switched off or broken.

### 1.1 Product boundaries

**In scope:** GeckoView embedding, tab model, session attribution, WebExtension-based observation,
capture domain, capture persistence, credential defence, progressive-disclosure UI, Browser→Taho
transfer (sender side), release engineering.

**Out of scope (permanent):** VPN/packet interception, TLS MITM, device-wide capture, desktop
DevTools parity, a general-purpose API client, a permanent network log, cloud traffic collection.

**Out of scope (this phase):** all implementation. This document set is architecture only.

---

## 2. The three findings that determine the architecture

These are verified, not assumed. They drive every decision downstream.

### Finding 1 — GeckoView exposes no WebExtension tab ID for a session

`WebExtension.java` (mozilla-central, 3198 lines) contains **no `tabId` field or accessor** on
`WebExtension`, `WebExtension.SessionController`, or `GeckoSession`. `GeckoSession` offers only:

- `getWebExtensionController()` → `setMessageDelegate` / `getMessageDelegate`
- `setActionDelegate` / `getActionDelegate`
- `setTabDelegate` / `getTabDelegate`
- (internally) browsing-data and download delegates

There is **no `getWebExtensionTabId()`**. Therefore the app cannot ask "which integer tab ID does
Gecko call this `GeckoSession`?"

**Consequence:** `webRequest` events, which arrive in the extension's *background* context carrying
`details.tabId`, cannot be mapped to a `GeckoSession` by direct lookup. The PRD's P0 requirement
("a captured event must never be assigned to the currently visible tab", §10) is therefore **not
satisfiable by attribute lookup** and requires the explicit join protocol in §5.

### Finding 2 — Session attribution is available, but only from content scripts

From the official guide:

> Note that, in the case of content scripts, `sender.session` will be a reference to the
> `GeckoSession` instance from which the message originated. For background scripts,
> `sender.session` will always be `null`.

And session-scoped delivery is opt-in per session:

```java
session.getWebExtensionController()
      .setMessageDelegate(extension, messageDelegate, "browser");
```

**Consequence:** there is exactly **one** trustworthy path from a GeckoView page to a known
`GeckoSession` — a content script posting native messaging, delivered through a session-scoped
delegate. The entire attribution design is built on that single primitive, and nothing else is
trusted as an identity signal.

### Finding 3 — Native messaging is real and connection-oriented

Confirmed from the official guide and javadoc:

| Capability | API | Verified |
|---|---|---|
| Built-in privileged extension | `runtime.getWebExtensionController().ensureBuiltIn(uri, id)` | yes |
| One-off messaging | `runtime.sendNativeMessage(app, msg)` → `MessageDelegate#onMessage` | yes |
| Connection messaging | `runtime.connectNative(app)` → `MessageDelegate#onConnect(WebExtension.Port)` | yes |
| Port messages | `port.setDelegate(PortDelegate)` → `onPortMessage(Object, Port)` | yes |
| Port disconnect | `PortDelegate#onDisconnect(Port)` | yes |
| Content-script native messaging | requires `geckoViewAddons` **+** `nativeMessagingFromContent` | yes |
| Backpressure to extension | returning `GeckoResult` from `onMessage` | yes |

Note the class is `WebExtension.Port` (nested), **not** a top-level `WebExtensionPort`. Any design
assuming the latter is wrong.

Extension lifetime: `ensureBuiltIn` persists the extension across app restarts, and installing at
every start is explicitly documented as acceptable.

---

## 3. Layering decision — and a documented conflict with the PRD

### 3.1 The conflict

The PRD (§ Core Technology Direction, and the §8 diagram) implies:

```
Taho Browser Application → Flutter UI → Native Android Bridge → Browser Runtime Layer → ...
```

This places **Flutter UI above a native bridge** as the primary UI surface.

**This architecture does not adopt Flutter as the browser shell, and the deviation is deliberate.**

### 3.2 Why

`GeckoView` **is an `android.view.View`**. It is hosted by `GeckoView.setSession(session)`. A browser
needs *N simultaneous `GeckoView` instances* (one per live tab) plus chrome that must interleave with
them: omnibox focus, find-in-page, selection handles, fullscreen transitions, permission prompts,
and a bottom sheet that must overlay the page without fighting its gesture stream.

Hosting N `GeckoView`s inside Flutter requires N `AndroidView` platform views. That is a known
weakness: platform-view compositing cost scales with instance count, and every one of the above
interactions becomes a cross-toolkit gesture and accessibility negotiation. For a *single* webview
embedded in an app it is acceptable. For a browser with a tab strip it is the dominant architectural
risk, and it would sit directly under the PRD's P0 correctness requirements.

Additional considerations:

- The browser must be correct under process death and rotation. That argues for the platform-native
  lifecycle model rather than a bridge.
- `GeckoSession` is `Parcelable` (`writeToParcel` / `readFromParcel`) and `GeckoRuntime` is
  process-global. Native gives direct access to both.
- Android's own browser stack, and GeckoView's reference consumers, are native.

### 3.3 Final decision

| Surface | Toolkit | Rationale |
|---|---|---|
| Browser shell: tab strip, omnibox, sheets, tab switcher, permission prompts, downloads | **Kotlin + Jetpack Compose**, hosting `GeckoView` directly | correct platform integration; N live `GeckoView`s; no bridge tax |
| Capture domain, storage, security, transfer | **Kotlin** | shares types with the shell; no cross-language boundary on the hot path |
| Request inspector / technical workspace | **Kotlin + Compose**, first | shares the capture model; avoids a second UI toolkit in v1 |
| Flutter | **Deferred, optional, not in v1** | see §3.4 |

The PRD's phrase is *"Flutter where appropriate"*. For a browser shell it is not appropriate. This is
the PRD's latitude being exercised, not the PRD being overruled.

### 3.4 If Flutter is later introduced

Only as a **separate, later module** for the fullscreen technical workspace, communicating over a
narrow, versioned, asynchronous command/state interface — never embedding a `GeckoView` in it, and
never on the capture hot path. It must be justified by measurement, and it must not be allowed to
hold a `GeckoSession` reference.

---

## 4. Final architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  PROCESS 1 — app process                                               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ L1  BROWSER SHELL (Kotlin/Compose)                              │  │
│  │     Activity · TabManager · Omnibox · Sheets · Permissions      │  │
│  │     hosts GeckoView instances directly (one per live tab)       │  │
│  └───────────────────────────┬────────────────────────────────────┘  │
│                              │                                       │
│  ┌───────────────────────────▼────────────────────────────────────┐  │
│  │ L2  ENGINE RUNTIME (Kotlin)                                    │  │
│  │     GeckoRuntimeHolder · GeckoSessionFactory · SessionRegistry  │  │
│  │     GeckoViewHost · Delegates · ContentBlocking · Recovery      │  │
│  └───────────────────────────┬────────────────────────────────────┘  │
│                              │                                       │
│  ┌───────────────────────────▼────────────────────────────────────┐  │
│  │ L3  OBSERVATION BRIDGE (Kotlin)                                │  │
│  │     BuiltinExtensionHost · PortRegistry · EventIngress         │  │
│  │     FrameAttributor (Finding 1+2 solution) · SchemaGate         │  │
│  └───────────────────────────┬────────────────────────────────────┘  │
│                              │                                       │
│  ┌───────────────────────────▼────────────────────────────────────┐  │
│  │ L4  CAPTURE DOMAIN (Kotlin, pure)                              │  │
│  │     CaptureCoordinator · RelevanceEngine · SecretDetector      │  │
│  │     TransactionStateMachine · BoundedEventQueue · Backpressure │  │
│  │     NO Android imports above the repository line                │  │
│  └───────────────────────────┬────────────────────────────────────┘  │
│                              │                                       │
│  ┌───────────────────────────▼────────────────────────────────────┐  │
│  │ L5  REPRESENTATION + PERSISTENCE (Kotlin)                      │  │
│  │     CaptureRepository · RepresentationMapper · Provenance      │  │
│  │     Room · KeystoreCipher · RetentionController                │  │
│  └───────────────────────────┬────────────────────────────────────┘  │
│                              │                                       │
│  ┌───────────────────────────▼────────────────────────────────────┐  │
│  │ L6  TRANSFER OUT (Kotlin)                                      │  │
│  │     RequestNormalizer · SecretPolicyApplier · EnvelopeBuilder  │  │
│  │     TransferCoordinator · TempArtifactStore · IntentSender       │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │ WebExtension ⇄ native messaging
┌──────────────────────────────────▼───────────────────────────────────┐
│  PROCESS 2 — Gecko content process (sandboxed, NOT our code)          │
│  GeckoSessions · DOM · network stack · WebExtension background       │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.1 Layer contract

Dependencies point **downward only**. A layer may call the layer directly beneath it and nothing
above or across. The PRD's §147 principle ("no layer should bypass the next layer") is enforced by
Gradle module boundaries, not convention.

| Gradle module | May depend on | Must NOT |
|---|---|---|
| `:app` | all | — |
| `:browser:shell` | `:browser:runtime` | touch capture internals |
| `:browser:runtime` | `:capture:domain` (interfaces only) | import Room, Compose |
| `:browser:observation` | `:capture:domain` | import Compose |
| `:capture:domain` | **nothing project-internal** | import `android.*`, `androidx.room.*` |
| `:capture:persist` | `:capture:domain` | import Compose, GeckoView |
| `:transfer:core` | `:capture:domain` | import Compose, GeckoView |
| `:transfer:android` | `:transfer:core` | import Compose |
| `:contract:taho-transfer` | nothing | anything project-internal |

`:capture:domain` is a **pure Kotlin/JVM module**. No `android.*`. This is the single most valuable
structural decision in the document: it makes the transaction state machine, relevance engine,
secret detector, and normaliser testable on the JVM in milliseconds, with no emulator, which is where
the PRD's P0 correctness items actually live.

### 4.2 Ownership

| Component | Layer | Owner | Notes |
|---|---|---|---|
| `GeckoRuntime` | L2 | `GeckoRuntimeHolder` | one per process, main thread, never recreated |
| `GeckoSession` creation | L2 | `GeckoSessionFactory` | settings incl. private mode, tracking protection |
| `GeckoView` hosting | L2 | `GeckoViewHost` | one view per live tab |
| Tab list / selection | L1 | `TabManager` | presentation only, never attribution |
| `tabId ⇄ GeckoSession` map | L3 | `FrameAttributor` | the attribution authority |
| Capture lifecycle | L4 | `CaptureCoordinator` | independent of UI |
| Relevance / classification | L4 | `RelevanceEngine` | hostname-boundary matching (§15) |
| Secret detection | L4 | `SecretDetector` | classification, not redaction |
| Bounded queue | L4 | `BoundedEventQueue` | real backpressure, priority lanes |
| Capture persistence | L5 | `CaptureRepository` | Room + Keystore |
| Secret policy at transfer | L6 | `SecretPolicyApplier` | PARAMETERIZE / MASK / EXPLICIT |
| Android handoff | L6 | `IntentSender`, `TempArtifactStore` | explicit package, no chooser |
| Web content | — | Gecko | never touched directly by app code |

---

## 5. Session attribution — the core mechanism

This is the PRD's P0 requirement (§10) and the hardest problem in the system. Design in full in
`TAHO_BROWSER_IPC_CONTRACT.md` §4; summarised here because everything depends on it.

**The join protocol:**

```
 ① tab opens          TabManager creates GeckoSession S, registers S in SessionRegistry,
                      installs a session-scoped MessageDelegate on S.

 ② content script     Top-frame content script posts runtime.sendMessage({type:"taho:hello"})
                      to the BACKGROUND script.

 ③ background replies  Background handler sees sender.tab.id (= the WebExtension tab id)
                      and returns { extTabId: sender.tab.id }.

 ④ content script     Content script calls runtime.sendNativeMessage("taho",
                      { type:"tab:register", extTabId, url, docId }).

 ⑤ native ingress     Delivered through S's SESSION-SCOPED delegate.
                      sender.session === S   ← the only trustworthy identity signal.

 ⑥ registry           FrameAttributor binds extTabId → S → TahoTabId.

 ⑦ webRequest events  Arrive in background, carry details.tabId (== extTabId).
                      Resolved through the registry, not through UI state.
```

**Hard rules, enforced by test:**

- `details.tabId == -1` → attributed to `Attribution.UNATTRIBUTED`. Never to the selected tab.
- Registry miss → `Attribution.UNRESOLVED`. The event is still captured, marked unresolved, and
  excluded from per-tab views. It is **never** silently reassigned.
- Tab switch, selection, or UI index changes have **zero** effect on the registry. This is the
  explicit regression test for PRD §53.3.
- If step ⑤ ever yields `sender.session == null` for a content-script message, that is a bug —
  drop the message and raise a diagnostic. Never proceed.

**Honest caveat:** steps ②–⑦ depend on `sender.tab.id` (background side) and `webRequest`
`details.tabId` sharing one ID space. That is true in Firefox, but it is **unverified for GeckoView
built-in extensions** and is the single highest-priority device spike (`SPIKE-01`,
`TAHO_BROWSER_GECKOVIEW_CAPABILITY_MATRIX.md` §5). If it fails, the fallback is page-level
instrumentation via content-script `fetch`/`XHR` wrapping, which is strictly weaker and must be
reflected honestly in the product copy per PRD §131.

---

## 6. Threading and concurrency

| Work | Thread | Rule |
|---|---|---|
| `GeckoRuntime` create | main (UI) | once; `GeckoRuntime` is process-global and non-recreatable |
| `installBuiltIn` / `ensureBuiltIn` | `@HandlerThread` | startup, before session creation |
| `GeckoView.setSession`, `session.open` | UI | |
| `MessageDelegate#onMessage`, `onConnect` | UI | marshalled by GeckoView |
| `PortDelegate#onPortMessage` | UI | **hot path** — must be O(1) and enqueue only |
| Room writes | IO dispatcher | single writer |
| Envelope serialisation | `Dispatchers.Default` pool, off UI | PRD §43 |
| Keystore crypto | IO dispatcher | Keystore ops are not cheap |
| Capture domain logic | caller thread, pure | no dispatchers inside L4 |

**Design rule:** the native-messaging ingress thread must never do I/O, never touch Room, never
encrypt, and never allocate unbounded buffers. It parses a bounded envelope, stamps it, and offers
it to a bounded priority queue. Everything expensive happens on the capture pipeline's own
coroutine. This is what makes the PRD's "capture failure does not crash browsing" (§51) achievable.

---

## 7. Degradation model

PRD §53.1 requires the browser to remain usable when capture fails. Modelled explicitly, not
incidentally:

| Failure | Browser | Capture UI | Recovery |
|---|---|---|---|
| `GeckoRuntime` init fails | **fatal** — no engine | n/a | none possible |
| `ensureBuiltIn` fails | working | `ERROR` + reason | retry on next launch |
| Port disconnects | working | `ERROR` | extension reconnects; `PortRegistry` re-binds |
| Attributor registry miss | working | `LIMITED` + count | self-heals on next navigation |
| Room unavailable | working, in-memory only | `LIMITED` | retry with backoff |
| Keystore invalidated | working | `ERROR`, capture **disabled** | requires user re-auth |
| Queue saturated | working | `LIMITED` + dropped count | automatic on drain |
| Content process crash | tab shows crash UI | history intact | reload / new session |

The capture subsystem is initialised **after** the first session renders, and its failure is caught
at a boundary that cannot propagate into browsing.

---

## 8. Architectural principles — how each is honoured

| PRD principle | Mechanism |
|---|---|
| Browser First, Tools Second | Capture surfaces reachable only via explicit tap; no permanent panels (§ UX doc) |
| Ambient Intelligence | `RelevanceEngine` + ambient count; default filter `Relevant` only |
| Progressive Disclosure | Four fixed levels: Ambient → Summary → Inspector → Fullscreen |
| Thumb-Zone-First | Omnibox, capture pill, primary actions, confirm/cancel all bottom-anchored |
| Decoupled Observation State | L4 independent of L1; capture outlives UI, survives process death |
| Immutable Provenance | Provenance record written at ingress, never rewritten; travels into Taho |
| Zero False Confidence | `Completeness` enum derived from capture evidence only; `LIMITED` surfaced |
| Credential Defense | Separate representations; mask ≠ delete (§ Data Model) |
| Decoupled Data Boundaries | Browser data and capture data in separate stores, separate clear paths |
| Touch-Engineered Workspaces | 48dp rows, no horizontal scroll, no colour-only signalling |
| Local-first capture | All capture local; transfer is on-device IPC only |
| Secure Browser→Taho | Explicit package Intent, no chooser, no cloud, never auto-execute |

---

## 9. Known architectural risks

Full register in `TAHO_BROWSER_RISK_REGISTER.md`. The three that would change the architecture:

1. **`webRequest` unavailable in GeckoView built-in extensions.** Would collapse the observation
   layer to page-level instrumentation. *Spike SPIKE-01 before committing to Phase 3.*
2. **`sender.tab.id` ≠ `webRequest.details.tabId` in GeckoView.** Would break the attribution join and
   force a weaker model. *Spike SPIKE-01, same session.*
3. **`filterResponseData` destabilises page loads.** A stream filter that is not closed hangs the
   response forever (documented Gecko behaviour). Response-body capture is therefore opt-in,
   allowlist-scoped, and hard-capped — or postponed entirely. *Spike SPIKE-03.*

---

## 10. Document map

| Document | Owns |
|---|---|
| `..._COMPONENT_ARCHITECTURE.md` | per-component contracts and interfaces |
| `..._DATA_MODEL.md` | transaction model, representations, provenance |
| `..._IPC_CONTRACT.md` | envelopes, attribution protocol, ordering, backpressure |
| `..._GECKOVIEW_CAPABILITY_MATRIX.md` | verified API support + device spikes |
| `..._SECURITY_ARCHITECTURE.md` | credential defence, Android security boundary |
| `..._LIFECYCLE_ARCHITECTURE.md` | process death, rotation, crash, restoration |
| `..._STORAGE_ARCHITECTURE.md` | Room schema, encryption, retention, migrations |
| `..._TAHO_INTEGRATION_CONTRACT.md` | versioned transfer envelope, both directions |
| `..._TEST_ARCHITECTURE.md` | test strategy and mandatory device scenarios |
| `..._IMPLEMENTATION_PLAN.md` | phased order, dependency graph, first task |
| `..._RISK_REGISTER.md` | risks, severity, mitigation, triggers |
