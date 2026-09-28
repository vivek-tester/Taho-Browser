# TAHO_BROWSER_RISK_REGISTER.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26

**Scoring:** Likelihood (L) 1–5 × Impact (I) 1–5. Exposure = L × I.
**Bands:** ≥20 Critical · 12–19 High · 6–11 Medium · ≤5 Low
**Rule:** every risk has an owner, a trigger, and a *decision point* — not just a mitigation. A risk
without a decision point is an unowned risk.

---

## 1. Critical and high risks

### R-01 — `webRequest` is unavailable to GeckoView built-in extensions

| | |
|---|---|
| **L / I / Exposure** | 3 × 5 = **15 High** |
| **Evidence** | GeckoView consumer docs document *only* messaging; they never mention `webRequest`. A 2019 third-party note lists `webRequest` ✔ for a Nightly build; a 2020 GeckoView issue reports `browser.webRequest` `undefined`. Firefox for Android (which is GeckoView) supports it, so the engine does — but GeckoView's *embedder* configuration is unconfirmed. |
| **Impact** | The observation layer collapses. Capture would fall back to page-level instrumentation, which misses non-JS traffic, cache-served responses, and anything outside the page's own APIs. Product copy must change (PRD §131). Phases 4–8 largely re-planned. |
| **Mitigation** | `CaptureEventSource` is an interface (Component §4.5); the fallback is a swap at L3, not a rewrite of L4. SPIKE-01 runs in Phase 0.5, before any capture work. |
| **Trigger** | SPIKE-01 shows `webRequest` undefined for a built-in extension. |
| **Decision** | **Stop. Re-plan before Phase 3.** Evaluate: (a) page-instrumentation fallback and an honest product promise; (b) a privileged experiment API on a privileged built-in extension; (c) reconsider the engine — a last resort, and the brief requires documenting the reason rather than silently switching. Do **not** quietly downgrade the product. |
| **Owner** | Architect |

### R-02 — `sender.tab.id` ≠ `webRequest.details.tabId`

| | |
|---|---|
| **L / I / Exposure** | 3 × 5 = **15 High** |
| **Evidence** | True in Firefox (one ID space). Unverified for GeckoView built-in extensions. |
| **Impact** | The attribution join in System Architecture §5 breaks. Release Gate A is unreachable. Fallback heuristics (`documentId`, `originUrl`) carry a real false-association rate — which is precisely the defect the PRD's #1 P0 requirement exists to prevent. |
| **Mitigation** | `documentId` as a second join key (SPIKE-05). Multi-value candidate keys designed into `FrameAttributor` rather than a single hard-coded `Int`. SPIKE-02 validates under realistic multi-tab stress. |
| **Trigger** | SPIKE-01 shows divergent IDs. |
| **Decision** | If no reliable join exists, **stop and re-architect the observation layer** — potentially around per-session content-script channels carrying a session-scoped identity, rather than `webRequest.tabId`. Do not ship heuristics that guess. |
| **Owner** | Architect |

### R-03 — Response-body capture destabilises page loads

| | |
|---|---|
| **L / I / Exposure** | 4 × 3 = **12 High** |
| **Evidence** | Documented Gecko behaviour: the extension **must** `close()` or `disconnect()` the `StreamFilter` or the response is held open forever. Additionally, script requests served from Gecko's optimised byte cache are not available in useful form. |
| **Impact** | Pages hang. A browser that hangs pages is a failed browser, regardless of capture quality. |
| **Mitigation** | Response-body capture is **off by default** and **postponed** (Plan §3). If ever enabled: explicit host allowlist, hard byte/time caps, a watchdog force-`disconnect()`, and SPIKE-03's 500-iteration latency test. `webRequestBlocking` is removed from the manifest if the feature is dropped. |
| **Trigger** | Any SPIKE-03 iteration showing a hang or a latency regression. |
| **Decision** | **Postpone indefinitely.** Completeness reports `UNAVAILABLE`; PRD §31 and §107 explicitly permit this. This is the correct default for a browser. |
| **Owner** | Architect |

### R-04 — Cross-tab attribution defect ships

| | |
|---|---|
| **L / I / Exposure** | 3 × 5 = **15 High** |
| **Evidence** | The most common class of capture bug, and the PRD's #1 P0 item (§10, §49). The temptation is structurally present: the selected tab is right there. |
| **Impact** | Release Gate A fails. The product's central claim is falsified. |
| **Mitigation** | Four-valued `Attribution`; no code path from an event to "the current tab"; `resolve()` never returns `Known` for an unknown id; CI grep fails the build if the capture module reads `displayIndex`; D1 runs 1 000 events across multiple tabs; property test M3 enforces write-once. |
| **Trigger** | Any single D1 misattribution, or a CI grep hit. |
| **Decision** | Hard stop. No release. Fix the design, not the symptom. |
| **Owner** | Architect + QA |

### R-05 — Raw secret reaches a log, crash file, or analytics payload

| | |
|---|---|
| **L / I / Exposure** | 3 × 5 = **15 High** |
| **Evidence** | The single highest-consequence defect class. One `Log.d("body", …)` and the whole credential-defence posture is void. |
| **Impact** | Release Gate B fails. Users' credentials on disk in plaintext, in files they cannot see or delete. |
| **Mitigation** | Secrets are a distinct type (`SecretValue`) held as `ByteArray`, not `String`; loggers accept only an opaque `SecretRef`; allow-list logging API with no generic `String` sink in capture-reachable modules; CI grep gate; S1 end-to-end test asserting the plaintext is in no log buffer; `SecretValue.toString()` redacts. |
| **Trigger** | Any CI grep hit, or any S1 failure. |
| **Decision** | Hard stop. No release. |
| **Owner** | Security owner |

### R-06 — Token-bearing URLs stored and searchable in plaintext

| | |
|---|---|
| **L / I / Exposure** | 3 × 3 = **9 Medium** |
| **Evidence** | URLs must be stored for search and display (PRD §59), but `?api_key=…` is a real pattern. Storage §3.2 mitigates by redacting query values whose *key* classifies as secret. |
| **Impact** | Residual credential exposure in the `transaction` table and in search results. |
| **Mitigation** | Secret-classified query values stored redacted; the full URL only inside encrypted provenance; search never touches encrypted columns; a token is not in any searchable field. |
| **Residual risk** | A secret in a **path** segment or an unrecognised query key is not caught. Path-based tokens are rare but exist. |
| **Trigger** | A real capture shows a credential in a path segment. |
| **Decision** | Extend classification to path segments if observed in the wild. Accept the residual risk explicitly rather than claiming full coverage. |
| **Owner** | Security owner |

### R-07 — GeckoView engine upgrade breaks capture or attribution

| | |
|---|---|
| **L / I / Exposure** | 3 × 4 = **12 High** |
| **Evidence** | PRD §130 treats upgrades as compatibility changes. WebExtension internals, `webRequest` field availability, and stream-filter behaviour are all engine-version-sensitive. `ensureBuiltIn` idempotence and permission handling can shift. |
| **Impact** | Silent capture regression — the most dangerous kind, because the browser still works and the user does not know data is missing. |
| **Mitigation** | Engine pinned in the version catalogue; capability matrix is **version-scoped**; `engine_version` stored per capture (Storage §2.7); the full SPIKE-01…12 suite re-runs on every bump; PRD §130 forbids upgrading to chase an unverified capability. |
| **Trigger** | Any engine version change. |
| **Decision** | Bump only with a green spike suite. If a spike fails on a new version, either fix forward or stay pinned — never ship an unverified engine. |
| **Owner** | Architect |

### R-08 — The Flutter decision is wrong

| | |
|---|---|
| **L / I / Exposure** | 2 × 3 = **6 Medium** |
| **Evidence** | The PRD's diagram implies Flutter above a native bridge. This architecture deliberately does not, on the grounds that N live `GeckoView`s inside Flutter platform views is the dominant risk (System Architecture §3.2). The team is an experienced Flutter shop (a large Flutter app already exists), so there is real institutional pull toward Flutter. |
| **Impact** | If Flutter hosting is in fact fine at N instances, this is unnecessary native work. If it is fine and we went native, we lost reuse — a cost, not a defect. |
| **Mitigation** | The decision is documented with its reasoning (System Architecture §3.2) rather than asserted. A **spike is warranted**: host 4–8 `GeckoView`s in Flutter `AndroidView`s and measure compositing cost, input arbitration, and accessibility. That is cheap and would settle the question with data. |
| **Trigger** | Phase 1 planning. |
| **Decision** | Run the Flutter-hosting spike during Phase 0/1. If it passes convincingly, revisit this document and record the change. **This is a documented, revisitable decision, not a settled truth.** |
| **Owner** | Architect |

---

## 2. Medium risks

### R-09 — Secrets recoverable from process memory

| | |
|---|---|
| **L / I / Exposure** | 3 × 2 = **6 Medium** |
| **Evidence** | `ByteArray` secrets are zeroed on release, but a determined local attacker with root/ADB on a non-hardened device can read process memory. |
| **Impact** | Secret exposure in a narrow threat window. |
| **Mitigation** | Bounded plaintext lifetime; zero-on-release; no `String` copies; no secret in logs. |
| **Residual risk** | **Not fully mitigable** on a general-purpose Android browser. Accepted and stated. |
| **Decision** | Accept. Do not claim memory-level confidentiality. |
| **Owner** | Security owner |

### R-10 — Extension `installBuiltIn` fails or is slow on some devices

| | |
|---|---|
| **L / I / Exposure** | 2 × 3 = **6 Medium** |
| **Evidence** | Official docs note installing at every start "could be slow", and `ensureBuiltIn` is idempotent by version. Storage-backed profiles on slow devices are the likely case. |
| **Impact** | Capture `ERROR` at startup; slow cold start. |
| **Mitigation** | `ensureBuiltIn` (not `installBuiltIn`); install before session creation but **off the critical render path**; the browser renders regardless (Lifecycle §3.3); retry with backoff; SPIKE-12. |
| **Trigger** | Measurable cold-start regression or repeated install failure. |
| **Decision** | If it proves persistently slow, move installation to a post-first-render background task and accept a brief `ERROR`-then-`OBSERVING` transition. |
| **Owner** | Android owner |

### R-11 — Capture load degrades browsing responsiveness

| | |
|---|---|
| **L / I / Exposure** | 3 × 3 = **9 Medium** |
| **Evidence** | Busy pages generate thousands of requests per minute. Naive capture on the UI thread would jank the browser and violate "Browser First". |
| **Impact** | Jank, ANRs, and a browser that feels broken — a direct PRD violation. |
| **Mitigation** | `EventIngress` is O(1) with no I/O, no crypto, no Room; all heavy work on the capture pipeline's own coroutine with a `SupervisorJob`; bounded queues shed low-priority data first; P0/P1 have a reserved floor; degradation is surfaced, not hidden; ingress benchmarks in CI (< 50 µs/event); D10 on slow storage. |
| **Trigger** | Ingress benchmark regression, or visible jank in D10. |
| **Decision** | Tighten caps and shed more aggressively. Browsing smoothness outranks capture completeness — always. |
| **Owner** | Android owner |

### R-12 — Room database grows without bound

| | |
|---|---|
| **L / I / Exposure** | 3 × 2 = **6 Medium** |
| **Impact** | Storage exhaustion; the app starts failing for reasons unrelated to capture. |
| **Mitigation** | Explicit budgets (Storage §8); retention policies per session kind; `RetentionSweeper`; soft ceiling with policy-ordered eviction; every eviction counted and surfaced. D13 on a near-full device. |
| **Trigger** | Approaching the 512 MB ceiling in the field. |
| **Decision** | Tighten defaults toward `SESSION_ONLY`. Silent retention of sensitive traffic is explicitly discouraged by PRD §84. |
| **Owner** | Android owner |

### R-13 — Content-process crash loses in-flight capture

| | |
|---|---|
| **L / I / Exposure** | 3 × 2 = **6 Medium** |
| **Impact** | In-flight transactions lost; risk of the user believing they captured something they did not. |
| **Mitigation** | Transactions persisted on every state change, so a crash yields `PARTIAL` rather than nothing; crash UI offers "View Captured Requests"; attribution becomes `Detached`, never reassigned. |
| **Trigger** | D5 shows lost persisted rows. |
| **Decision** | Accept `PARTIAL` as the honest outcome. Never present it as complete. |
| **Owner** | Android owner |

### R-14 — Transfer artifact leaks via a stale URI grant

| | |
|---|---|
| **L / I / Exposure** | 2 × 4 = **8 Medium** |
| **Impact** | A captured request — possibly containing credentials under `EXPLICIT` policy — readable by another app. |
| **Mitigation** | App-private encrypted file; `FileProvider` with a narrow path; grant to one package, read-only; revoked after receipt; 1-hour sweeper; artifact deleted on receipt. |
| **Trigger** | Any artifact surviving its TTL. |
| **Decision** | Hard stop. A leaked artifact is a Gate B failure. |
| **Owner** | Security owner |

### R-15 — Taho receiver accepts a forged or hostile transfer

| | |
|---|---|
| **L / I / Exposure** | 2 × 4 = **8 Medium** |
| **Impact** | A malicious app could cause Taho to import a crafted request. Mitigated in severity by "import never auto-executes" — but the user could still be socially engineered into pressing Send. |
| **Mitigation** | 16-step validation before any object is constructed (Security §4.2); package check; size caps; CR/LF rejection; declared-vs-actual size check; fuzz suite; no deep links in MVP; no auto-execution (Gate E). |
| **Trigger** | S5 or S3 failure. |
| **Decision** | Hard stop. Gate D/E. |
| **Owner** | Security owner + Taho-side owner |

### R-16 — Relevance engine misclassifies real traffic

| | |
|---|---|
| **L / I / Exposure** | 3 × 3 = **9 Medium** |
| **Evidence** | Classification is heuristic. A SPA calling `/graphql` on a CDN host, or an API on a different subdomain, or a first-party check against an IP literal or punycode host, all have edge cases. |
| **Impact** | Either noise (the browser starts feeling like a network log — a PRD violation) or silence (a relevant request is never surfaced — a product failure). |
| **Mitigation** | Pure, deterministic, unit-tested function; hostname-boundary matching only; `main_frame` never API; default filter `Relevant` with an explicit `Everything` escape hatch (PRD §58); relevance *reason* surfaced so a user can see why something was classified; rules versioned. |
| **Trigger** | D2 shows missed or noisy classification on a real site. |
| **Decision** | Prefer false negatives over false positives for `Relevant` — noise breaks the browser-first thesis, while a missed request is still findable via `All`. |
| **Owner** | Product + Architect |

### R-17 — Private-mode capture leaks

| | |
|---|---|
| **L / I / Exposure** | 2 × 4 = **8 Medium** |
| **Impact** | A private-browsing request persisted, contradicting the user's expectation. |
| **Mitigation** | `retention = PRIVATE` enforced **at write time**, not by a sweeper; repository refuses secret writes for private sessions; private tabs excluded from session restore; PRD §34 warning before transfer leaves the private context. |
| **Trigger** | D20 failure. |
| **Decision** | Hard stop. |
| **Owner** | Security owner |

### R-18 — Keystore invalidation makes capture unreadable

| | |
|---|---|
| **L / I / Exposure** | 2 × 3 = **6 Medium** |
| **Impact** | Previously captured requests become unreadable after a device lock change or restore. |
| **Mitigation** | `allowBackup=false`; `KEY_INVALIDATED` surfaces as a clear `ERROR` with an explicit discard action; no silent partial decryption; key-versioned re-encryption that resumes safely. |
| **Decision** | Do not attempt recovery of data that cannot be decrypted. Offer discard. |
| **Owner** | Android owner |

### R-19 — 16 KB page-size incompatibility

| | |
|---|---|
| **L / I / Exposure** | 2 × 4 = **8 Medium** |
| **Evidence** | Android 15+ requires 16 KB page-size support for new apps on affected devices. GeckoView support depends on the pinned version. |
| **Impact** | The app is unusable on newer devices, or is blocked from publication. |
| **Mitigation** | SPIKE-10; D8; engine version chosen to satisfy the requirement; verified in CI where emulators permit. |
| **Trigger** | SPIKE-10 failure. |
| **Decision** | Engine pin must satisfy this. It is a **release blocker**, not a nice-to-have. |
| **Owner** | Android owner |

### R-20 — MV2 removal pressures a manifest migration

| | |
|---|---|
| **L / I / Exposure** | 2 × 3 = **6 Medium** |
| **Evidence** | MV2 is chosen for simplicity with privileged built-ins (Mozilla's own GeckoView examples ship MV2), and MV3 adds a `webRequestFilterResponse` requirement. MV2 support is being phased out upstream. |
| **Impact** | A future forced migration touching permissions and stream-filter behaviour. |
| **Mitigation** | MV2 pinned with a documented decision (Security §5.3); capability matrix version-scoped; manifest isolated to one directory so migration is a contained change. |
| **Trigger** | Engine release notes removing MV2 support for built-ins. |
| **Decision** | Migrate when forced, with a full spike re-run. Do not migrate early on speculation. |
| **Owner** | Architect |

### R-21 — Taho-side unknowns block Phase 8

| | |
|---|---|
| **L / I / Exposure** | 3 × 3 = **9 Medium** |
| **Evidence** | All eleven PRD §50 discovery items are unverified. The actual package ID alone determines whether `setPackage` targeting works. |
| **Impact** | Phase 8 cannot complete; the MVP loop of PRD §135 cannot close. |
| **Mitigation** | Discovery items are enumerated (Integration Contract §15) and are read-only investigation against the existing `Taho App/` repository. Branch B in the plan runs them early, in parallel. |
| **Trigger** | Phase 7 exit. |
| **Decision** | **Do not guess any of them.** An invented package name or route produces an integration that appears to work and silently fails on a user's device. |
| **Owner** | Architect + Taho-side owner |

---

## 3. Low risks

| ID | Risk | L×I | Mitigation / decision |
|---|---|---|---|
| R-22 | Two-toolkit UI cost if Flutter is later introduced | 2×2=4 | Isolated module, narrow async interface, never holds a `GeckoSession`. Not in v1. |
| R-23 | `SSE`/`WebSocket` page-level frames misread as wire capture | 2×2=4 | `ObservationSource.PAGE` carried end-to-end; UI says "observed in page context". Copy gate H. |
| R-24 | WebSocket/SSE floods memory | 2×2=4 | 2 000-frame cap per stream → `truncated`; lowest priority lane. |
| R-25 | Clock skew makes provenance timestamps wrong | 3×1=3 | DB-assigned `receivedAt` authoritative; divergence > 60 s surfaced. Never used for ordering or expiry. |
| R-26 | Migration path leaves orphan data | 2×2=4 | Two-release rename policy; `OrphanSweeper`; `FOREIGN KEYS` on. |
| R-27 | Contract drift between the two apps | 2×3=6 | **Shared fixture suite run by both CIs** (PRD §128); a fixture only one side handles is a contract defect. |
| R-28 | Corrupt DB loses capture | 2×2=4 | Per-row `CorruptRecord` isolation; explicit user-facing reset; never silent auto-wipe. |
| R-29 | Accessibility regressions in the technical surfaces | 2×2=4 | D16/D17; 48dp targets; no colour-only signalling; TalkBack labels asserted. |
| R-30 | `webRequest` field availability varies by request type | 3×2=6 | SPIKE-04 enumerates fields per type; `Completeness` is derived from evidence, never assumed. |
| R-31 | Users expect desktop DevTools parity | 3×2=6 | Explicit non-claim (PRD §132, §131); capability-honesty copy map; Gate H. |
| R-32 | Third-party tracker volume dominates capture | 3×2=6 | Lowest priority lane; aggregation under pressure; `Relevant` default filter. |

---

## 4. Top risks — the honest summary

If only five risks are tracked, these are the ones that decide whether the product is truthful:

| # | Risk | Why it dominates |
|---|---|---|
| **R-01** | `webRequest` unavailable | Would change the product's capability, not just its implementation |
| **R-02** | Tab-ID join fails | Would make the P0 correctness requirement unachievable as designed |
| **R-04** | Cross-tab attribution ships | Falsifies the product's central claim |
| **R-05** | Secret in a log | Falsifies the credential-defence claim and endangers users |
| **R-21** | Taho unknowns | The MVP loop cannot close without them |

**The first two are resolved by Phase 0.5, cheaply and early, precisely because they are
architectural rather than incidental.** That is the strongest argument for the gate existing at all:
a week of spikes can prevent a month of building on an unverified assumption.

---

## 5. Risk review cadence

| When | Action |
|---|---|
| End of Phase 0.5 | Re-score R-01, R-02, R-03, R-10 with measured evidence |
| End of Phase 3 | Re-score R-04; Gate A decision |
| End of Phase 5 | Re-score R-05, R-18; Gate B decision |
| End of Phase 7 | Re-score R-21; unblock Branch B |
| End of Phase 8 | Gates C, D, E, F |
| Any engine bump | Full SPIKE-01…12 re-run; re-score R-07, R-20, R-19 |
| Every release | Gates A–H; top-5 review |
