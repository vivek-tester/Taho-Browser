# DISC-01 — Actual Taho import surface

Status: RESOLVED FOR DESIGN; receiver implementation still required  
Taho app repository inspected: `vivek-tester/Project-Taho`  
Source commit inspected: `5825b8207df1081b27873e209d43be58eaf983b9`  
Date: 2026-09-27

The repository named `vivek-tester/Taho` is the marketing website. The Android/Flutter API-testing
application referenced by the Browser integration contract is `vivek-tester/Project-Taho`.

## Discovery answers

| # | Question | Finding |
|---|---|---|
| 1 | Application ID / package | `com.eternal.taho`, from `android/app/build.gradle.kts`. |
| 2 | Import entry Activity | No dedicated Browser-transfer import Activity exists. `.MainActivity` is exported and `singleTop`; it currently accepts launcher, auth/OAuth deep links, and `ACTION_SEND text/plain`. A structured Browser receiver must be added rather than pretending the share-text path is the versioned transport. |
| 3 | `ApiRequest` construction | `lib/core/models/api_request.dart`. Constructor and `ApiRequest.fromJson` cover method, URL, headers, params, body/bodyType, auth and GraphQL fields. Browser mapping should construct an `ApiRequest` only after transfer validation. |
| 4 | Request editor route | There is no named router. `HomeScreen` uses an `IndexedStack`; `navIndexProvider = 0` displays `RequestScreen`. Existing share import loads the request then sets index 0. |
| 5 | Import/export serializers | `ApiRequest.toJson/fromJson` exist. cURL, Postman and Bruno importers exist; `ExportService` handles cURL/share output. No `taho.request-transfer` serializer/validator exists in the app. |
| 6 | Collection insertion | `collectionsProvider.notifier.addRequestToCollection(collectionId, request)` persists immediately. Browser import must not call it automatically. |
| 7 | Environment variable creation | `environmentsProvider.notifier.setVariable(envId, key, value, type: ...)` persists immediately; secret variables use secure storage. Browser parameterisation must not silently create environment variables. |
| 8 | Exported components / deep links | `MainActivity` is exported. Manifest currently declares `taho://auth-callback`, `taho://oauth-callback`, and `ACTION_SEND text/plain`. `MainActivity.kt` buffers shared text into the `com.eternal.taho/shared_text` method channel. No Browser-transfer action exists. |
| 9 | Request persistence model | `CurrentRequestNotifier.loadRequest` pushes into the active `requestTabsProvider` session. `updateActiveRequest` marks the tab dirty and schedules a 600 ms draft snapshot. Snapshot persistence strips request secrets. This is draft/session recovery, not collection insertion. |
| 10 | Security-analysis invocation | `SecurityEngine.analyze(response)` and `DiagnosticEngine.diagnose(...)` run from the send/response pipeline. Importing a request must not invoke them because no request has been executed and no response exists. |
| 11 | Shared transfer contract | No existing `taho.request-transfer`, `transferId`, or `minimumReaderVersion` implementation was found. The Browser v1 contract therefore needs a matching receiver implementation in Project-Taho. |

## Existing share-import behavior

The current text-share path is useful precedent for navigation, not for transport security:

1. Android `MainActivity` receives `ACTION_SEND text/plain`.
2. `ShareIntentService.checkSharedText()` reads the one-shot native buffer.
3. `SmartPasteService.parse(...)` creates a request-shaped result.
4. `HomeScreen` calls `currentRequestProvider.notifier.loadRequest(result.request)`.
5. `navIndexProvider` is set to `0`, opening the request editor.

The Browser receiver should preserve steps 4–5 after validation, but it must not encode the
versioned JSON envelope as shared text or route it through SmartPaste.

## Receiver design consequence

TAHO-01 requires a small Project-Taho change:

- add one explicit import action owned by `com.eternal.taho`;
- validate schema/version/byte budgets before constructing `ApiRequest`;
- deduplicate by `transferId`;
- import into the active/new request editor as a dirty draft;
- do not execute, add to a collection, create environment variables, or copy live credentials implicitly;
- return a bounded receipt to the Browser.

Until that receiver exists, Taho Browser may build and test envelope creation but must not advertise
a successful Send-to-Taho path.

## Source paths

- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/kotlin/com/eternal/taho/MainActivity.kt`
- `lib/core/models/api_request.dart`
- `lib/core/providers/current_request_providers.dart`
- `lib/core/providers/request_sessions.dart`
- `lib/core/providers/collections_providers.dart`
- `lib/core/providers/environments_providers.dart`
- `lib/core/services/share_intent_service.dart`
- `lib/core/services/export_service.dart`
- `lib/core/providers/send_pipeline.dart`
- `lib/core/services/security_engine.dart`
- `lib/core/services/diagnostic_engine.dart`
- `lib/screens/home_screen.dart`
