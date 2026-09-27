# M2 implementation status — 2026-09-27

Status: COMPLETE — source/build/test exit gate  
Branch: `build/m2-production-foundation`

## Build-plan exit criteria

### Buildable skeleton

The production app, GeckoView spike, pure domain/contract/transfer modules and M2 test-support
modules share one Gradle graph. CI compiles the production Android app and disposable GeckoView
spike while running the pure JVM suites independently.

Canonical toolchain pins:

- JDK 17;
- Gradle 9.3.1 in CI;
- Android Gradle Plugin 9.1.1;
- Kotlin 2.3.21;
- Android SDK `platforms;android-37.2-beta3`;
- Android Build Tools 36.0.0;
- GeckoView candidate `156.0.20260921121718`.

### Pure-module dependency gate

`verifyArchitecture` enforces that correctness/security-critical JVM modules and the M2
test-support modules do not apply Android plugins or import `android.*`. Capture-reachable code
also rejects generic `Log.*`, stdout and stderr sinks.

### Corrected reducer and property tests

The reducer preserves observed evidence when start/headers events are missing, treats body
truncation as field completeness rather than a terminal request outcome, supports explicit PARTIAL
recovery, bounds event de-duplication and keeps resolved origin attribution write-once.

Existing unit/property tests remain in place. M2 adds fixture-driven sequence tests from
`capture/domain/src/test/resources/reducer/` for:

- missing start;
- response-before-headers reordering;
- duplicate event delivery;
- process-death recovery.

### Controlled fixture service

`:test-support:http-fixtures` provides a JVM-only HTTP service bound to `127.0.0.1` on an
ephemeral port. It exposes deterministic health, GET, JSON POST, redirect and failure fixtures and
records only synthetic fixture requests. It does not require external network access.

### Companion receiver harness

`:test-support:taho-receiver-harness` depends only on the versioned Browser→Taho contract. It:

- validates the transfer envelope;
- rejects unsupported/invalid/unsafe envelopes;
- de-duplicates by `transferId`;
- stages a deterministic in-memory import;
- returns a versioned receipt;
- exposes zero execution and zero persistence side effects.

This harness is not the real Project-Taho Android receiver and does not satisfy M4 by itself.

## Boundary after M2

M2 completion does not change the M1 physical-device gate. Production Gecko observation remains
blocked until trustworthy attribution is measured on-device. It also does not start M3 device
acceptance or the Project-Taho receiver.
