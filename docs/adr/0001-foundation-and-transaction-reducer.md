# ADR 0001 — Foundation and transaction reduction

Status: Accepted for M0/M2 foundation  
Date: 2026-09-27

## Context

The build plan requires the production foundation and reducer to be established before full capture implementation. It also identifies contradictions in the earlier state table: an interrupted STARTED transaction must be recoverable as PARTIAL, a missed start/headers event must not cause a request to be silently dropped, and body truncation must not discard later response status or headers.

## Decisions

1. `:capture:domain`, `:transfer:core`, and `:contract:taho-transfer` remain pure JVM modules.
2. Runtime-scoped request identity is `(runtimeGeneration, requestId)`. Tab attribution is separate and may begin unresolved.
3. Origin attribution is write-once after resolution. Live binding status is separate and may become detached without rewriting origin.
4. Transaction outcome states are `STARTED`, `HEADERS_CAPTURED`, `RESPONSE_STARTED`, `COMPLETED`, `PARTIAL`, `FAILED`, and `CANCELLED`.
5. `TRUNCATED` is field/body completeness, not a terminal transaction outcome. A truncated request body can still be followed by a response and a completed transaction.
6. Recovery may transition any non-terminal transaction, including STARTED, to PARTIAL with an explicit reason such as `PROCESS_DIED`.
7. Missing `TX_START`, request headers, or response-start events create diagnostics while preserving observed evidence. They are not silently dropped.
8. Event de-duplication is bounded to a recent in-memory window. Durable de-dup ownership remains a persistence-contract task; no unbounded event archive is introduced.
9. GeckoView is not added until M1 records a tested engine pin and capability evidence.
10. `minSdk = 26` is provisional scaffolding until project input fixes the supported Android floor.

## Build toolchain

The foundation pins AGP 8.13.2, Kotlin 2.3.21, Gradle 8.13 in CI, and JDK 17. This keeps the configured AGP and Kotlin plugin lines within their documented supported ranges.

## Consequences

The capture reducer can be exhaustively tested on the JVM before engine integration. UI and persistence receive explicit partial/unavailable states instead of inferred success. Engine-specific assumptions stay outside the pure domain.
