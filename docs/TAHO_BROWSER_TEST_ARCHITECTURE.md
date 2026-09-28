# TAHO_BROWSER_TEST_ARCHITECTURE.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** The full verification strategy. Designed *before* implementation, because the PRD's P0
items are correctness properties that must be provable, not observed.

---

## 1. Strategy

The architecture's most important test-enabling decision is that `:capture:domain`,
`:transfer:core`, and `:contract:taho-transfer` are **pure JVM modules with no `android.*` imports**.
Every P0 correctness requirement — the state machine, attribution, relevance, secret detection,
normalisation, envelope compatibility — is therefore testable in **milliseconds on the JVM with no
emulator**. That is where the bulk of the test suite lives.

```
                     ┌──────────────────────────────────────┐
  ~70% of tests ───▶ │  JVM unit + property tests           │  seconds, no device
                     │  :capture:domain, :transfer:core,    │
                     │  :contract:taho-transfer             │
                     └──────────────────────────────────────┘
                     ┌──────────────────────────────────────┐
                     │  Robolectric (JVM, Android stubs)    │  seconds
                     │  Room, Keystore stubs, ViewModel     │
                     └──────────────────────────────────────┘
                     ┌──────────────────────────────────────┐
                     │  Instrumentation (emulator/device)   │  minutes
                     │  GeckoView, WebExtension, IPC, UI     │
                     └──────────────────────────────────────┘
                     ┌──────────────────────────────────────┐
                     │  Physical device (manual + scripted) │  hours, per release
                     │  SPIKE-*, multi-tab, process death    │
                     └──────────────────────────────────────┘
```

**Rule:** a P0 defect that can be caught by a JVM test must never be caught first on a device. If a
device test is the only thing standing between a state-machine bug and release, the design is wrong.

---

## 2. Unit tests — JVM, no Android

### 2.1 Transaction state machine (P0)

| Test | Assertion |
|---|---|
| Exhaustive transition table | every `(from, to)` pair's permissibility matches Data Model §4.3 exactly |
| Terminal states have no exits | `COMPLETED/TRUNCATED/PARTIAL/FAILED/CANCELLED` reject all transitions |
| Invalid transition is a no-op | transaction deep-equals its prior value, and a diagnostic is emitted |
| No terminal state is reachable from `STARTED` except via a legal path | graph reachability check |
| `COMPLETED` requires a non-null `response` | invariant M4 |
| `TRUNCATED` records the cap that caused it | provenance of the truncation is retained |

### 2.2 Attribution (P0)

| Test | Assertion |
|---|---|
| `resolve` for a bound id | `Attribution.Known` with the correct `TahoTabId` |
| `resolve` for `-1` | `Attribution.Unattributed`, never `Known` |
| `resolve` for an unknown id | `Attribution.Unresolved`, never `Known`, never the selected tab |
| Switching the selected tab changes nothing | resolve results identical before/after — **the PRD §53.3 regression test** |
| `invalidate` after close | existing attributions become `Detached`, never reassigned |
| `Attribution` immutability | property test: after any event sequence, the first-assigned attribution is unchanged (M3) |
| Reconnect | bindings are re-confirmed, never trusted from stale state |

### 2.3 Relevance engine

| Test | Assertion |
|---|---|
| `main_frame` never classified as an API | M8 |
| `isFirstParty("evil-example.com", "example.com") == false` | M7 — substring regression |
| `isFirstParty("api.example.com", "example.com") == true` | boundary match |
| `isFirstParty("example.com", "example.com") == true` | exact match |
| `isFirstParty("notexample.com", "example.com") == false` | boundary match |
| Determinism | same input → same `Relevance`, always |
| Purity | no clock, no I/O, no randomness — verified by calling twice and by review |

### 2.4 Secret detector

| Test | Assertion |
|---|---|
| `Authorization` is HIGH confidence by **name**, regardless of value shape | structural defence, not heuristic |
| `Cookie`, `Set-Cookie`, `Proxy-Authorization` | always detected |
| JWT shape `eyJ…` | detected, category `JWT` |
| `Bearer …` / `Basic …` | detected |
| JSON body `{"password": "…"}` at any depth | detected with a JSON path |
| Form field `api_key` | detected |
| `X-Request-ID` is **not** classified secret | PRD §75 |
| `evidence` never contains any substring of the value | **security-critical**: assert the secret value is not a substring of the evidence for a corpus of secrets |
| No `toString()` leaks a value | M10 — reflect over every domain type |
| Misses fail closed | an undetected credential still yields a non-executable transfer |

### 2.5 Normaliser

| Test | Assertion |
|---|---|
| Strips the transport deny-list | exact set |
| Retains unrecognised headers, including unknown `X-*` | PRD §75 — the allow-list bug this prevents |
| Idempotent: `n(n(x)) == n(x)` | M12 — property test |
| Version recorded | `normalizerVersion` set on output |
| Behaviour change requires a version bump | a test asserting the version constant changed with the rule set |

### 2.6 Envelope / contract

| Test | Assertion |
|---|---|
| Round-trip for every fixture | encode → decode → equal |
| Older sender → newer receiver | imports |
| Newer sender → older receiver | graceful `UNSUPPORTED_VERSION` |
| Unknown `schema` | `INVALID_SCHEMA` |
| Unknown **optional** field | ignored, not reinterpreted |
| Unknown **required** enum member | validation failure, never defaulted |
| Missing `completeness` field | validation failure |
| Declared size ≠ actual size | flagged (PRD §119) |
| Header with CR/LF | rejected (PRD §118) |
| Control characters in any string | rejected |
| Fuzz: 10 000 malformed payloads | no crash, no partial object, no unbounded allocation |

### 2.7 Property tests

| Property | Generator |
|---|---|
| State machine never leaves a terminal state | random event sequences |
| Attribution is write-once | random event sequences |
| Body invariants: `capturedSize <= declaredSize` when declared; `COMPLETE` implies non-zero stored bytes | random bodies incl. truncation |
| Normaliser idempotence | random header sets |
| Provenance equality independent of event arrival order | shuffled event permutations |
| Queue never exceeds capacity under random overload | random priority distributions |

---

## 3. Robolectric tests — Android without a device

| Area | What is verified |
|---|---|
| Room DAOs | queries, cascades, `FOREIGN KEYS` enforcement, orphan queries, `UNRESOLVED` excluded from per-tab queries (M14) |
| Migrations | every version-to-version migration against a seeded production-shaped DB; assert data survives |
| `KeystoreCipher` (faked) | encrypt/decrypt round-trip, key-version migration, wrong-key failure |
| `BodyStore` | inline vs file threshold, chunk reassembly, missing-file degradation |
| ViewModels | capture state rendering, `LIMITED` propagation, filter behaviour |
| `EventIngress` | length-before-parse, type rejection, `seq` gap/regression accounting |
| `TempArtifactStore` | encryption, one-time grant, TTL, sweeper deletion |
| `TransferConfirmationActivity` (Robolectric) | itemised secrets, `EXPLICIT` gate, private-session warning, transport selection |

---

## 4. Instrumentation tests — emulator

| Suite | What it verifies |
|---|---|
| `GeckoRuntimeBootstrapTest` | runtime creation, `ensureBuiltIn` idempotence, ordering (runtime → extension → session) |
| `NativeMessagingTest` | `connectNative`, `onConnect`, `PortDelegate`, `HELLO` handshake, `onDisconnect` |
| `SessionAttributionTest` | session-scoped `sender.session` is the expected `GeckoSession`; `null` for background |
| `WebRequestSmokeTest` | which `webRequest` events fire and which `details` fields are populated (feeds SPIKE-04) |
| `CapturePipelineTest` | end-to-end: navigate → events → transactions → Room |
| `MultiTabAttributionTest` | 2–4 tabs, interleaved traffic, zero cross-tab attribution |
| `LifecycleTest` | rotation, background/foreground, `onTrimMemory` |
| `ProcessDeathTest` | `am kill` → restart → capture intact, in-flight → `PARTIAL` |
| `ContentCrashTest` | kill the content process → crash UI → captured requests still reachable |
| `PermissionTest` | per-capability prompts, correct session and origin attribution |
| `ExternalIntentTest` | `mailto:`/`tel:`/custom scheme confirmation; session survives return |
| `TransferEndToEndTest` | build envelope → Intent → (fake) receiver → receipt → cleanup |

---

## 5. Physical-device scenarios (mandatory per release)

These cannot be emulated. Each is scripted where possible; each has a recorded result.

| ID | Scenario | Why it must be physical |
|---|---|---|
| D1 | Two tabs, interleaved XHR, rapid switching — 1 000 events, zero misattribution | **Release Gate A.** The P0 requirement. |
| D2 | Real-world SPA: login → dashboard → 3 API calls, all surfaced as `Relevant` | Relevance quality on real traffic |
| D3 | OAuth login leaving and re-entering the app | Session survival across external intents |
| D4 | `adb shell am kill` mid-capture, then restart | Process-death honesty; `PARTIAL` labelling |
| D5 | Content-process OOM with 6 tabs open | Crash UI, capture survival, memory |
| D6 | `adb shell am send-trim-memory` at each level | Low-memory degradation order |
| D7 | Android 14 / 15 / 16 targets | Platform coverage |
| D8 | **16 KB page-size device** | Android 15+ requirement; PRD §47 |
| D9 | Low-memory device (2 GB RAM) with 8 tabs + capture | Memory ceilings hold |
| D10 | Slow storage — capture write throughput under load | No UI jank (PRD §43) |
| D11 | Intermittent connectivity: Wi-Fi ⇄ cellular ⇄ offline | `FAILED` classification, no false "capture active" |
| D12 | Offline: existing captures inspectable, transfer still available | PRD §93 |
| D13 | Low-storage device near full | Capture degrades honestly, does not crash |
| D14 | 500-iteration page-load latency with response filtering enabled/disabled | SPIKE-03; proves no page-load regression |
| D15 | Screen rotation during active capture, during a transfer, and mid-sheet | Lifecycle correctness |
| D16 | TalkBack over the full browse → inspect → transfer flow | Accessibility (PRD §37) |
| D17 | Large text scale + reduced motion | Accessibility |
| D18 | 5 MB body transfer → file-backed path, verified end to end | **Release Gate F** |
| D19 | Forged Intent to the Taho receiver with a hostile payload | **Release Gate D/E** |
| D20 | Private-mode session: capture, transfer attempt, and the privacy warning | PRD §34, §74 |

---

## 6. Security tests

| ID | Test | Gate |
|---|---|---|
| S1 | Seeded `Authorization`/`Cookie` through capture → inspect → transfer; assert the plaintext appears in **no** log buffer, crash file, `ContentObserver`, or analytics payload | **B** |
| S2 | CI static gate: no `Log.*` call in capture-reachable modules takes a body, header map, or `SecretValue` | **B** |
| S3 | Fuzz the import validator: 10 000 malformed payloads → no crash, no partial object | **D** |
| S4 | Assert no network call occurs during import | **E** |
| S5 | Forged transfer Intent with a valid-looking but hostile payload is rejected at step N of validation | **D** |
| S6 | `content://` URI without a **held** grant fails cleanly rather than throwing | **D** |
| S7 | A 5 MB body never lands in Intent extras (assert extras size) | **F** |
| S8 | Header CRLF injection rejected at the receiver | **D** |
| S9 | Declared-vs-actual size mismatch rejected | **D** |
| S10 | Hostile iframe cannot claim a foreign tab binding (§IPC 4.4) | attribution integrity |
| S11 | Destination-origin change on replay triggers the high-risk warning | PRD §35 |
| S12 | Debug build diagnostics contain no secret values | **B** |
| S13 | Keystore invalidation → capture degrades to `ERROR`, browser unaffected | — |
| S14 | Product strings match the capability allow-list (CI diff) | **H** |

---

## 7. Performance tests

| Metric | Target | Method |
|---|---|---|
| Selection feedback | < 100 ms perceived | Compose frame timing, D10 |
| Transfer (normal) | < 1 s perceived | D18, instrumented |
| Ingress cost per event | < 50 µs, allocation-bounded | JVM benchmark |
| Capture write throughput | sustains peak page load without jank | D10 |
| Queue behaviour under overload | capacity never exceeded; drops counted | property test |
| Memory ceiling | stays under budget with 8 tabs + capture | D9 |
| Room query latency for 250 k transactions | < 50 ms p95 | benchmark on seeded DB |

---

## 8. Accessibility tests

| Requirement | Verification |
|---|---|
| Touch targets ≥ 48dp | Compose UI tests + manual |
| TalkBack labels | manual script D16 + Compose semantics tests |
| No colour-only meaning | review + screenshot diff of greyscale rendering |
| Logical focus order | manual D16 |
| Large text, reduced motion | D17 |
| Announced capture state | `"Capture active, 6 relevant requests"` (PRD §37) |
| Announced masking | `"Authorization header, sensitive value hidden"` |

---

## 9. CI gates

| Stage | Blocks merge on |
|---|---|
| JVM unit + property | any failure; coverage floor on `:capture:domain` |
| Robolectric | any failure |
| Static analysis | detekt/ktlint; forbidden-import rule (no `android.*` in pure modules); the `displayIndex`-in-capture grep |
| Security gates | S2, S14 |
| Migration tests | any failure |
| Instrumentation (emulator) | any failure |
| Contract fixtures | any fixture failing on either side |
| **Device scenarios** | D1, D4, D5, D8, D18, D19, D20 — **required before a release build, not before merge** |

---

## 10. Definition of done per phase

| Phase | Exit criteria |
|---|---|
| 0 | Module graph builds; pure-module import gate active; CI green |
| 1 | A user can browse. D7 passes. |
| 2 | Tabs, background, crash, restore. D4, D5, D15 pass. |
| 3 | **SPIKE-01, SPIKE-02, SPIKE-12 pass.** Attribution proven on device. |
| 4 | Capture pipeline end-to-end. Ingress benchmarks pass. |
| 5 | No secret in any log (S1, S2). Migrations tested. |
| 6 | Browser-first UX. D16, D17 pass. |
| 7 | Inspector truthful. Completeness matches evidence. |
| 8 | **Gates C, D, E, F pass.** D18, D19, D20 pass. |
| 9 | Advanced tooling; must not precede a stable Phase 8 (PRD §49) |
| 10 | All 12 release gates green; SPIKE-10, SPIKE-12 re-verified on the pinned engine |
