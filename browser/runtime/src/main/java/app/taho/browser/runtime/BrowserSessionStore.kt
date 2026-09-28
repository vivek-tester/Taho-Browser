package app.taho.browser.runtime

import android.content.Context

data class PersistedBrowserTab(
    val id: String,
    val title: String?,
    val location: String?,
    val serializedSessionState: String?,
    val lastAccessedAtEpochMs: Long? = null,
)

data class PersistedBrowserState(
    val tabs: List<PersistedBrowserTab>,
    val selectedTabId: String?,
)

class BrowserSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): PersistedBrowserState {
        val raw = preferences.getString(KEY_STATE, null)
            ?: return PersistedBrowserState(emptyList(), null)

        val decoded = BrowserSessionCodec.decode(raw)
        if (decoded != null) return decoded

        preferences.edit().remove(KEY_STATE).apply()
        return PersistedBrowserState(emptyList(), null)
    }

    fun save(state: PersistedBrowserState) {
        preferences.edit()
            .putString(KEY_STATE, BrowserSessionCodec.encode(state))
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "taho_browser_sessions"
        private const val KEY_STATE = "browser_state"
    }
}
