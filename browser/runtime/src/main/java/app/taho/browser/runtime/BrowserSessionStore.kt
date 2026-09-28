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

    fun load(profileId: String = PERSONAL_PROFILE_ID): PersistedBrowserState {
        val key = keyFor(profileId)
        var raw = preferences.getString(key, null)

        // One-time compatibility path for the original single-profile store.
        if (raw == null && profileId == PERSONAL_PROFILE_ID) {
            raw = preferences.getString(LEGACY_KEY_STATE, null)
            if (raw != null) {
                preferences.edit()
                    .putString(key, raw)
                    .remove(LEGACY_KEY_STATE)
                    .apply()
            }
        }

        raw ?: return PersistedBrowserState(emptyList(), null)
        val decoded = BrowserSessionCodec.decode(raw)
        if (decoded != null) return decoded

        preferences.edit().remove(key).apply()
        return PersistedBrowserState(emptyList(), null)
    }

    fun save(
        profileId: String = PERSONAL_PROFILE_ID,
        state: PersistedBrowserState,
    ) {
        preferences.edit()
            .putString(keyFor(profileId), BrowserSessionCodec.encode(state))
            .apply()
    }

    fun clear(profileId: String) {
        preferences.edit().remove(keyFor(profileId)).apply()
    }

    private fun keyFor(profileId: String): String {
        val safe = profileId
            .takeIf { PROFILE_ID_PATTERN.matches(it) }
            ?: PERSONAL_PROFILE_ID
        return KEY_PREFIX + safe
    }

    companion object {
        const val PERSONAL_PROFILE_ID = "profile_personal"
        private const val PREFERENCES_NAME = "taho_browser_sessions"
        private const val LEGACY_KEY_STATE = "browser_state"
        private const val KEY_PREFIX = "browser_state:"
        private val PROFILE_ID_PATTERN = Regex("""[A-Za-z0-9._-]{1,128}""")
    }
}
