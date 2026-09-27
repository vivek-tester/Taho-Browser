package app.taho.browser.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.UUID

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
    val canGoBack: Boolean,
    val canGoForward: Boolean,
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
        var canGoBack: Boolean = false,
        var canGoForward: Boolean = false,
        var sessionState: GeckoSession.SessionState? = null,
    )

    private val runtime = GeckoRuntimeHolder.get(context)
    private val sessionStore = BrowserSessionStore(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tabs = mutableListOf<RuntimeTab>()
    private lateinit var selectedTabId: String
    private var listener: ((BrowserSnapshot) -> Unit)? = null

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
            canGoBack = selected.canGoBack,
            canGoForward = selected.canGoForward,
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
        ).id

    fun newTab(privateMode: Boolean): String {
        val id = createTab(privateMode)
        selectTab(id)
        return id
    }

    fun closeTab(tabId: String) {
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

    fun persistNow() {
        mainHandler.removeCallbacks(persistRunnable)

        val normalTabs = tabs
            .filterNot { it.isPrivate }
            .map { tab ->
                PersistedBrowserTab(
                    id = tab.id,
                    title = tab.title,
                    location = tab.location,
                    serializedSessionState = tab.sessionState?.toString(),
                )
            }

        val selectedNormal = selectedTabId.takeIf { selected ->
            normalTabs.any { it.id == selected }
        }

        sessionStore.save(
            PersistedBrowserState(
                tabs = normalTabs,
                selectedTabId = selectedNormal,
            ),
        )
    }

    private fun createRuntimeTab(
        id: String,
        privateMode: Boolean,
        initialLocation: String?,
        initialTitle: String?,
        restoredState: GeckoSession.SessionState?,
    ): RuntimeTab {
        val session = newSession(privateMode)
        val tab = RuntimeTab(
            id = id,
            session = session,
            isPrivate = privateMode,
            title = initialTitle,
            location = initialLocation,
            sessionState = restoredState,
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
                persistSoon()
                notifyChangedIfReady()
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.isLoading = false
                tab.loadFailed = !success
                persistSoon()
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
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.location = url
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

            override fun onCrash(session: GeckoSession) {
                markCrashed(tab)
            }

            override fun onKill(session: GeckoSession) {
                markCrashed(tab)
            }
        })
    }

    private fun markCrashed(tab: RuntimeTab) {
        tab.crashed = true
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
        if (persist) persistSoon()
        notifyChanged()
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
