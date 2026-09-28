# TAHO_BROWSER_STORAGE_ARCHITECTURE.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** On-device persistence for Taho Browser. Two independent stores, one transient artifact
store, and the rules that keep them from contaminating each other.

---

## 1. Three stores, three purposes (PRD §33)

```
┌────────────────────────────┐  ┌────────────────────────────┐  ┌────────────────────────────┐
│  BROWSER STORE             │  │  CAPTURE STORE             │  │  TRANSFER ARTIFACT STORE   │
│  (engine-owned)            │  │  (app-owned, Room)         │  │  (app-owned, transient)    │
│                            │  │                            │  │                            │
│  cookies, history, cache,  │  │  capture sessions,         │  │  in-flight envelopes       │
│  site storage, TLS state,  │  │  transactions, bodies,     │  │  awaiting Taho import      │
│  browsing state            │  │  secrets (encrypted),      │  │                            │
│                            │  │  provenance, stream frames │  │  TTL: minutes–hours        │
│  Owner: Gecko              │  │  Owner: app                │  │  Owner: app                │
│  Cleared by: "Clear        │  │  Cleared by: "Clear        │  │  Cleared by: receipt or    │
│   browsing data"           │  │   capture data"            │  │   sweeper                  │
└────────────────────────────┘  └────────────────────────────┘  └────────────────────────────┘
             ▲                              ▲                                ▲
             │                              │                                │
      MUST NOT clear capture        MUST NOT log the user         MUST NOT be readable
      unless explicitly             out of websites                after transfer
      selected (PRD §86)
```

**The two independence rules, from PRD §85/§86:**

1. "Clear browsing data" lists Cookies / History / Cache / Site storage, and lists
   **"Captured requests" as a separate, separately-ticked category**. It never deletes capture
   implicitly.
2. "Clear capture data" deletes capture records, capture temp files, and in-memory capture state. It
   **never** touches cookies or site storage. The confirmation says so in words:
   *"Your websites and login sessions will remain."*

This is enforced structurally: the two stores have no foreign keys between them, no shared DAOs, and
the clear operations are separate repository methods with separate confirmation flows. There is no
`clearAll()` anywhere.

---

## 2. Capture store — schema

Room. Version 1. All IDs are ULIDs stored as `TEXT` (sortable, debuggable, no integer coupling).

### 2.1 `capture_session`

```sql
CREATE TABLE capture_session (
  id                TEXT PRIMARY KEY NOT NULL,
  kind              TEXT NOT NULL,          -- EPHEMERAL | WORKSPACE | PRIVATE
  state             TEXT NOT NULL,          -- ACTIVE | PAUSED | CLOSED
  label             TEXT,
  target_host       TEXT,
  retention         TEXT NOT NULL,          -- SESSION_ONLY | KEEP_UNTIL_DELETED | PRIVATE
  created_at        INTEGER NOT NULL,       -- epoch millis, DB-assigned
  closed_at         INTEGER
);
CREATE INDEX idx_session_state ON capture_session(state, created_at DESC);
```

### 2.2 `tab`

```sql
CREATE TABLE tab (
  id                TEXT PRIMARY KEY NOT NULL,   -- TahoTabId
  ext_tab_id        INTEGER,                      -- nullable: unknown until handshake
  session_state     TEXT NOT NULL,                -- PENDING | BOUND | UNOBSERVABLE | CLOSED
  is_private        INTEGER NOT NULL DEFAULT 0,
  display_index     INTEGER NOT NULL,             -- presentation ONLY, never attribution
  created_at        INTEGER NOT NULL,
  closed_at         INTEGER
);
CREATE UNIQUE INDEX idx_tab_ext ON tab(ext_tab_id) WHERE ext_tab_id IS NOT NULL;
```

`display_index` is stored but is **presentation-only**. It exists so the tab strip can restore order.
Nothing in the capture path may read it. A CI check greps the capture module for `displayIndex` and
fails the build — a mechanical guard against the PRD's #1 correctness risk.

### 2.3 `transaction`

```sql
CREATE TABLE "transaction" (
  id                  TEXT PRIMARY KEY NOT NULL,
  capture_session_id  TEXT NOT NULL REFERENCES capture_session(id) ON DELETE CASCADE,
  taho_tab_id         TEXT,                        -- null when UNATTRIBUTED
  attribution         TEXT NOT NULL,                -- KNOWN|UNATTRIBUTED|UNRESOLVED|DETACHED
  ext_tab_id          INTEGER NOT NULL,
  frame_id            INTEGER NOT NULL DEFAULT 0,
  document_id         TEXT,
  engine_request_id   TEXT,
  initiator           TEXT,

  method              TEXT NOT NULL,
  url                 TEXT NOT NULL,
  query_json          TEXT,                        -- canonicalised
  req_headers_enc     BLOB,                        -- encrypted
  req_headers_redacted TEXT,                       -- masked, searchable
  req_body_ref        TEXT,                        -- FK-ish to body table
  req_completeness    TEXT NOT NULL,

  status              INTEGER,
  status_text         TEXT,
  resp_headers_enc    BLOB,
  resp_headers_redacted TEXT,
  resp_body_ref       TEXT,
  resp_completeness   TEXT NOT NULL,

  state               TEXT NOT NULL,
  relevance_category  TEXT NOT NULL,
  relevance_reason    TEXT,
  is_first_party      INTEGER NOT NULL DEFAULT 0,
  secret_summary_json TEXT,                        -- findings, NO values
  observation_source  TEXT NOT NULL,                -- ENGINE | PAGE

  provenance_json     TEXT NOT NULL,                -- immutable
  normalizer_version  TEXT NOT NULL,

  created_at          INTEGER NOT NULL,
  updated_at          INTEGER NOT NULL
);

CREATE INDEX idx_tx_session   ON "transaction"(capture_session_id, created_at DESC);
CREATE INDEX idx_tx_tab       ON "transaction"(taho_tab_id, created_at DESC);
CREATE INDEX idx_tx_relevance ON "transaction"(relevance_category, created_at DESC);
CREATE INDEX idx_tx_state     ON "transaction"(state);
CREATE INDEX idx_tx_method    ON "transaction"(method);
CREATE INDEX idx_tx_extreq    ON "transaction"(ext_tab_id, engine_request_id);
```

`secret_summary_json` stores `SecretFinding` records **without** any value or value-derived evidence
(Data Model §8). This is what makes the summary list queryable without decrypting anything.

`*_redacted` columns exist so that **search never requires decryption** (PRD §59: "Sensitive values
must not become searchable plaintext by default"). Searching for `checkout` in a path, or `201` in a
status, or `POST` in a method, works against plaintext columns. A user can never search for a token
value because the token is not in any searchable column.

### 2.4 `body`

```sql
CREATE TABLE body (
  id                TEXT PRIMARY KEY NOT NULL,
  transaction_id    TEXT NOT NULL REFERENCES "transaction"(id) ON DELETE CASCADE,
  side              TEXT NOT NULL,          -- REQUEST | RESPONSE
  chunk_index       INTEGER NOT NULL,
  content_type      TEXT,
  charset           TEXT,
  encoding          TEXT NOT NULL,          -- UTF8 | BASE64 | BINARY_REFERENCE
  storage_ref       TEXT,                   -- for on-disk large bodies
  declared_size     INTEGER,                -- engine's CLAIM, nullable
  captured_size     INTEGER NOT NULL,
  truncated         INTEGER NOT NULL DEFAULT 0,
  representation    TEXT NOT NULL,          -- TEXT|JSON|FORM|MULTIPART|GRAPHQL|BINARY
  completeness      TEXT NOT NULL,
  cipher            TEXT,                   -- AES-256-GCM when encrypted inline
  iv                BLOB,
  ciphertext        BLOB,
  created_at        INTEGER NOT NULL,
  UNIQUE(transaction_id, side, chunk_index)
);
```

Bodies ≤ `INLINE_BODY_LIMIT` (default 64 KB) are stored encrypted inline. Larger bodies are written
to app-private files and referenced by `storage_ref`; the file itself is encrypted with a
Keystore-wrapped key (Security §3.1).

### 2.5 `stream_frame`

```sql
CREATE TABLE stream_frame (
  id             TEXT PRIMARY KEY NOT NULL,
  transaction_id TEXT NOT NULL REFERENCES "transaction"(id) ON DELETE CASCADE,
  direction      TEXT NOT NULL,       -- INBOUND | OUTBOUND
  opcode         TEXT,
  body_ref       TEXT,
  payload_size   INTEGER NOT NULL,
  at             INTEGER NOT NULL
);
CREATE INDEX idx_frame_tx ON stream_frame(transaction_id, at);
```

### 2.6 `transfer_record`

```sql
CREATE TABLE transfer_record (
  transfer_id     TEXT PRIMARY KEY NOT NULL,
  transaction_id  TEXT NOT NULL REFERENCES "transaction"(id) ON DELETE CASCADE,
  state           TEXT NOT NULL,   -- NOT_STARTED|PREPARING|AWAITING_CONFIRMATION|TRANSFERRING|RECEIVED|FAILED|CANCELLED|EXPIRED
  secret_policy   TEXT NOT NULL,
  transport       TEXT,            -- INTENT | FILE_URI
  envelope_bytes  INTEGER,
  target_package  TEXT NOT NULL,
  receipt_json    TEXT,            -- result, importedAt, requestId
  error_code      TEXT,            -- PRD §115 taxonomy
  artifact_path   TEXT,            -- cleaned after receipt
  created_at      INTEGER NOT NULL,
  settled_at      INTEGER
);
CREATE INDEX idx_transfer_state ON transfer_record(state, created_at DESC);
```

### 2.7 `meta`

```kotlin
@Entity(tableName = "meta")
data class MetaRow(
    @PrimaryKey val key: String,
    val value: String
)
```

Keys: `schema_version`, `normalizer_version`, `envelope_version_supported`, `engine_version`,
`first_run_at`, `last_capture_session_id`, `sweeper_last_run`, `retention_sweep_cursor`.

`engine_version` is stored so the capability matrix can be scoped to the engine that actually produced
a given capture (Capability Matrix §8). A record captured on engine X is interpreted with X's
capabilities, not today's.

---

## 3. Encryption at rest (PRD §46.4)

### 3.1 Key hierarchy

```
Android Keystore (hardware-backed where available)
  └── MasterKey (AES-256-GCM, non-exportable, app-scoped)
        ├── wraps ──> BodyKey      (rotatable, versioned)
        ├── wraps ──> SecretKey     (rotatable, versioned)
        └── wraps ──> TransferKey   (ephemeral per artifact)
```

`EncryptedFile` / direct Keystore cipher operations via a small `KeystoreCipher` interface in
`:capture:persist`. `:capture:domain` has no knowledge of any of this — it receives and returns
plaintext buffers and never sees a key.

### 3.2 What is encrypted

| Data | Encrypted | Note |
|---|---|---|
| Request/response headers | **yes** | contain `Authorization`, `Cookie` |
| Request/response bodies | **yes** | all bodies, regardless of content type |
| Secret findings | values never stored at all | only category + location |
| Large body files | **yes** | Keystore-wrapped file key |
| Transfer artifacts | **yes** | ephemeral key, one-time URI grant |
| URLs, method, status, timing, category | no | needed for search/display; not secret *as a class*. Query values for secret-classified keys are stored redacted |
| Provenance | no | ids and timestamps only, no values |
| **Browser store** | Gecko-managed | not ours to encrypt; it is already profile-protected |

**URLs are the judgement call.** A URL can carry a token in a query string
(`?api_key=…`, `?access_token=…`). Mitigation: query values whose *key* is classified secret are
stored redacted in `query_json`; the full URL is retained only in the encrypted provenance/body set.
So a token-bearing URL is not searchable in plaintext, and display shows the redacted form. This is
recorded as Risk R-06 with its residual exposure stated plainly.

### 3.3 Key invalidation

If the Keystore key is invalidated (device lock change, biometric enrolment, restore to a new device):

- capture **reading** fails with `KEY_INVALIDATED` → capture surfaces show `ERROR` with a clear
  "re-encryption required" action,
- the browser continues to work normally (capture is optional),
- the user may **discard** capture data; there is no silent partial decryption,
- we do **not** attempt a soft re-encryption of data we cannot read.

`allowBackup=false` (Security §4.1) prevents the worst case — a backup restored onto a device with a
different key.

---

## 4. Retention (PRD §84)

| Data | Default retention | Rationale |
|---|---|---|
| `EPHEMERAL` session capture | session close | browsing shouldn't accumulate a permanent log |
| `WORKSPACE` session capture | until user deletes | explicit promotion implies intent to keep |
| `PRIVATE` session secret material | **never persisted** | regardless of other settings |
| `PRIVATE` session non-secret metadata | session close | |
| Transfer artifacts | 15 minutes after creation, or immediately on receipt | much shorter than capture history (PRD §84) |
| Abandoned transfer artifacts | swept after 1 hour | PRD §65 |
| Engine browsing data | Gecko default | not ours |

### 4.1 Sweepers

A single `MaintenanceWorker` (WorkManager, `ExistingWorkPolicy.KEEP`) runs three jobs:

| Job | Frequency | Action |
|---|---|---|
| `ArtifactSweeper` | hourly | delete expired transfer artifacts; delete orphans (no `transfer_record`) |
| `RetentionSweeper` | daily | drop `SESSION_ONLY` sessions closed > 24 h; drop `PRIVATE` secret material immediately |
| `OrphanSweeper` | daily | delete bodies/frames with no parent transaction; delete transactions with no session |

WorkManager is used rather than `AlarmManager` or a foreground service: it survives process death and
respects Doze, which is exactly the requirement (the existing Taho App already uses
`workmanager`, so this is a familiar dependency).

### 4.2 Private-mode enforcement

`retention = PRIVATE` is enforced at **write time**, not by a sweeper: the repository refuses to
write `req_headers_enc`/`resp_body`/secret plaintext for a `PRIVATE` session, keeping only
non-secret metadata, and it never writes to the transfer artifact store without an explicit,
separately-confirmed `EXPLICIT` policy (Security §3.3, PRD §34).

---

## 5. Migrations (PRD §9: no destructive migration as production strategy)

| Change type | Strategy |
|---|---|
| Add table | `CREATE TABLE` — additive |
| Add nullable column | `ALTER TABLE ADD COLUMN` — additive |
| Add column with default | add nullable, backfill in a `Migration`, then add an index — never `NOT NULL DEFAULT` on a large table in one step |
| Rename column | add new → backfill → drop old across **two** releases |
| Change type / nullability | new table → backfill → swap, with the old table retained for one release |
| Drop column/table | only after one release with no reader, and only in a major-version release |

**Rules:**

1. `exportSchema = true`; every schema version committed under `schemas/`.
2. **Every release ships a tested `Migration` from the immediately previous version**, and CI runs
   the migration test on a seeded database with production-shaped data volume.
3. No `fallbackToDestructiveMigration()`. Ever. `IllegalStateException` on a missing migration is
   the correct production behaviour — it is caught by a crash reporter, not by silently deleting a
   user's captured API requests.
4. Encryption re-keying is a migration-shaped operation: new key version column, re-encrypt row by
   row, drop old. Interrupted re-keying resumes safely because each row records its key version.
5. `PRAGMA foreign_keys = ON`; the `CASCADE`s in the schema are real, which is what makes orphan
   sweeping belt-and-braces rather than load-bearing.

---

## 6. Corruption recovery

| Failure | Behaviour |
|---|---|
| Room database corrupt | `SQLiteDatabaseCorruptException` → offer "Reset capture data" with an explicit count of what will be lost. Browser store untouched. Never auto-wipe silently. |
| A single row unreadable | catch at the DAO boundary, map to a `CorruptRecord` placeholder rendered as "unreadable record", exclude from queries, count toward the `LIMITED` indicator. One bad row must not take the list down. |
| Body file missing (ref points to nothing) | `BodyPart.completeness = UNAVAILABLE`, `capturedSize = 0`, UI says "body file missing" — **not** an empty body. |
| Encrypted blob undecryptable (key rotation) | same as missing, with reason `KEY_ROTATION` |
| Envelope JSON unparseable | drop the record, log the id, increment counter. Never partially parse. |

The governing rule: **a degraded record is always visibly degraded.** The failure mode of storage is
reduced metadata plus an honest label, never a confident-looking empty value.

---

## 7. Browser store integration

The browser store is owned by Gecko. The app touches it only through:

| Need | API | Note |
|---|---|---|
| Clear browsing data | Gecko browsing-data APIs via the runtime's browsing-data delegate | enumerated types, so "Captured requests" is never in the same call |
| Private mode | `GeckoSessionSettings.usePrivateMode(true)` at construction | init-only; not a store setting |
| Session restore | `GeckoSession` Parcel | app persists the bundle |
| Profile location | Gecko-managed app-private dir | no custom path |

**The app does not read cookies or site storage.** Not for capture, not for display. Capture observes
requests as the engine reports them; it does not go and fish credentials out of the cookie jar. This
removes an entire class of unnecessary credential exposure and is the reason the capture store needs
its own secret handling rather than deferring to the browser store.

---

## 8. Size budgets

| Budget | Default | Enforcement |
|---|---|---|
| Inline body ciphertext | 64 KB | `BodyPart` splits into chunks |
| Single body total | 8 MiB | → `TRUNCATED` |
| Transactions per capture session | 50 000 | → oldest `STATIC`/`TELEMETRY` evicted first, counted, surfaced as `LIMITED` |
| Transactions total | 250 000 | same policy, session-scoped oldest first |
| Stream frames per stream | 2 000 | → `truncated = true` |
| Capture DB soft ceiling | 512 MB | `RetentionSweeper` evicts by policy and reports |
| Transfer artifact | 32 MiB | larger transfers rejected with a clear message, not truncated |

All budgets are configuration with defaults, and every eviction is **counted and surfaced**. Silent
truncation is forbidden (PRD §95, §107) — the user must be able to learn that data was dropped.
