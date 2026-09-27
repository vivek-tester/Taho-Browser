# M5 implementation status — 2026-09-28

Status: SOURCE/AUTOMATED CANDIDATE IMPLEMENTED — physical/device exit gate pending  
Branch: `build/m5-capture-durability`

## Implemented

- Durable Room capture store with committed schema v1.
- Capture-session, transaction and body records remain independent from Gecko browser storage.
- AES-GCM encryption for sensitive headers/bodies; production key material is Android Keystore scoped.
- Bodies up to the inline limit remain encrypted in Room; larger bodies use encrypted app-private files.
- SESSION_ONLY records are deleted immediately when an explicit capture session closes.
- Crash-recovery retention sweeps remove abandoned session-only rows and associated file-backed ciphertext.
- PRIVATE captures never persist.
- Interrupted STARTED transactions recover as PARTIAL.
- Relevance classification and search/tab/method/state/category filters operate on safe/redacted fields.
- Redirect chains are retained as evidence.
- JSON/GraphQL/form/multipart/binary request-body handling exposes measured/explicit support limitations rather than fabricating payloads.
- Completeness is represented as COMPLETE, PARTIAL, TRUNCATED, UNAVAILABLE or NOT_APPLICABLE in the inspector.
- Storage degradation never blocks browsing and is surfaced to the UI.
- Durable history can be rendered after repository/database recreation.
- M6-only durable re-transfer/large-payload transport remains blocked.

## Automated exit evidence

The final branch must pass:

- architecture/static gates;
- capture domain tests;
- `:capture:persist:testDebugUnitTest`;
- Browser observation/runtime/shell tests;
- production Browser APK assembly;
- committed Room schema drift check;
- M5 APK artifact upload.

The persistence suite covers database reopen, interrupted recovery, private non-persistence,
key invalidation, low storage, missing files, clear-capture independence, filters, abandoned-session
closure, immediate SESSION_ONLY close deletion, and retention behavior.

## Physical/device evidence still required

M5 cannot be marked as a full build-plan exit until device evidence demonstrates process death,
real Keystore behavior, low storage, unresolved-attribution views, private non-persistence and the
actual Gecko body capabilities used by the supported-type labels.

No M6 secure handoff work is part of this phase.
