# Taho Browser

Android browser companion for Taho API Testing.

## Build authority

Implementation follows `TAHO_BROWSER_BUILD_PLAN.md`. UI/UX follows `TAHO_BROWSER_UI_UX_SPEC.md` and its prototype visual intent, while runtime facts come from measured capabilities rather than prototype fixtures.

## Current milestone

M0/M1/M2 foundation is in progress:

- Native Kotlin + Jetpack Compose browser shell.
- Browser-first AMOLED chrome with a real GeckoView canvas behind it.
- Process-scoped GeckoRuntime and tab/session ownership foundation.
- Candidate GeckoView pin `156.0.20260921121718`; capability status remains VERIFY until device spikes pass.
- Disposable two-tab SPIKE-01 app with bundled WebExtension attribution instrumentation.
- Pure capture-domain identity/evidence model and corrected transaction reducer.
- Pure transfer contract and initial payload budgets.
- JVM CI for correctness-critical modules.

The production shell defaults capture to OFF until a real capture coordinator supplies engine state; it never claims capture is active merely because GeckoView is running.

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

Browsing must remain independent of capture health, transfer is user initiated, and imported requests must remain unsaved and unexecuted until explicit action in Taho.

## M1 verification

Run `:spikes:geckoview` on a physical Android device and follow `docs/spikes/SPIKE-01-attribution.md`. Do not promote the observation path from VERIFY to SUPPORTED until the recorded evidence passes.
