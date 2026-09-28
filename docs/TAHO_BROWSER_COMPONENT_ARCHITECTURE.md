# TAHO_BROWSER_COMPONENT_ARCHITECTURE.md

**Status:** Architecture baseline — pre-implementation
**Date:** 2026-09-26
**Scope:** Every component, its interface, its owner, and its dependencies. The Gradle module graph
is the enforcement mechanism; this document is the intent behind it.

---

## 1. Module graph

```
:app
 ├── :browser:shell          Compose UI, tabs, omnibox, sheets
 ├── :browser:runtime        GeckoRuntime, GeckoSession, GeckoView hosting, delegates
 ├── :browser:observation    WebExtension host, port, ingress, attributor
 ├── :capture:domain         PURE JVM — no android.*, no Room, no GeckoView
 ├── :capture:persist        Room, Keystore, repositories
 ├── :transfer:core          PURE JVM — normaliser, policies, envelope builder
 ├── :transfer:android       Intent sender, artifact store
 └── :contract:taho-transfer PURE JVM — schema, (de)serialisation, compat
```

Enforced rules:

| Module | May import | Forbidden |
|---|---|---|
| `:capture:domain` | `kotlinx.coroutines`, `kotlinx.serialization`, JDK | `android.*`, `androidx.room.*`, `org.mozilla.geckoview.*`, `:capture:persist` |
| `:contract:taho-transfer` | `kotlinx.serialization`, JDK | everything project-internal |
| `:transfer:core` | `:capture:domain`, `:contract:taho-transfer` | `android.*`, GeckoView, Compose |
| `:browser:observation` | `:capture:domain` | Compose, Room |
| `:browser:runtime` | `:capture:domain` (interfaces only) | Compose, Room |
| `:browser:shell` | `:browser:runtime`, `:browser:observation`, `:transfer:android` | `:capture:persist` internals |

`:capture:domain` and `:contract:taho-transfer` are enforced by a Gradle configuration that fails the
build if an `android.*` import appears — not by review discipline. This is the highest-leverage
structural decision in the project: the P0 correctness logic (state machine, attribution, relevance,
secrets, normalisation) becomes JVM-unit-testable in milliseconds, with no emulator.

---

## 2. Tab model

### 2.1 `BrowserTab`

```kotlin
data class BrowserTab(
    val id: TahoTabId,                 // ULID — stable, survives process death & crash
    val isPrivate: Boolean,
    val state: TabState,
    val sessionBinding: SessionBinding,
    val displayIndex: Int               // PRESENTATION ONLY — never read by capture
)

sealed interface SessionBinding {
    data object Detached : SessionBinding                       // no live session
    data class Live(val session: GeckoSession) : SessionBinding  // live GeckoSession
    data class Crashed(val reason: String, val recoverable: Boolean) : SessionBinding
    data class Restoring(val parcel: ByteArray) : SessionBinding
}

enum class TabState { CREATING, LOADING, IDLE, ACTIVE, BACKGROUND, CRASHED, CLOSING, CLOSED }
```

### 2.2 Why `BrowserTab` does **not** hold the `GeckoSession` directly

Holding a `GeckoSession` in a UI-facing data class invites the classic bug: the UI layer passes
"the tab" to the capture layer, and the capture layer asks the tab for its session. That path *works*
until a background tab's session is swapped during restore, at which point events silently attach to
the wrong session.

Instead, `SessionRegistry` is the **only** thing that can resolve a `TahoTabId` to a
`GeckoSession`, and it is owned by the runtime layer. The capture layer never sees a tab object at
all — it receives already-attributed events. There is no code path from a capture event to "the
current tab", because that path does not exist.

### 2.3 Invariants

| # | Invariant |
|---|---|
| T1 | `TahoTabId` is app-minted and stable for the tab's whole life, across crash and process death |
| T2 | `isPrivate` is fixed at construction (GeckoView `usePrivateMode` is init-only) — a private tab is a **different kind of tab**, not a flag |
| T3 | `displayIndex` is written only by `TabManager` on reorder, and read only by the tab strip |
| T4 | Changing selection changes `displayIndex` and `TabState` only. It touches no capture state |
| T5 | A closed tab's transactions become `Detached`, never reassigned |
| T6 | Closing a tab drives its in-flight transactions to `CANCELLED` (IPC §10) before releasing the session |

### 2.4 Private tabs

Because `usePrivateMode` is `initOnly`, private browsing is a **tab type**, not a toggle:

```kotlin
sealed interface TabKind {
    data object Normal : TabKind
    data object Private : TabKind       // session built with usePrivateMode(true)
}
```

Private tabs get a separate `CaptureSession` of kind `PRIVATE` with `retention = PRIVATE`, whose
repository refuses to write secret material (Storage §4.2). Private tabs are visually and
acoustically distinct (PRD's design language) and are excluded from session restore unless the user
restores private tabs explicitly — standard browser behaviour, and a privacy expectation.

---

## 3. Runtime components

### 3.1 `GeckoRuntimeHolder`

```kotlin
interface GeckoRuntimeHolder {
    suspend fun ensureRuntime(): GeckoRuntime     // main thread; idempotent
    fun isInitialised(): Boolean
}
```

- One runtime per process, created on the main thread. There is no shutdown API (Lifecycle §9).
- Failure here is the **only fatal** condition in the app: no engine, no browser.
- Holds `GeckoRuntimeSettings` including content-blocking configuration and the tracking-protection
  default.

### 3.2 `SessionFactory`

```kotlin
interface SessionFactory {
    fun create(kind: TabKind, trackingProtection: TrackingProtectionMode): GeckoSession
    fun attachDelegates(session: GeckoSession, tabId: TahoTabId): Unit
}
```

Applies, **before `open()`**: `usePrivateMode`, `userAgentOverride`, `viewportMode`,
`displayMode`, `suspendMediaWhenInactive`. Installs `ContentDelegate`, `NavigationDelegate`,
`PermissionDelegate`, `ContentBlocking.Delegate`, and the session-scoped WebExtension message
delegate used for attribution (IPC §4.2).

Everything init-only must be set here; `GeckoSessionSettings` will not allow it later.

### 3.3 `GeckoViewHost`

One `GeckoView` per live tab, held by the shell. Responsibilities: bind a session, detach on view
teardown, reattach on re-creation, handle fullscreen transitions, and forward input to the page while
leaving the bottom chrome to Compose.

**Explicitly not here:** any capture logic. `GeckoViewHost` knows nothing about transactions.

### 3.4 `TabManager`

```kotlin
interface TabManager {
    fun createTab(kind: TabKind): BrowserTab
    fun select(tabId: TahoTabId)
    fun close(tabId: TahoTabId)
    fun reorder(ordered: List<TahoTabId>)
    fun bindSession(tabId: TahoTabId, session: GeckoSession)
    fun markCrashed(tabId: TahoTabId, reason: String)
    fun observe(): StateFlow<List<TabListItem>>
    fun currentTabId(): TahoTabId?
}
```

Exposes an immutable `StateFlow` of *presentation* items. Capture never subscribes to it.

---

## 4. Observation components

### 4.1 `BuiltinExtensionHost`

```kotlin
interface BuiltinExtensionHost {
    suspend fun ensureInstalled(): WebExtension
    val isReady: StateFlow<ExtensionState>   // INSTALLING | READY | FAILED
}
```

Wraps `ensureBuiltIn(uri, id)`. Idempotent by design (documented: the extension persists across
restarts and re-installing is harmless). Retries with backoff; on persistent failure, capture goes
`ERROR` and **browsing continues** (Lifecycle §7, PRD §53.1).

### 4.2 `PortRegistry`

Holds live `WebExtension.Port` instances keyed by `conn`. Responsibilities: register/unregister,
deliver app→extension commands, expose connection state for the capture indicator, and count
reconnections. Reconnect backoff per IPC §9.

### 4.3 `EventIngress`

The hot path. Deliberately tiny:

```kotlin
class EventIngress(
    private val maxMessageBytes: Int,
    private val queue: BoundedEventQueue
) {
    fun accept(raw: Any?, port: WebExtension.Port)   // UI thread
}
```

Order of operations, all O(1), no allocation beyond the envelope:
1. reject if `raw !is JSONObject`
2. reject if the encoded length estimate exceeds `maxMessageBytes` — **before** deep parsing
3. read and range-check `v`, `type`, `id`, `seq`, `conn`
4. drop if `conn` is unregistered
5. drop if `seq` regresses; count a gap if it skips
6. parse `payload` into a typed `CaptureEvent`, catching all exceptions → drop + count
7. compute priority, offer to `queue`

Everything expensive (persistence, decryption, relevance, UI) happens on the capture pipeline's
coroutine. `EventIngress` never blocks. This is what keeps browsing responsive under capture load
and what makes "capture failure does not crash browsing" structurally true.

### 4.4 `FrameAttributor` — the attribution authority

```kotlin
interface FrameAttributor {
    fun registerPending(session: GeckoSession, tabId: TahoTabId)
    fun bind(extTabId: Int, session: GeckoSession, tabId: TahoTabId): Boolean
    fun resolve(extTabId: Int): Attribution
    fun invalidate(session: GeckoSession)
    fun confirm(tabId: TahoTabId, extTabId: Int, origin: String): Boolean
}
```

- `registerPending` — a session is created and awaits its handshake.
- `bind` — called from the session-scoped delegate. Validates origin, then binds.
- `resolve` — the only function the capture path may use. Returns the four-valued `Attribution`
  (Data Model §4.4). **Never throws, never guesses, never returns the selected tab.**
- `invalidate` — on session close; existing attributions become `Detached`, bindings are removed.
- `confirm` — on restore, a persisted binding is treated as a *hint* until re-confirmed.

A single-threaded `Mutex` guards the map. All mutations and reads are cheap and short; no I/O under
the lock.

### 4.5 `CaptureEventSource`

```kotlin
interface CaptureEventSource {
    fun events(): Flow<CaptureEvent>
    val health: StateFlow<SourceHealth>
}
```

One implementation, `GeckoViewWebExtensionSource`, wraps the extension. A second,
`PageInstrumentationSource`, is specified as the fallback if SPIKE-01 fails (Capability Matrix §10).
Because the capture domain consumes `CaptureEvent`, the fallback is a swap at L3, not a rewrite of
L4. This interface is the reason the architecture survives the biggest unknown.

---

## 5. Capture domain components (pure JVM)

### 5.1 `CaptureCoordinator`

```kotlin
interface CaptureCoordinator {
    fun start(session: CaptureSession)
    fun pause(sessionId: CaptureSessionId)
    fun resume(sessionId: CaptureSessionId)
    fun submit(event: CaptureEvent)          // called by the pipeline, not by ingress
    fun observe(): StateFlow<CaptureState>   // OFF|OBSERVING|CAPTURING|PAUSED|LIMITED|ERROR
    fun observeTransactions(sessionId: CaptureSessionId): StateFlow<List<TransactionSummary>>
}
```

Owns the `CaptureSession`, the `CorrelationIndex`, the `TransactionStateMachine`, and the pipeline
coroutine. **Independent of UI** (PRD "Decoupled Observation State") — the shell observes it; it
never observes the shell.

### 5.2 `RelevanceEngine`

```kotlin
interface RelevanceEngine {
    fun classify(tx: Transaction, session: CaptureSession): Relevance
}
```

Pure. No clock, no I/O, no randomness. Rules in Data Model §7, with the two PRD-mandated
corrections encoded as unit tests: document navigation is never an API request, and first-party
matching is hostname-boundary only.

### 5.3 `SecretDetector`

```kotlin
interface SecretDetector {
    fun assess(req: RequestPart, resp: ResponsePart?, url: String): SecretAssessment
}
```

Classifies; never redacts. Order of confidence: name rules → shape rules → context rules. Emits
`evidence` that can never contain a value (Data Model §8). Structurally advisory; the transfer
default (`PARAMETERIZE`) is what makes a miss fail closed.

### 5.4 `BoundedEventQueue`

```kotlin
interface BoundedEventQueue {
    fun offer(event: CaptureEvent): Boolean      // false = dropped (counted by class)
    fun drain(): Flow<CaptureEvent>
    fun stats(): QueueStats                        // depth, capacity, droppedByClass
}
```

Priority lanes with a reserved floor for P0/P1 (IPC §8). Real backpressure: `offer` fails when full
rather than growing. Drops are counted per class and surfaced as `LIMITED` — never silent.

### 5.5 `TransactionStateMachine`

```kotlin
interface TransactionStateMachine {
    fun canTransition(from: TransactionState, to: TransactionState): Boolean
    fun apply(tx: Transaction, event: CaptureEvent): Transaction
}
```

Total transition table (Data Model §4.3). An invalid transition is a no-op plus a diagnostic, never a
mutation. Exhaustively unit-tested on the JVM — this is where the P0 "invalid transitions must not
corrupt persisted state" requirement is actually proven.

---

## 6. Persistence components

| Component | Interface | Notes |
|---|---|---|
| `CaptureRepository` | `observeTransactions`, `upsert`, `get`, `deleteSession`, `markOrphans` | declared in `:capture:domain`, implemented in `:capture:persist` — this is how the domain stays pure |
| `RoomCaptureRepository` | — | Room DAOs, single writer dispatcher |
| `KeystoreCipher` | `encrypt`, `decrypt`, `isAvailable` | AES-256-GCM, key-versioned |
| `BodyStore` | `writeChunk`, `read`, `delete` | inline ≤64 KB, else encrypted app-private file |
| `RetentionController` | `enforce`, `sweep` | policy from Storage §4 |
| `MaintenanceWorker` | WorkManager jobs | artifact, retention, orphan sweepers |

---

## 7. Transfer components

| Component | Module | Responsibility |
|---|---|---|
| `RequestNormalizer` | `:transfer:core` (pure) | versioned deny-list header stripping; idempotent; `normalizerVersion` recorded |
| `SecretPolicyApplier` | `:transfer:core` (pure) | PARAMETERIZE / MASK / EXPLICIT; produces `TransferRepresentation` |
| `EnvelopeBuilder` | `:transfer:core` (pure) | builds the versioned envelope; size estimation |
| `TransferCoordinator` | `:transfer:android` | orchestrates confirm → build → transport → receipt → cleanup |
| `TransportSelector` | `:transfer:android` | INTENT below budget, FILE_URI above; automatic (PRD §44) |
| `TempArtifactStore` | `:transfer:android` | encrypted app-private file, `FileProvider` URI, one-time grant, TTL |
| `IntentSender` | `:transfer:android` | `setPackage`-targeted explicit Intent; error taxonomy mapping |

`SecretPolicyApplier` **cannot** produce a `TransferRepresentation` without a policy decision
(Data Model invariant I4). This makes "no silent secret transfer" a type-level property.

---

## 8. UI components (`:browser:shell`)

| Surface | Level | Content |
|---|---|---|
| `BrowserSurface` | Ambient | `GeckoView` + bottom chrome. No technical chrome. |
| `Omnibox` | Ambient | URL/search, tokenised long URLs, scheme/host/path/query distinction, IME-aware |
| `CaptureIndicator` | Ambient | `● 6 relevant requests` / `Ⅱ paused` / `△ limited` — state + count only, no values (PRD §87) |
| `CaptureSummarySheet` | Summary | relevant rows: method, endpoint, status, latency, category, sensitive-badge |
| `CaptureFilterBar` | Summary | Relevant (default) / All / Auth / API / GraphQL / WS / Static / Analytics / Telemetry |
| `RequestInspector` | Inspector | 10 sections (Data Model / PRD §56), with a completeness block |
| `TransferConfirmationSheet` | Inspector | itemised included/protected; `EXPLICIT` secret gate; private-session warning |
| `TechnicalWorkspace` | Fullscreen | large bodies, raw headers, binary metadata. Retains back + request identity + primary action (PRD §105) |
| `TabSwitcher` | Ambient | visual tabs; capture counts per tab, no values |
| `PermissionPrompt` | Ambient | per-capability, per-origin, session-attributed |
| `ExternalAppPrompt` | Ambient | PRD §89 wording |

**Progressive disclosure is a navigation invariant, not a styling choice.** There is no code path
that shows the inspector without passing through the summary, and no floating technical control
exists (PRD §133). Fullscreen retains back-navigation and the request identity so the user never
loses track of which request they are looking at.

---

## 9. State exposure

```kotlin
// Shell
val tabs: StateFlow<List<TabListItem>>            // TabManager
val activeTab: StateFlow<TahoTabId?>

// Capture
val captureState: StateFlow<CaptureState>         // CaptureCoordinator
val relevant: StateFlow<List<TransactionSummary>> // filtered, summarised, masked
val health: StateFlow<SourceHealth>               // Observation

// Transfer
val transferState: StateFlow<TransferState>       // TransferCoordinator
```

All capture-facing state flows into the UI as **summaries** — `TransactionSummary`, never a
`Transaction` with plaintext secrets. The UI cannot render a secret it was never given, which is a
stronger guarantee than "the UI remembers to mask it" (Data Model I1).

---

## 10. Dependency-injection shape

Constructor injection, no framework. Rationale: the pure-JVM modules must be constructible in a unit
test with plain fakes, and a DI framework would either drag `android.*` into test scope or add
indirection for a graph this small.

```kotlin
class CaptureCoordinator(
    private val repository: CaptureRepository,        // interface from :capture:domain
    private val relevance: RelevanceEngine,
    private val secrets: SecretDetector,
    private val machine: TransactionStateMachine,
    private val queue: BoundedEventQueue,
    private val scope: CoroutineScope                  // app-scoped, supervisor job
)
```

A single application-scoped `CoroutineScope` with a `SupervisorJob` owns the capture pipeline. A
failure in one transaction's processing must not cancel the coordinator — `SupervisorJob` plus
per-event `runCatching` gives that. Capture degradation is expected, not exceptional.
