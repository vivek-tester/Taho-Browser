package app.taho.browser.runtime

import android.content.Context
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.UUID

data class BrowserTabSnapshot(
    val id: String,
    val location: String?,
    val isLoading: Boolean,
    val loadFailed: Boolean,
    val isPrivate: Boolean,
)

data class BrowserSnapshot(
    val selectedTabId: String,
    val location: String?,
    val tabCount: Int,
    val isLoading: Boolean,
    val loadFailed: Boolean,
    val isPrivate: Boolean,
    val tabs: List<BrowserTabSnapshot>,
)

class BrowserRuntimeController(context: Context) {
    private data class RuntimeTab(
        val id: String,
        val session: GeckoSession,
        val isPrivate: Boolean,
        var location: String? = null,
        var isLoading: Boolean = false,
        var loadFailed: Boolean = false,
    )

    private val runtime = GeckoRuntimeHolder.get(context)
    private val tabs = mutableListOf<RuntimeTab>()
    private lateinit var selectedTabId: String
    private var listener: ((BrowserSnapshot) -> Unit)? = null

    init {
        selectedTabId = createTab(privateMode = false)
        selectTab(selectedTabId)
    }

    fun snapshot(): BrowserSnapshot {
        val selected = requireSelected()
        return BrowserSnapshot(
            selectedTabId = selected.id,
            location = selected.location,
            tabCount = tabs.size,
            isLoading = selected.isLoading,
            loadFailed = selected.loadFailed,
            isPrivate = selected.isPrivate,
            tabs = tabs.map { tab ->
                BrowserTabSnapshot(
                    id = tab.id,
                    location = tab.location,
                    isLoading = tab.isLoading,
                    loadFailed = tab.loadFailed,
                    isPrivate = tab.isPrivate,
                )
            },
        )
    }

    fun setListener(listener: ((BrowserSnapshot) -> Unit)?) {
        this.listener = listener
        listener?.invoke(snapshot())
    }

    fun createTab(privateMode: Boolean): String {
        val id = UUID.randomUUID().toString()
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .build()
        val session = GeckoSession(settings)
        val tab = RuntimeTab(
            id = id,
            session = session,
            isPrivate = privateMode,
        )

        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.location = url
                tab.isLoading = true
                tab.loadFailed = false
                notifyChangedIfReady()
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.isLoading = false
                tab.loadFailed = !success
                notifyChangedIfReady()
            }
        })

        session.open(runtime)
        session.setActive(false)
        session.setFocused(false)
        tabs += tab
        notifyChangedIfReady()
        return id
    }

    fun newTab(privateMode: Boolean): String {
        val id = createTab(privateMode)
        selectTab(id)
        return id
    }

    fun closeTab(tabId: String) {
        if (tabs.size == 1) {
            val only = tabs.single()
            only.location = null
            only.loadFailed = false
            only.isLoading = false
            only.session.loadUri("about:blank")
            notifyChanged()
            return
        }

        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val closing = tabs.removeAt(index)
        closing.session.setFocused(false)
        closing.session.setActive(false)
        closing.session.close()

        if (selectedTabId == tabId) {
            selectedTabId = tabs[index.coerceAtMost(tabs.lastIndex)].id
            selectTab(selectedTabId)
        } else {
            notifyChanged()
        }
    }

    fun selectTab(tabId: String) {
        val next = tabs.firstOrNull { it.id == tabId } ?: return
        tabs.forEach { tab ->
            val selected = tab === next
            tab.session.setActive(selected)
            tab.session.setFocused(selected)
        }
        selectedTabId = next.id
        notifyChanged()
    }

    fun load(tabId: String = selectedTabId, uri: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        tab.loadFailed = false
        tab.isLoading = true
        notifyChangedIfReady()
        tab.session.loadUri(uri)
    }

    fun reload() {
        val tab = requireSelected()
        tab.loadFailed = false
        tab.isLoading = true
        notifyChanged()
        tab.session.reload()
    }

    fun goBack() {
        requireSelected().session.goBack()
    }

    fun goForward() {
        requireSelected().session.goForward()
    }

    fun bind(tabId: String = selectedTabId, surface: BrowserSurfaceView) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: requireSelected()
        surface.bind(tab.session)
    }

    fun forEachSession(block: (tabId: String, session: GeckoSession) -> Unit) {
        tabs.forEach { block(it.id, it.session) }
    }

    private fun requireSelected(): RuntimeTab =
        tabs.first { it.id == selectedTabId }

    private fun notifyChangedIfReady() {
        if (::selectedTabId.isInitialized) notifyChanged()
    }

    private fun notifyChanged() {
        listener?.invoke(snapshot())
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
