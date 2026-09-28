# M8 Release Hardening & Milestone Audit

**Milestone:** M8 Full Release Hardening  
**Target Application:** Taho Browser Companion App (`app.taho.browser`)  
**Status:** Certified & Hardened  
**Date:** 2026-09-28  

---

## 1. Release Hardening Audit

### 1.1 Security Hardening
- **Cleartext Traffic Disabled:** `android:usesCleartextTraffic="false"` declared on `<application>` in `app/src/main/AndroidManifest.xml`.
- **Sensitive Screen Recents Protection:** Window flag `FLAG_SECURE` activated dynamically during request inspection, confirmation sheets, and technical workspace viewing in `TahoBrowserApp.kt`.
- **Release Remote-Debugging Hardened:** `GeckoRuntimeHolder.kt` checks `(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` before enabling remote debugging. Remote debugging is strictly disabled in production builds.
- **Secure Download Sanitization:** `SecureDownloadSanitizer.kt` strips path traversals (`../`), invalid characters, control bytes, and bounds filename length to prevent directory traversal exploits.
- **GeckoView Content Blocking & Tracking Protection:** Configured in `GeckoRuntimeHolder.kt` with `STRICT` cookie/tracking protection and `useTrackingProtection(true)` on browser sessions.

### 1.2 M3 Navigation & Intent Defense
- **External-App Confirmation Prompt:** Dialog prompt in `MainActivity.kt` presents destination URI and resolved application label with user opt-in before dispatch.
- **Defensive intent:// Handling:** `DefensiveIntentHandler.kt` validates URIs, parses `Intent.URI_INTENT_SCHEME`, strips package/component/selector targets, enforces `CATEGORY_BROWSABLE`, and falls back safely to `browser_fallback_url`.
- **Return to Originating Tab:** Captures `originatingTabId` before leaving and re-selects originating tab on `onResume()`.
- **Low-Memory Degradation:** `ComponentCallbacks2.onTrimMemory` gracefully pauses or limits capture and purges disposable cached frames.
- **Offline / Network Transition:** `ConnectivityManager.NetworkCallback` displays offline banner and marks in-flight transactions honestly.

### 1.3 Inspection & Workspace UI
- **Filters:** GraphQL, WebSocket, Static-resource, Analytics, and Telemetry chips in `M7ProductUx.kt`.
- **Search:** Request-ID and Content-Type search implemented in `M7TransferSearch.kt`.
- **Technical Workspace:** Full-screen technical view (`M7TechnicalWorkspaceView`) with raw headers, timing, completeness breakdown, and payload view.
- **Inspector Actions:** Per-request Delete action, Replay action with cross-origin warning sheet, and Export Instead fallback when Taho is not installed.
- **Haptics:** Tactile feedback on capture toggle, filter selection, export, and delete.

### 1.4 Storage & Schema Integrity
- **Retention Mode:** User-configurable `KEEP_UNTIL_DELETED` / workspace capture mode in `M7SettingsSheet`.
- **Canonical Room Tables:** `TabEntity`, `StreamFrameEntity`, `TransferRecordEntity`, and `MetaEntity` registered in `CaptureDatabase` with queries in `CaptureDao` and repository methods in `RoomCaptureRepository`.

### 1.5 Contract & Fixtures Matrix
- Full matrix of fixtures in `contract/taho-transfer/fixtures/` covering simple GET/POST/PUT/PATCH/DELETE, multiple headers, cookies, JWT, multipart, binary, GraphQL, redirects, partial bodies, missing bodies, large bodies, and malformed envelopes.
- Documented in `contract/taho-transfer/SCHEMA_CHANGELOG.md`.
- `BackwardCompatibilityFixtureTest` with 10,000-case fuzzing passed with 0 errors.

### 1.6 Release Signing & R8 Minification
- Configured in `app/build.gradle.kts` with configurable release keystore signing.
- `app/proguard-rules.pro` keeps GeckoView JNI, Room DAOs/entities, and contract models while stripping debug logs (`android.util.Log.*`).

---

## 2. Milestone Release Checklist

- [x] P0 Tab attribution verified on M1 architecture and activated in production (`M1_ATTRIBUTION_VERIFIED=true`).
- [x] Zero secret leakage in logs, toString(), or diagnostics (`FullSeededSecretLeakTest` passing).
- [x] Pure JVM modules verified with zero `android.*` imports (`verifyArchitecture` passing).
- [x] Room schema migration and canonical persistence tables verified.
- [x] Full fixture matrix and backward-compatibility suite passing.
- [x] Browser defensive intent dispatch and external app confirmation prompts active.
- [x] 10,000-case envelope fuzz test passing.
- [x] Real cross-app GET/POST/5MB stream transfers verified.
- [x] R8 ProGuard rules and release signing configured.
- [x] Release acceptance evidence documented for D1–D20 and Gates A–H.
