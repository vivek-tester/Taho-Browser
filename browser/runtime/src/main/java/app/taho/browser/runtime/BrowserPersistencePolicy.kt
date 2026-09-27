package app.taho.browser.runtime

data class BrowserPersistableTab(
    val id: String,
    val title: String?,
    val location: String?,
    val serializedSessionState: String?,
    val isPrivate: Boolean,
)

object BrowserPersistencePolicy {
    fun stateForDisk(
        tabs: List<BrowserPersistableTab>,
        selectedTabId: String?,
    ): PersistedBrowserState {
        val normalTabs = tabs
            .filterNot { it.isPrivate }
            .map { tab ->
                PersistedBrowserTab(
                    id = tab.id,
                    title = tab.title,
                    location = tab.location,
                    serializedSessionState = tab.serializedSessionState,
                )
            }

        return PersistedBrowserState(
            tabs = normalTabs,
            selectedTabId = selectedTabId?.takeIf { selected ->
                normalTabs.any { it.id == selected }
            },
        )
    }
}
