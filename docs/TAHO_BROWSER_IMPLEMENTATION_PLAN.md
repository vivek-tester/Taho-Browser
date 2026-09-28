# TAHO_BROWSER_IMPLEMENTATION_PLAN.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** Dependency-ordered build sequence, the build-from-zero dependency graph, release gates,
and the single first implementation task.

---

## 1. Phase overview

The phase names follow the brief's suggested shape. Two adjustments are made, and both are
load-bearing rather than cosmetic:

1. **A "Phase 0.5" (capability verification) is inserted before any capture work.** The PRD's
   observation layer rests on `webRequest` availability in GeckoView built-in extensions and on
   `sender.tab.id == webRequest.details.tabId`. Neither is confirmed. Building the capture pipeline
   before resolving them risks building on sand. The spikes are cheap; the pipeline is not.
2. **Session attribution precedes the WebExtension pipeline.** The PRD's #1 P0 item (§49) is
   attribution, and the attribution join needs the port and the extension. So the ordering is
   *extension → port → handshake → attribution → pipeline*, not *pipeline → attribution*.

```
Phase 0    Architecture foundation        module graph, CI, gates, pure-module enforcement
Phase 0.5  Capability verification        SPIKE-01/02/04/05/06/12  ← throwaway
Phase 1    Minimal GeckoView browser       runtime, session, GeckoView, omnibox, nav
Phase 2    Tab + session lifecycle         tab model, private tabs, crash, restore
Phase 3    WebExtension + native messaging  install, port, HELLO, attribution handshake
Phase 4    Capture pipeline               ingress, queue, state machine, relevance, secrets
Phase 5    Persistence + security         Room, Keystore, retention, migrations, log gates
Phase 6    Browser UX                     progressive disclosure, indicators, filters
Phase 7    Capture inspector              summary → inspector → fullscreen
Phase 8    Browser → Taho                 contract, normaliser, policies, transfer, receipt
Phase 9    Advanced tooling               WebSocket/SSE views, replay, export, HAR-lite
Phase 10   Production hardening           gates, perf, accessibility, release engineering
```

---

## 2. Phases in detail

### PHASE 0 — Architecture foundation

**Produces:** the skeleton that makes everything else cheap to build and verify.

- Gradle multi-module project, exactly the graph in `..._COMPONENT_ARCHITECTURE.md` §1
- `:capture:domain`, `:transfer:core`, `:contract:taho-transfer` as **pure JVM** modules, with a
  build-time gate failing on any `android.*` import
- Package/lint baseline: detekt, ktlint, dependency locking, version catalogue
- CI pipeline: JVM → Robolectric → static → instrumentation; the forbidden-import rule and the
  `displayIndex`-in-capture grep active from day one
- GeckoView version pinned in the version catalogue, with a documented bump procedure (PRD §130)
- Design tokens (AMOLED base, warm gold accent, Taho sans, technical mono) as a shared module
- `docs/` decision records for: the Flutter deviation (System Architecture §3), MV2 choice
  (Security §5.3), deny-list normalisation (Data Model §11)

**Exit:** the module graph builds, the pure-module gate is proven to fail when violated, CI is green,
and the whole domain test suite can run in seconds.

**Why first:** the pure-module boundary is what makes P0 testable without an emulator. Every later
phase's cost depends on getting this right.

### PHASE 0.5 — Capability verification (throwaway)

**Produces:** answers, not code. Everything built here is labelled throwaway and is not carried into
Phase 1+.

| Spike | Question |
|---|---|
| SPIKE-01 | `webRequest` available to a GeckoView built-in extension? `sender.tab.id == details.tabId`? session-scoped `sender.session` as documented? |
| SPIKE-02 | Does the join hold across 2+ tabs, rapid switching, background tabs, redirects? |
| SPIKE-04 | Which `details` fields are actually populated, per request type? |
| SPIKE-05 | Is `documentId` present and stable? Usable as a second join key? |
| SPIKE-06 | When does `tabId == -1` occur? |
| SPIKE-12 | Extension lifecycle: port reconnect, runtime teardown, `ensureBuiltIn` idempotence |

**Exit:** the capability matrix §5/§6 is updated from *hypothesis* to *measured*, with the engine
version recorded. **If SPIKE-01 fails, stop and re-plan** — the observation layer changes shape
(System Architecture §9, risk R-01).

**Explicitly not deferred:** this phase exists because the PRD's own §130 forbids upgrading the engine
to chase an unverified capability, which means the capabilities must be *measured* on the pin.

### PHASE 1 — Minimal GeckoView browser

`GeckoRuntimeHolder` → `SessionFactory` → `GeckoViewHost` → `BrowserSurface` + `Omnibox`.

Browsing works: launch, load URL, back/forward, reload, error pages, external intents, permission
prompts. **No capture at all.** The browser must be genuinely good before anything is layered on it —
that is the "Browser First" principle expressed as sequencing.

**Exit:** a user can browse. D7 passes. Capture absent, and nothing in the shell references it.

### PHASE 2 — Tab and session lifecycle

`TabManager`, `BrowserTab`, `SessionRegistry`, private tabs (as a tab *type*, per
`usePrivateMode` being init-only), crash handling (`onCrash`/`onKill`), Parcel restore, low-memory
response.

**Exit:** D4 (process death), D5 (content crash), D15 (rotation) pass. Tabs survive crash and death.
No capture exists yet, which makes this a clean test of the tab model in isolation.

### PHASE 3 — WebExtension and native messaging

`BuiltinExtensionHost` (MV2, built-in, privileged) → `PortRegistry` → `HELLO` handshake →
`FrameAttributor` handshake → attribution resolution.

This phase delivers the PRD's P0 #1 and P0 #2 and nothing else. No capture domain yet — just correct
attribution.

**Exit:** **SPIKE-01, SPIKE-02, SPIKE-12 pass on device.** D1 passes: 1 000 events across multiple
tabs, zero cross-tab attribution. `Attribution.Unresolved` occurs in known situations and is never
guessed away.

**This is the highest-risk phase and the natural place for the project to stop and re-evaluate.**

### PHASE 4 — Capture pipeline

`EventIngress` → `BoundedEventQueue` → `CaptureCoordinator` → `TransactionStateMachine` →
`RelevanceEngine` → `SecretDetector` → `CorrelationIndex`.

All pure-JVM. The state machine, relevance, and secrets are complete and exhaustively tested before
they ever meet a device.

**Exit:** JVM property tests green; ingress benchmark under budget; end-to-end capture on device;
degradation behaves under deliberate overload.

### PHASE 5 — Persistence and security

`CaptureRepository` + Room + `KeystoreCipher` + `BodyStore` + retention/sweeps + migrations +
the logging gates (S1, S2).

**Exit:** no secret in any log; migrations tested against a seeded DB; retention and the three
independence rules (Storage §1) proven; capture survives process death as `PARTIAL`, not as loss.

### PHASE 6 — Browser UX

`Omnibox` polish, `CaptureIndicator`, `CaptureSummarySheet`, `CaptureFilterBar`, tab switcher with
counts, progressive disclosure wiring, all failure/limited states, accessibility.

**Exit:** D16, D17 pass; no permanent technical chrome exists; the browser is pleasant with capture
switched off entirely.

### PHASE 7 — Capture inspector

`RequestInspector` (10 sections), completeness block, provenance block, `TechnicalWorkspace`,
search and filters, cURL export (masked by default).

**Exit:** completeness is truthful against real captures; masking is structural (I1/I2); a masked
value cannot reach persistence or transfer.

### PHASE 8 — Browser → Taho

Contract repository + `RequestNormalizer` + `SecretPolicyApplier` + `EnvelopeBuilder` +
`TransportSelector` + `TempArtifactStore` + `IntentSender` + confirmation UI. Plus the **Taho-side
receiver**, after the PRD §50 discovery items are answered against the real codebase.

**Exit:** **Gates C, D, E, F pass.** D18, D19, D20 pass. The MVP loop of PRD §135 completes end to
end with no manual cURL.

### PHASE 9 — Advanced tooling

WebSocket/SSE page-level views (explicitly labelled), replay with destination-change warning, share
export, response evidence if and only if SPIKE-03 succeeded.

**Gate:** PRD §49 — *"Do not expand advanced tooling until capture correctness and the Browser→Taho
handoff are reliable."* Phase 9 does not start until Phase 8 has shipped cleanly.

### PHASE 10 — Production hardening

Release gates A–H green, performance budgets, accessibility sign-off, security review, ProGuard/R8
rules for the serialisation layer, signing config (no dev identities in production), 16 KB page-size
verification, engine pin re-verification, store metadata.

---

## 3. What is explicitly postponed

| Postponed | Until | Why |
|---|---|---|
| Response body capture | SPIKE-03 proves it safe | An unclosed `StreamFilter` hangs the response forever. Risk is not worth it pre-MVP. |
| Batch transfer | Phase 8 stable | PRD §29, §48 Phase 3 |
| Environment parameterisation | Phase 8 stable | PRD §73; needs a receiver API (discovery item 7) |
| Workflow / session transfer | Phase 8 stable + `taho.workspace-transfer` designed | PRD §30, §145 |
| WebSocket workspace in Taho | after single + batch transfer | PRD §32 |
| Full HAR export | after MVP | PRD §5 non-goal |
| Public `ContentProvider` | only if a persistent shared workspace is ever designed | PRD §24.5 |
| AIDL | only for a future persistent service | PRD §24.4 |
| Deep links | not recommended at all for MVP | Security §4.3 |
| Product telemetry | opt-in, aggregate-only, post-MVP | PRD §99 |
| Flutter technical workspace | measurement-justified, separate module | System Architecture §3.4 |
| uBlock-style filter lists | not planned | GeckoView TP is not that (Capability Matrix §7) |
| Desktop DevTools parity | never | PRD §132, §133 |
| MV3 migration | scheduled compatibility item | Security §5.3 |

---

# 4. Build From Zero — Dependency Graph

Derived from the architecture, not from the brief's example. Read top-to-bottom: each node requires
every node above it that it names.

```
                    ┌──────────────────────────────┐
                    │  GRADLE MODULE GRAPH          │  Phase 0
                    │  pure-JVM boundary enforced   │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │  PINNED GECKOVIEW VERSION      │  Phase 0
                    │  + design tokens + CI gates    │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │  CAPABILITY VERIFICATION       │  Phase 0.5  ◄── HARD GATE
                    │  SPIKE-01 02 04 05 06 12      │     throwaway
                    └──────────────┬───────────────┘
                    if webRequest unavailable → STOP, re-plan
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  GeckoRuntimeHolder            (process singleton)   │  Phase 1
        │  GeckoRuntimeSettings                                │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  SessionFactory               (init-only settings)   │  Phase 1
        │  Delegates · private mode · tracking protection     │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  GeckoViewHost + BrowserSurface + Omnibox           │  Phase 1
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  BrowserTab · TabManager · SessionRegistry          │  Phase 2
        │  (TahoTabId stable across crash + process death)    │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  Lifecycle: crash · Parcel restore · low-memory     │  Phase 2
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  BuiltinExtensionHost  (ensureBuiltIn, MV2)         │  Phase 3
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  PortRegistry + HELLO handshake                     │  Phase 3
        │  (runtime.connectNative → WebExtension.Port)         │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  session-scoped MessageDelegate (sender.session)     │  Phase 3
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  FrameAttributor  ◄── P0 GATE (Release Gate A)      │  Phase 3
        │  extTabId ⇄ GeckoSession, four-valued Attribution    │
        │  never falls back to the selected tab                │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  EventIngress  (O(1): length→schema→seq→priority)    │  Phase 4
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  BoundedEventQueue  (real backpressure, lanes)       │  Phase 4
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  CaptureCoordinator + CorrelationIndex              │  Phase 4
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  TransactionStateMachine   ◄── P0 GATE              │  Phase 4
        │  RelevanceEngine · SecretDetector                    │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  CaptureRepository (iface) + KeystoreCipher (iface) │  Phase 5
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  Room + Keystore + BodyStore + Retention            │  Phase 5
        │  ◄── P0 GATE: no raw-secret logging path (Gate B)   │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  Representations: Display / Persistence / Export    │  Phase 5
        │  (masking is structural, not conventional)          │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  Browser UX: indicator → summary → filters          │  Phase 6
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  RequestInspector + TechnicalWorkspace              │  Phase 7
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  taho.request-transfer contract + fixtures          │  Phase 8
        │  RequestNormalizer · SecretPolicyApplier            │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  TransportSelector + TempArtifactStore + IntentSender│  Phase 8
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  User confirmation (G3) + Taho receiver (G4)        │  Phase 8
        │  ◄── GATES C, D, E, F                                │
        └──────────────────────────┬──────────────────────────┘
                                   │
        ┌──────────────────────────▼──────────────────────────┐
        │  Advanced tooling · Hardening · Gates A–H           │  Phase 9/10
        └─────────────────────────────────────────────────────┘
```

### 4.1 Independent branches

These can proceed in parallel with the main chain once their inputs exist:

```
Branch A (pure, no device)          Branch B (Taho-side)
──────────────────────────          ──────────────────────────
:contract:taho-transfer              PRD §50 discovery items
  schema + fixtures + compat          actual package ID
  ↳ needed by Phase 8                 ApiRequest construction path
:transfer:core                        request-editor route
  normaliser + policies               existing serializers
  ↳ needed by Phase 8                 collection / environment APIs
                                      ↳ needed by Phase 8 receiver
```

Branch A has **no dependency on GeckoView at all** and can be built and reviewed during Phase 0–5.
Starting it early is the cheapest schedule win in the plan, because it is pure JVM and its fixtures
become the contract tests for both apps.

---

## 5. Release gates (PRD §143)

| Gate | Requirement | Verified by | Phase |
|---|---|---|---|
| **A** Capture correctness | No known cross-tab attribution defect | D1, SPIKE-02, unit M3 | 3 |
| **B** Security | No known raw-secret logging path | S1, S2, S12 | 5 |
| **C** Transfer correctness | Capture → equivalent normalised Taho request | contract suite, §129 | 8 |
| **D** Failure safety | Malformed/unsupported transfers cannot crash Taho | S3, S5, S6, S8, S9 | 8 |
| **E** Execution safety | Import cannot auto-execute | S4 | 8 |
| **F** Large payload safety | No oversized Intent extras | S7, D18 | 8 |
| **G** UX | Browser remains browser-first | D16, D17, review | 6 |
| **H** Capability honesty | Product claims match verified behaviour | S14 vs capability matrix | 10 |

---

## 6. Critical path and schedule risk

```
Phase 0 ──▶ 0.5 ──▶ 1 ──▶ 2 ──▶ 3 ──▶ 4 ──▶ 5 ──▶ 6 ──▶ 7 ──▶ 8 ──▶ 9 ──▶ 10
  │         │                    │
  │         │                    └── HARD GATE: if SPIKE-01 fails, replan
  │         └── HARD GATE: capabilities must be measured before capture is built
  └── enables everything by making P0 testable without a device
```

**Phase 3 is the schedule risk.** It is the first phase that depends on an unverified platform
behaviour, and it delivers the P0 requirement. If the attribution join does not hold, the fallback
(page-level instrumentation, Capability Matrix §10) is a materially weaker product and requires
re-planning Phases 4–8, not patching Phase 3.

**Recommendation:** treat Phase 0.5 as a hard gate with a real decision at the end, and do not
begin Phase 3 work in parallel with it. The spikes are days of work; a mis-built capture pipeline is
weeks.

---

# 7. First Implementation Task

**Exactly one.** It is the smallest foundational production task that can be implemented and
verified independently — and it is deliberately *not* "create the Gradle project", because scaffolding
proves nothing.

---

## First Task — Transaction state machine with exhaustive tests

**Module:** `:capture:domain` (pure Kotlin/JVM)
**Phase:** 4 (but sequenced first — see below)
**Size:** one source file, one test file

### Why this task, and why first

The brief asks for the smallest foundational production task that can be implemented and verified
independently. This is it, for four reasons:

1. **It is the P0 correctness core.** PRD §49 ranks "Capture state machine" third of five P0 items,
   and §13 requires that "invalid/out-of-order transitions must not corrupt persisted state." That
   requirement is only *provable* if the state machine is a pure, exhaustively-testable unit.
2. **It has zero platform dependency.** No GeckoView, no Android, no Room, no UI. It compiles and
   tests in seconds, on any machine, with no device and no emulator.
3. **It cannot be built on sand.** Unlike the WebExtension pipeline, its correctness does not depend
   on any unverified platform behaviour. It is the one high-value component whose design is already
   certain.
4. **It forces the module boundary that everything else depends on.** Writing it as a pure-JVM module
   with no `android.*` import *proves* the boundary that makes Phases 4–8 testable. If the boundary
   cannot hold for this component, it will not hold later.

Building it before Phase 0's other scaffolding is deliberate: it is the smallest artifact that
simultaneously delivers product value, proves the architecture's most important structural claim, and
requires nothing from any unverified dependency.

### Scope — in

- `TransactionState` enum with the eight states from PRD §13/§54: `STARTED`, `HEADERS_CAPTURED`,
  `RESPONSE_STARTED`, `COMPLETED`, `FAILED`, `CANCELLED`, `TRUNCATED`, `PARTIAL`
- `TransactionStateMachine` with:
  - `canTransition(from, to): Boolean` — backed by a complete, explicit transition table
  - `apply(tx, event): Transaction` — returns a **new** transaction; never mutates the input
- The complete transition table exactly as specified in `TAHO_BROWSER_DATA_MODEL.md` §4.3, including
  the rule that terminal states have no exits
- An invalid transition returns the input transaction **unchanged** and emits a diagnostic — never a
  partial mutation, never an exception
- `PARTIAL` and `TRUNCATED` are terminal: a late event cannot retroactively "complete" a transaction
  whose outcome was never observed
- No dependency on any other project module

### Scope — out

- Persistence, Room, encryption
- Relevance, secrets, attribution, body handling
- Any GeckoView, Android, or Compose type
- Any UI, any state holder, any coroutine
- Any I/O

### Verification

The test file is the deliverable's real content. It must contain:

| # | Test | Assertion |
|---|---|---|
| T1 | Exhaustive table | For all 8×8 = 64 `(from, to)` pairs, permissibility matches the specified table **exactly** — a hand-maintained expected table, so a change to either side fails |
| T2 | Terminal states | `COMPLETED`, `TRUNCATED`, `PARTIAL`, `FAILED`, `CANCELLED` permit no outgoing transition |
| T3 | Legal paths | Every state is reachable from `STARTED`; every terminal state is reachable from `STARTED` |
| T4 | Immutability | `apply` returns a distinct instance; the input transaction is byte-identical afterwards |
| T5 | Invalid transition | Returns the input unchanged **and** increments an emitted-diagnostic count |
| T6 | No exceptions | No input sequence causes a throw — property test over random event sequences |
| T7 | Terminal stability | Applying any event to a terminal transaction never changes its state |
| T8 | Late-event rule | A `TX_COMPLETE` arriving after `PARTIAL` does **not** produce `COMPLETED` |
| T9 | Determinism | Same (state, event) → same result, always |
| T10 | Purity | No clock, I/O, randomness, or static mutable state — verified by repeated invocation and by the absence of any injected dependency |

### Definition of done

- `:capture:domain` compiles as a pure Kotlin/JVM module
- The forbidden-`android.*`-import gate is active and **proven to fail** when a violation is
  temporarily introduced
- All 10 tests pass
- The test run completes in under a second
- The transition table in the implementation matches `TAHO_BROWSER_DATA_MODEL.md` §4.3 character for
  character, or the document is corrected first

**Not implemented here. This document defines it.**
