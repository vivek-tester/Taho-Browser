# M4 implementation status — 2026-09-27

Status: SOURCE/AUTOMATED SLICE IMPLEMENTED — two-app/device exit gate blocked  
Branch: `build/m4-end-to-end-slice`

## Implemented source path

### Observation and attribution

- Production built-in extension assets are separate from the M1 spike assets.
- TAB_REGISTER uses the identity lane; the native coordinator validates `sender.session`, top-level
  sender and committed origin before binding an engine tab ID.
- Bulk webRequest traffic uses a separate native channel with HELLO connection IDs and monotonic
  sequence numbers.
- Unknown/unresolved engine tab IDs never fall back to the selected browser tab.
- Per-message byte caps are enforced before JSON parsing.
- Parsed events pass through a bounded priority-aware queue. Authentication traffic receives the
  reserved high-priority lane; drops/evictions become LIMITED.

### Request assembly and safety

- Completed requests are assembled in memory only; M5 persistence is not started.
- GET and JSON POST request fields include method, normalized URL/query, headers, JSON request body
  when safe, response metadata, completeness and provenance.
- Header/query credentials are converted to secret refs before the review model.
- JSON body fields receive structural secret scanning. Malformed, opaque-sensitive or
  credential-shaped bodies fail closed.
- A secret-bearing JSON body is discarded from the transfer candidate after classification rather
  than retained as transferable plaintext.
- Response bodies remain UNAVAILABLE on the M4 path.

### Review and transfer

- The browser shell includes masked Summary → Inspector → Send-to-Taho confirmation sheets.
- Sensitive header UI values must already be masked/parameterized.
- Parameterize and Mask are selectable. Explicit is disabled unless an explicit live-secret source is
  separately available.
- Transfer preparation runs normalization, policy projection, contract validation and the direct
  256 KiB envelope budget.
- Transfer lifecycle requires AWAITING_CONFIRMATION before dispatch and terminal receipts are
  transfer-ID scoped.
- Android direct-transfer code requires an explicit package/action and parses the versioned receipt.

### Automated contract slice

- The controlled loopback service performs a real HTTP GET and JSON POST.
- Those requests are projected through the M4 preparation contract and the companion receiver
  harness.
- Tests assert method/query/headers/body/provenance and that the receiver harness has zero execution
  and zero persistence side effects.
- This is not claimed as real Gecko capture or the actual Project-Taho application.

## Activation gates

The committed application is deliberately non-activating:

- `BuildConfig.M1_ATTRIBUTION_VERIFIED = false`.
- `BuildConfig.TAHO_TRANSFER_ACTION = "com.eternal.taho.action.IMPORT_TAHO_REQUEST"`.

The structured receiver action now exists on the Project-Taho M4 branch. Production capture still
remains OFF because the M1 attribution gate is false; no unverified Gecko traffic is activated by
this source integration.

## M4 exit blockers

M4 remains blocked until all of the following are demonstrated:

1. M1 on-device attribution/capability evidence passes.
2. The M3 browser physical-device acceptance gate demonstrates the Browser actually runs correctly.
3. The implemented Project-Taho receiver/editor path is built and exercised on-device: it must open
   an unsaved ephemeral editor request, return a matching receipt, and never auto-execute.
4. Browser and Project-Taho run together on-device for a real captured GET and JSON POST with the
   expected method/query/headers/body/provenance.

No M5 persistence/durability work is included in this phase.


## Real Project-Taho receiver source

Project-Taho PR #23 implements the matching action and direct-v1 receiver. The native entry stores a
bounded direct envelope and exposes it to Flutter over a private MethodChannel. Dart validates the
schema/policy, maps GET/complete-inline-JSON POST into the existing ApiRequest editor model, opens an
ephemeral non-restorable request session, and returns a transfer-scoped ResultReceiver receipt.
Nothing in the import path invokes Taho's send pipeline.
