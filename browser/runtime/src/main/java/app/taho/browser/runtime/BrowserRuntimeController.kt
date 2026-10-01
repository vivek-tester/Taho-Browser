package app.taho.browser.runtime

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.PageExtractionController
import org.mozilla.geckoview.StorageController
import java.util.UUID

enum class BrowserTrackingProtectionLevel {
    STANDARD,
    STRICT,
    CUSTOM,
}

data class BrowserPrivacyRuntimeSettings(
    val trackingProtectionLevel: BrowserTrackingProtectionLevel = BrowserTrackingProtectionLevel.STRICT,
    val blockTrackers: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val fingerprintingProtection: Boolean = true,
    val cryptominingProtection: Boolean = true,
    val httpsOnlyMode: Boolean = true,
    val safeBrowsingEnabled: Boolean = true,
    val popupBlockerEnabled: Boolean = true,
    val redirectBlockingEnabled: Boolean = true,
    val doNotTrack: Boolean = true,
    val globalPrivacyControl: Boolean = true,
    val perSiteTrackingExceptions: Set<String> = emptySet(),
)

enum class BrowserSitePermissionKind {
    LOCATION,
    PERSISTENT_STORAGE,
    CAMERA,
    MICROPHONE,
    CAMERA_AND_MICROPHONE,
}

data class BrowserSitePermissionPrompt(
    val id: String,
    val tabId: String,
    val origin: String,
    val kind: BrowserSitePermissionKind,
    val isPrivate: Boolean,
)

data class BrowserAndroidPermissionRequest(
    val id: String,
    val tabId: String,
    val permissions: List<String>,
)

data class BrowserExternalNavigationRequest(
    val id: String,
    val tabId: String,
    val uri: String,
    val scheme: String,
)

data class BrowserFindResult(
    val currentIndex: Int,
    val totalMatches: Int,
    val found: Boolean,
)

data class BrowserReaderContent(
    val text: String,
    val wordCount: Int,
    val language: String,
    val isReaderable: Boolean,
    val isGated: Boolean,
)

data class BrowserSecuritySnapshot(
    val isSecure: Boolean,
    val isException: Boolean,
    val host: String,
    val certificateSubject: String?,
    val certificateIssuer: String?,
    val activeMixedContentLoaded: Boolean,
    val passiveMixedContentLoaded: Boolean,
)

data class BrowserTabSnapshot(
    val id: String,
    val title: String?,
    val location: String?,
    val isLoading: Boolean,
    val loadFailed: Boolean,
    val crashed: Boolean,
    val isPrivate: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
)

data class BrowserSnapshot(
    val selectedTabId: String,
    val location: String?,
    val tabCount: Int,
    val isLoading: Boolean,
    val loadFailed: Boolean,
    val crashed: Boolean,
    val isPrivate: Boolean,
    val isFullScreen: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val security: BrowserSecuritySnapshot?,
    val sitePermission: BrowserSitePermissionPrompt?,
    val androidPermissionRequest: BrowserAndroidPermissionRequest?,
    val externalNavigationRequest: BrowserExternalNavigationRequest?,
    val notice: String?,
    val tabs: List<BrowserTabSnapshot>,
)

class BrowserRuntimeController(context: Context) {
    private data class RuntimeTab(
        val id: String,
        var session: GeckoSession,
        val isPrivate: Boolean,
        var title: String? = null,
        var location: String? = null,
        var isLoading: Boolean = false,
        var loadFailed: Boolean = false,
        var crashed: Boolean = false,
        var isFullScreen: Boolean = false,
        var canGoBack: Boolean = false,
        var canGoForward: Boolean = false,
        var security: BrowserSecuritySnapshot? = null,
        var sessionState: GeckoSession.SessionState? = null,
        var lastAccessedAtEpochMs: Long = System.currentTimeMillis(),
        val redirectTargets: MutableSet<String> = linkedSetOf(),
    )

    private sealed interface PendingSitePermission {
        val prompt: BrowserSitePermissionPrompt

        data class Content(
            override val prompt: BrowserSitePermissionPrompt,
            val result: GeckoResult<Int>,
        ) : PendingSitePermission

        data class Media(
            override val prompt: BrowserSitePermissionPrompt,
            val callback: GeckoSession.PermissionDelegate.MediaCallback,
            val video: GeckoSession.PermissionDelegate.MediaSource?,
            val audio: GeckoSession.PermissionDelegate.MediaSource?,
        ) : PendingSitePermission
    }

    private data class PendingAndroidPermission(
        val request: BrowserAndroidPermissionRequest,
        val callback: GeckoSession.PermissionDelegate.Callback,
        var launched: Boolean = false,
    )

    private data class PendingExternalNavigation(
        val request: BrowserExternalNavigationRequest,
        var launched: Boolean = false,
    )

    private val runtime = GeckoRuntimeHolder.get(context)
    private val sessionStore = BrowserSessionStore(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferenceThread = HandlerThread("taho-gecko-preferences").apply { start() }
    private val preferenceHandler = Handler(preferenceThread.looper)
    private val tabs = mutableListOf<RuntimeTab>()
    private lateinit var selectedTabId: String
    private var listener: ((BrowserSnapshot) -> Unit)? = null

    private var pendingSitePermission: PendingSitePermission? = null
    private var pendingAndroidPermission: PendingAndroidPermission? = null
    private var pendingExternalNavigation: PendingExternalNavigation? = null
    private var notice: String? = null
    private var privacySettings = BrowserPrivacyRuntimeSettings()

    private val persistRunnable = Runnable { persistNow() }

    init {
        val restored = sessionStore.load()
        if (restored.tabs.isNotEmpty()) {
            restored.tabs.forEach { saved ->
                val state = saved.serializedSessionState
                    ?.let { serialized ->
                        GeckoSession.SessionState.fromString(serialized)
                    }

                createRuntimeTab(
                    id = saved.id,
                    privateMode = false,
                    initialLocation = saved.location,
                    initialTitle = saved.title,
                    restoredState = state,
                    restoredLastAccessedAtEpochMs = saved.lastAccessedAtEpochMs,
                )
            }

            selectedTabId = restored.selectedTabId
                ?.takeIf { id -> tabs.any { it.id == id } }
                ?: tabs.first().id
            selectTab(selectedTabId, persist = false)
        } else {
            selectedTabId = createTab(privateMode = false)
            selectTab(selectedTabId, persist = false)
        }
    }

    fun snapshot(): BrowserSnapshot {
        val selected = requireSelected()
        return BrowserSnapshot(
            selectedTabId = selected.id,
            location = selected.location,
            tabCount = tabs.size,
            isLoading = selected.isLoading,
            loadFailed = selected.loadFailed,
            crashed = selected.crashed,
            isPrivate = selected.isPrivate,
            isFullScreen = selected.isFullScreen,
            canGoBack = selected.canGoBack,
            canGoForward = selected.canGoForward,
            security = selected.security,
            sitePermission = pendingSitePermission?.prompt,
            androidPermissionRequest = pendingAndroidPermission?.request,
            externalNavigationRequest = pendingExternalNavigation?.request,
            notice = notice,
            tabs = tabs.map { tab ->
                BrowserTabSnapshot(
                    id = tab.id,
                    title = tab.title,
                    location = tab.location,
                    isLoading = tab.isLoading,
                    loadFailed = tab.loadFailed,
                    crashed = tab.crashed,
                    isPrivate = tab.isPrivate,
                    canGoBack = tab.canGoBack,
                    canGoForward = tab.canGoForward,
                )
            },
        )
    }

    fun setListener(listener: ((BrowserSnapshot) -> Unit)?) {
        this.listener = listener
        listener?.invoke(snapshot())
    }

    fun createTab(privateMode: Boolean): String =
        createRuntimeTab(
            id = UUID.randomUUID().toString(),
            privateMode = privateMode,
            initialLocation = null,
            initialTitle = null,
            restoredState = null,
            restoredLastAccessedAtEpochMs = null,
        ).id

    fun newTab(privateMode: Boolean): String {
        val id = createTab(privateMode)
        selectTab(id)
        return id
    }

    /**
     * Replace the current normal browsing session with a single blank tab.
     * Used only for explicit startup policy; private tabs are never restored.
     */
    fun resetToSingleTab(initialUri: String? = null) {
        val existing = tabs.toList()
        existing.forEach { tab ->
            rejectPermissionsForTab(tab.id)
            clearExternalNavigationForTab(tab.id)
            if (!tab.crashed) {
                runCatching {
                    tab.session.setFocused(false)
                    tab.session.setActive(false)
                    tab.session.close()
                }
            }
        }
        tabs.clear()

        val id = createRuntimeTab(
            id = UUID.randomUUID().toString(),
            privateMode = false,
            initialLocation = null,
            initialTitle = null,
            restoredState = null,
            restoredLastAccessedAtEpochMs = null,
        ).id
        selectedTabId = id
        selectTab(id, persist = false)
        initialUri?.takeIf { it.isNotBlank() && it != "about:blank" }?.let { load(id, it) }
        persistNow()
    }

    /**
     * Close stale non-selected tabs while preserving pinned tabs. Returns the
     * number actually closed so the shell can report truthful cleanup.
     */
    fun closeInactiveTabs(
        olderThanEpochMs: Long,
        pinnedTabIds: Set<String> = emptySet(),
    ): Int {
        val candidates = BrowserTabRetentionPolicy.staleTabIds(
            tabs = tabs.map { tab ->
                BrowserTabRetentionCandidate(
                    id = tab.id,
                    lastAccessedAtEpochMs = tab.lastAccessedAtEpochMs,
                    selected = tab.id == selectedTabId,
                    pinned = tab.id in pinnedTabIds,
                )
            },
            olderThanEpochMs = olderThanEpochMs,
        )

        candidates.forEach(::closeTab)
        return candidates.size
    }

    fun closeTab(tabId: String) {
        rejectPermissionsForTab(tabId)
        clearExternalNavigationForTab(tabId)

        if (tabs.size == 1) {
            val only = tabs.single()
            if (only.crashed) {
                replaceCrashedSession(only, restorePreviousState = false)
            }

            only.title = null
            only.location = null
            only.loadFailed = false
            only.crashed = false
            only.isLoading = false
            only.canGoBack = false
            only.canGoForward = false
            only.sessionState = null
            only.session.loadUri("about:blank")
            persistSoon()
            notifyChanged()
            return
        }

        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val closing = tabs.removeAt(index)
        if (!closing.crashed) {
            closing.session.setFocused(false)
            closing.session.setActive(false)
            closing.session.close()
        }

        if (selectedTabId == tabId) {
            selectedTabId = tabs[index.coerceAtMost(tabs.lastIndex)].id
            selectTab(selectedTabId)
        } else {
            persistSoon()
            notifyChanged()
        }
    }

    fun selectTab(tabId: String) {
        selectTab(tabId, persist = true)
    }

    fun load(tabId: String = selectedTabId, uri: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return

        if (tab.crashed) {
            replaceCrashedSession(tab, restorePreviousState = false)
        }

        notice = null
        tab.loadFailed = false
        tab.crashed = false
        tab.isLoading = true
        notifyChangedIfReady()
        tab.session.loadUri(uri)
    }

    fun reload() {
        val tab = requireSelected()
        if (tab.crashed) {
            recoverCrashedTab(tab.id)
            return
        }

        tab.loadFailed = false
        tab.isLoading = true
        notifyChanged()
        tab.session.reload()
    }

    fun recoverCrashedTab(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        if (!tab.crashed) {
            tab.session.reload()
            return
        }

        replaceCrashedSession(tab, restorePreviousState = true)
        notifyChanged()
    }

    fun goBack() {
        val tab = requireSelected()
        if (!tab.crashed && tab.canGoBack) {
            tab.session.goBack()
        }
    }

    fun goForward() {
        val tab = requireSelected()
        if (!tab.crashed && tab.canGoForward) {
            tab.session.goForward()
        }
    }

    fun exitFullScreen() {
        val tab = requireSelected()
        if (tab.crashed || !tab.isFullScreen) return
        tab.session.exitFullScreen()
    }

    fun findInPage(
        query: String,
        backwards: Boolean = false,
        onResult: (BrowserFindResult) -> Unit,
    ) {
        val tab = requireSelected()
        if (tab.crashed || query.isBlank()) {
            tab.session.finder.clear()
            onResult(BrowserFindResult(currentIndex = 0, totalMatches = 0, found = false))
            return
        }

        val finder = tab.session.finder
        finder.setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL)
        val direction = if (backwards) {
            GeckoSession.FINDER_FIND_BACKWARDS
        } else {
            GeckoSession.FINDER_FIND_FORWARD
        }
        finder.find(query, direction).accept(
            { result ->
                onResult(
                    BrowserFindResult(
                        currentIndex = (result?.current ?: 0).coerceAtLeast(0),
                        totalMatches = (result?.total ?: 0).coerceAtLeast(0),
                        found = result?.found == true,
                    ),
                )
            },
            {
                onResult(BrowserFindResult(currentIndex = 0, totalMatches = 0, found = false))
            },
        )
    }

    fun clearFindInPage() {
        requireSelected().session.finder.clear()
    }

    fun extractReaderContent(onResult: (BrowserReaderContent?) -> Unit) {
        val tab = requireSelected()
        if (tab.crashed || tab.location.isNullOrBlank()) {
            onResult(null)
            return
        }

        val extractor = tab.session.sessionPageExtractor
        extractor.getPageMetadata().accept(
            { metadata ->
                extractor.getPageContent(
                    PageExtractionController.ContentParams(
                        true, // remove boilerplate through reader-mode extraction
                        true, // plain prose, not markdown
                    ),
                ).accept(
                    { content ->
                        val text = content?.trim().orEmpty()
                        if (text.isBlank()) {
                            onResult(null)
                        } else {
                            onResult(
                                BrowserReaderContent(
                                    text = text,
                                    wordCount = metadata?.wordCount ?: 0,
                                    language = metadata?.language.orEmpty(),
                                    isReaderable = metadata?.isReaderable == true,
                                    isGated = metadata?.isGated == true,
                                ),
                            )
                        }
                    },
                    { onResult(null) },
                )
            },
            { onResult(null) },
        )
    }

    fun setDesktopMode(enabled: Boolean) {
        val tab = requireSelected()
        if (tab.crashed) return
        val settings = tab.session.settings
        settings.setUserAgentMode(
            if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE,
        )
        settings.setViewportMode(
            if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
        )
        tab.session.reload()
    }

    /**
     * Apply the browser's Secure DNS choice directly to Gecko's Trusted
     * Recursive Resolver (DNS-over-HTTPS) runtime settings.
     *
     * A null URI means the user explicitly selected system DNS. A non-null
     * HTTPS URI is treated as an explicit provider choice and therefore uses
     * TRR-only mode so Gecko does not silently fall back to platform DNS.
     */
    fun setSecureDns(resolverUri: String?) {
        val runtimeSettings = runtime.settings
        runtimeSettings.setDohAutoselectEnabled(false)

        if (resolverUri.isNullOrBlank()) {
            runtimeSettings.setTrustedRecursiveResolverMode(
                GeckoRuntimeSettings.TRR_MODE_DISABLED,
            )
            return
        }

        require(resolverUri.startsWith("https://")) {
            "Secure DNS resolver must use HTTPS."
        }

        runtimeSettings.setTrustedRecursiveResolverUri(resolverUri)
        runtimeSettings.setDefaultRecursiveResolverUri(resolverUri)
        runtimeSettings.setTrustedRecursiveResolverMode(
            GeckoRuntimeSettings.TRR_MODE_ONLY,
        )
    }

    fun applyPrivacySettings(
        settings: BrowserPrivacyRuntimeSettings,
        onPreferencesApplied: (() -> Unit)? = null,
    ) {
        privacySettings = settings

        val runtimeSettings = runtime.settings
        val contentBlocking = runtimeSettings.contentBlocking

        val antiTracking = when {
            !settings.blockTrackers &&
                !settings.fingerprintingProtection &&
                !settings.cryptominingProtection ->
                ContentBlocking.AntiTracking.NONE

            settings.trackingProtectionLevel == BrowserTrackingProtectionLevel.STRICT ->
                ContentBlocking.AntiTracking.STRICT

            settings.trackingProtectionLevel == BrowserTrackingProtectionLevel.STANDARD ->
                ContentBlocking.AntiTracking.DEFAULT or
                    (if (settings.fingerprintingProtection) ContentBlocking.AntiTracking.FINGERPRINTING else 0) or
                    (if (settings.cryptominingProtection) ContentBlocking.AntiTracking.CRYPTOMINING else 0)

            else -> {
                (if (settings.blockTrackers) ContentBlocking.AntiTracking.DEFAULT else 0) or
                    (if (settings.fingerprintingProtection) ContentBlocking.AntiTracking.FINGERPRINTING else 0) or
                    (if (settings.cryptominingProtection) ContentBlocking.AntiTracking.CRYPTOMINING else 0)
            }
        }

        contentBlocking.setAntiTracking(antiTracking)
        contentBlocking.setEnhancedTrackingProtectionCategory(
            when (settings.trackingProtectionLevel) {
                BrowserTrackingProtectionLevel.STANDARD -> ContentBlocking.EtpCategory.STANDARD
                BrowserTrackingProtectionLevel.STRICT -> ContentBlocking.EtpCategory.STRICT
                BrowserTrackingProtectionLevel.CUSTOM -> ContentBlocking.EtpCategory.CUSTOM
            },
        )
        contentBlocking.setEnhancedTrackingProtectionLevel(
            when (settings.trackingProtectionLevel) {
                BrowserTrackingProtectionLevel.STRICT -> ContentBlocking.EtpLevel.STRICT
                else -> ContentBlocking.EtpLevel.DEFAULT
            },
        )
        contentBlocking.setCookieBehavior(
            when {
                settings.blockThirdPartyCookies ->
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
                settings.blockTrackers ->
                    ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
                else -> ContentBlocking.CookieBehavior.ACCEPT_ALL
            },
        )
        contentBlocking.setCookieBehaviorPrivateMode(
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS,
        )
        contentBlocking.setSafeBrowsing(
            if (settings.safeBrowsingEnabled) {
                ContentBlocking.SafeBrowsing.DEFAULT
            } else {
                ContentBlocking.SafeBrowsing.NONE
            },
        )

        runtimeSettings.setAllowInsecureConnections(
            if (settings.httpsOnlyMode) {
                GeckoRuntimeSettings.HTTPS_ONLY
            } else {
                GeckoRuntimeSettings.ALLOW_ALL
            },
        )
        runtimeSettings.setFingerprintingProtection(settings.fingerprintingProtection)
        runtimeSettings.setFingerprintingProtectionPrivateBrowsing(
            settings.fingerprintingProtection,
        )
        runtimeSettings.setGlobalPrivacyControl(settings.globalPrivacyControl)

        preferenceHandler.post {
            val dnt = GeckoPreferenceController.setGeckoPref(
                "privacy.donottrackheader.enabled",
                settings.doNotTrack,
                GeckoPreferenceController.PREF_BRANCH_USER,
            )
            dnt.accept(
                {
                    val popup = GeckoPreferenceController.setGeckoPref(
                        "dom.disable_open_during_load",
                        settings.popupBlockerEnabled,
                        GeckoPreferenceController.PREF_BRANCH_USER,
                    )
                    popup.accept(
                        { mainHandler.post { onPreferencesApplied?.invoke() } },
                        { mainHandler.post { onPreferencesApplied?.invoke() } },
                    )
                },
                {
                    val popup = GeckoPreferenceController.setGeckoPref(
                        "dom.disable_open_during_load",
                        settings.popupBlockerEnabled,
                        GeckoPreferenceController.PREF_BRANCH_USER,
                    )
                    popup.accept(
                        { mainHandler.post { onPreferencesApplied?.invoke() } },
                        { mainHandler.post { onPreferencesApplied?.invoke() } },
                    )
                },
            )
        }

        tabs.forEach(::applyTrackingProtectionForTab)
    }

    fun closePrivateTabs(): Int {
        val privateIds = tabs.filter { it.isPrivate }.map { it.id }
        if (privateIds.isEmpty()) return 0

        if (tabs.none { !it.isPrivate }) {
            val normalTabId = createTab(privateMode = false)
            selectTab(normalTabId)
        }

        privateIds.forEach { tabId ->
            if (tabs.any { it.id == tabId }) {
                closeTab(tabId)
            }
        }
        return privateIds.size
    }

    fun printCurrentPage(): Boolean {
        val tab = requireSelected()
        if (tab.crashed || tab.location.isNullOrBlank()) return false
        return runCatching {
            tab.session.printPageContent()
            true
        }.getOrDefault(false)
    }

    fun clearBrowsingStorage(
        clearCache: Boolean,
        clearCookiesAndSiteData: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        var flags = 0L
        if (clearCache) {
            flags = flags or StorageController.ClearFlags.ALL_CACHES
        }
        if (clearCookiesAndSiteData) {
            flags = flags or StorageController.ClearFlags.SITE_DATA
        }
        if (flags == 0L) {
            onComplete(true)
            return
        }

        runtime.storageController.clearData(flags).accept(
            { onComplete(true) },
            { onComplete(false) },
        )
    }

    fun clearSiteDataForHost(host: String, onComplete: (Boolean) -> Unit) {
        val normalizedHost = host.trim().lowercase()
        if (normalizedHost.isBlank() || '/' in normalizedHost || ':' in normalizedHost) {
            onComplete(false)
            return
        }
        runtime.storageController
            .clearDataFromHost(normalizedHost, StorageController.ClearFlags.SITE_DATA)
            .accept(
                { onComplete(true) },
                { onComplete(false) },
            )
    }

    fun bind(tabId: String = selectedTabId, surface: BrowserSurfaceView) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: requireSelected()
        if (!tab.crashed) {
            surface.bind(tab.session)
        } else {
            surface.unbind()
        }
    }

    fun forEachSession(block: (tabId: String, session: GeckoSession) -> Unit) {
        tabs.filterNot { it.crashed }.forEach { block(it.id, it.session) }
    }

    fun locationForTab(tabId: String): String? =
        tabs.firstOrNull { it.id == tabId }?.location

    fun resolveSitePermission(requestId: String, allow: Boolean) {
        val pending = pendingSitePermission
        if (pending?.prompt?.id != requestId) return

        pendingSitePermission = null
        when (pending) {
            is PendingSitePermission.Content -> {
                pending.result.complete(
                    if (allow) {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    } else {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                    },
                )
            }

            is PendingSitePermission.Media -> {
                if (allow) {
                    pending.callback.grant(pending.video, pending.audio)
                } else {
                    pending.callback.reject()
                }
            }
        }
        notifyChanged()
    }

    fun claimAndroidPermissionRequest(requestId: String): Boolean {
        val pending = pendingAndroidPermission
        if (pending?.request?.id != requestId || pending.launched) return false
        pending.launched = true
        return true
    }

    fun claimExternalNavigationRequest(requestId: String): Boolean {
        val pending = pendingExternalNavigation
        if (pending?.request?.id != requestId || pending.launched) return false
        pending.launched = true
        return true
    }

    fun resolveExternalNavigation(requestId: String, opened: Boolean) {
        val pending = pendingExternalNavigation
        if (pending?.request?.id != requestId) return

        pendingExternalNavigation = null
        notice = if (opened) null else "No installed app can open this link."
        notifyChanged()
    }

    fun dismissNotice() {
        if (notice == null) return
        notice = null
        notifyChanged()
    }

    fun resolveAndroidPermissions(requestId: String, granted: Boolean) {
        val pending = pendingAndroidPermission
        if (pending?.request?.id != requestId) return

        pendingAndroidPermission = null
        if (granted) {
            pending.callback.grant()
        } else {
            pending.callback.reject()
        }
        notifyChanged()
    }

    fun persistNow() {
        mainHandler.removeCallbacks(persistRunnable)

        sessionStore.save(
            BrowserPersistencePolicy.stateForDisk(
                tabs = tabs.map { tab ->
                    BrowserPersistableTab(
                        id = tab.id,
                        title = tab.title,
                        location = tab.location,
                        serializedSessionState = tab.sessionState?.toString(),
                        isPrivate = tab.isPrivate,
                        lastAccessedAtEpochMs = tab.lastAccessedAtEpochMs,
                    )
                },
                selectedTabId = selectedTabId,
            ),
        )
    }

    private fun createRuntimeTab(
        id: String,
        privateMode: Boolean,
        initialLocation: String?,
        initialTitle: String?,
        restoredState: GeckoSession.SessionState?,
        restoredLastAccessedAtEpochMs: Long?,
    ): RuntimeTab {
        val session = newSession(privateMode)
        val tab = RuntimeTab(
            id = id,
            session = session,
            isPrivate = privateMode,
            title = initialTitle,
            location = initialLocation,
            sessionState = restoredState,
            lastAccessedAtEpochMs = restoredLastAccessedAtEpochMs ?: System.currentTimeMillis(),
        )

        attachDelegates(tab)
        session.open(runtime)

        if (restoredState != null) {
            session.restoreState(restoredState)
        } else {
            initialLocation
                ?.takeUnless { it == "about:blank" }
                ?.let(session::loadUri)
        }

        session.setActive(false)
        session.setFocused(false)
        tabs += tab

        persistSoon()
        notifyChangedIfReady()
        return tab
    }

    private fun newSession(privateMode: Boolean): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(
                privacySettings.blockTrackers ||
                    privacySettings.fingerprintingProtection ||
                    privacySettings.cryptominingProtection,
            )
            .build()
        return GeckoSession(settings)
    }

    private fun attachDelegates(tab: RuntimeTab) {
        val session = tab.session

        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.location = url
                tab.isLoading = true
                tab.loadFailed = false
                tab.crashed = false
                tab.security = null
                persistSoon()
                notifyChangedIfReady()
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.isLoading = false
                tab.loadFailed = !success
                persistSoon()
                notifyChangedIfReady()
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                val cert = securityInfo.certificate
                tab.security = BrowserSecuritySnapshot(
                    isSecure = securityInfo.isSecure,
                    isException = securityInfo.isException,
                    host = securityInfo.host,
                    certificateSubject = cert?.subjectX500Principal?.name,
                    certificateIssuer = cert?.issuerX500Principal?.name,
                    activeMixedContentLoaded =
                        securityInfo.mixedModeActive ==
                            GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED,
                    passiveMixedContentLoaded =
                        securityInfo.mixedModePassive ==
                            GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED,
                )
                notifyChangedIfReady()
            }

            override fun onSessionStateChange(
                session: GeckoSession,
                sessionState: GeckoSession.SessionState,
            ) {
                tab.sessionState = sessionState
                persistSoon()
            }
        })

        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                applyTrackingProtectionForUri(tab, request.uri)

                if (!request.isRedirect) {
                    tab.redirectTargets.clear()
                } else if (
                    privacySettings.redirectBlockingEnabled &&
                    !tab.redirectTargets.add(request.uri)
                ) {
                    notice = "Redirect loop blocked."
                    notifyChangedIfReady()
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (
                    request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW &&
                    !request.hasUserGesture &&
                    !privacySettings.popupBlockerEnabled
                ) {
                    val newTabId = newTab(privateMode = tab.isPrivate)
                    load(tabId = newTabId, uri = request.uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                val decision = BrowserNavigationPolicy.decide(
                    uri = request.uri,
                    targetNewWindow =
                    request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW,
                    hasUserGesture = request.hasUserGesture,
                    isRedirect = request.isRedirect,
                    externalRequestPending = pendingExternalNavigation != null,
                )

                return when (decision.disposition) {
                    BrowserNavigationDisposition.ALLOW_IN_BROWSER -> null

                    BrowserNavigationDisposition.OPEN_NEW_TAB -> {
                        val newTabId = newTab(privateMode = tab.isPrivate)
                        load(tabId = newTabId, uri = request.uri)
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }

                    BrowserNavigationDisposition.REQUEST_EXTERNAL_APP -> {
                        val external = BrowserExternalNavigationRequest(
                            id = UUID.randomUUID().toString(),
                            tabId = tab.id,
                            uri = request.uri,
                            scheme = decision.scheme.orEmpty(),
                        )
                        pendingExternalNavigation = PendingExternalNavigation(external)
                        notifyChangedIfReady()
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }

                    BrowserNavigationDisposition.DENY ->
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                }
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.location = url
                applyTrackingProtectionForUri(tab, url)
                persistSoon()
                notifyChangedIfReady()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
                notifyChangedIfReady()
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
                notifyChangedIfReady()
            }
        })

        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title
                persistSoon()
                notifyChangedIfReady()
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                tab.isFullScreen = fullScreen
                notifyChangedIfReady()
            }

            override fun onCrash(session: GeckoSession) {
                markCrashed(tab)
            }

            override fun onKill(session: GeckoSession) {
                markCrashed(tab)
            }
        })

        session.setPermissionDelegate(object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                val requested = permissions.orEmpty().toList().distinct()
                if (
                    requested.isEmpty() ||
                    pendingAndroidPermission != null ||
                    requested.any { it !in SUPPORTED_ANDROID_PERMISSIONS }
                ) {
                    callback.reject()
                    return
                }

                val request = BrowserAndroidPermissionRequest(
                    id = UUID.randomUUID().toString(),
                    tabId = tab.id,
                    permissions = requested,
                )
                pendingAndroidPermission = PendingAndroidPermission(
                    request = request,
                    callback = callback,
                )
                notifyChangedIfReady()
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int>? {
                if (
                    perm.value !=
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
                ) {
                    return GeckoResult.fromValue(perm.value)
                }

                val kind = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION ->
                        BrowserSitePermissionKind.LOCATION

                    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE ->
                        BrowserSitePermissionKind.PERSISTENT_STORAGE

                    else -> return GeckoResult.fromValue(
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
                    )
                }

                if (pendingSitePermission != null) {
                    return GeckoResult.fromValue(
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
                    )
                }

                val result = GeckoResult<Int>()
                val prompt = BrowserSitePermissionPrompt(
                    id = UUID.randomUUID().toString(),
                    tabId = tab.id,
                    origin = BrowserNavigationPolicy.displayOrigin(perm.uri),
                    kind = kind,
                    isPrivate = tab.isPrivate,
                )
                pendingSitePermission = PendingSitePermission.Content(
                    prompt = prompt,
                    result = result,
                )
                notifyChangedIfReady()
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                if (pendingSitePermission != null) {
                    callback.reject()
                    return
                }

                val camera = video
                    ?.firstOrNull {
                        it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
                    }
                val microphone = audio
                    ?.firstOrNull {
                        it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
                    }

                if (camera == null && microphone == null) {
                    // Screen/device-audio capture needs a dedicated Android flow.
                    callback.reject()
                    return
                }

                val kind = when {
                    camera != null && microphone != null ->
                        BrowserSitePermissionKind.CAMERA_AND_MICROPHONE
                    camera != null -> BrowserSitePermissionKind.CAMERA
                    else -> BrowserSitePermissionKind.MICROPHONE
                }

                val prompt = BrowserSitePermissionPrompt(
                    id = UUID.randomUUID().toString(),
                    tabId = tab.id,
                    origin = BrowserNavigationPolicy.displayOrigin(uri),
                    kind = kind,
                    isPrivate = tab.isPrivate,
                )
                pendingSitePermission = PendingSitePermission.Media(
                    prompt = prompt,
                    callback = callback,
                    video = camera,
                    audio = microphone,
                )
                notifyChangedIfReady()
            }
        })
    }

    private fun markCrashed(tab: RuntimeTab) {
        rejectPermissionsForTab(tab.id)
        clearExternalNavigationForTab(tab.id)
        tab.crashed = true
        tab.isFullScreen = false
        tab.isLoading = false
        tab.loadFailed = false
        tab.canGoBack = false
        tab.canGoForward = false
        persistSoon()
        notifyChangedIfReady()
    }

    private fun replaceCrashedSession(
        tab: RuntimeTab,
        restorePreviousState: Boolean,
    ) {
        val replacement = newSession(tab.isPrivate)
        tab.session = replacement
        tab.crashed = false
        tab.loadFailed = false
        tab.isLoading = true
        tab.canGoBack = false
        tab.canGoForward = false

        attachDelegates(tab)
        replacement.open(runtime)

        val restored = if (restorePreviousState) tab.sessionState else null
        if (restored != null) {
            replacement.restoreState(restored)
        } else {
            tab.location
                ?.takeUnless { it == "about:blank" }
                ?.let(replacement::loadUri)
                ?: replacement.loadUri("about:blank")
        }

        val selected = tab.id == selectedTabId
        replacement.setActive(selected)
        replacement.setFocused(selected)
        persistSoon()
    }

    private fun selectTab(tabId: String, persist: Boolean) {
        val next = tabs.firstOrNull { it.id == tabId } ?: return
        tabs.forEach { tab ->
            if (tab.crashed) return@forEach
            val selected = tab === next
            tab.session.setActive(selected)
            tab.session.setFocused(selected)
        }
        selectedTabId = next.id
        next.lastAccessedAtEpochMs = System.currentTimeMillis()
        if (persist) persistSoon()
        notifyChanged()
    }

    private fun clearExternalNavigationForTab(tabId: String) {
        if (pendingExternalNavigation?.request?.tabId == tabId) {
            pendingExternalNavigation = null
        }
    }

    private fun rejectPermissionsForTab(tabId: String) {
        val site = pendingSitePermission
        if (site?.prompt?.tabId == tabId) {
            pendingSitePermission = null
            when (site) {
                is PendingSitePermission.Content -> site.result.complete(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
                )
                is PendingSitePermission.Media -> site.callback.reject()
            }
        }

        val android = pendingAndroidPermission
        if (android?.request?.tabId == tabId) {
            pendingAndroidPermission = null
            android.callback.reject()
        }
    }

    private fun applyTrackingProtectionForTab(tab: RuntimeTab) {
        applyTrackingProtectionForUri(tab, tab.location)
    }

    private fun applyTrackingProtectionForUri(tab: RuntimeTab, rawUrl: String?) {
        if (tab.crashed) return
        val origin = rawUrl
            ?.let(BrowserNavigationPolicy::displayOrigin)
            ?.takeUnless { it == "Unknown origin" }

        val exception = origin != null && privacySettings.perSiteTrackingExceptions.any { saved ->
            BrowserNavigationPolicy.displayOrigin(saved) == origin || saved == origin
        }
        val protectionEnabled =
            privacySettings.blockTrackers ||
                privacySettings.fingerprintingProtection ||
                privacySettings.cryptominingProtection
        tab.session.settings.setUseTrackingProtection(
            protectionEnabled && !exception,
        )
    }

    private fun requireSelected(): RuntimeTab =
        tabs.first { it.id == selectedTabId }

    private fun persistSoon() {
        mainHandler.removeCallbacks(persistRunnable)
        mainHandler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS)
    }

    private fun notifyChangedIfReady() {
        if (::selectedTabId.isInitialized) notifyChanged()
    }

    private fun notifyChanged() {
        listener?.invoke(snapshot())
    }

    companion object {
        private const val PERSIST_DEBOUNCE_MS = 500L
        private val SUPPORTED_ANDROID_PERMISSIONS = setOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}

object BrowserRuntimeStore {
    @Volatile
    private var instance: BrowserRuntimeController? = null

    fun get(context: Context): BrowserRuntimeController =
        instance ?: synchronized(this) {
            instance ?: BrowserRuntimeController(context.applicationContext)
                .also { instance = it }
        }
}
