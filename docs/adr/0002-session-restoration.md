# ADR 0002 — Browser session restoration and crash recovery

Status: Candidate implementation; pinned-build/device verification required  
Date: 2026-09-27

## Context

The lifecycle baseline says browser tabs must survive Activity recreation and should recover navigation
state after process death. It also states that a content-process crash must not silently close the
tab: reloading creates a new GeckoSession for the same application tab identity.

The pre-implementation lifecycle text describes persisting a parcelled GeckoSession. Source-level
inspection of the current GeckoView API instead exposes restoration through
`GeckoSession.SessionState`:

- `ProgressDelegate.onSessionStateChange(session, state)` provides the latest history/scroll/form state.
- `GeckoSession.restoreState(state)` restores that state into a newly opened session.
- `SessionState.toString()` / `SessionState.fromString(...)` provide a bounded serialization surface.
- `NavigationDelegate.onCanGoBack/onCanGoForward` provides authoritative history-button state.
- `ContentDelegate.onCrash/onKill` reports an unusable content session that must be reopened.

This is a source-level implementation candidate. The pinned GeckoView artifact and physical-device
SPIKE-09 remain the acceptance authority.

## Decisions

1. Application tab identity is stable across a content crash; GeckoSession identity is not.
2. Normal tabs cache the latest GeckoSession.SessionState and debounce restore-store writes by 500 ms.
3. Private tabs may keep SessionState in memory for same-process crash recovery, but are excluded from
   every persisted browser-session snapshot.
4. Process restart recreates normal GeckoSessions, opens them on the process-global GeckoRuntime, then
   calls restoreState when a valid saved SessionState exists.
5. If no serialized state exists, a saved normal-tab location may be loaded as a fallback.
6. Back/forward controls use Gecko's onCanGoBack/onCanGoForward callbacks. The UI does not infer
   history availability from URL changes.
7. onCrash/onKill marks the tab crashed and detaches its rendering surface. It is not silently closed.
8. Reloading a crashed tab creates a replacement GeckoSession for the same app tab ID and restores
   the last in-memory SessionState when available.
9. Capture history is not touched by browser-session recovery. Capture integration will later mark
   attribution binding detached/reconfirmed as defined by the capture lifecycle.
10. This ADR does not mark SPIKE-09 passed. Compile and device restore evidence are still mandatory.

## Security and privacy

The restore store is browser state, not capture history. Private tabs are never serialized to it.
No captured headers, bodies, credentials, or transfer artifacts are written by this mechanism.

## Verification still required

- Compile this API surface against the pinned GeckoView candidate.
- Kill/restart the Android app and verify normal-tab restoration.
- Verify private tabs do not reappear after process restart.
- Crash/kill a Gecko content process and verify the tab remains visible with a reload action.
- Verify restored history drives correct back/forward callbacks.
- Repeat after an engine upgrade before accepting the new pin.
