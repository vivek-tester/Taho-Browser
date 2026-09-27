# ADR 0006 — Normalization, provenance, budgets, and bounded backpressure

Status: Accepted for pure-domain foundation  
Date: 2026-09-27

## Context

The capture-domain and IPC architecture require four related guarantees before production Gecko
observation is trusted:

- request normalization is pure, versioned, deny-list based, and idempotent;
- provenance is immutable and records the normalizer version and observation source;
- inbound payloads and cumulative bodies have hard byte ceilings before expensive work;
- the parsed event queue is bounded, reports every loss, and reserves at least 60% of capacity for
  P0/P1 traffic.

These guarantees belong in pure Kotlin/JVM so their correctness does not depend on Android or
GeckoView.

## Normalization decisions

1. Request normalizer version starts at `request/1.0.0`.
2. Version 1 strips exactly the documented transport headers:
   `Host`, `Content-Length`, `Connection`, `Keep-Alive`, `Proxy-Connection`, `TE`,
   `Trailer`, `Transfer-Encoding`, `Upgrade`, `Sec-Fetch-*`, `Sec-CH-UA`, and
   `Sec-CH-UA-*`.
3. Unknown headers are retained. There is no application-header allow-list.
4. The source document marks DNT removal optional. Version 1 retains DNT so a single version has one
   deterministic meaning.
5. The source architecture says the normalized request has a canonical URL/query but does not specify
   canonicalization rules. This slice therefore preserves the captured URL byte-for-byte instead of
   inventing canonicalization behavior. A later rule must define that behavior and bump the version.
6. Secret detection/classification occurs before normalization. Normalized headers accept either
   public text or an opaque `SecretRef`; the normalizer never receives `SecretValue`.

## Provenance decisions

1. Provenance carries the source-defined fields and is immutable.
2. `capturedAt` is the canonical received timestamp; `originReportedAt` remains advisory.
3. `normalizerVersion` is stored with every provenance instance.
4. `observation` is explicit (`ENGINE` or `PAGE`).
5. The immutable original URL is retained because provenance requires it. Its diagnostic/string
   representation removes userinfo, removes fragments, and masks every query value. This protects
   accidental logging while leaving encrypted provenance/explicit policy paths able to retain the
   original.

## Budget decisions

The initial defaults mirror the architecture:

- 64 KiB inline body ciphertext;
- 8 MiB body ceiling;
- 512 unparsed messages;
- 2,048 parsed messages;
- 4,000 messages/s port read budget;
- 2,000 stream frames;
- 50,000 transactions per session;
- 250,000 transactions total;
- 512 MiB capture DB soft ceiling;
- 256 KiB direct transfer-envelope budget;
- 32 MiB transfer-artifact ceiling.

Per-message caps are represented by a closed enum matching the IPC catalogue. An oversize message is
rejected whole. A body chunk that would cross the cumulative ceiling is not partially accepted; the
body becomes truncated and subsequent chunks are refused.

## Queue decisions

1. The queue preserves global FIFO among events that remain admitted.
2. P0/P1 are the reserved-high classes. The reserve uses `ceil(capacity * 0.60)`, so the floor is
   never below 60%.
3. P2-P4 cannot consume reserved slots.
4. When the general area is full, a higher-priority P2/P3 event may evict the newest event from a
   strictly lower general-priority lane. The eviction is counted as a drop.
5. When the full queue contains lower-priority traffic, P0/P1 may evict the lowest non-high lane and
   remain admitted.
6. The architecture simultaneously requires a hard queue depth, non-blocking ingress, and that P0/P1
   are never dropped. Those three guarantees cannot all hold if the queue is saturated entirely by
   P0/P1. This implementation does not hide that edge case: it returns
   `HIGH_PRIORITY_SATURATED`, increments counters, and forces the future coordinator to surface
   `LIMITED` and apply upstream body degradation/flow control. It does not silently exceed memory
   bounds or claim healthy capture.
7. Queue event `toString()` never renders the payload.

## Required tests

- exact deny-list and unknown-header retention;
- normalization idempotence over randomized header sets;
- provenance equality for equal capture facts regardless of arrival ordering;
- provenance URL diagnostic redaction;
- exact message and body ceilings;
- random-overload queue capacity/reservation invariants;
- FIFO for retained events;
- drop accounting and high-priority saturation reporting;
- randomized no-plaintext checks across secret-bearing/domain diagnostic strings.
