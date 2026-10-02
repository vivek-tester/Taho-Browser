package app.taho.browser

import android.Manifest
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.hardware.biometrics.BiometricPrompt
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.PersistableBundle
import android.os.Looper
import android.os.ResultReceiver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.taho.browser.runtime.DefensiveIntentHandler
import app.taho.browser.runtime.DefensiveIntentResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import app.taho.browser.observation.MobileDevToolsRuntime
import app.taho.browser.observation.ObservedBrowserSession
import app.taho.browser.observation.ProductionCaptureGate
import app.taho.browser.runtime.BrowserPrivacyRuntimeSettings
import app.taho.browser.runtime.BrowserRuntimeController
import app.taho.browser.runtime.BrowserTrackingProtectionLevel
import app.taho.browser.runtime.BrowserRuntimeStore
import app.taho.browser.runtime.BrowserSitePermissionKind
import app.taho.browser.runtime.BrowserSnapshot
import app.taho.browser.runtime.BrowserSurfaceView
import app.taho.browser.runtime.GeckoRuntimeHolder
import app.taho.browser.runtime.NavigationInput
import app.taho.browser.shell.BrowserTabUiState
import app.taho.browser.shell.BrowserUiState
import app.taho.browser.shell.DevToolsUiState
import app.taho.browser.shell.M4CaptureHeaderUiState
import app.taho.browser.shell.M4CaptureRequestUiState
import app.taho.browser.shell.M4CompletenessUi
import app.taho.browser.shell.M4SecretPolicyUi
import app.taho.browser.shell.M7TransferPhaseUi
import app.taho.browser.shell.ReaderPageContentUi
import app.taho.browser.shell.SitePermissionUiState
import app.taho.browser.shell.SiteSecurityUiState
import app.taho.browser.shell.TahoBrowserApp
import app.taho.browser.shell.TahoBrowserStateStore
import app.taho.browser.shell.TahoStartupBehavior
import app.taho.browser.shell.TahoSecureDns
import app.taho.browser.shell.TahoAutoCloseTabs
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
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {
    private companion object {
        const val TAHO_TRANSFER_REQUEST_CODE = 0x5448
        const val MAX_BODY_PREVIEW_CHARS = 64 * 1024
    }

    private lateinit var controller: BrowserRuntimeController
    private lateinit var captureRuntime: M4CaptureRuntime
    private lateinit var devToolsRuntime: MobileDevToolsRuntime
    private lateinit var captureRepository: RoomCaptureRepository
    private lateinit var transferCoordinator: TahoSecureTransferCoordinator
    private var activeAndroidPermissionRequestId: String? = null
    private var activeAndroidPermissions: List<String> = emptyList()
    private var transferNotice by mutableStateOf<String?>(null)
    private var transferPhase by mutableStateOf(M7TransferPhaseUi.NOT_STARTED)
    private var pendingTransferId: String? = null
    private var transferPreparationInFlight: Boolean = false
    private var isOffline by mutableStateOf(false)
    private var originatingTabId: String? = null
    private var pendingPrivateAction: (() -> Unit)? = null
    private var privateBiometricCancellation: CancellationSignal? = null
    private var privateBiometricFallingBack: Boolean = false
    private var pendingExternalNavDialog by mutableStateOf<Triple<String, String, Intent>?>(null)
    private var captureRetentionMode by mutableStateOf("Session only")
    private var appliedSecureDnsKey: String? = null
    private var appliedPrivacyKey: String? = null
    private var pendingCaptureExportText: String? = null
    private val committedHistoryLocationByTab = mutableMapOf<String, String?>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { isOffline = false }
        }
        override fun onLost(network: Network) {
            runOnUiThread { isOffline = true }
        }
    }

    private val captureExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val text = pendingCaptureExportText
        pendingCaptureExportText = null
        if (uri == null || text == null) return@registerForActivityResult

        runCatching {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open export destination")
        }.onSuccess {
            transferNotice = "Captured packets exported."
        }.onFailure {
            transferNotice = "Unable to export captured packets."
        }
    }

    private val privateTabCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        privateBiometricFallingBack = false
        val action = pendingPrivateAction
        pendingPrivateAction = null
        if (result.resultCode == RESULT_OK) {
            action?.invoke()
        } else {
            transferNotice = "Private tabs remain locked."
        }
    }

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

        TahoBrowserStateStore.initialize(this)
        controller = BrowserRuntimeStore.get(this)
        applySecureDnsSetting(reloadSelectedPage = false)
        applyPrivacySettings(reloadSelectedPage = false)
        if (savedInstanceState == null && !isIncomingWebIntent(intent)) {
            applyStartupBehavior()
        }
        applyInactiveTabPolicy()
        TahoBrowserStateStore.prunePinnedTabs(
            controller.snapshot().tabs.mapTo(mutableSetOf()) { it.id },
        )
        handleIncomingBrowserIntent(intent)
        controller.snapshot().tabs.forEach { tab ->
            if (!tab.isPrivate) {
                committedHistoryLocationByTab[tab.id] = tab.location
            }
        }
        captureRepository = CapturePersistenceStore.repository(this)
        CaptureMaintenance.schedule(this)
        transferCoordinator = TahoSecureTransferCoordinator(this)
        TransferArtifactMaintenance.schedule(this)

        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback,
            )
        }

        val recoveredReceipt = TransferReceiptRecoveryStore.consumeLatest(this)
        if (recoveredReceipt != null) {
            transferNotice = receiptNotice(recoveredReceipt)
        } else if (transferCoordinator.recoverExpiredAttempts().isNotEmpty()) {
            transferNotice = "A previous Taho transfer expired. The source capture is still available."
        }
        val captureGate = if (BuildConfig.M1_ATTRIBUTION_VERIFIED) {
            ProductionCaptureGate.ENABLED
        } else {
            ProductionCaptureGate.BLOCKED_M1_DEVICE_EVIDENCE
        }
        captureRuntime = M4CaptureRuntime(
            runtime = GeckoRuntimeHolder.get(this),
            gate = captureGate,
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
        captureRuntime.setEnabled(TahoBrowserStateStore.captureEnabled)
        captureRuntime.start()

        devToolsRuntime = MobileDevToolsRuntime(
            runtime = GeckoRuntimeHolder.get(this),
            gate = captureGate,
        )
        devToolsRuntime.setEnabled(TahoBrowserStateStore.captureEnabled)

        setContent {
            var snapshot by remember { mutableStateOf(controller.snapshot()) }
            var captureSnapshot by remember {
                mutableStateOf(captureRuntime.snapshot())
            }
            var devToolsConnections by remember {
                mutableStateOf(devToolsRuntime.connectedTabs())
            }
            var devToolsUi by remember {
                mutableStateOf(DevToolsUiState())
            }
            val captureEnabled = TahoBrowserStateStore.captureEnabled
            val secureDns = TahoBrowserStateStore.settings.secureDns
            val customDnsProvider = TahoBrowserStateStore.settings.customDnsProvider
            val privacySettings = TahoBrowserStateStore.settings
            val privacyRuntimeKey = buildString {
                append(privacySettings.trackingProtectionLevel.name)
                append('|').append(privacySettings.blockTrackers)
                append('|').append(privacySettings.blockThirdPartyCookies)
                append('|').append(privacySettings.fingerprintingProtection)
                append('|').append(privacySettings.cryptominingProtection)
                append('|').append(privacySettings.httpsOnlyMode)
                append('|').append(privacySettings.safeBrowsingEnabled)
                append('|').append(privacySettings.popupBlockerEnabled)
                append('|').append(privacySettings.redirectBlockingEnabled)
                append('|').append(privacySettings.doNotTrack)
                append('|').append(privacySettings.globalPrivacyControl)
                append('|').append(privacySettings.perSiteTrackingExceptions.sorted().joinToString(","))
            }

            LaunchedEffect(secureDns, customDnsProvider) {
                applySecureDnsSetting(reloadSelectedPage = true)
            }
            LaunchedEffect(privacyRuntimeKey) {
                applyPrivacySettings(reloadSelectedPage = true)
            }

            DisposableEffect(controller, captureRuntime, devToolsRuntime) {
                controller.setListener { next ->
                    snapshot = next
                    syncCaptureSessions(next)
                    syncBrowserHistory(next)
                }
                captureRuntime.setListener { captureSnapshot = it }
                devToolsRuntime.setListener { connected ->
                    devToolsConnections = connected
                }
                onDispose {
                    controller.setListener(null)
                    captureRuntime.setListener(null)
                    devToolsRuntime.setListener(null)
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

            androidx.compose.runtime.LaunchedEffect(selectedTabId) {
                devToolsUi = DevToolsUiState(
                    connected = captureEnabled && selectedTabId in devToolsConnections,
                )
            }

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

                when (val result = DefensiveIntentHandler.parse(request.uri, this@MainActivity)) {
                    is DefensiveIntentResult.SafeIntent -> {
                        pendingExternalNavDialog = Triple(
                            request.id,
                            result.displayLabel,
                            result.intent,
                        )
                    }
                    is DefensiveIntentResult.FallbackInBrowser -> {
                        controller.resolveExternalNavigation(request.id, opened = true)
                        controller.load(uri = result.fallbackUrl)
                    }
                    is DefensiveIntentResult.Blocked -> {
                        controller.resolveExternalNavigation(request.id, opened = false)
                        transferNotice = "Blocked external request: ${result.reason}"
                    }
                }
            }

            pendingExternalNavDialog?.let { (reqId, label, intent) ->
                AlertDialog(
                    onDismissRequest = {
                        controller.resolveExternalNavigation(reqId, opened = false)
                        pendingExternalNavDialog = null
                    },
                    title = { Text("Open in $label?") },
                    text = { Text("You are leaving Taho Browser to open an external application.") },
                    confirmButton = {
                        TextButton(onClick = {
                            originatingTabId = controller.snapshot().selectedTabId
                            val opened = runCatching {
                                startActivity(intent)
                                true
                            }.getOrDefault(false)
                            controller.resolveExternalNavigation(reqId, opened = opened)
                            pendingExternalNavDialog = null
                        }) {
                            Text("Open")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            controller.resolveExternalNavigation(reqId, opened = false)
                            pendingExternalNavDialog = null
                        }) {
                            Text("Stay in Browser")
                        }
                    },
                )
            }

            val isTahoInstalled = transferCoordinator.isTargetAvailable(
                TahoDirectTransferTarget(
                    packageName = BuildConfig.TAHO_PACKAGE_NAME,
                    action = BuildConfig.TAHO_TRANSFER_ACTION,
                ),
            )

            TahoBrowserApp(
                state = BrowserUiState(
                    captureState = captureSnapshot.state,
                    captureEnabled = captureEnabled,
                    relevantCount = captureRequests.count { it.relevantByDefault },
                    omniboxText = visibleLocation,
                    tabCount = snapshot.tabCount,
                    isLoading = snapshot.isLoading,
                    loadFailed = snapshot.loadFailed,
                    crashed = snapshot.crashed,
                    isPrivate = snapshot.isPrivate,
                    isFullScreen = snapshot.isFullScreen,
                    canGoBack = snapshot.canGoBack,
                    canGoForward = snapshot.canGoForward,
                    securityInfo = snapshot.security?.let { security ->
                        SiteSecurityUiState(
                            isSecure = security.isSecure,
                            isException = security.isException,
                            host = security.host,
                            certificateSubject = security.certificateSubject,
                            certificateIssuer = security.certificateIssuer,
                            activeMixedContentLoaded = security.activeMixedContentLoaded,
                            passiveMixedContentLoaded = security.passiveMixedContentLoaded,
                        )
                    },
                    isTahoInstalled = isTahoInstalled,
                    retentionMode = captureRetentionMode,
                    notice = if (isOffline) {
                        "Device is offline. Network requests cannot be made until connected."
                    } else {
                        transferNotice
                            ?: captureSnapshot.storageDegradedReason?.let(::storageNotice)
                            ?: snapshot.notice
                    },
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
                    devTools = devToolsUi.copy(
                        connected = captureEnabled && selectedTabId in devToolsConnections,
                    ),
                ),
                onCaptureEnabledChange = { enabled ->
                    TahoBrowserStateStore.updateCaptureEnabled(enabled)
                    captureRuntime.setEnabled(enabled)
                    devToolsRuntime.setEnabled(enabled)
                    if (!enabled) {
                        devToolsUi = DevToolsUiState(connected = false)
                    }
                },
                onDevToolsRequest = { command, argument ->
                    devToolsUi = DevToolsUiState(
                        connected = selectedTabId in devToolsConnections,
                        loading = true,
                        command = command,
                    )
                    devToolsRuntime.request(
                        tabId = selectedTabId,
                        command = command,
                        argument = argument,
                    ) { result ->
                        devToolsUi = DevToolsUiState(
                            connected = selectedTabId in devToolsConnections,
                            loading = false,
                            command = result.command,
                            payloadJson = result.payloadJson,
                            error = result.error,
                        )
                    }
                },
                onDevToolsReloadPage = {
                    if (snapshot.crashed) {
                        controller.recoverCrashedTab(snapshot.selectedTabId)
                    } else {
                        controller.reload()
                    }
                },
                onNavigate = { input ->
                    val searchTemplate = TahoBrowserStateStore.searchEngines
                        .firstOrNull { it.id == TahoBrowserStateStore.settings.defaultSearchEngineId }
                        ?.queryUrl
                        ?: "https://www.google.com/search?q=%s"
                    NavigationInput.resolve(
                        raw = input,
                        searchUrlTemplate = searchTemplate,
                    )?.let { uri ->
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
                onExitFullScreen = controller::exitFullScreen,
                onNewTab = { controller.newTab(privateMode = false) },
                onNewPrivateTab = {
                    val current = controller.snapshot()
                    if (current.isPrivate) {
                        controller.newTab(privateMode = true)
                    } else {
                        requestPrivateTabAccess {
                            controller.newTab(privateMode = true)
                        }
                    }
                },
                onSelectTab = { tabId ->
                    val current = controller.snapshot()
                    val target = current.tabs.firstOrNull { it.id == tabId }
                    if (target?.isPrivate == true && !current.isPrivate) {
                        requestPrivateTabAccess {
                            controller.selectTab(tabId)
                        }
                    } else {
                        controller.selectTab(tabId)
                    }
                },
                onCloseTab = { tabId ->
                    snapshot.tabs.firstOrNull { it.id == tabId }?.let { tab ->
                        if (!tab.isPrivate) {
                            TahoBrowserStateStore.recordClosedTab(
                                title = tab.title,
                                url = tab.location,
                                isPrivate = false,
                            )
                        }
                    }
                    committedHistoryLocationByTab.remove(tabId)
                    controller.closeTab(tabId)
                },
                onSitePermissionDecision = controller::resolveSitePermission,
                onCopyCurl = ::copyMaskedCurl,
                onShare = ::shareMaskedRequest,
                onExportCaptureFile = ::exportCaptureFile,
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
                onDeleteRequest = { id ->
                    captureRepository.deleteTransaction(id)
                    transferNotice = "Captured request deleted."
                },
                onInstallTaho = {
                    runCatching {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=${BuildConfig.TAHO_PACKAGE_NAME}"),
                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
                        )
                    }.onFailure {
                        runCatching {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.TAHO_PACKAGE_NAME}"),
                                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
                            )
                        }
                    }
                },
                onRetentionModeChanged = { mode ->
                    captureRetentionMode = mode
                    transferNotice = "Capture retention set to: $mode"
                },
                onReplayRequest = { url ->
                    controller.load(uri = url)
                },
                onFindInPage = { query, backwards, callback ->
                    controller.findInPage(
                        query = query,
                        backwards = backwards,
                    ) { result ->
                        callback(result.currentIndex, result.totalMatches)
                    }
                },
                onClearFindInPage = controller::clearFindInPage,
                onSetDesktopMode = controller::setDesktopMode,
                onClearBrowserStorage = { clearCache, clearCookies, callback ->
                    controller.clearBrowsingStorage(
                        clearCache = clearCache,
                        clearCookiesAndSiteData = clearCookies,
                        onComplete = callback,
                    )
                },
                onClearSiteDataForHost = { host, callback ->
                    controller.clearSiteDataForHost(host, callback)
                },
                onExtractReaderContent = { callback ->
                    controller.extractReaderContent { extracted ->
                        callback(
                            extracted?.let {
                                ReaderPageContentUi(
                                    text = it.text,
                                    wordCount = it.wordCount,
                                    language = it.language,
                                    isGated = it.isGated,
                                )
                            },
                        )
                    }
                },
                onPrintPage = controller::printCurrentPage,
                onAddToHomeScreen = ::pinPageShortcut,
                browserContent = {
                    AndroidView(
                        factory = { context ->
                            BrowserSurfaceView(context).also { surface ->
                                surface.onRefresh = { controller.reload() }
                                controller.bind(selectedTabId, surface)
                                surface.onPageLoadingChanged(snapshot.isLoading)
                            }
                        },
                        update = { surface ->
                            surface.onRefresh = { controller.reload() }
                            controller.bind(selectedTabId, surface)
                            surface.onPageLoadingChanged(snapshot.isLoading)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (
            ::captureRuntime.isInitialized &&
            TahoBrowserStateStore.captureEnabled &&
            !TahoBrowserStateStore.captureInBackground
        ) {
            captureRuntime.setEnabled(true)
            devToolsRuntime.setEnabled(true)
        }
        originatingTabId?.let { tabId ->
            originatingTabId = null
            if (controller.snapshot().tabs.any { it.id == tabId }) {
                controller.selectTab(tabId)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            controller.persistNow()
            transferNotice = "Device low on memory. Capture operating in degraded mode."
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::controller.isInitialized) {
            handleIncomingBrowserIntent(intent)
        }
    }

    private fun isIncomingWebIntent(source: Intent?): Boolean {
        if (source?.action != Intent.ACTION_VIEW) return false
        val scheme = source.data?.scheme?.lowercase()
        return scheme == "https" || scheme == "http"
    }

    private fun applyStartupBehavior() {
        val settings = TahoBrowserStateStore.settings
        when (settings.startupBehavior) {
            TahoStartupBehavior.PREVIOUS_TABS -> Unit
            TahoStartupBehavior.START_PAGE -> controller.resetToSingleTab()
            TahoStartupBehavior.CUSTOM_PAGE -> {
                val raw = settings.customStartupUrl.trim()
                val searchTemplate = TahoBrowserStateStore.searchEngines
                    .firstOrNull { it.id == settings.defaultSearchEngineId }
                    ?.queryUrl
                    ?: "https://www.google.com/search?q=%s"
                val uri = raw
                    .takeIf { it.isNotBlank() }
                    ?.let { NavigationInput.resolve(it, searchTemplate) }
                controller.resetToSingleTab(uri)
            }
        }
    }

    private fun applyInactiveTabPolicy() {
        val ageMillis = when (TahoBrowserStateStore.settings.autoCloseTabs) {
            TahoAutoCloseTabs.NEVER -> return
            TahoAutoCloseTabs.AFTER_1_DAY -> 24L * 60L * 60L * 1000L
            TahoAutoCloseTabs.AFTER_1_WEEK -> 7L * 24L * 60L * 60L * 1000L
            TahoAutoCloseTabs.AFTER_1_MONTH -> 30L * 24L * 60L * 60L * 1000L
        }
        controller.closeInactiveTabs(
            olderThanEpochMs = System.currentTimeMillis() - ageMillis,
            pinnedTabIds = TahoBrowserStateStore.pinnedTabIds,
        )
    }

    private fun handleIncomingBrowserIntent(source: Intent?) {
        if (source?.action != Intent.ACTION_VIEW) return
        val uri = source.data ?: return
        if (uri.scheme != "https" && uri.scheme != "http") return
        controller.load(uri = uri.toString())
    }

    override fun onStop() {
        if (
            ::captureRuntime.isInitialized &&
            TahoBrowserStateStore.captureEnabled &&
            !TahoBrowserStateStore.captureInBackground
        ) {
            captureRuntime.setEnabled(false)
            devToolsRuntime.setEnabled(false)
        }
        if (
            isFinishing &&
            !isChangingConfigurations &&
            TahoBrowserStateStore.settings.clearPrivateTabsOnExit
        ) {
            controller.closePrivateTabs()
        }
        TahoBrowserStateStore.persistNow()
        controller.persistNow()
        super.onStop()
    }

    override fun onDestroy() {
        privateBiometricCancellation?.cancel()
        privateBiometricCancellation = null
        privateBiometricFallingBack = false
        pendingPrivateAction = null
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
        }
        devToolsRuntime.close()
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

        val isWorkspace = captureRetentionMode == "Keep until deleted"
        return captureRepository.upsertSession(
            DurableCaptureSession(
                id = sessionId,
                kind = if (isWorkspace) CaptureSessionKind.WORKSPACE else CaptureSessionKind.EPHEMERAL,
                lifecycle = CaptureSessionLifecycle.ACTIVE,
                retention = if (isWorkspace) RetentionPolicy.KEEP_UNTIL_DELETED else RetentionPolicy.SESSION_ONLY,
                createdAtEpochMs = now,
            ),
        )
    }

    private fun syncBrowserHistory(snapshot: BrowserSnapshot) {
        val liveTabIds = snapshot.tabs.mapTo(mutableSetOf()) { it.id }
        committedHistoryLocationByTab.keys.retainAll(liveTabIds)

        snapshot.tabs.forEach { tab ->
            if (tab.isPrivate || tab.isLoading || tab.loadFailed || tab.crashed) return@forEach
            val location = tab.location
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: return@forEach
            if (committedHistoryLocationByTab[tab.id] == location) return@forEach

            committedHistoryLocationByTab[tab.id] = location
            TahoBrowserStateStore.recordHistory(
                title = tab.title,
                url = location,
            )
        }
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
        devToolsRuntime.syncSessions(observed)
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

    private fun requestPrivateTabAccess(action: () -> Unit) {
        val settings = TahoBrowserStateStore.settings
        if (!settings.privateTabLock && !settings.biometricLockForPrivateTabs) {
            action()
            return
        }
        if (pendingPrivateAction != null) {
            transferNotice = "Private-tab authentication is already in progress."
            return
        }

        pendingPrivateAction = action

        if (settings.biometricLockForPrivateTabs && Build.VERSION.SDK_INT >= 28) {
            showPrivateTabBiometricPrompt()
            return
        }

        launchPrivateTabDeviceCredential()
    }

    private fun launchPrivateTabDeviceCredential() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            "Unlock private tabs",
            "Authenticate to enter Taho private browsing.",
        )
        if (intent == null) {
            pendingPrivateAction = null
            transferNotice = "Set a device screen lock before enabling private-tab protection."
            return
        }
        privateTabCredentialLauncher.launch(intent)
    }

    @androidx.annotation.RequiresApi(28)
    private fun showPrivateTabBiometricPrompt() {
        privateBiometricCancellation?.cancel()
        privateBiometricFallingBack = false
        val cancellation = CancellationSignal()
        privateBiometricCancellation = cancellation
        val executor = Executor { command -> runOnUiThread(command) }

        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Unlock private tabs")
            .setSubtitle("Authenticate to enter Taho private browsing.")
            .setNegativeButton("Use device lock", executor) { _, _ ->
                privateBiometricCancellation = null
                privateBiometricFallingBack = true
                launchPrivateTabDeviceCredential()
            }
            .build()

        prompt.authenticate(
            cancellation,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    privateBiometricCancellation = null
                    privateBiometricFallingBack = false
                    val action = pendingPrivateAction
                    pendingPrivateAction = null
                    action?.invoke()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    privateBiometricCancellation = null
                    if (privateBiometricFallingBack) {
                        return
                    }
                    if (pendingPrivateAction != null) {
                        transferNotice = "Private tabs remain locked."
                        pendingPrivateAction = null
                    }
                }

                override fun onAuthenticationFailed() {
                    transferNotice = "Biometric authentication was not recognized."
                }
            },
        )
    }

    private fun applyPrivacySettings(reloadSelectedPage: Boolean) {
        val settings = TahoBrowserStateStore.settings
        val runtimeSettings = BrowserPrivacyRuntimeSettings(
            trackingProtectionLevel = when (settings.trackingProtectionLevel) {
                app.taho.browser.shell.TahoTrackingProtectionLevel.STANDARD ->
                    BrowserTrackingProtectionLevel.STANDARD
                app.taho.browser.shell.TahoTrackingProtectionLevel.STRICT ->
                    BrowserTrackingProtectionLevel.STRICT
                app.taho.browser.shell.TahoTrackingProtectionLevel.CUSTOM ->
                    BrowserTrackingProtectionLevel.CUSTOM
            },
            blockTrackers = settings.blockTrackers,
            blockThirdPartyCookies = settings.blockThirdPartyCookies,
            fingerprintingProtection = settings.fingerprintingProtection,
            cryptominingProtection = settings.cryptominingProtection,
            httpsOnlyMode = settings.httpsOnlyMode,
            safeBrowsingEnabled = settings.safeBrowsingEnabled,
            popupBlockerEnabled = settings.popupBlockerEnabled,
            redirectBlockingEnabled = settings.redirectBlockingEnabled,
            doNotTrack = settings.doNotTrack,
            globalPrivacyControl = settings.globalPrivacyControl,
            perSiteTrackingExceptions = settings.perSiteTrackingExceptions,
        )
        val key = runtimeSettings.toString()
        if (key == appliedPrivacyKey) return

        val hadPreviousSetting = appliedPrivacyKey != null
        val shouldReload =
            reloadSelectedPage &&
                hadPreviousSetting &&
                controller.snapshot().location
                    ?.takeUnless { it == "about:blank" }
                    .isNullOrBlank()
                    .not()

        runCatching {
            controller.applyPrivacySettings(runtimeSettings) {
                if (shouldReload) {
                    controller.reload()
                }
            }
        }.onSuccess {
            appliedPrivacyKey = key
        }.onFailure {
            transferNotice = "Privacy protection settings could not be applied."
        }
    }

    private fun applySecureDnsSetting(reloadSelectedPage: Boolean) {
        val browserSettings = TahoBrowserStateStore.settings
        val resolverUri = when (browserSettings.secureDns) {
            TahoSecureDns.CLOUDFLARE -> "https://cloudflare-dns.com/dns-query"
            TahoSecureDns.QUAD9 -> "https://dns.quad9.net/dns-query"
            TahoSecureDns.GOOGLE -> "https://dns.google/dns-query"
            TahoSecureDns.CUSTOM -> browserSettings.customDnsProvider
                .trim()
                .takeIf { it.startsWith("https://", ignoreCase = true) }
            TahoSecureDns.OFF -> null
        }
        val key = browserSettings.secureDns.name + "|" + resolverUri.orEmpty()
        if (key == appliedSecureDnsKey) return

        val hadPreviousSetting = appliedSecureDnsKey != null
        runCatching {
            controller.setSecureDns(resolverUri)
        }.onSuccess {
            appliedSecureDnsKey = key
            if (
                reloadSelectedPage &&
                hadPreviousSetting &&
                controller.snapshot().location
                    ?.takeUnless { it == "about:blank" }
                    .isNullOrBlank()
                    .not()
            ) {
                controller.reload()
            }
        }.onFailure {
            transferNotice = "Secure DNS could not be applied. Check the resolver configuration."
        }
    }

    private fun hostOf(location: String?): String? =
        location
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { runCatching { URI(it).host }.getOrNull() }

    private fun pinPageShortcut(title: String, url: String) {
        val supportedUrl = Uri.parse(url)
            .takeIf { it.scheme == "https" || it.scheme == "http" }
        if (supportedUrl == null) {
            transferNotice = "Only HTTP(S) pages can be added to the Home screen."
            return
        }

        val manager = getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            transferNotice = "Your launcher does not support pinned web shortcuts."
            return
        }

        val launchIntent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(supportedUrl)

        val shortcut = ShortcutInfo.Builder(
            this,
            "web-" + Integer.toHexString(url.hashCode()),
        )
            .setShortLabel(title.take(40).ifBlank { supportedUrl.host ?: "Web page" })
            .setLongLabel(title.take(80).ifBlank { url })
            .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_view))
            .setIntent(launchIntent)
            .build()

        val requested = runCatching {
            manager.requestPinShortcut(shortcut, null)
        }.getOrDefault(false)

        transferNotice = if (requested) {
            "Home screen shortcut request sent to your launcher."
        } else {
            "Unable to request a Home screen shortcut."
        }
    }

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

    private fun exportCaptureFile(
        fileName: String,
        mimeType: String,
        text: String,
    ) {
        pendingCaptureExportText = text
        runCatching {
            captureExportLauncher.launch(fileName)
        }.onFailure {
            pendingCaptureExportText = null
            transferNotice = "Unable to open the export destination."
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
