package app.taho.browser

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.taho.browser.capture.domain.CaptureQuery
import app.taho.browser.capture.domain.CaptureRepositoryResult
import app.taho.browser.capture.domain.CaptureSessionKind
import app.taho.browser.capture.domain.CaptureSessionLifecycle
import app.taho.browser.capture.domain.DurableCaptureSession
import app.taho.browser.capture.domain.NormalizedHeaderValue
import app.taho.browser.capture.domain.RequestNormalizer
import app.taho.browser.capture.domain.RelevanceClassifier
import app.taho.browser.capture.domain.RelevanceInput
import app.taho.browser.capture.domain.RetentionPolicy
import app.taho.browser.capture.domain.SecretPolicy
import app.taho.browser.capture.domain.StorageDegradationReason
import app.taho.browser.capture.persist.CaptureMaintenance
import app.taho.browser.capture.persist.RoomCaptureRepository
import app.taho.browser.observation.M4CaptureRuntime
import app.taho.browser.observation.M4CaptureRuntimeSnapshot
import app.taho.browser.observation.ObservedBrowserSession
import app.taho.browser.observation.ProductionCaptureGate
import app.taho.browser.runtime.BrowserRuntimeController
import app.taho.browser.runtime.BrowserRuntimeStore
import app.taho.browser.runtime.BrowserSitePermissionKind
import app.taho.browser.runtime.BrowserSnapshot
import app.taho.browser.runtime.BrowserSurfaceView
import app.taho.browser.runtime.GeckoRuntimeHolder
import app.taho.browser.runtime.NavigationInput
import app.taho.browser.shell.BrowserTabUiState
import app.taho.browser.shell.BrowserUiState
import app.taho.browser.shell.M4CaptureHeaderUiState
import app.taho.browser.shell.M4CaptureRequestUiState
import app.taho.browser.shell.M4CompletenessUi
import app.taho.browser.shell.M4SecretPolicyUi
import app.taho.browser.shell.M7TransferPhaseUi
import app.taho.browser.shell.SitePermissionUiState
import app.taho.browser.shell.TahoBrowserApp
import app.taho.browser.transfer.android.TahoDirectTransferIntentFactory
import app.taho.browser.transfer.android.TahoDirectTransferTarget
import app.taho.browser.transfer.android.TahoSecureTransferCoordinator
import app.taho.browser.transfer.android.TransferArtifactMaintenance
import app.taho.browser.transfer.android.TahoTransferTransport
import app.taho.browser.transfer.android.TransferReceiptRecoveryStore
import app.taho.browser.transfer.core.M4PreparationBlock
import app.taho.browser.transfer.core.M4PreparationResult
import app.taho.browser.transfer.core.M4TransferPreparer
import java.net.URI

class MainActivity : ComponentActivity() {
    private companion object {
        const val TAHO_TRANSFER_REQUEST_CODE = 0x5448
        const val MAX_BODY_PREVIEW_CHARS = 64 * 1024
    }

    private lateinit var controller: BrowserRuntimeController
    private lateinit var captureRuntime: M4CaptureRuntime
    private lateinit var captureRepository: RoomCaptureRepository
    private lateinit var transferCoordinator: TahoSecureTransferCoordinator
    private var activeAndroidPermissionRequestId: String? = null
    private var activeAndroidPermissions: List<String> = emptyList()
    private var transferNotice by mutableStateOf<String?>(null)
    private var transferPhase by mutableStateOf(M7TransferPhaseUi.NOT_STARTED)
    private var pendingTransferId: String? = null
    private var transferPreparationInFlight: Boolean = false

    private val androidPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val requestId = activeAndroidPermissionRequestId
        val requested = activeAndroidPermissions
        activeAndroidPermissionRequestId = null
        activeAndroidPermissions = emptyList()

        if (requestId != null) {
            controller.resolveAndroidPermissions(
                requestId = requestId,
                granted = permissionsSatisfied(requested, grants),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge is deliberate: GeckoView paints behind the system bars while
        // Compose chrome owns the navigation-bar safe area. This removes the opaque
        // top/bottom bands without allowing Browser controls under system gestures.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        controller = BrowserRuntimeStore.get(this)
        captureRepository = CapturePersistenceStore.repository(this)
        CaptureMaintenance.schedule(this)
        transferCoordinator = TahoSecureTransferCoordinator(this)
        TransferArtifactMaintenance.schedule(this)
        val recoveredReceipt = TransferReceiptRecoveryStore.consumeLatest(this)
        if (recoveredReceipt != null) {
            transferNotice = receiptNotice(recoveredReceipt)
        } else if (transferCoordinator.recoverExpiredAttempts().isNotEmpty()) {
            transferNotice = "A previous Taho transfer expired. The source capture is still available."
        }
        captureRuntime = M4CaptureRuntime(
            runtime = GeckoRuntimeHolder.get(this),
            gate = if (BuildConfig.M1_ATTRIBUTION_VERIFIED) {
                ProductionCaptureGate.ENABLED
            } else {
                ProductionCaptureGate.BLOCKED_M1_DEVICE_EVIDENCE
            },
            appVersion = BuildConfig.VERSION_NAME,
            engineVersion = BuildConfig.GECKOVIEW_VERSION,
            captureSessionId = CapturePersistenceStore.captureSessionId(),
            sessionInitializer = ::initializeCaptureSession,
            durableSink = captureRepository::commit,
            historyLoader = {
                captureRepository.list(
                    CaptureQuery(limit = 500),
                )
            },
            clearStorage = captureRepository::clearCaptureData,
        )
        captureRuntime.start()

        setContent {
            var snapshot by remember { mutableStateOf(controller.snapshot()) }
            var captureSnapshot by remember {
                mutableStateOf(captureRuntime.snapshot())
            }

            DisposableEffect(controller, captureRuntime) {
                controller.setListener { next ->
                    snapshot = next
                    syncCaptureSessions(next)
                }
                captureRuntime.setListener { captureSnapshot = it }
                onDispose {
                    controller.setListener(null)
                    captureRuntime.setListener(null)
                }
            }

            val visibleLocation = snapshot.location
                ?.takeUnless { it == "about:blank" }
                ?: "Search or enter address"
            val selectedTabId = snapshot.selectedTabId
            val androidPermission = snapshot.androidPermissionRequest
            val externalNavigation = snapshot.externalNavigationRequest
            val targetHostsByTab = snapshot.tabs.associate { tab ->
                tab.id to hostOf(tab.location)
            }
            val captureRequests = captureUiRequests(
                captureSnapshot = captureSnapshot,
                selectedTabId = selectedTabId,
                targetHost = targetHostsByTab[selectedTabId],
            )
            val captureCountsByTab = relevantCaptureCounts(
                captureSnapshot = captureSnapshot,
                targetHostsByTab = targetHostsByTab,
            )

            androidx.compose.runtime.LaunchedEffect(androidPermission?.id) {
                val request = androidPermission ?: return@LaunchedEffect
                activeAndroidPermissionRequestId = request.id
                activeAndroidPermissions = request.permissions

                if (controller.claimAndroidPermissionRequest(request.id)) {
                    runCatching {
                        androidPermissionLauncher.launch(request.permissions.toTypedArray())
                    }.onFailure {
                        activeAndroidPermissionRequestId = null
                        activeAndroidPermissions = emptyList()
                        controller.resolveAndroidPermissions(request.id, granted = false)
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(externalNavigation?.id) {
                val request = externalNavigation ?: return@LaunchedEffect
                if (!controller.claimExternalNavigationRequest(request.id)) {
                    return@LaunchedEffect
                }

                val opened = runCatching {
                    val action = if (request.scheme == "tel") {
                        Intent.ACTION_DIAL
                    } else {
                        Intent.ACTION_VIEW
                    }
                    startActivity(Intent(action, Uri.parse(request.uri)))
                    true
                }.getOrDefault(false)

                controller.resolveExternalNavigation(
                    requestId = request.id,
                    opened = opened,
                )
            }

            TahoBrowserApp(
                state = BrowserUiState(
                    captureState = captureSnapshot.state,
                    relevantCount = captureRequests.count { it.relevantByDefault },
                    omniboxText = visibleLocation,
                    tabCount = snapshot.tabCount,
                    isLoading = snapshot.isLoading,
                    loadFailed = snapshot.loadFailed,
                    crashed = snapshot.crashed,
                    isPrivate = snapshot.isPrivate,
                    canGoBack = snapshot.canGoBack,
                    canGoForward = snapshot.canGoForward,
                    notice = transferNotice
                        ?: captureSnapshot.storageDegradedReason?.let(::storageNotice)
                        ?: snapshot.notice,
                    transferPhase = transferPhase,
                    captureCapabilityNote = when {
                        !BuildConfig.M1_ATTRIBUTION_VERIFIED ->
                            "Production capture remains off until on-device tab attribution is verified."
                        captureSnapshot.storageDegradedReason != null ->
                            storageNotice(requireNotNull(captureSnapshot.storageDegradedReason))
                        else -> null
                    },
                    sitePermission = snapshot.sitePermission?.let { permission ->
                        val copy = permissionCopy(permission.kind)
                        SitePermissionUiState(
                            id = permission.id,
                            origin = permission.origin,
                            title = copy.first,
                            detail = copy.second,
                            isPrivate = permission.isPrivate,
                        )
                    },
                    tabs = snapshot.tabs.map { tab ->
                        BrowserTabUiState(
                            id = tab.id,
                            title = tab.title,
                            location = tab.location,
                            isPrivate = tab.isPrivate,
                            isLoading = tab.isLoading,
                            loadFailed = tab.loadFailed,
                            crashed = tab.crashed,
                            selected = tab.id == snapshot.selectedTabId,
                            relevantCaptureCount = captureCountsByTab[tab.id] ?: 0,
                        )
                    },
                    captureRequests = captureRequests,
                ),
                onNavigate = { input ->
                    NavigationInput.resolve(input)?.let { uri ->
                        controller.load(uri = uri)
                    }
                },
                onBack = controller::goBack,
                onForward = controller::goForward,
                onReload = {
                    if (snapshot.crashed) {
                        controller.recoverCrashedTab(snapshot.selectedTabId)
                    } else {
                        controller.reload()
                    }
                },
                onNewTab = { controller.newTab(privateMode = false) },
                onNewPrivateTab = { controller.newTab(privateMode = true) },
                onSelectTab = controller::selectTab,
                onCloseTab = controller::closeTab,
                onSitePermissionDecision = controller::resolveSitePermission,
                onCopyCurl = ::copyMaskedCurl,
                onShare = ::shareMaskedRequest,
                onDismissNotice = {
                    if (transferNotice != null) {
                        transferNotice = null
                        transferPhase = M7TransferPhaseUi.NOT_STARTED
                    } else {
                        controller.dismissNotice()
                    }
                },
                onClearCaptureData = ::clearCaptureData,
                onSendToTaho = ::sendToTaho,
                browserContent = {
                    AndroidView(
                        factory = { context ->
                            BrowserSurfaceView(context).also { surface ->
                                controller.bind(selectedTabId, surface)
                            }
                        },
                        update = { surface ->
                            controller.bind(selectedTabId, surface)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }

    override fun onStop() {
        controller.persistNow()
        super.onStop()
    }

    override fun onDestroy() {
        captureRuntime.close()
        super.onDestroy()
    }

    private fun initializeCaptureSession(
        sessionId: String,
    ): CaptureRepositoryResult<Unit> {
        val now = System.currentTimeMillis()

        when (
            val closed = captureRepository.closeAbandonedActiveSessions(
                currentSessionId = sessionId,
                nowEpochMs = now,
            )
        ) {
            is CaptureRepositoryResult.Degraded -> return closed
            is CaptureRepositoryResult.Success -> Unit
        }

        when (val recovered = captureRepository.markInterruptedPartial(now)) {
            is CaptureRepositoryResult.Degraded -> return recovered
            is CaptureRepositoryResult.Success -> Unit
        }

        return captureRepository.upsertSession(
            DurableCaptureSession(
                id = sessionId,
                kind = CaptureSessionKind.EPHEMERAL,
                lifecycle = CaptureSessionLifecycle.ACTIVE,
                retention = RetentionPolicy.SESSION_ONLY,
                createdAtEpochMs = now,
            ),
        )
    }

    private fun syncCaptureSessions(snapshot: BrowserSnapshot) {
        val byId = snapshot.tabs.associateBy { it.id }
        val observed = mutableListOf<ObservedBrowserSession>()
        controller.forEachSession { tabId, session ->
            val tab = byId[tabId] ?: return@forEachSession
            observed += ObservedBrowserSession(
                tahoTabId = tabId,
                session = session,
                isPrivate = tab.isPrivate,
                committedUrl = { controller.locationForTab(tabId) },
            )
        }
        captureRuntime.syncSessions(observed)
    }

    private fun relevantCaptureCounts(
        captureSnapshot: M4CaptureRuntimeSnapshot,
        targetHostsByTab: Map<String, String?>,
    ): Map<String, Int> {
        val liveTransactionIds = captureSnapshot.requests.map { it.transactionId }.toSet()
        val counts = mutableMapOf<String, Int>()

        captureSnapshot.requests.forEach { request ->
            val tabId = request.tabId ?: return@forEach
            val relevance = request.relevance ?: RelevanceClassifier.classify(
                RelevanceInput(
                    url = request.url,
                    resourceType = request.initiator,
                    method = request.method,
                    targetHost = targetHostsByTab[tabId],
                    contentType = request.body?.contentType,
                ),
            )
            if (RelevanceClassifier.isRelevantByDefault(relevance)) {
                counts[tabId] = (counts[tabId] ?: 0) + 1
            }
        }

        captureSnapshot.persistedRecords
            .asSequence()
            .filter { it.id !in liveTransactionIds }
            .filter { RelevanceClassifier.isRelevantByDefault(it.relevance) }
            .forEach { stored ->
                val tabId = stored.tahoTabId ?: return@forEach
                counts[tabId] = (counts[tabId] ?: 0) + 1
            }

        return counts
    }

    private fun captureUiRequests(
        captureSnapshot: M4CaptureRuntimeSnapshot,
        selectedTabId: String,
        targetHost: String?,
    ): List<M4CaptureRequestUiState> {
        val liveTransactionIds = captureSnapshot.requests
            .map { it.transactionId }
            .toSet()

        val live = captureSnapshot.requests
            .filter { it.tabId == selectedTabId }
            .mapNotNull { request ->
                val display = captureRuntime.display(request.transferId) ?: return@mapNotNull null
                val relevance = request.relevance ?: RelevanceClassifier.classify(
                    RelevanceInput(
                        url = request.url,
                        resourceType = request.initiator,
                        method = request.method,
                        targetHost = targetHost,
                        contentType = request.body?.contentType,
                    ),
                )
                M4CaptureRequestUiState(
                    id = request.transferId,
                    method = display.method,
                    url = display.url,
                    status = display.status,
                    durationMs = display.durationMs,
                    category = request.initiator ?: "API",
                    headers = display.headers.map { header ->
                        val source = request.headers.firstOrNull { candidate ->
                            candidate.name.equals(header.name, ignoreCase = true) &&
                                candidate.value is NormalizedHeaderValue.Protected
                        }
                        val secretCategory =
                            (source?.value as? NormalizedHeaderValue.Protected)
                                ?.ref
                                ?.category
                                ?.name
                        M4CaptureHeaderUiState(
                            name = header.name,
                            displayValue = header.displayValue,
                            sensitive = header.sensitive,
                            secretCategory = secretCategory,
                        )
                    },
                    requestUrlCompleteness =
                        M4CompletenessUi.valueOf(request.completeness.requestUrl.name),
                    requestHeadersCompleteness =
                        M4CompletenessUi.valueOf(request.completeness.requestHeaders.name),
                    requestBodyCompleteness =
                        M4CompletenessUi.valueOf(display.requestBodyCompleteness.name),
                    responseHeadersCompleteness =
                        M4CompletenessUi.valueOf(request.completeness.responseHeaders.name),
                    responseBodyCompleteness =
                        M4CompletenessUi.valueOf(display.responseBodyCompleteness.name),
                    timingCompleteness =
                        M4CompletenessUi.valueOf(request.completeness.timing.name),
                    tlsCompleteness =
                        M4CompletenessUi.valueOf(request.completeness.tlsInfo.name),
                    bodyRepresentation = display.bodyRepresentation?.name,
                    bodyLimitation = display.bodyLimitation,
                    sensitiveCount = display.sensitiveCount,
                    fromPrivateSession = request.fromPrivateSession,
                    transferBlockedReason = when {
                        request.reviewRequired ->
                            display.bodyLimitation
                                ?: "This request needs additional sensitive-data review before transfer."
                        else -> null
                    },
                    explicitPolicyAllowed = false,
                    relevanceCategory = relevance.category.name,
                    relevantByDefault = RelevanceClassifier.isRelevantByDefault(relevance),
                    captureSessionId = request.captureSessionId,
                    tabId = request.tabId,
                    capturedAtEpochMs = request.capturedAt,
                    sourceVersion = request.appVersion,
                    captureEngineVersion = request.engineVersion,
                    normalizerVersion = RequestNormalizer.VERSION,
                    observationSource = request.observation.name,
                    redirectCount = request.redirectCount,
                    requestBodyCapturedBytes = request.body?.size,
                    requestBodyDeclaredBytes = request.body?.declaredSize,
                    safeBodyPreview = request.body?.content?.take(MAX_BODY_PREVIEW_CHARS),
                    safeBodyPreviewTruncated =
                        (request.body?.content?.length ?: 0) > MAX_BODY_PREVIEW_CHARS,
                )
            }

        val durable = captureSnapshot.persistedRecords
            .asSequence()
            .filter { it.tahoTabId == selectedTabId }
            .filter { it.id !in liveTransactionIds }
            .map { stored ->
                M4CaptureRequestUiState(
                    id = "stored:" + stored.id,
                    method = stored.method,
                    url = stored.url,
                    status = stored.status,
                    durationMs = null,
                    category = stored.relevance.category.name,
                    headers = emptyList(),
                    requestBodyCompleteness =
                        M4CompletenessUi.valueOf(stored.requestBodyCompleteness.name),
                    responseBodyCompleteness =
                        M4CompletenessUi.valueOf(stored.responseBodyCompleteness.name),
                    bodyRepresentation = stored.requestBodyRepresentation?.name,
                    bodyLimitation = stored.bodyLimitation,
                    sensitiveCount = 0,
                    fromPrivateSession = false,
                    transferBlockedReason =
                        "Stored capture survived lifecycle/process recovery. " +
                            "Re-transfer from durable encrypted storage is not exposed by the current Browser UI.",
                    explicitPolicyAllowed = false,
                    relevanceCategory = stored.relevance.category.name,
                    relevantByDefault = RelevanceClassifier.isRelevantByDefault(stored.relevance),
                    captureSessionId = stored.captureSessionId,
                    tabId = stored.tahoTabId,
                    capturedAtEpochMs = stored.createdAtEpochMs,
                    transactionState = stored.state.name,
                )
            }
            .toList()

        return live + durable
    }

    private fun sendToTaho(
        transferId: String,
        policy: M4SecretPolicyUi,
    ) {
        if (transferPreparationInFlight || pendingTransferId != null) {
            transferNotice = "A Taho transfer is already in progress."
            return
        }
        if (BuildConfig.TAHO_TRANSFER_ACTION.isBlank()) {
            transferNotice =
                "Project-Taho structured receiver is not configured yet. Nothing was sent."
            return
        }

        val captured = captureRuntime.capturedRequest(transferId)
        if (captured == null) {
            transferNotice = "The captured request is no longer available."
            return
        }

        val target = TahoDirectTransferTarget(
            packageName = BuildConfig.TAHO_PACKAGE_NAME,
            action = BuildConfig.TAHO_TRANSFER_ACTION,
        )
        if (!transferCoordinator.isTargetAvailable(target)) {
            transferNotice = "Taho API Testing isn't installed."
            transferPhase = M7TransferPhaseUi.FAILED
            return
        }

        transferPhase = M7TransferPhaseUi.PREPARING

        val domainPolicy = SecretPolicy.valueOf(policy.name)
        val expectedTransferId = captured.transferId
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (pendingTransferId != expectedTransferId) return
                pendingTransferId = null
                transferPhase = M7TransferPhaseUi.RECEIVED
                val receipt = TahoDirectTransferIntentFactory.parseReceipt(resultData)
                if (receipt?.transferId == expectedTransferId) {
                    transferCoordinator.settle(expectedTransferId)
                    TransferReceiptRecoveryStore.clearIfMatches(
                        this@MainActivity,
                        expectedTransferId,
                    )
                }
                transferNotice = when {
                    receipt == null -> {
                        transferPhase = M7TransferPhaseUi.FAILED
                        "Taho returned no valid transfer receipt."
                    }
                    receipt.transferId != expectedTransferId -> {
                        transferPhase = M7TransferPhaseUi.FAILED
                        "Taho returned a receipt for a different transfer."
                    }
                    else -> receiptNotice(receipt)
                }
            }
        }

        transferPreparationInFlight = true
        transferPhase = M7TransferPhaseUi.PREPARING
        transferNotice = null

        Thread({
            val preparation = runCatching {
                M4TransferPreparer.prepare(
                    input = captured,
                    requestedPolicy = domainPolicy,
                )
            }.getOrElse {
                runOnUiThread {
                    transferPreparationInFlight = false
                    transferPhase = M7TransferPhaseUi.FAILED
                    transferNotice =
                        "Taho could not receive this request. Your captured request remains in Taho Browser."
                }
                return@Thread
            }

            when (preparation) {
                is M4PreparationResult.Blocked -> {
                    runOnUiThread {
                        transferPreparationInFlight = false
                        transferPhase = M7TransferPhaseUi.FAILED
                        transferNotice = when (preparation.reason) {
                            M4PreparationBlock.REVIEW_REQUIRED ->
                                "Transfer blocked: sensitive data still requires review."
                            M4PreparationBlock.EXPLICIT_SECRET_UNAVAILABLE ->
                                "Explicit credential transfer is unavailable for this capture."
                            M4PreparationBlock.INVALID_CONTRACT ->
                                "Transfer blocked: request does not satisfy the Taho contract."
                        }
                    }
                }

                is M4PreparationResult.Prepared -> {
                    val dispatch = runCatching {
                        transferCoordinator.prepareDispatch(
                            target = target,
                            prepared = preparation.value,
                            resultReceiver = receiver,
                        )
                    }.getOrElse {
                        runOnUiThread {
                            transferPreparationInFlight = false
                            transferPhase = M7TransferPhaseUi.FAILED
                            transferNotice =
                                "Taho could not receive this request. Your captured request remains in Taho Browser."
                        }
                        return@Thread
                    }

                    runOnUiThread {
                        transferPreparationInFlight = false
                        if (isDestroyed) {
                            transferCoordinator.cancel(expectedTransferId)
                            return@runOnUiThread
                        }
                        pendingTransferId = expectedTransferId
                        transferPhase = M7TransferPhaseUi.TRANSFERRING
                        transferNotice = if (
                            dispatch.transport == TahoTransferTransport.ARTIFACT_URI
                        ) {
                            "This request is large. Using secure file transfer…"
                        } else {
                            null
                        }

                        runCatching {
                            @Suppress("DEPRECATION")
                            startActivityForResult(
                                dispatch.intent,
                                TAHO_TRANSFER_REQUEST_CODE,
                            )
                        }.onFailure {
                            pendingTransferId = null
                            transferCoordinator.cancel(expectedTransferId)
                            transferPhase = M7TransferPhaseUi.FAILED
                            transferNotice =
                                "Taho could not receive this request. Your captured request remains in Taho Browser."
                        }
                    }
                }
            }
        }, "taho-transfer-prepare").start()
    }

    private fun receiptNotice(
        receipt: app.taho.browser.contract.TransferReceiptV1,
    ): String =
        when {
            receipt.result.name == "IMPORTED" ->
                "Request received by Taho. Import does not execute it."
            receipt.result.name == "DUPLICATE" ->
                "Taho already imported this request. Nothing was executed."
            receipt.errorCode?.name == "TAHO_TRANSFER_UNSUPPORTED_VERSION" ->
                "Taho needs an update to import this request."
            receipt.errorCode?.name == "TAHO_TRANSFER_URI_EXPIRED" ->
                "The transfer expired. Try again."
            receipt.errorCode?.name == "TAHO_TRANSFER_ACCESS_DENIED" ->
                "Taho could not be given access to the transfer."
            else ->
                "Taho could not import this request. No network request was sent."
        }

    private fun clearCaptureData() {
        captureRuntime.clearCaptureData { result ->
            when (result) {
                is CaptureRepositoryResult.Success -> {
                    transferNotice =
                        "Captured requests cleared. Websites and login sessions were preserved."
                }

                is CaptureRepositoryResult.Degraded -> {
                    transferNotice = storageNotice(result.reason)
                }
            }
        }
    }

    private fun hostOf(location: String?): String? =
        location
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { runCatching { URI(it).host }.getOrNull() }

    private fun copyMaskedCurl(text: String) {
        val copied = runCatching {
            val clipboard = getSystemService(ClipboardManager::class.java)
            val clip = ClipData.newPlainText("Taho Browser masked cURL", text)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
        }.isSuccess
        transferNotice = if (copied) {
            "Masked cURL copied."
        } else {
            "Unable to copy the masked cURL."
        }
    }

    private fun shareMaskedRequest(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        runCatching {
            startActivity(Intent.createChooser(shareIntent, "Share masked request"))
        }.onFailure {
            transferNotice = "Unable to open the Android share sheet."
        }
    }

    private fun storageNotice(reason: StorageDegradationReason): String =
        when (reason) {
            StorageDegradationReason.KEY_INVALIDATED ->
                "Captured-request encryption key is unavailable. Browsing still works; stored capture data must be cleared."
            StorageDegradationReason.LOW_STORAGE ->
                "Capture storage is low. New request bodies may be unavailable."
            StorageDegradationReason.BODY_FILE_MISSING ->
                "A captured body file is missing; metadata is still available."
            StorageDegradationReason.KEY_ROTATION ->
                "A captured body cannot be decrypted with the current key."
            StorageDegradationReason.CORRUPT_RECORD ->
                "Some captured request data is unreadable; browsing is unaffected."
            StorageDegradationReason.DATABASE_CORRUPT ->
                "Captured-request storage is corrupt. Browsing remains available."
        }

    private fun permissionCopy(kind: BrowserSitePermissionKind): Pair<String, String> =
        when (kind) {
            BrowserSitePermissionKind.LOCATION ->
                "Allow location access?" to
                    "This site wants to access your device location."

            BrowserSitePermissionKind.PERSISTENT_STORAGE ->
                "Allow persistent site storage?" to
                    "This site wants to keep site data persistently on this device."

            BrowserSitePermissionKind.CAMERA ->
                "Allow camera access?" to
                    "This site wants to use your camera."

            BrowserSitePermissionKind.MICROPHONE ->
                "Allow microphone access?" to
                    "This site wants to use your microphone."

            BrowserSitePermissionKind.CAMERA_AND_MICROPHONE ->
                "Allow camera and microphone?" to
                    "This site wants to use your camera and microphone."
        }

    private fun permissionsSatisfied(
        requested: List<String>,
        grants: Map<String, Boolean>,
    ): Boolean {
        if (requested.isEmpty()) return false

        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true

        return requested.all { permission ->
            when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION ->
                    if (Manifest.permission.ACCESS_COARSE_LOCATION in requested) {
                        fineGranted || coarseGranted
                    } else {
                        fineGranted
                    }

                else -> grants[permission] == true
            }
        }
    }
}
