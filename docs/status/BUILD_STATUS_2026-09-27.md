# Build status — 2026-09-27

Canonical integration branch: `main`

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

**Source/automated status:** complete; physical-device exit gate NOT PASSED.

Implemented and automated:

- real GeckoView canvas hosted by Compose chrome;
- URL/search omnibox, including regression coverage for localhost-with-port input;
- back/forward/reload and Android Back routing;
- new/close/select tabs and user-initiated target=_blank → real browser tab;
- popup denial without a user gesture;
- normal/private tab creation with a tested private non-persistence policy;
- versioned normal-session codec and Gecko SessionState restoration path;
- loading/failure states and content crash/kill Reload Page recovery;
- origin-aware site permission prompts that do not echo URL credentials/path/query/fragment;
- Android location/camera/microphone runtime permission bridge;
- unsupported/concurrent permission requests fail closed;
- allowlisted external app handoff for mailto/tel/sms/geo;
- redirected/programmatic external launches denied;
- arbitrary custom/intent/javascript/file schemes denied by the production navigation policy;
- return from external apps preserves the browser session by process-scoped controller design;
- runtime/shell JVM regression tests in CI;
- exact pinned GeckoView production APK build in CI;
- downloadable M3 debug APK artifact for device acceptance.

Automated evidence: branch head `701c46b5ca31ba9af355fd30fde89ceeeba5fae9`, Actions run
`36337004128`, all configured jobs PASS.

Still required for the physical M3 exit gate:

- D3 external-app return on device;
- D4 browser process-death subset and normal/private restore evidence;
- D5 content-process crash/OOM recovery on device;
- D7 physical Android 14 / 15 / 16 coverage;
- D15 browser/sheet rotation subset;
- device verification of permission allow/deny/dismiss and target=_blank behavior.

The supplied D4/D15 definitions also contain later capture/transfer assertions. Those portions cannot
be truthfully completed in M3 while production capture and transfer are absent; the M3 device
procedure records that dependency explicitly.

## M4 — One real end-to-end request

**Source/automated status:** gated implementation present; M4 exit gate NOT PASSED.

Implemented behind explicit safety gates:

- production built-in WebExtension source and native identity/bulk handshake;
- no selected-tab fallback: ExtTabId is accepted only after sender-session/origin validation;
- per-message byte caps before JSON parsing plus bounded priority-aware parsed ingestion;
- reconnect connection IDs, monotonic sequence validation and LIMITED state on gaps/drops;
- in-memory GET/JSON POST assembly with provenance/completeness;
- unresolved attribution excluded from per-tab request lists;
- structural header/query/JSON-body secret classification;
- secret-bearing or suspicious JSON bodies discarded from the transfer candidate after review;
- versioned normalization and safe display projection;
- masked Capture Summary → Inspector → Send-to-Taho confirmation UI;
- Parameterize/Mask policy selection, with Explicit disabled unless a consent-safe live-secret source exists;
- direct-transfer envelope JSON, 256 KiB budget enforcement, explicit transfer lifecycle and receipt parsing;
- Android sender that requires an explicit package/action and never invents a Project-Taho receiver action;
- loopback GET and JSON POST contract-slice tests through the M2 receiver harness;
- receiver-harness assertions that import causes zero execution and zero persistence.

Production activation is intentionally blocked in the app build:

- `M1_ATTRIBUTION_VERIFIED=false`, so the production observer remains OFF;
- `TAHO_TRANSFER_ACTION=""`, so Send-to-Taho cannot launch an absent structured receiver.

Still required before M4 may be marked complete:

1. Pass the M1 physical-device attribution/capability gate and explicitly enable production observation.
2. Demonstrate the Browser on-device through the still-open M3 physical acceptance gate.
3. Implement the real Project-Taho structured receiver/editor import path after that Browser gate, preserving
   the unsaved/import-not-execute boundary.
4. Configure the confirmed Project-Taho transfer action and receipt path.
5. Run the build-plan two-app device slice with a real captured GET and JSON POST, verifying
   method/query/headers/body/provenance and zero automatic execution.

The pure receiver harness and loopback contract slice are automated evidence only; they do not satisfy
the required two-app/device M4 exit condition.

## Next phase boundary

Do not start M5 durability/persistence work until the M4 gate state above is explicitly resolved or
the project chooses to continue source work while carrying the documented device dependency.


## M5 — Capture durability and supported request types

**Source/automated status:** complete candidate; physical/device exit evidence still required.

Implemented:

- Room-backed capture sessions, transactions and encrypted body metadata;
- AES-GCM capture encryption with Android Keystore production implementation;
- inline encrypted bodies and encrypted file-backed bodies above the inline threshold;
- immediate SESSION_ONLY deletion on explicit session close, including file-backed ciphertext;
- retention sweep for abandoned session-only data with file cleanup;
- private capture memory-only/non-persistence;
- interrupted STARTED recovery to PARTIAL;
- committed-record survival across repository/database recreation;
- storage degradation states for key invalidation, low storage, missing/corrupt bodies and database failures;
- separate capture-data clear operation with no browser-store dependency;
- relevance classification, default relevance filtering and search/method/state/tab filters;
- redirect evidence;
- GraphQL, form, multipart and binary body classification with explicit unsupported/limited states;
- COMPLETE/PARTIAL/TRUNCATED/UNAVAILABLE/NOT_APPLICABLE presentation states;
- durable records surfaced after lifecycle/process recovery without pretending re-transfer support;
- committed Room v1 schema checked in CI.

Automated evidence is required on the final M5 branch/main head before merge.

Still required for the full M5 exit gate:

- physical process-death demonstration with committed records and interrupted-record PARTIAL labeling;
- device evidence for Keystore/key-loss behavior;
- device low-storage behavior;
- unresolved-attribution durable-view evidence;
- private-session non-persistence evidence on device;
- measured Gecko support confirmation for the request-body types advertised as supported/limited.

M6 secure large-payload handoff is not included.
