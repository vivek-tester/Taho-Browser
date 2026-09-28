package app.taho.browser.runtime

import org.json.JSONArray
import org.json.JSONObject

object BrowserSessionCodec {
    private const val SCHEMA_VERSION = 2

    fun encode(state: PersistedBrowserState): String {
        val root = JSONObject()
        val items = JSONArray()

        state.tabs.forEach { tab ->
            items.put(
                JSONObject().apply {
                    put("id", tab.id)
                    putNullable("title", tab.title)
                    putNullable("location", tab.location)
                    putNullable("sessionState", tab.serializedSessionState)
                    tab.lastAccessedAtEpochMs?.let { put("lastAccessedAtEpochMs", it) }
                },
            )
        }

        root.put("version", SCHEMA_VERSION)
        root.put("tabs", items)
        root.putNullable("selectedTabId", state.selectedTabId)
        return root.toString()
    }

    fun decode(raw: String): PersistedBrowserState? =
        runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("version", -1)
            if (version !in 1..SCHEMA_VERSION) {
                return null
            }

            val items = root.optJSONArray("tabs") ?: JSONArray()
            val seenIds = mutableSetOf<String>()
            val tabs = buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank)
                        ?: continue
                    if (!seenIds.add(id)) continue

                    add(
                        PersistedBrowserTab(
                            id = id,
                            title = item.optNullableString("title"),
                            location = item.optNullableString("location"),
                            serializedSessionState = item.optNullableString("sessionState"),
                            lastAccessedAtEpochMs = item
                                .takeIf { version >= 2 && it.has("lastAccessedAtEpochMs") }
                                ?.optLong("lastAccessedAtEpochMs"),
                        ),
                    )
                }
            }

            val selected = root.optNullableString("selectedTabId")
                ?.takeIf { candidate -> tabs.any { it.id == candidate } }

            PersistedBrowserState(
                tabs = tabs,
                selectedTabId = selected,
            )
        }.getOrNull()

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private fun JSONObject.putNullable(name: String, value: String?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }
}
