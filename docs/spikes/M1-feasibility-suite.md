# M1 — GeckoView feasibility suite

Status: IMPLEMENTATION READY — DEVICE GATE NOT YET PASSED  
Pinned candidate: `org.mozilla.geckoview:geckoview-omni:156.0.20260921121718`  
Prepared: 2026-09-27

This is the disposable M1 probe required by the build plan. It is not production capture code.

## What the harness now covers

The `:spikes:geckoview` app contains one loopback-only HTTP fixture server, three base Gecko sessions
(A, B, and private P), optional five additional sessions for the eight-tab memory probe, the bundled
WebExtension, a bounded metadata result recorder, and manual controls for lifecycle/reconnect tests.

The extension deliberately emits no request/response header values, no bodies, no cookies,
authorization values, or full URLs. For fixture traffic it emits only a known local pathname plus a
synthetic tab marker (for example `/api-json#A`) so cross-tab joins can be checked without user data.

### SPIKE-01 / SPIKE-02 — attribution

- Content script announces at `document_start`.
- Background returns JavaScript `sender.tab.id`.
- Session-scoped native messaging records `MessageSender.session`.
- `webRequest.details.tabId` is recorded for the synthetic local requests.
- Result JSON reports session mismatches, unstable JS tab bindings, and cross-tab conflicts.

### SPIKE-04 — detail-field availability

The probe records only the sorted names of keys present on each `webRequest` details object,
resource type, method, request-body-field presence, document ID, frame ID and request ID.
The local fixture generates GET, JSON POST, form POST, multipart POST, GraphQL-shaped POST,
redirects, WebSocket handshake, SSE, failed requests, prefetch/preload, beacon, service worker
registration and continuous background fetches.

### SPIKE-05 / SPIKE-06 — document ID and unattributed requests

Document IDs are grouped by safe fixture tag. `tabId == -1` and null tab IDs are counted rather
than reassigned.

### SPIKE-07 — navigation security signal

The disposable sessions record whether Gecko emitted a security callback, whether it reported
secure state/certificate presence, security mode, and mixed-content modes. Certificate contents,
origin and host are not exported.

### SPIKE-08 — content crash/kill

The activity records `ContentDelegate.onCrash` and `onKill`. The **Crash Content** button requests
`about:crashcontent`; if that URI is unsupported by the pinned build, record that as a probe result
rather than treating the absence of a callback as success.

### SPIKE-09 — process-death restore

**Save+Kill** persists only the latest normal-session `SessionState` strings, writes the safe result
summary, then kills the app process. Relaunch the spike app and verify A/B receive restore requests.
Private P is deliberately never persisted.

### SPIKE-10 — page size and eight-tab memory

At startup the app records `_SC_PAGESIZE` and the Android memory class. **8 Tabs** loads eight
sessions with synthetic traffic and records total PSS after five seconds. Run this on the intended
16 KiB-page/low-memory device class as required by the capability matrix.

### SPIKE-11 — private isolation

Load A, B and P. Confirm P gets its own stable JS/WebRequest tab ID and never shares A/B attribution.
Then run **Save+Kill** and confirm P does not restore.

### SPIKE-12 — extension reconnect/idempotence

**Drop Port** forces the native bulk port to disconnect. The extension retries after 250 ms.
The summary must show another port connection and resumed heartbeats/events. **Ensure Ext** calls
`ensureBuiltIn` again; it must remain idempotent and the probe must continue.

## Required device run

1. Build and install `:spikes:geckoview:assembleDebug`.
2. Launch and confirm `extension-ready`, `webRequest available=true`, and a bulk-port connection.
3. Tap **Load A**, **Load B**, then rapidly alternate **Tab A** / **Tab B** for at least 20 seconds.
4. Tap **Redirect A** and **Redirect B**.
5. Tap **Load P** and exercise normal/private switching.
6. Tap **Drop Port**, wait for a new connection/heartbeats, then generate more A/B traffic.
7. Tap **Ensure Ext** twice and confirm capture metadata continues.
8. Tap **8 Tabs** and retain the automatic memory measurement.
9. Tap **Crash Content** and record which callback appears, if any.
10. Tap **Save+Kill**, relaunch, verify A/B restoration and P non-restoration.
11. Tap **Export**. Retrieve `files/m1-spike-result.json` with Android Studio Device Explorer or
    `adb run-as app.taho.browser.spike cat files/m1-spike-result.json`.

## Pass gate

M1 is passed only when the physical-device result demonstrates all of the following:

- exact pinned artifact compiled and installed;
- built-in extension installed and repeated `ensureBuiltIn` remained usable;
- required `webRequest` listeners registered;
- top-level session announcements had the correct registered GeckoSession;
- each app tab retained one JavaScript tab ID for its lifetime;
- synthetic A/B/P traffic produced zero cross-tab conflicts;
- redirect/background traffic stayed correctly attributed or explicitly unattributed;
- field availability, `documentId`, and `tabId == -1` behavior were recorded;
- port disconnect was followed by reconnect and resumed events;
- security callback availability was recorded honestly;
- crash/kill behavior was recorded;
- normal-state process-death restoration and private non-restoration were observed;
- page-size and eight-tab memory evidence were recorded.

A failure of trustworthy attribution blocks production observation wiring. No selected-tab or URL
heuristic may be added as a workaround.
