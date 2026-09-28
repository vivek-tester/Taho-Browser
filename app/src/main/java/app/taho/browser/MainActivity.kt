package app.taho.browser

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.os.Looper
import android.os.ResultReceiver
import android.util.Rational
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.taho.browser.runtime.DefensiveIntentHandler
import app.taho.browser.runtime.DefensiveIntentResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.fragment.app.FragmentActivity
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
import app.taho.browser.runtime.BrowserAutofillPromptKind
import app.taho.browser.runtime.BrowserCookiePolicy
import app.taho.browser.runtime.BrowserRuntimePreferences
import app.taho.browser.runtime.BrowserSecureDnsMode
import app.taho.browser.runtime.BrowserTrackingLevel
import app.taho.browser.runtime.BrowserWebColorScheme
import app.taho.browser.runtime.BrowserAutofillStore
import app.taho.browser.runtime.BrowserRuntimeController
import app.taho.browser.runtime.BrowserStoredAddress
import app.taho.browser.runtime.BrowserStoredCreditCard
import app.taho.browser.runtime.BrowserStoredLogin
import app.taho.browser.runtime.BrowserRuntimeStore
import app.taho.browser.runtime.BrowserSitePermissionKind
import app.taho.browser.runtime.BrowserSnapshot
import app.taho.browser.runtime.BrowserSurfaceView
import app.taho.browser.runtime.GeckoRuntimeHolder
import app.taho.browser.runtime.NavigationInput
import app.taho.browser.shell.BrowserAutofillPromptKindUi
import app.taho.browser.shell.BrowserSettingsState
import app.taho.browser.shell.TahoCookiePolicy
import app.taho.browser.shell.TahoSecureDns
import app.taho.browser.shell.TahoThemeMode
import app.taho.browser.shell.TahoTrackingProtectionLevel
import app.taho.browser.shell.BrowserAutofillPromptOptionUi
import app.taho.browser.shell.BrowserAutofillPromptUiState
import app.taho.browser.shell.BrowserTabUiState
import app.taho.browser.shell.BrowserUiState
import app.taho.browser.shell.BrowserUpdateStatusUi
import app.taho.browser.shell.DownloadItemUi
import app.taho.browser.shell.M4CaptureHeaderUiState
import app.taho.browser.shell.M4CaptureRequestUiState
import app.taho.browser.shell.M4CompletenessUi
import app.taho.browser.shell.M4SecretPolicyUi
import app.taho.browser.shell.M7TransferPhaseUi
import app.taho.browser.shell.OfflinePageUi
import app.taho.browser.shell.ReaderPageContentUi
import app.taho.browser.shell.SitePermissionUiState
import app.taho.browser.shell.SiteSecurityUiState
import app.taho.browser.shell.TahoBrowserApp
import app.taho.browser.shell.TahoBrowserStateStore
import app.taho.browser.shell.TahoStartupBehavior
import app.taho.browser.shell.TahoAutoCloseTabs
import app.taho.browser.shell.WebAppManifestUi
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

class MainActivity : FragmentActivity() {
    private companion object {
        const val TAHO_TRANSFER_REQUEST_CODE = 0x5448
        const val MAX_BODY_PREVIEW_CHARS = 64 * 1024
    }

    private lateinit var controller: BrowserRuntimeController
    private lateinit var captureRuntime: M4CaptureRuntime
    private lateinit var captureRepository: RoomCaptureRepository
    private lateinit var transferCoordinator: TahoSecureTransferCoordinator
    private lateinit var updateChecker: BrowserUpdateChecker
    private lateinit var backupManager: BrowserBackupManager
    private var activeAndroidPermissionRequestId: String? = null
    private var pendingNotificationSitePermissionId: String? = null
    private var activeAndroidPermissions: List<String> = emptyList()
    private var transferNotice by mutableStateOf<String?>(null)
    private var transferPhase by mutableStateOf(M7TransferPhaseUi.NOT_STARTED)
    private var pendingTransferId: String? = null
    private var transferPreparationInFlight: Boolean = false
    private var isOffline by mutableStateOf(false)
    private var inPictureInPicture by mutableStateOf(false)
    private var originatingTabId: String? = null
    private var pendingExternalNavDialog by mutableStateOf<Triple<String, String, Intent>?>(null)
    private var captureRetentionMode by mutableStateOf("Session only")
    private val committedHistoryLocationByTab = mutableMapOf<String, String?>()
    private val passwordBreachChecker = PasswordBreachChecker()
    private lateinit var downloadManager: BrowserDownloadManager
    private lateinit var offlinePageManager: OfflinePageManager
    private lateinit var extensionManager: BrowserExtensionManager
    private lateinit var webNotificationManager: BrowserWebNotificationManager
    private lateinit var extensionMarketplace: BrowserExtensionMarketplace
    private var pendingExtensionPermissionRequest by
        mutableStateOf<BrowserExtensionPermissionRequest?>(null)
    private var pendingExtensionPermissionDecision: ((Boolean) -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { isOffline = false }
        }
        override fun onLost(network: Network) {
            runOnUiThread { isOffline = true }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requestId = pendingNotificationSitePermissionId
        pendingNotificationSitePermissionId = null
        if (requestId != null && ::controller.isInitialized) {
            controller.resolveSitePermission(requestId, granted)
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
        downloadManager = BrowserDownloadManager(this) { item ->
            runOnUiThread {
                TahoBrowserStateStore.upsertDownload(item)
            }
        }
        offlinePageManager = OfflinePageManager(this)
        controller = BrowserRuntimeStore.get(this)
        TahoBrowserStateStore.profiles
            .firstOrNull { it.isActive }
            ?.let { profile ->
                if (
                    profile.id != controller.activeProfileId() ||
                    profile.isGuest
                ) {
                    controller.switchProfile(profile.id, profile.isGuest)
                }
            }
        controller.setExternalResponseConsumer(downloadManager::accept)
        controller.setAutofillStore(
            object : BrowserAutofillStore {
                override val loginAutofillEnabled: Boolean
                    get() = TahoBrowserStateStore.settings.passwordAutofillEnabled
                override val passwordSavePromptEnabled: Boolean
                    get() = TahoBrowserStateStore.settings.passwordSavePromptEnabled
                override val addressAutofillEnabled: Boolean
                    get() = TahoBrowserStateStore.settings.addressAutofillEnabled
                override val paymentAutofillEnabled: Boolean
                    get() = TahoBrowserStateStore.settings.paymentAutofillEnabled

                override fun loginsForDomain(domain: String): List<BrowserStoredLogin> {
                    if (!loginAutofillEnabled) return emptyList()
                    val normalized = domain
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore('/')
                        .lowercase()
                    return TahoBrowserStateStore.savedPasswords
                        .filter { it.domain.equals(normalized, ignoreCase = true) }
                        .map { item ->
                            BrowserStoredLogin(
                                id = item.id,
                                domain = item.domain,
                                username = item.username,
                                password = item.password,
                            )
                        }
                }

                override fun allLogins(): List<BrowserStoredLogin> {
                    if (!loginAutofillEnabled) return emptyList()
                    return TahoBrowserStateStore.savedPasswords.map { item ->
                        BrowserStoredLogin(
                            id = item.id,
                            domain = item.domain,
                            username = item.username,
                            password = item.password,
                        )
                    }
                }

                override fun addresses(): List<BrowserStoredAddress> {
                    if (!addressAutofillEnabled) return emptyList()
                    return TahoBrowserStateStore.savedAddresses.map { item ->
                        BrowserStoredAddress(
                            id = item.id,
                            label = item.label,
                            fullName = item.fullName,
                            street = item.street,
                            city = item.city,
                            state = item.state,
                            postalCode = item.zipCode,
                            country = item.country,
                            phone = item.phone,
                            email = item.email,
                        )
                    }
                }

                override fun creditCards(): List<BrowserStoredCreditCard> {
                    if (!paymentAutofillEnabled) return emptyList()
                    return TahoBrowserStateStore.savedPayments.mapNotNull { item ->
                        val number = TahoBrowserStateStore.paymentCardNumberOrNull(item)
                            ?: return@mapNotNull null
                        val (month, year) = parseCardExpiry(item.cardExpiry)
                        BrowserStoredCreditCard(
                            id = item.id,
                            cardholderName = item.cardHolder,
                            number = number,
                            expirationMonth = month,
                            expirationYear = year,
                        )
                    }
                }

                override fun saveLogin(login: BrowserStoredLogin) {
                    TahoBrowserStateStore.upsertAutofillLogin(
                        id = login.id,
                        domain = login.domain,
                        username = login.username,
                        pass = login.password,
                    )
                }

                override fun markLoginUsed(id: String) {
                    TahoBrowserStateStore.markSavedPasswordUsed(id)
                }

                override fun saveAddress(address: BrowserStoredAddress) {
                    TahoBrowserStateStore.upsertSavedAddress(
                        id = address.id,
                        label = address.label,
                        fullName = address.fullName,
                        street = address.street,
                        city = address.city,
                        state = address.state,
                        zipCode = address.postalCode,
                        country = address.country,
                        phone = address.phone,
                        email = address.email,
                    )
                }

                override fun saveCreditCard(card: BrowserStoredCreditCard) {
                    val expiry = listOf(card.expirationMonth, card.expirationYear)
                        .filter(String::isNotBlank)
                        .joinToString("/")
                    TahoBrowserStateStore.upsertSavedPayment(
                        id = card.id,
                        cardHolder = card.cardholderName,
                        cardNumber = card.number,
                        cardExpiry = expiry,
                    )
                }
            },
        )
        extensionManager = BrowserExtensionManager(
            runtime = GeckoRuntimeHolder.get(this),
            onInventory = { extensions ->
                runOnUiThread {
                    TahoBrowserStateStore.extensions = extensions
                }
            },
            onPermissionRequest = { request, decision ->
                runOnUiThread {
                    if (pendingExtensionPermissionRequest != null) {
                        decision(false)
                    } else {
                        pendingExtensionPermissionRequest = request
                        pendingExtensionPermissionDecision = decision
                    }
                }
            },
        )
        extensionManager.refresh()
        webNotificationManager = BrowserWebNotificationManager(
            context = this,
            runtime = GeckoRuntimeHolder.get(this),
            notificationsEnabled = {
                TahoBrowserStateStore.settings.notificationsEnabled
            },
            onRecorded = { origin, title, text ->
                runOnUiThread {
                    TahoBrowserStateStore.addWebsiteNotification(
                        origin = origin,
                        title = title.ifBlank { origin },
                        message = text,
                    )
                }
            },
        )
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
        updateChecker = BrowserUpdateChecker(this)
        backupManager = BrowserBackupManager(this)
        extensionMarketplace = BrowserExtensionMarketplace()
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
            val browserSettings = TahoBrowserStateStore.settings
            var snapshot by remember { mutableStateOf(controller.snapshot()) }
            var captureSnapshot by remember {
                mutableStateOf(captureRuntime.snapshot())
            }

            androidx.compose.runtime.LaunchedEffect(browserSettings) {
                controller.applyRuntimePreferences(
                    browserSettings.toRuntimePreferences(),
                )
            }

            DisposableEffect(controller, captureRuntime) {
                controller.setListener { next ->
                    snapshot = next
                    syncCaptureSessions(next)
                    syncBrowserHistory(next)
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

            pendingExtensionPermissionRequest?.let { request ->
                val details = buildList {
                    if (request.permissions.isNotEmpty()) {
                        add("Permissions: " + request.permissions.joinToString(", "))
                    }
                    if (request.origins.isNotEmpty()) {
                        add("Sites: " + request.origins.joinToString(", "))
                    }
                    if (request.dataCollectionPermissions.isNotEmpty()) {
                        add(
                            "Data collection: " +
                                request.dataCollectionPermissions.joinToString(", "),
                        )
                    }
                    if (isEmpty()) add("This extension requests no additional permissions.")
                }.joinToString("\n\n")

                AlertDialog(
                    onDismissRequest = {
                        pendingExtensionPermissionDecision?.invoke(false)
                        pendingExtensionPermissionDecision = null
                        pendingExtensionPermissionRequest = null
                    },
                    title = {
                        Text(
                            when (request.kind) {
                                BrowserExtensionPromptKind.INSTALL ->
                                    "Install ${request.extensionName}?"
                                BrowserExtensionPromptKind.UPDATE ->
                                    "Allow extension update?"
                                BrowserExtensionPromptKind.OPTIONAL_PERMISSION ->
                                    "Allow extension permission?"
                            },
                        )
                    },
                    text = { Text(details) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingExtensionPermissionDecision?.invoke(true)
                                pendingExtensionPermissionDecision = null
                                pendingExtensionPermissionRequest = null
                            },
                        ) {
                            Text("Allow")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                pendingExtensionPermissionDecision?.invoke(false)
                                pendingExtensionPermissionDecision = null
                                pendingExtensionPermissionRequest = null
                            },
                        ) {
                            Text("Deny")
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

            val selectedWebAppManifest = snapshot.tabs
                .firstOrNull { it.id == snapshot.selectedTabId }
                ?.webAppManifest

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
                    isPictureInPicture = inPictureInPicture,
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
                    webAppManifest = selectedWebAppManifest?.let { manifest ->
                        WebAppManifestUi(
                            name = manifest.name,
                            shortName = manifest.shortName,
                            startUrl = manifest.startUrl,
                            scope = manifest.scope,
                            display = manifest.display,
                            themeColor = manifest.themeColor,
                            backgroundColor = manifest.backgroundColor,
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
                    autofillPrompt = snapshot.autofillPrompt?.let { prompt ->
                        BrowserAutofillPromptUiState(
                            id = prompt.id,
                            origin = prompt.origin,
                            kind = when (prompt.kind) {
                                BrowserAutofillPromptKind.LOGIN_SAVE ->
                                    BrowserAutofillPromptKindUi.LOGIN_SAVE
                                BrowserAutofillPromptKind.LOGIN_SELECT ->
                                    BrowserAutofillPromptKindUi.LOGIN_SELECT
                                BrowserAutofillPromptKind.ADDRESS_SAVE ->
                                    BrowserAutofillPromptKindUi.ADDRESS_SAVE
                                BrowserAutofillPromptKind.ADDRESS_SELECT ->
                                    BrowserAutofillPromptKindUi.ADDRESS_SELECT
                                BrowserAutofillPromptKind.CREDIT_CARD_SAVE ->
                                    BrowserAutofillPromptKindUi.CREDIT_CARD_SAVE
                                BrowserAutofillPromptKind.CREDIT_CARD_SELECT ->
                                    BrowserAutofillPromptKindUi.CREDIT_CARD_SELECT
                            },
                            options = prompt.options.map { option ->
                                BrowserAutofillPromptOptionUi(
                                    index = option.index,
                                    title = option.title,
                                    subtitle = option.subtitle,
                                )
                            },
                        )
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
                onNewTab = { controller.newTab(privateMode = false) },
                onNewPrivateTab = { controller.newTab(privateMode = true) },
                onSelectTab = controller::selectTab,
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
                onSitePermissionDecision = { requestId, allow ->
                    val prompt = snapshot.sitePermission
                    val isNotification =
                        prompt?.id == requestId &&
                            prompt.kind == BrowserSitePermissionKind.NOTIFICATIONS
                    if (!allow || !isNotification) {
                        controller.resolveSitePermission(requestId, allow)
                    } else if (
                        android.os.Build.VERSION.SDK_INT <
                        android.os.Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        controller.resolveSitePermission(requestId, true)
                    } else {
                        pendingNotificationSitePermissionId = requestId
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    }
                },
                onAutofillPromptDecision = controller::resolveAutofillPrompt,
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
                onAuthenticateSensitive = ::authenticateSensitive,
                onCheckPasswordBreach = passwordBreachChecker::check,
                onDownloadPauseResume = downloadManager::pauseResume,
                onDownloadCancel = downloadManager::cancel,
                onDownloadRetry = { item ->
                    TahoBrowserStateStore.removeDownload(item.id)
                    controller.load(uri = item.url)
                },
                onDownloadOpen = { item ->
                    if (!downloadManager.open(item)) {
                        transferNotice = "No installed app can open this downloaded file."
                    }
                },
                onDownloadDelete = { item ->
                    if (downloadManager.delete(item)) {
                        TahoBrowserStateStore.removeDownload(item.id)
                    } else {
                        transferNotice = "Downloaded file could not be deleted."
                    }
                },
                onSaveOfflinePage = { title, url ->
                    controller.saveCurrentPageAsPdf { stream, error ->
                        if (stream == null) {
                            transferNotice = error ?: "Offline snapshot could not be created."
                        } else {
                            offlinePageManager.savePdf(title, url, stream) { page, saveError ->
                                runOnUiThread {
                                    if (page != null) {
                                        TahoBrowserStateStore.upsertOfflinePage(page)
                                        transferNotice = "Offline PDF snapshot saved."
                                    } else {
                                        transferNotice =
                                            saveError ?: "Offline snapshot could not be written."
                                    }
                                }
                            }
                        }
                    }
                },
                onOpenOfflinePage = { page ->
                    if (!offlinePageManager.open(page)) {
                        transferNotice = "Offline snapshot file is unavailable."
                    }
                },
                onDeleteOfflinePage = { page ->
                    if (offlinePageManager.delete(page)) {
                        TahoBrowserStateStore.removeOfflinePage(page.id)
                    } else {
                        transferNotice = "Offline snapshot could not be deleted."
                    }
                },
                onAddToHomeScreen = ::pinPageShortcut,
                onInstallWebApp = ::installWebApp,
                onTranslatePage = { targetLanguage, callback ->
                    controller.translateCurrentPage(targetLanguage) { success, source, error ->
                        runOnUiThread { callback(success, source, error) }
                    }
                },
                onRestorePageTranslation = { callback ->
                    controller.restoreOriginalPageTranslation { success, error ->
                        runOnUiThread { callback(success, error) }
                    }
                },
                onExportFullBackup = { uri, passphrase, callback ->
                    backupManager.exportTo(uri, passphrase.toCharArray()) { result ->
                        callback(result.success, result.message)
                    }
                },
                onRestoreFullBackup = { uri, passphrase, callback ->
                    backupManager.restoreFrom(uri, passphrase.toCharArray()) { result ->
                        if (result.success) {
                            controller.applyRuntimePreferences(
                                TahoBrowserStateStore.settings.toRuntimePreferences(),
                            )
                            extensionManager.refresh()
                        }
                        val summary = result.restoreSummary
                        val message = if (result.success && summary != null) {
                            result.message +
                                " Bookmarks: " + summary.bookmarks +
                                ", history: " + summary.historyEntries +
                                ", passwords: " + summary.passwords +
                                ", downloads: " + summary.downloads +
                                ", offline pages: " + summary.offlinePages + "."
                        } else {
                            result.message
                        }
                        callback(result.success, message)
                    }
                },
                onCheckForUpdates = { callback ->
                    updateChecker.check { result ->
                        callback(
                            BrowserUpdateStatusUi(
                                latestVersion = result.latestVersion,
                                releaseUrl = result.releaseUrl,
                                updateAvailable = result.updateAvailable,
                                error = result.error,
                            ),
                        )
                    }
                },
                onRefreshExtensions = {
                    extensionManager.refresh { success ->
                        if (!success) {
                            runOnUiThread {
                                transferNotice = "Gecko extension inventory could not be refreshed."
                            }
                        }
                    }
                },
                onInstallExtension = { uri ->
                    extensionManager.install(uri) { success, reason ->
                        runOnUiThread {
                            transferNotice = if (success) {
                                "Extension installed by Gecko."
                            } else {
                                reason ?: "Extension installation failed."
                            }
                        }
                    }
                },
                onSetExtensionEnabled = { id, enabled ->
                    extensionManager.setEnabled(id, enabled) { success ->
                        if (!success) runOnUiThread {
                            transferNotice = "Extension state could not be changed."
                        }
                    }
                },
                onSetExtensionPrivate = { id, allowed ->
                    extensionManager.setAllowedInPrivate(id, allowed) { success ->
                        if (!success) runOnUiThread {
                            transferNotice = "Private-browsing access could not be changed."
                        }
                    }
                },
                onUpdateExtension = { id ->
                    extensionManager.update(id) { success, changed ->
                        runOnUiThread {
                            transferNotice = when {
                                !success -> "Extension update check failed."
                                changed -> "Extension updated by Gecko."
                                else -> "No extension update is available."
                            }
                        }
                    }
                },
                onUninstallExtension = { id ->
                    extensionManager.uninstall(id) { success ->
                        runOnUiThread {
                            transferNotice = if (success) {
                                "Extension uninstalled."
                            } else {
                                "Extension could not be uninstalled."
                            }
                        }
                    }
                },
                onSwitchProfile = { profileId ->
                    downloadManager.cancelAllActive()
                    val target = TahoBrowserStateStore.switchProfile(profileId)
                    if (target != null) {
                        controller.switchProfile(target.id, target.isGuest)
                        committedHistoryLocationByTab.clear()
                        controller.snapshot().tabs.forEach { tab ->
                            if (!tab.isPrivate) {
                                committedHistoryLocationByTab[tab.id] = tab.location
                            }
                        }
                        transferNotice = if (target.isGuest) {
                            "Guest session started. Guest browsing data will not be persisted."
                        } else {
                            "Switched to " + target.name + " profile."
                        }
                    }
                },
                onCreateLocalProfile = { name ->
                    val created = TahoBrowserStateStore.addLocalProfile(name)
                    transferNotice = "Created local profile: " + created.name + "."
                },
                onSearchExtensionMarketplace = { query, callback ->
                    extensionMarketplace.search(query, callback)
                },
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPictureInPictureForActiveMedia()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    private fun enterPictureInPictureForActiveMedia() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!TahoBrowserStateStore.settings.pictureInPictureEnabled) return
        if (!::controller.isInitialized || !controller.snapshot().isFullScreen) return
        if (isInPictureInPictureMode) return

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        runCatching { enterPictureInPictureMode(params) }
    }

    override fun onResume() {
        super.onResume()
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

    private fun authenticateSensitive(
        reason: String,
        callback: (Boolean) -> Unit,
    ) {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            transferNotice = "Biometric or device-credential authentication is unavailable."
            callback(false)
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    callback(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    callback(false)
                }

                override fun onAuthenticationFailed() {
                    // Keep the system prompt open so the user may retry.
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Taho Browser")
            .setSubtitle(reason)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun BrowserSettingsState.toRuntimePreferences(): BrowserRuntimePreferences {
        val secureDnsUri = when (secureDns) {
            TahoSecureDns.CLOUDFLARE ->
                "https://mozilla.cloudflare-dns.com/dns-query"
            TahoSecureDns.QUAD9 ->
                "https://dns.quad9.net/dns-query"
            TahoSecureDns.GOOGLE ->
                "https://dns.google/dns-query"
            TahoSecureDns.CUSTOM ->
                customDnsProvider.trim().takeIf(String::isNotBlank)
            TahoSecureDns.OFF -> null
        }

        return BrowserRuntimePreferences(
            trackingLevel = when (trackingProtectionLevel) {
                TahoTrackingProtectionLevel.STANDARD -> BrowserTrackingLevel.STANDARD
                TahoTrackingProtectionLevel.STRICT -> BrowserTrackingLevel.STRICT
                TahoTrackingProtectionLevel.CUSTOM -> BrowserTrackingLevel.CUSTOM
            },
            blockTrackers = blockTrackers,
            blockFingerprinting = fingerprintingProtection,
            blockCryptomining = cryptominingProtection,
            blockSocialTrackers = socialTrackerProtection,
            cookiePolicy = when (cookiePolicy) {
                TahoCookiePolicy.BLOCK_THIRD_PARTY ->
                    BrowserCookiePolicy.BLOCK_THIRD_PARTY
                TahoCookiePolicy.BLOCK_ALL ->
                    BrowserCookiePolicy.BLOCK_ALL
                TahoCookiePolicy.ALLOW_ALL ->
                    BrowserCookiePolicy.ALLOW_ALL
            },
            safeBrowsingEnabled = safeBrowsingEnabled,
            phishingProtectionEnabled = phishingProtection,
            httpsOnlyEnabled = httpsOnlyMode,
            globalPrivacyControlEnabled = globalPrivacyControl,
            secureDnsMode =
                if (secureDns == TahoSecureDns.OFF) {
                    BrowserSecureDnsMode.OFF
                } else {
                    BrowserSecureDnsMode.FIRST
                },
            secureDnsUri = secureDnsUri,
            javascriptEnabled = javascriptEnabled,
            forceUserScalable = forceZoomEnabled,
            fontScale = fontScalingPercent.coerceIn(75, 200) / 100f,
            forceAccessibilityTree = screenReaderOptimized,
            webColorScheme = when (themeMode) {
                TahoThemeMode.SYSTEM -> BrowserWebColorScheme.SYSTEM
                TahoThemeMode.DARK -> BrowserWebColorScheme.DARK
                TahoThemeMode.LIGHT -> BrowserWebColorScheme.LIGHT
            },
            suspendBackgroundMedia = !backgroundAudioEnabled,
            speculativePreconnectEnabled = preloadingEnabled,
        )
    }

    private fun parseCardExpiry(raw: String): Pair<String, String> {
        val parts = raw
            .trim()
            .split('/', '-', ' ')
            .filter(String::isNotBlank)
        val month = parts.getOrNull(0)?.filter(Char::isDigit).orEmpty()
        val yearRaw = parts.getOrNull(1)?.filter(Char::isDigit).orEmpty()
        val year = when (yearRaw.length) {
            2 -> "20$yearRaw"
            else -> yearRaw
        }
        return month to year
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
        TahoBrowserStateStore.persistNow()
        controller.persistNow()
        super.onStop()
    }

    override fun onDestroy() {
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
        }
        captureRuntime.close()
        passwordBreachChecker.close()
        if (::downloadManager.isInitialized) {
            downloadManager.close()
        }
        if (::offlinePageManager.isInitialized) {
            offlinePageManager.close()
        }
        pendingExtensionPermissionDecision?.invoke(false)
        pendingExtensionPermissionDecision = null
        pendingExtensionPermissionRequest = null
        if (::extensionManager.isInitialized) {
            extensionManager.close()
        }
        if (::webNotificationManager.isInitialized) {
            webNotificationManager.close()
        }
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

    private fun installWebApp(manifest: WebAppManifestUi) {
        val startUri = Uri.parse(manifest.startUrl)
        if (startUri.scheme != "https" && startUri.scheme != "http") {
            transferNotice = "This web app has an unsupported start URL."
            return
        }

        val existing = TahoBrowserStateStore.installedPwas
            .firstOrNull { it.url == manifest.startUrl }
        val pwa = existing ?: app.taho.browser.shell.InstalledPwaUi(
            id = java.util.UUID.randomUUID().toString(),
            name = manifest.name,
            url = manifest.startUrl,
            iconGlyph = (manifest.shortName ?: manifest.name)
                .take(2)
                .uppercase()
                .ifBlank { "PW" },
        )

        if (existing == null) {
            TahoBrowserStateStore.installedPwas =
                TahoBrowserStateStore.installedPwas + pwa
            TahoBrowserStateStore.persistNow()
        }

        val launchIntent = Intent(this, TahoPwaActivity::class.java)
            .putExtra(TahoPwaActivity.EXTRA_PWA_ID, pwa.id)
            .putExtra(TahoPwaActivity.EXTRA_START_URL, manifest.startUrl)
            .putExtra(TahoPwaActivity.EXTRA_NAME, manifest.name)
            .putExtra(TahoPwaActivity.EXTRA_SCOPE, manifest.scope)
            .putExtra(TahoPwaActivity.EXTRA_DISPLAY, manifest.display)
            .putExtra(TahoPwaActivity.EXTRA_THEME_COLOR, manifest.themeColor)
            .setAction(Intent.ACTION_VIEW)
            .setData(startUri)

        val manager = getSystemService(ShortcutManager::class.java)
        if (manager != null) {
            val shortcut = ShortcutInfo.Builder(this, "pwa-" + pwa.id)
                .setShortLabel((manifest.shortName ?: manifest.name).take(40))
                .setLongLabel(manifest.name.take(80))
                .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_view))
                .setIntent(launchIntent)
                .build()

            runCatching { manager.addDynamicShortcuts(listOf(shortcut)) }
            if (manager.isRequestPinShortcutSupported) {
                runCatching { manager.requestPinShortcut(shortcut, null) }
            }
        }

        transferNotice = "Web app installed in Taho."
    }

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

            BrowserSitePermissionKind.NOTIFICATIONS ->
                "Allow website notifications?" to
                    "This site wants to create Android notifications through Taho Browser."

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
