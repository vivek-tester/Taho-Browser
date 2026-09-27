# ADR 0007 — Gated M4 observation and direct transfer

Status: Accepted for gated M4 source implementation  
Date: 2026-09-27

## Context

M4 requires a production request path from Gecko observation through bounded native ingestion,
review/normalization and direct Browser→Taho handoff. The build plan also requires trustworthy
per-tab attribution and an actual Project-Taho editor import before M4 can exit.

Those device/two-app prerequisites are not yet satisfied. Enabling production observation early would
create false confidence about attribution, while inventing a Project-Taho Intent action would create a
non-existent integration surface.

## Decisions

1. The production WebExtension, identity handshake, native protocol and in-memory assembler are
   implemented now but activated only when `BuildConfig.M1_ATTRIBUTION_VERIFIED` is true.
2. The committed build keeps that flag false. Capture therefore remains OFF in the user-facing app
   until measured M1 device evidence is accepted.
3. Identity uses the content/background handshake and native `sender.session`/origin validation.
   Bulk webRequest events never create an ExtTabId→TahoTabId binding and never fall back to the
   selected/recent tab.
4. Native IPC enforces the documented type-specific byte ceiling before JSON parsing. Accepted parsed
   events then enter the bounded priority-aware queue; any eviction/drop/sequence gap surfaces
   `LIMITED`.
5. M4 keeps completed requests in memory only. Durable capture storage belongs to M5.
6. The M4 body slice supports JSON request bodies. Unsupported/opaque body shapes remain unavailable
   or review-blocked rather than being guessed.
7. Header/query secrets become refs before display/transfer. JSON bodies with detected or
   credential-shaped sensitive content are review-blocked and discarded from the transfer candidate
   after classification.
8. UI receives pre-masked display values only. A sensitive UI header rejects an unmasked display
   value by construction.
9. Parameterize is the default transfer policy. Cookies retain the existing MASK default. Explicit
   requires a separate live-secret provider and is disabled in the M4 UI because no consent-safe
   provider is retained.
10. Direct handoff is limited to the v1 envelope budget. Large payload transfer remains M6.
11. The Android sender requires a caller-supplied target package and action. The known Project-Taho
    package may be configured, but the structured receiver action remains blank until the real
    receiver is implemented and verified.
12. A receipt must match the pending `transferId`. Import is never interpreted as execution.
13. The M2 receiver harness may verify contract semantics and zero-execution behavior, but it is not
    accepted as the M4 two-app integration result.

## Consequences

The repository can compile/test the production-shaped M4 pipeline without silently activating an
unverified capture path. The gated APK remains a browser with capture OFF by default. M4 cannot be
declared complete until the M1/M3 device dependencies and the actual Project-Taho receiver/editor
path have been exercised together on a device.
