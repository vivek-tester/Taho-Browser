# M3 physical-device acceptance

This checklist records the M3 browser-baseline device gate from the build plan. It does not mark any
scenario as passed until a physical-device run is attached.

## Build under test

Use the debug APK produced by the `android-compile` CI job as artifact
`taho-browser-m3-debug`. Record the exact commit SHA and Android build/device model before testing.

## Browser baseline checks

Before the numbered scenarios, verify capture is OFF and ordinary browsing still works:

- launch and load HTTP(S);
- omnibox URL/search;
- back, forward and reload;
- new/close/switch tabs;
- a user-initiated `target=_blank` opens a Taho tab;
- a non-user popup is blocked;
- load failure and reload UI;
- normal and private tabs operate independently;
- location/camera/microphone prompts name the requesting origin;
- `mailto:`, `tel:`, `sms:` and `geo:` only leave the browser after a user gesture;
- redirected/custom/`intent:`/`javascript:`/`file:` launches remain blocked.

## D3 — external app return

1. Open a page with an allowlisted external link.
2. Record selected tab ID/location.
3. Trigger the link by user gesture and enter the external app.
4. Return to Taho Browser.
5. Pass only if the same tab/session/page is still selected and usable.

## D4 — process death

The test architecture describes D4 as process death *mid-capture*. M3 has no production capture path,
so M3 can only execute the browser-lifecycle subset here; capture PARTIAL labelling remains a later
capture milestone assertion.

1. Keep at least two normal tabs open and make one selected.
2. Also open a private tab, but return selection to a normal tab.
3. Background Taho Browser.
4. Run `adb shell am kill app.taho.browser`.
5. Relaunch.
6. Pass the M3 subset only if normal tabs/order/selected normal tab restore through Gecko
   SessionState and no private tab or private SessionState is restored.

## D5 — content-process crash/OOM

1. Open six tabs with real pages.
2. Induce Gecko content-process termination on the physical device using the device/lab procedure.
3. Pass only if the affected tab remains represented, shows the crash recovery UI, other tabs remain
   usable, and Reload Page replaces/restores the GeckoSession without silently closing the tab.

## D7 — Android platform coverage

Run the baseline checks on physical Android 14, Android 15 and Android 16 targets. Record model,
OS/build number and result separately. A single emulator result does not satisfy D7.

## D15 — rotation

The test architecture defines D15 for rotation during active capture, transfer and mid-sheet. M3
cannot truthfully execute the capture/transfer portions because those features do not exist yet.

For the M3 subset:
1. Rotate while a page is loaded.
2. Rotate with the tab sheet open.
3. Rotate with a site-permission sheet open.
4. Pass only if the process-scoped controller/session remains intact, the page is not recreated as a
   new tab, and no permission is silently granted.

The full D15 scenario remains open until capture and transfer exist.

## Evidence

For each executed case retain: commit SHA, APK artifact/run ID, device/OS, exact steps, result,
screenshots/log excerpts that contain no request secrets, and any limitation. Do not convert an
unexecuted or partial scenario into PASS.
