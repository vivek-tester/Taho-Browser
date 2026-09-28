# TAHO_BROWSER_LIFECYCLE_ARCHITECTURE.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** Activity lifecycle, configuration changes, process death, Gecko content-process death,
low-memory pressure, and the network-state transitions that affect capture.

---

## 1. The governing separation

Three lifetimes are independent and are restored independently (PRD §90):

```
BROWSER SESSIONS        CAPTURE HISTORY         IN-FLIGHT CAPTURE
(GeckoSession liveness)  (persisted rows)        (in-memory transactions)
        │                        │                        │
  may be LOST               survives content          LOST on process death
  on content crash          process death            → driven to PARTIAL
        │                        │                        on next start
  restored via Parcel       restored from DB         never silently discarded
```

**Rule:** losing the web page must never lose a captured request. Losing the process must never lose
persisted capture. Losing in-flight capture is acceptable **provided it is recorded truthfully** —
transactions are reopened as `PARTIAL`, not silently dropped and not shown as complete.

---

## 2. Android configuration changes

| Change | Default behaviour | Rationale |
|---|---|---|
| Rotation | Activity recreated; `GeckoView` detached and reattached | GeckoView does not survive a destroyed view hierarchy without an explicit transfer |
| Dark/light mode | recreate (unless handled) | |
| Font scale / locale | recreate | |
| Multi-window resize | **no recreate**; layout only | |
| Keyboard show/hide | **no recreate** | the omnibox animating with the IME must not recreate the Activity |

**Implementation:**

- The `Activity` holds no session state in fields that survive recreation. All state lives in
  `TabManager` / `CaptureCoordinator`, which are **process-scoped singletons** held by the
  `Application`.
- `GeckoView` instances are held by the session layer, not by the view hierarchy. On
  `onDestroy`/`onDetachFromWindow`, each `GeckoView` is detached from its window but its
  `GeckoSession` is retained. On re-attach, `geckoView.setSession(session)` re-binds.
- `android:configChanges` is **not** used to dodge recreation for rotation. GeckoView's surface and
  input handling must be recreated correctly; suppressing recreation is how browsers end up with
  broken input after rotation. Recreation is handled properly instead.

**`screenOrientation` is not locked.** Locking portrait would be a real usability regression on
large-screen devices and tablets, and the PRD's thumb-zone design works in landscape.

---

## 3. Process death

### 3.1 What Android gives us

`GeckoSession` is `Parcelable` (`writeToParcel` / `readFromParcel`, `GeckoSession.java:3186/3195`).
The app can therefore persist a session bundle and restore it. Fidelity is bounded — restored
sessions are **not** live sessions; they are rehydrated navigation state. This is verified by
**SPIKE-09**, not assumed.

### 3.2 What must be persisted, and when

| State | Persisted | Mechanism | When |
|---|---|---|---|
| `TahoTabId` list + order | yes | `tab` table | on every mutation (debounced 500 ms) |
| `GeckoSession` bundle | yes | `Parcel` → app-private file, per tab | on `onStop` and on tab mutation |
| `ext_tab_id` bindings | yes | `tab.ext_tab_id` | immediately on handshake |
| Capture sessions | yes | Room | continuously |
| Transactions | yes | Room | continuously, on terminal state at minimum |
| **In-flight transaction partials** | yes | Room, on every state change | continuously — this is what turns process death into `PARTIAL` rather than data loss |
| Omnibox text, sheet state | no | — | ephemeral by design |
| Secret plaintext in memory | n/a | — | process death clears it, which is a security feature |

### 3.3 Restart sequence

```
1  Application.onCreate
     • KeystoreCipher.ensureAvailable()      → if key invalid, mark capture ERROR, continue
     • CaptureRepository.open()              → if unavailable, in-memory mode, capture LIMITED
     • GeckoRuntimeHolder.ensureRuntime()    → process-global, create once
     • BuiltinExtensionHost.ensureInstalled()→ ensureBuiltIn (idempotent, SPIKE-12)
     • PortRegistry.awaitPort()              → bounded wait; capture stays ERROR until connected

2  BrowserActivity.onCreate
     • TabManager.restoreTabs()              → TabRows from DB
     • for each: rehydrate GeckoSession, attach to a GeckoView
     • Render the first tab IMMEDIATELY
     • Only then: rebuild capture state and re-handshake attribution

3  Attribution rebuild
     • Existing ext_tab_id bindings are retained as HINTS
     • Each restored session installs a session-scoped delegate
     • The content script re-announces → rebinding is re-confirmed, never assumed
     • Unconfirmed bindings are marked UNRESOLVED, never assumed correct
```

**Why the browser renders before capture is ready.** PRD §53.1: the browser must be usable
independently, and capture failure must degrade to `Browser available / Capture unavailable`. Startup
order enforces this as a structural property rather than a hope.

### 3.4 In-flight transactions at process death

On restart, any transaction found in a non-terminal state is transitioned to `PARTIAL` with reason
`PROCESS_DIED`, and the reason is displayed. It is **not** upgraded to `COMPLETED`, and it is not
hidden. A request whose response was never seen is shown as a request whose outcome is unknown —
which is the truth (PRD §13, §55, §138).

---

## 4. Gecko content-process death

`ContentDelegate.onCrash(session)` and `onKill(session)` are **SUPPORTED** (Capability Matrix §3).
Automatic session recovery is **not** — no API exists. The recovery UI is app-implemented, and PRD
§90/§91 make it mandatory:

```
┌──────────────────────────────┐
│ This page stopped responding.│
│                              │
│ [ Reload Page ]              │
│ [ View Captured Requests ]   │
└──────────────────────────────┘
```

**Requirements on the implementation:**

| Requirement | Reason |
|---|---|
| The second action is present and enabled whenever persisted capture exists for that tab | PRD §91 — "The user should not lose an already captured request merely because the web page process crashed" |
| The crashed tab's `attribution` becomes `DETACHED`, never reassigned | Data Model §4.4 |
| The tab is **not** silently closed; it shows the crash surface | losing the tab loses the user's context |
| `Reload Page` creates a **new** `GeckoSession` for the same `TahoTabId` | the old session is unusable; the tab identity persists, and the ext_tab_id binding is re-established by handshake |
| Persisted capture for the tab is untouched | separate store |
| Capture for *other* tabs continues | the crash is per-session |

**Tab identity across crash:** `TahoTabId` is stable, `GeckoSession` is not. This is why
`TahoTabId` is the app-minted ULID and not derived from the session. A design that used
`GeckoSession` identity as the tab identity would lose tabs on every crash.

---

## 5. Low-memory pressure

`onTrimMemory` / `onLowMemory` (PRD §94). Order of response, highest value preserved first:

| Step | Action | Rationale |
|---|---|---|
| 1 | Flush in-memory capture state to Room | persisted data is the irreplaceable part |
| 2 | Release `RawCapture` buffers; zero secret plaintext | security before memory |
| 3 | Evict P3/P4 in-memory events; reduce the queue | low-value data first |
| 4 | Release large body buffers; keep metadata | bodies are the memory hogs |
| 5 | Discard fullscreen inspector view state | cheap to rebuild |
| 6 | Suspend background tabs' media (`suspendMediaWhenInactive`) | Gecko-native, GeckoView-supported |
| 7 | **Do not** close tabs | the user's tabs are the user's |
| 8 | **Do not** drop the active tab | — |

Steps 1–2 run on every `onTrimMemory` level. Steps 3–6 only at `TRIM_MEMORY_RUNNING_CRITICAL` and
below. The browser must survive; the capture subsystem is the thing that gives way.

**Never:** keep unlimited response bodies in memory (PRD §94). Bodies above
`INLINE_BODY_LIMIT` are streamed to encrypted storage as they arrive, not accumulated in RAM.

---

## 6. Backgrounding and external authentication

The PRD's hardest UX case: OAuth / payment flows that leave the app and must come back to a
**specific tab** (§53.1, §89).

| Requirement | Implementation |
|---|---|
| Return to the originating tab | TabManager tracks a `pendingExternalReturn: TahoTabId?` set when an external intent is launched; on `onResume`, if set, that tab is focused and the marker cleared |
| Capture context intact | The `GeckoSession` is retained; in-flight transactions keep their attribution. The page never unloaded, so its network activity continues to be observed |
| The user does not lose the page | the tab is not reloaded or recreated on return |
| Interruption by a phone call / other app | handled identically — `onStop` persists session state; `onStart` restores view binding without touching sessions |
| Long backgrounding → process death | §3 applies; the tab is restored from `TahoTabId` + Parcel, and the external-return marker is cleared because the flow's context is gone (and the user is told the page was reloaded) |

**Custom-tab-style behaviour is deliberately *not* implemented.** Chrome's "Custom Tabs" hands the
page to another app and destroys ours. That is the opposite of what Taho needs: the whole value is
that the originating session keeps living and keeps being observed. Taho launches external apps and
stays resident.

---

## 7. Network state changes

| Event | Browser | Capture |
|---|---|---|
| Wi-Fi → cellular | ongoing requests may fail | affected transactions → `FAILED` with the engine's error; the rest continue |
| Offline | pages fail to load | existing captures remain inspectable; **no false "capture active"** implying success (PRD §93) |
| Reconnect | normal | `CaptureSessionState` unchanged; a `DIAG` network event is recorded |
| Intermittent | normal | individual transactions record their own outcomes; the session is not marked degraded by one failure |

**Rule:** capture state and network state are orthogonal. A capture session in `OBSERVING` with zero
transactions is a legitimate state meaning "watching, nothing relevant happened" — not an error, and
not "capture is broken". PRD §14 forbids inferring capture state from the existence of transactions,
and by the same logic forbids inferring network health from capture state.

---

## 8. State ownership

| State | Owner | Survives rotation | Survives process death |
|---|---|---|---|
| `GeckoRuntime` | `GeckoRuntimeHolder` (Application) | yes | recreated |
| `GeckoSession` per tab | `TabManager` (Application) | yes | rehydrated from Parcel |
| `GeckoView` per tab | `BrowserShell` (Activity-scoped view) | reattached | recreated |
| `ext_tab_id` bindings | `FrameAttributor` + `tab` table | yes | retained as hints, re-confirmed |
| Capture coordinator | `CaptureCoordinator` (Application) | yes | rebuilt from Room |
| In-flight transactions | `CaptureCoordinator` | yes | → `PARTIAL` |
| Persisted capture | Room | yes | yes |
| Selected tab | `TabManager` | yes | restored (best effort) |
| Sheet/inspector UI state | Compose `rememberSaveable` | yes | no, by design |
| Transfer in progress | `transfer_record` + artifact store | yes | resumable / swept |

**Single-Activity architecture.** One `Activity` hosts the whole browser. Tabs are a composable
surface, not multiple `Activity`s. Multiple `Activity`s would multiply `GeckoView` lifecycles,
multiply session restore complexity, and make the tab model harder for no benefit. This is a
deliberate simplification.

---

## 9. Startup / shutdown ordering

```
STARTUP (hard requirements)
  main thread        GeckoRuntime.create()            once per process
  HandlerThread      ensureBuiltIn(extension)         before any session
  main thread        first GeckoSession.open()
  render             first tab visible
  background         Room open, capture coordinator start
  background         port await → HELLO → capture OBSERVING

SHUTDOWN
  background         flush in-memory capture → Room
  background         persist session Parcels
  main thread        detach GeckoViews
  —                  GeckoRuntime is NOT shut down explicitly; it dies with the process
  background         zero secret buffers
```

**No explicit `GeckoRuntime` shutdown exists or is needed.** It is a process singleton; Android
terminating the process is the shutdown. Flush-on-`onStop` is the real durability boundary, and it is
what makes process death survivable.
