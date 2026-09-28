# M7 implementation status — 2026-09-28

Status: SOURCE/AUTOMATED CANDIDATE IMPLEMENTED — physical/device UX exit pending  
Branch: `build/m7-product-ux`  
Base: Browser M6 main `19d21bc38ded848754bbe72f6fb57f63aa30faae`

## Scope implemented

M7 finishes the supplied Browser product UX without changing the Browser/Taho execution boundary.

Implemented:

- browser-first bottom chrome and real per-tab relevant capture counts;
- Capture Summary with Relevant / All / Auth / API filters;
- noise visible only under All, dimmed and read-only;
- required relevant empty-state copy plus filter-specific empty states;
- Summary → tall Inspector → confirmation progressive disclosure;
- Summary filter, scroll position and selected row retained after returning from Inspector;
- Inspector tabs: Overview, Headers, Body, Response and Timing;
- fixed completeness vocabulary rendered from capture evidence only;
- headers displayed through the pre-masked projection; raw credentials never enter the shell UI model;
- secret-category-aware Parameterize / Mask / Explicit preview;
- Cookie keeps the Browser MASK default when Parameterize is selected;
- Explicit remains unavailable without a consent-safe live-secret source;
- provenance for source product/version, capture session, tab, captured time, engine, normalizer and redirects when known;
- private-session persistence warning before handoff;
- failure copy for missing/outdated Taho, large secure transfer, rejected/failed import and page crash;
- Settings surface and separate Clear capture data confirmation;
- capture clearing serialized with persistence writes and leaves browser/site state untouched;
- 44–48dp key hit targets and TalkBack descriptions for capture state, filters, sensitive headers, body completeness and Send to Taho;
- 64 KiB UI-only body preview cap for large bodies, clearly distinguished from capture truncation;
- transfer serialization and secure artifact preparation moved off the UI thread for slow-storage resilience;
- prepared artifacts cancelled/cleaned if rotation destroys the originating Activity before launch;
- no mock requests, no fake success states and no timer-driven handoff completion.

The S3 tall Inspector is the current M7 expansion state. The UI authority reserves the separate fullscreen technical variant for large/raw future views; M7 does not fabricate a second data surface merely to satisfy that future variant.

## Capability honesty

- `M1_ATTRIBUTION_VERIFIED` remains false.
- Production capture is not enabled by M7.
- Response body, TLS and timing rows use explicit completeness from capture evidence.
- Missing data is rendered as unavailable/not captured rather than inferred.
- Partial/truncated request bodies are not presented as complete.
- Recovered durable rows do not pretend raw header/body detail or re-transfer support exists when the safe projection does not contain it.
- The Browser continues to prepare/transfer requests only; Project-Taho remains the API execution application.

## Automated evidence

Code candidate: `8fd75a35936afd46b87dfa5b71655144f7060604`  
GitHub Actions: `36379006124` — PASS

Passed jobs include:

- architecture gates;
- pure capture-domain, transfer-contract/core and test-support suites;
- Browser shell M7 filter/accessibility/policy-preview tests;
- `evil-example.com` host-boundary relevance regression;
- GeckoView spike/runtime/shell/observation/persistence/transfer Android unit tests;
- production Browser + pinned GeckoView debug assembly;
- Room schema drift check;
- M7 Browser APK artifact upload.

Earlier failing M7 runs were implementation iterations and are not counted as exit evidence. The final code candidate above is the evidence-bearing run.

## Physical/device evidence still required

M7 is not a full build-plan exit until device evidence covers:

- D10 — slow-storage behavior/no UI jank;
- D15 — rotation during active capture, transfer and mid-sheet;
- D16 — TalkBack through browse → summary → inspector → confirmation/transfer;
- D17 — 1.3× text scale and reduced motion;
- inherited M1 attribution/capability proof and the still-open M3/M4/M5/M6 physical slices.

No physical-device pass is claimed here.

## Phase boundary

M8 release hardening/beta work is not part of this change and has not started.
