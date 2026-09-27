# M1 implementation status — 2026-09-27

Branch: `build/m1-feasibility-suite`

## Source implementation

M1's disposable feasibility harness is now implemented in source:

- pinned GeckoView candidate remains unchanged;
- reconnecting native bulk port and forced-disconnect control;
- session-scoped attribution announcement at document start;
- safe fixture tags for cross-tab verification;
- loopback HTTP fixture generating the request classes needed by SPIKE-01/02/04/05/06;
- WebSocket handshake, SSE, redirects, failed traffic, prefetch/preload, beacon and service worker probes;
- private session P plus normal A/B sessions;
- process-death save/restore control with private state excluded;
- Gecko security-signal recording;
- content crash/kill callback recording;
- eight-tab memory and runtime page-size evidence;
- metadata-only JSON result export;
- unit coverage that result export omits announcement tokens and detects cross-tab conflicts.

## Exit gate

**NOT PASSED YET.** The build plan requires these spikes to be executed on a physical Android device.
Source readiness is not capability evidence.

Do not begin production Gecko capture wiring on the basis of this file alone. The exported device
result must be attached and reviewed first.
