# Taho Browser

Android capture browser companion for Taho. The Browser captures and prepares requests; Project-Taho is responsible for API editing, execution, testing, diagnostics, and security analysis.

## Build authority

Implementation follows `TAHO_BROWSER_BUILD_PLAN.md`. UI/UX follows `TAHO_BROWSER_UI_UX_SPEC.md` and its prototype visual intent, while runtime facts come from measured capabilities rather than prototype fixtures.

## Current milestone

M0/M1/M2 foundation is in progress:

- Native Kotlin + Jetpack Compose browser shell.
- Browser-first AMOLED chrome with a real GeckoView canvas behind it.
- Editable URL/search omnibox, history-aware back/forward/reload controls, and normal/private tab switching.
- Normal-tab session-state persistence candidate plus explicit content-crash recovery; private tabs are never written to the restore store.
- Process-scoped GeckoRuntime and tab/session ownership foundation.
- Candidate GeckoView pin `156.0.20260921121718`; capability status remains VERIFY until device spikes pass.
- Disposable two-tab SPIKE-01 app with bundled WebExtension attribution instrumentation.
- Pure capture-domain identity/evidence model and corrected transaction reducer.
- Pure transfer contract and initial payload budgets.
- JVM CI for correctness-critical modules.

The production shell defaults capture to OFF until a real capture coordinator supplies engine state; it never claims capture is active merely because GeckoView is running.

## Product boundary

Taho Browser is **not** an API testing client. Its responsibility ends at browsing, observing,
capturing, reviewing, normalising and explicitly transferring a request.

**Taho Browser owns:** browsing, GeckoView session management, network observation, attribution,
capture/relevance, local capture persistence, credential-safe transfer preparation and the
Browser→Taho sender.

**Project-Taho owns:** receiving the transfer, constructing the editable API request, request
execution/replay, diagnostics/security analysis, environments, collections, test assertions,
history and saved API-testing work.

The receiver is intentionally deferred until the browser runtime/capture path is working. The
versioned transfer contract is maintained now so both apps can implement against the same boundary
later.

## Module graph

- `:app`
- `:browser:shell`
- `:browser:runtime`
- `:browser:observation`
- `:capture:domain`
- `:capture:persist`
- `:transfer:core`
- `:transfer:android`
- `:contract:taho-transfer`
- `:spikes:geckoview` (disposable M1 verification harness)

Browsing must remain independent of capture health. Taho Browser never executes or tests captured APIs. Transfer is user initiated; Project-Taho owns request editing, execution, testing, diagnostics, and security analysis. The Project-Taho receiver will be implemented after the Browser capture/transfer path is runnable.

## M1 verification

Run `:spikes:geckoview` on a physical Android device and follow `docs/spikes/SPIKE-01-attribution.md`. Do not promote the observation path from VERIFY to SUPPORTED until the recorded evidence passes.
