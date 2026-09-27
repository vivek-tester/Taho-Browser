package app.taho.browser

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.taho.browser.capture.domain.SecretPolicy
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
import app.taho.browser.shell.SitePermissionUiState
import app.taho.browser.shell.TahoBrowserApp
import app.taho.browser.transfer.android.TahoDirectTransferIntentFactory
import app.taho.browser.transfer.android.TahoDirectTransferTarget
import app.taho.browser.transfer.core.M4PreparationBlock
import app.taho.browser.transfer.core.M4PreparationResult

class MainActivity : ComponentActivity() {
    private lateinit var controller: BrowserRuntimeController
    private lateinit var captureRuntime: M4CaptureRuntime
    private var activeAndroidPermissionRequestId: String? = null
    private var activeAndroidPermissions: List<String> = emptyList()
    private var transferNotice by mutableStateOf<String?>(null)
    private var pendingTransferId: String? = null

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

        controller = BrowserRuntimeStore.get(this)
        captureRuntime = M4CaptureRuntime(
            runtime = GeckoRuntimeHolder.get(this),
            gate = if (BuildConfig.M1_ATTRIBUTION_VERIFIED) {
                ProductionCaptureGate.ENABLED
            } else {
                ProductionCaptureGate.BLOCKED_M1_DEVICE_EVIDENCE
            },
            appVersion = BuildConfig.VERSION_NAME,
            engineVersion = BuildConfig.GECKOVIEW_VERSION,
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
            val captureRequests = captureUiRequests(
                captureSnapshot = captureSnapshot,
                selectedTabId = selectedTabId,
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
                    relevantCount = captureRequests.size,
                    omniboxText = visibleLocation,
                    tabCount = snapshot.tabCount,
                    isLoading = snapshot.isLoading,
                    loadFailed = snapshot.loadFailed,
                    crashed = snapshot.crashed,
                    isPrivate = snapshot.isPrivate,
                    canGoBack = snapshot.canGoBack,
                    canGoForward = snapshot.canGoForward,
                    notice = transferNotice ?: snapshot.notice,
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
                onDismissNotice = {
                    if (transferNotice != null) {
                        transferNotice = null
                    } else {
                        controller.dismissNotice()
                    }
                },
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

    private fun captureUiRequests(
        captureSnapshot: M4CaptureRuntimeSnapshot,
        selectedTabId: String,
    ): List<M4CaptureRequestUiState> =
        captureSnapshot.requests
            .filter { it.tabId == selectedTabId }
            .mapNotNull { request ->
                val display = captureRuntime.display(request.transferId) ?: return@mapNotNull null
                M4CaptureRequestUiState(
                    id = request.transferId,
                    method = display.method,
                    url = display.url,
                    status = display.status,
                    durationMs = display.durationMs,
                    category = request.initiator ?: "API",
                    headers = display.headers.map { header ->
                        M4CaptureHeaderUiState(
                            name = header.name,
                            displayValue = header.displayValue,
                            sensitive = header.sensitive,
                        )
                    },
                    requestBodyCompleteness =
                        M4CompletenessUi.valueOf(display.requestBodyCompleteness.name),
                    responseBodyCompleteness =
                        M4CompletenessUi.valueOf(display.responseBodyCompleteness.name),
                    sensitiveCount = display.sensitiveCount,
                    fromPrivateSession = request.fromPrivateSession,
                    transferBlockedReason = if (request.reviewRequired) {
                        "This request needs additional sensitive-data review before transfer."
                    } else {
                        null
                    },
                    explicitPolicyAllowed = false,
                )
            }

    private fun sendToTaho(
        transferId: String,
        policy: M4SecretPolicyUi,
    ) {
        val domainPolicy = SecretPolicy.valueOf(policy.name)
        when (val result = captureRuntime.prepare(transferId, domainPolicy)) {
            null -> transferNotice = "The captured request is no longer available."

            is M4PreparationResult.Blocked -> {
                transferNotice = when (result.reason) {
                    M4PreparationBlock.REVIEW_REQUIRED ->
                        "Transfer blocked: sensitive data still requires review."
                    M4PreparationBlock.EXPLICIT_SECRET_UNAVAILABLE ->
                        "Explicit credential transfer is unavailable for this capture."
                    M4PreparationBlock.INVALID_CONTRACT ->
                        "Transfer blocked: request does not satisfy the Taho contract."
                    M4PreparationBlock.REQUIRES_LARGE_PAYLOAD_M6 ->
                        "Transfer requires the large-payload handoff path planned for M6."
                }
            }

            is M4PreparationResult.Prepared -> {
                if (BuildConfig.TAHO_TRANSFER_ACTION.isBlank()) {
                    transferNotice =
                        "Project-Taho structured receiver is not configured yet. Nothing was sent."
                    return
                }

                val target = TahoDirectTransferTarget(
                    packageName = BuildConfig.TAHO_PACKAGE_NAME,
                    action = BuildConfig.TAHO_TRANSFER_ACTION,
                )
                val expectedTransferId = result.value.envelope.transferId
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (pendingTransferId != expectedTransferId) return
                        pendingTransferId = null
                        val receipt = TahoDirectTransferIntentFactory.parseReceipt(resultData)
                        transferNotice = when {
                            receipt == null ->
                                "Taho returned no valid transfer receipt."
                            receipt.transferId != expectedTransferId ->
                                "Taho returned a receipt for a different transfer."
                            receipt.result.name == "IMPORTED" ||
                                receipt.result.name == "DUPLICATE" ->
                                "Taho received the request. Import does not execute it."
                            else ->
                                "Taho rejected the request" +
                                    (receipt.errorCode?.let { ": " + it.name } ?: ".")
                        }
                    }
                }
                val intent = TahoDirectTransferIntentFactory.create(
                    target = target,
                    prepared = result.value,
                    resultReceiver = receiver,
                )
                if (intent.resolveActivity(packageManager) == null) {
                    transferNotice = "Compatible Project-Taho receiver is not installed."
                    return
                }

                pendingTransferId = expectedTransferId
                runCatching { startActivity(intent) }
                    .onFailure {
                        pendingTransferId = null
                        transferNotice = "Unable to open Project-Taho."
                    }
            }
        }
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
