# Taho Browser

Android browser companion for Taho API Testing.

## Build authority

Implementation follows `TAHO_BROWSER_BUILD_PLAN.md`. UI/UX follows `TAHO_BROWSER_UI_UX_SPEC.md` and its prototype visual intent, while runtime facts must come from measured capabilities rather than prototype fixtures.

## Current milestone

M0/M1/M2 foundation:

- Native Kotlin + Jetpack Compose shell.
- Browser-first AMOLED chrome foundation.
- Module boundaries from the build plan.
- Pure capture-domain identities and safe presentation model.
- GeckoView dependency intentionally remains unpinned until the M1 capability spike selects and records a tested engine release.

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

The remaining empty boundaries are added as their milestones begin. Browsing must remain independent of capture health, transfer is user initiated, and imported requests must remain unsaved and unexecuted until explicit action in Taho.
