# ADR 0005 — Secret classification foundation

Status: Accepted for pure-domain foundation; end-to-end secret gate still pending  
Date: 2026-09-27

## Context

The data/security architecture requires secret classification to happen on raw capture before any
display, persistence or transfer representation is created. It also explicitly states that
SecretDetector is heuristic and must not be treated as the sole security control.

The source documents define the SecretSeverity enum but do not assign a severity to each category.
A deterministic mapping is therefore required for the pure domain before the UI can compute
`highestSeverity`.

## Decisions

1. Raw observed values are owned byte arrays. Their `toString()` exposes size only, never content,
   and `close()` overwrites the owned array.
2. `SecretValue` is also byte-backed and opaque outside `:capture:domain`; its string form contains
   category plus the word `redacted`, never plaintext.
3. Name rules run before shape heuristics. Authorization, proxy authorization and cookie names are
   structural secret findings and have no confidence threshold.
4. Generic `X-*`, `X-Request-ID`, Accept, Content-Type and User-Agent are not blanket secrets.
5. Shape heuristics (Bearer/Basic/JWT/AWS-like/long token-shaped values) run only in auth-like named
   positions and produce LOW confidence unless the name rule already matched.
6. Evidence strings describe why a field matched but never include the observed value or a value
   prefix.
7. Finding IDs are deterministic hashes of rule version, transaction-scoped key, location, category
   and ordinal. Raw value bytes never participate in the identifier.
8. Cookie findings default to `MASK`. Other recognized categories default to `PARAMETERIZE`.
   `EXPLICIT` is never a default.
9. The initial severity mapping is:
   - CRITICAL: PASSWORD, CLIENT_SECRET
   - HIGH: AUTHORIZATION, BEARER_TOKEN, COOKIE, API_KEY, JWT, BASIC_AUTH, SESSION_ID
   - MEDIUM: CSRF_TOKEN, QUERY_TOKEN
   This mapping is a product decision introduced here because the source documents define the enum
   but not the mapping. Confidence remains independent from severity.
10. JSON/form parsing is not performed inside SecretDetector. The ingestion/normalization layer will
    supply observed body fields and paths; this keeps the detector pure and avoids adding a parser
    dependency merely for classification.

## Consequences

The domain can test secret-name boundaries, false-positive exclusions and value-leak invariants
without GeckoView or Android. This does not make missed-secret transfer safe by itself. Transfer
review/blocking rules for unclassified fields and opaque bodies remain mandatory before M4 can
demonstrate a secret-bearing request.
