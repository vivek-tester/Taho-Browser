# ADR 0004 — External navigation and user-created web tabs

Status: Candidate implementation; pinned-build/device verification required  
Date: 2026-09-27

## Context

A usable browser must handle user-initiated links that leave the web surface without turning arbitrary
custom schemes into an implicit app-launch primitive. It must also handle ordinary `target=_blank`
HTTP(S) links without depending on a second browser engine or on capture.

GeckoView exposes `NavigationDelegate.onLoadRequest`, including URI, target window, redirect state
and user-gesture state. Returning DENY after the application handles the request prevents Gecko from
also attempting the same navigation.

## Decisions

1. HTTP, HTTPS and Gecko's internal `about:` navigation stay inside Taho Browser.
2. A user-initiated `TARGET_WINDOW_NEW` HTTP(S)/about navigation creates a new Taho Browser tab;
   non-user-initiated popup attempts are denied.
3. External app handoff is allowlisted to `mailto:`, `tel:`, `sms:` and `geo:`.
4. External handoff requires an active user gesture and is denied for redirects.
5. Arbitrary custom schemes, `intent:`, script schemes and redirected external launches are never
   forwarded by this path.
6. `tel:` uses Android ACTION_DIAL rather than ACTION_CALL. Other allowlisted schemes use ACTION_VIEW.
7. The browser clears the pending handoff immediately after Android accepts/rejects the launch.
8. If no installed app can handle an allowlisted link, the current tab remains intact and the browser
   shows a bounded local notice.
9. Returning from the external app resumes the same browser tab/session; capture is not involved.

## Verification still required

- Compile the onLoadRequest override against the pinned GeckoView candidate.
- Exercise target=_blank foreground links and blocked popup attempts.
- Exercise mailto/tel/sms/geo with and without installed handlers.
- Verify returning from another app preserves the selected tab and page state.
- Verify redirect-driven/custom-scheme launches are not forwarded.
