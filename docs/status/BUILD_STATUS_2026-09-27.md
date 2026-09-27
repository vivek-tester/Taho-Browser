# Build status — 2026-09-27

Branch: `build/m0-m2-foundation`

## M0 — Reconcile baseline

**Source status:** substantially complete for the current slice.

- Product boundary recorded: Taho Browser captures/reviews/transfers; Project-Taho tests APIs.
- Project-Taho discovery completed against `vivek-tester/Project-Taho`.
- Transfer contract v1/schema/fixtures exist in the Browser repo.
- Session-restore, permission, external-navigation and secret-classification decisions are recorded as ADRs.
- Gecko-dependent facts remain labelled candidate/unverified where physical-device evidence is missing.

## M1 — Prove GeckoView feasibility

**Status:** harness implemented; device gate NOT PASSED.

- Candidate GeckoView pin: `156.0.20260921121718`.
- Disposable `:spikes:geckoview` two-tab attribution harness exists.
- Built-in extension emits metadata-only session/tab/webRequest probes.
- SPIKE-01 procedure and pass/fail criteria are documented.

Still required on a physical Android device:

- exact pinned-build compile;
- extension install/reconnect;
- first-load observation;
- two-tab attribution;
- redirects/background traffic;
- crash/restore/private behavior;
- capability recording.

No production capture source is allowed to claim trusted attribution until this evidence passes.

## M2 — Production foundation

**Status:** complete for the M2 source/build/test exit gate.

Implemented and automated:

- native Kotlin/Compose module graph with pinned build dependencies;
- pure `:capture:domain`, `:contract:taho-transfer`, `:transfer:core` plus pure test-support modules;
- architecture/static dependency gates;
- corrected transaction reducer with bounded de-duplication and write-once attribution;
- property/unit coverage plus language-neutral fixtures for missing, reordered, duplicate and recovery sequences;
- secret-safe byte-backed values, structural secret detection, masked display boundaries and log-sink gates;
- versioned normalisation, immutable safe provenance, byte budgets and bounded backpressure primitives;
- Browser→Taho v1 contract and validator;
- loopback-only controlled HTTP fixture service;
- companion Taho receiver harness that validates, deduplicates and stages imports without execution or persistence;
- CI coverage for pure modules and production Android/spike assembly.

The real Project-Taho receiver is intentionally not implemented in M2. Per the product boundary, the
harness proves the contract-side behavior without turning Taho Browser into an API client or bypassing
the later M4 two-app integration gate.

## M3 — Browser baseline

**Source status:** candidate implementation complete; device exit gate NOT PASSED.

Implemented in source:

- real GeckoView canvas hosted by Compose chrome;
- URL/search omnibox;
- back/forward/reload;
- Android Back routed through browser history/chrome;
- new/close/select tabs;
- user-initiated target=_blank → real browser tab;
- normal/private tab creation;
- loading/failure states;
- content crash/kill state with Reload Page recovery;
- normal-tab SessionState restore candidate;
- private tabs excluded from persisted restore state;
- origin-aware site permission sheet;
- Android location/camera/microphone runtime permission bridge;
- unsupported/concurrent permission requests fail closed;
- allowlisted external app handoff for mailto/tel/sms/geo;
- redirected/programmatic external launches denied;
- return from external apps preserves the browser session by design.

Still required before marking M3 complete:

- compile against the exact pinned GeckoView artifact;
- run D3/D4/D5/D7/D15 device scenarios;
- verify normal/private process-death behavior;
- verify content crash replacement on device;
- verify permission allow/deny/dismiss behavior;
- verify target=_blank/pop-up behavior;
- verify external app round trips.

## M4 — One real end-to-end request

**Status:** intentionally not started as a production capture path.

Dependencies not yet satisfied:

1. M1 trustworthy attribution device gate.
2. M3 pinned-build/device exit evidence.
3. Project-Taho structured receiver implementation.

Per the product decision, Project-Taho receiver work begins after the Browser is demonstrably running.
The shared contract remains ready so the receiver can be implemented without redesigning the handoff.

## Next source work while device verification is pending

Safe work that does not assume Gecko attribution may continue in pure modules:

- secret representation/property tests and static leak gates;
- byte-budget/backpressure primitives;
- normalization rules and safe-provenance projection;
- contract compatibility/fuzz fixtures;
- controlled fixture definitions for the eventual device run.

Production Gecko observation/capture wiring remains behind M1.
