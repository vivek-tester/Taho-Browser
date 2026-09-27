# ADR 0003 — Site and Android permission boundary

Status: Candidate implementation; pinned-build/device verification required  
Date: 2026-09-27

## Context

M3 requires a usable browser permission flow even when capture is disabled. The UI/UX specification
does not define a dedicated permission component, so the browser uses a minimal bottom sheet that
follows the existing dark/gold chrome tokens without inventing a new capture surface.

GeckoView exposes three distinct permission callbacks:

- Android runtime permissions via `PermissionDelegate.onAndroidPermissionsRequest`;
- content/site permissions via `onContentPermissionRequest`;
- camera/microphone source selection via `onMediaPermissionRequest`.

These are browser responsibilities and are independent of capture.

## Decisions

1. Permission requests are fail-closed. Unknown Android permission strings and unsupported Gecko
   content-permission types are rejected rather than silently allowed.
2. The first supported content permissions are geolocation and persistent site storage.
3. Camera and microphone media prompts are supported. Screen capture and device-audio capture are
   rejected until a dedicated Android flow exists.
4. Android runtime requests are limited to coarse/fine location, camera and record-audio.
5. Only one site prompt and one Android runtime request may be pending at a time. Concurrent requests
   are rejected rather than reordered or guessed.
6. The prompt displays a bounded origin string and the requested capability; it never displays
   captured traffic or credential values.
7. Dismissing the site sheet is equivalent to Deny so Gecko is never left waiting indefinitely.
8. Closing or crashing a tab rejects its pending permission callbacks.
9. Private-tab prompts are kept only in memory by Taho Browser. They are not written to the browser
   session restore store.
10. Browser notification permission, WebXR, tracking exceptions, storage-access exceptions,
    autoplay policy, protected-media access, screen sharing and device-audio capture remain
    unsupported until their complete product/runtime flows exist.

## Verification still required

- Compile callback signatures against the pinned GeckoView candidate.
- Test allow/deny for location, camera and microphone on physical Android hardware.
- Confirm approximate-location acceptance works when fine location is declined.
- Confirm dismiss/close/crash resolves every outstanding callback.
- Confirm private-tab prompts do not survive process restart.
- Confirm unsupported permission types fail safely without breaking page browsing.
