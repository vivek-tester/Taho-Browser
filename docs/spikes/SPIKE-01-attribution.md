# SPIKE-01 — GeckoView attribution join

Status: READY TO RUN — no device result recorded yet  
Candidate GeckoView pin: `156.0.20260921121718` (`geckoview-omni`)  
Date prepared: 2026-09-27

## Questions

1. Is `browser.webRequest` available to the bundled built-in extension?
2. Does JavaScript `sender.tab.id` share the same ID space as `webRequest.details.tabId`?
3. Does a content-script native message arrive with `WebExtension.MessageSender.session` equal to the registered `GeckoSession`?
4. Does the join remain correct with two live tabs and interleaved traffic?

No capability is marked supported until this is run on a physical Android device.

## Harness

Build and run `:spikes:geckoview`.

The APK contains a built-in privileged extension under
`assets/taho_attribution_spike/`.

The content script sends two announcements for each document:

- content → background, which records JavaScript `sender.tab.id`;
- content → native session delegate, which records native `sender.session`.

The background records metadata-only `webRequest` events. It intentionally does not send
request/response headers, bodies, cookies, authorization values, or full URLs.

## Procedure

1. Start the spike app and confirm `extension ready`.
2. Load a controlled fixture in Tab A.
3. Load distinguishable traffic in Tab B.
4. Alternate A/B rapidly while both pages generate requests.
5. Exercise a redirect chain in each tab.
6. Compare each app-tab/session announcement with its JavaScript tab ID.
7. Compare JavaScript tab IDs with observed `webRequest tab=` IDs.
8. Repeat with one tab in the background.
9. Record any `tabId == -1` as unattributed; never guess ownership.

## Pass criteria

- The extension installs without fallback.
- Every top-level content announcement has a non-null native `sender.session`.
- `sender.session` matches the GeckoSession registered for that app tab.
- JavaScript `sender.tab.id` is stable for the tab lifetime.
- Requests attributable to Tab A never acquire Tab B's webRequest tab ID, and vice versa.
- Redirect-chain events retain correct ownership.
- Unattributed events remain explicitly unattributed.

## Failure consequence

Any failure blocks production WebExtension attribution. Do not add URL or selected-tab heuristics.
Re-plan the observation source as required by the capability matrix.
