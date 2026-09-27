# Taho Browser

Android browser companion for Taho API Testing.

## Build authority

Implementation follows `TAHO_BROWSER_BUILD_PLAN.md`. UI/UX follows `TAHO_BROWSER_UI_UX_SPEC.md` and its prototype visual intent, while runtime facts come from measured capabilities rather than prototype fixtures.

## Current milestone

M0/M1/M2 foundation is in progress:

- Native Kotlin + Jetpack Compose shell.
- Browser-first AMOLED chrome foundation.
- Complete module boundaries from the build plan.
- Pure capture-domain identity/evidence model.
- Corrected transaction reducer with process-death recovery, provisional observation, bounded de-duplication, and write-once attribution.
- Pure transfer contract and initial payload budgets.
- JVM CI for correctness-critical modules.
- GeckoView dependency intentionally remains unpinned until the M1 capability spike selects and records a tested engine release.

The shell defaults capture to OFF until a real coordinator supplies engine state; the UI does not claim capture is active before capture exists.

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

Browsing must remain independent of capture health, transfer is user initiated, and imported requests must remain unsaved and unexecuted until explicit action in Taho.

## Pending capability gate

M1 must prove the GeckoView engine pin, two-tab attribution, first-load behavior, redirects, extension reconnect, lifecycle recovery, and private-mode behavior before production capture depends on those capabilities.
