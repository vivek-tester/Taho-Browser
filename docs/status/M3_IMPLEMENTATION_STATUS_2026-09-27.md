# M3 implementation status — 2026-09-27

Status: SOURCE/AUTOMATED IMPLEMENTATION COMPLETE — physical-device exit gate open  
Branch: `build/m3-browser-baseline`

## Implemented browser baseline

The production source supports real GeckoView browsing, URL/search input, back/forward/reload,
new/close/switch tabs, loading/failure UI, normal/private tab types, origin-labelled permission
prompts, allowlisted external-app handoff, content crash/kill recovery and normal-session
restoration.

M3 hardening adds:

- a deterministic navigation policy used by the production Gecko delegate;
- explicit fail-closed denial for arbitrary, `intent:`, `javascript:`, `file:` and custom schemes;
- user-gesture and redirect gates for external app handoff;
- origin rendering that never echoes URL userinfo/path/query/fragment into permission UI;
- a persistence policy proving private tabs and private Gecko SessionState never enter the disk model;
- a versioned session codec that rejects malformed/unknown state, removes duplicate tab IDs and
  clears an invalid selected-tab reference;
- JVM regression tests for navigation, private persistence, session codec and capture-OFF baseline;
- CI execution of runtime/shell unit tests;
- a downloadable debug APK artifact for physical M3 acceptance.

## Automated evidence

Validated on branch head `701c46b5ca31ba9af355fd30fde89ceeeba5fae9`, GitHub Actions run
`36337004128`:

- `:app:assembleDebug` — PASS;
- `:spikes:geckoview:assembleDebug` and spike unit tests — PASS;
- `:browser:runtime:testDebugUnitTest` — PASS;
- `:browser:shell:testDebugUnitTest` — PASS;
- existing pure-domain/architecture gates — PASS;
- APK artifact upload — PASS.

The run produced artifact `taho-browser-m3-debug` (artifact id `10937710935`) for physical-device
acceptance. This build evidence proves compilation/test execution only; it does not substitute for
the physical D3/D4/D5/D7/D15 results.

## Physical-device exit gate

The build plan names D3/D4/D5/D7/D15. Those remain physical-device evidence, not CI claims.

There is a specification dependency inside the supplied test architecture: D4 is written as process
death mid-capture and D15 includes active capture/transfer, while M3 itself requires browsing with
capture disabled or failing. Therefore M3 can execute the browser-lifecycle subsets of D4/D15 now,
but their capture/transfer assertions cannot be truthfully marked passed before later milestones.

The executable/manual acceptance procedure is in `docs/device/M3_DEVICE_ACCEPTANCE.md`.
