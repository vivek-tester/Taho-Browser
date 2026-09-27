package app.taho.browser.runtime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PersistedBrowserTab(
    val id: String,
    val title: String?,
    val location: String?,
    val serializedSessionState: String?,
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

        return runCatching {
            val root = JSONObject(raw)
            val items = root.optJSONArray("tabs") ?: JSONArray()
            val tabs = buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank)
                        ?: continue
                    add(
                        PersistedBrowserTab(
                            id = id,
                            title = item.optNullableString("title"),
                            location = item.optNullableString("location"),
                            serializedSessionState = item.optNullableString("sessionState"),
                        ),
                    )
                }
            }

            PersistedBrowserState(
                tabs = tabs,
                selectedTabId = root.optNullableString("selectedTabId"),
            )
        }.getOrElse {
            preferences.edit().remove(KEY_STATE).apply()
            PersistedBrowserState(emptyList(), null)
        }
    }

    fun save(state: PersistedBrowserState) {
        val root = JSONObject()
        val items = JSONArray()

        state.tabs.forEach { tab ->
            items.put(
                JSONObject().apply {
                    put("id", tab.id)
                    putNullable("title", tab.title)
                    putNullable("location", tab.location)
                    putNullable("sessionState", tab.serializedSessionState)
                },
            )
        }

        root.put("version", SCHEMA_VERSION)
        root.put("tabs", items)
        root.putNullable("selectedTabId", state.selectedTabId)

        preferences.edit()
            .putString(KEY_STATE, root.toString())
            .apply()
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private fun JSONObject.putNullable(name: String, value: String?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }

    companion object {
        private const val PREFERENCES_NAME = "taho_browser_sessions"
        private const val KEY_STATE = "browser_state"
        private const val SCHEMA_VERSION = 1
    }
}
