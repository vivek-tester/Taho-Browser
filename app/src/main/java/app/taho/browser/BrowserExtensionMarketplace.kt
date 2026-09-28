package app.taho.browser

import android.os.Handler
import android.os.Looper
import app.taho.browser.shell.ExtensionMarketplaceItemUi
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject

class BrowserExtensionMarketplace {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "taho-amo-search").apply { isDaemon = true }
        }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun search(
        query: String,
        callback: (List<ExtensionMarketplaceItemUi>, String?) -> Unit,
    ) {
        val normalized = query.trim().take(100)
        executor.execute {
            val result = runCatching {
                val encoded = URLEncoder.encode(
                    normalized,
                    StandardCharsets.UTF_8.name(),
                )
                val url = URL(
                    SEARCH_ENDPOINT + "?q=" + encoded + "&app=android&type=extension&page_size=20",
                )
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", "Taho-Browser/0.1")

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        error("Mozilla Add-ons returned HTTP " + connection.responseCode + ".")
                    }

                    val body = connection.inputStream
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { it.readText() }
                    parseResults(JSONObject(body))
                } finally {
                    connection.disconnect()
                }
            }

            result.fold(
                onSuccess = { items ->
                    mainHandler.post { callback(items, null) }
                },
                onFailure = { error ->
                    mainHandler.post {
                        callback(
                            emptyList(),
                            error.message?.takeIf(String::isNotBlank)
                                ?: error.javaClass.simpleName,
                        )
                    }
                },
            )
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun parseResults(root: JSONObject): List<ExtensionMarketplaceItemUi> {
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val addon = results.optJSONObject(index) ?: continue
                if (!addon.optString("type").equals("extension", ignoreCase = true)) continue
                if (!addon.optString("status").equals("public", ignoreCase = true)) continue

                val current = addon.optJSONObject("current_version") ?: continue
                val files = current.optJSONArray("files") ?: continue
                var installUrl: String? = null
                for (fileIndex in 0 until files.length()) {
                    val file = files.optJSONObject(fileIndex) ?: continue
                    val candidate = file.optString("url")
                    if (isTrustedInstallUrl(candidate)) {
                        installUrl = candidate
                        break
                    }
                }
                val safeInstall = installUrl ?: continue

                val id = addon.optString("guid")
                    .takeIf(String::isNotBlank)
                    ?: addon.optString("slug").takeIf(String::isNotBlank)
                    ?: continue
                val name = translated(addon.optJSONObject("name"))
                    ?: addon.optString("slug").takeIf(String::isNotBlank)
                    ?: id
                val summary = translated(addon.optJSONObject("summary"))
                    ?: translated(addon.optJSONObject("description"))
                    ?: ""
                val author = addon.optJSONArray("authors")
                    ?.optJSONObject(0)
                    ?.optString("name")
                    ?.takeIf(String::isNotBlank)
                    ?: "Unknown"
                val rating = addon.optJSONObject("ratings")
                    ?.takeIf { it.has("average") && !it.isNull("average") }
                    ?.optDouble("average")
                val users = addon
                    .takeIf { it.has("average_daily_users") && !it.isNull("average_daily_users") }
                    ?.optLong("average_daily_users")
                val detail = addon.optString("url")
                    .takeIf(::isHttpsUrl)

                add(
                    ExtensionMarketplaceItemUi(
                        id = id,
                        name = name,
                        summary = summary,
                        author = author,
                        version = current.optString("version").takeIf(String::isNotBlank) ?: "?",
                        rating = rating,
                        users = users,
                        installUrl = safeInstall,
                        detailUrl = detail,
                    ),
                )
            }
        }
    }

    private fun translated(value: JSONObject?): String? {
        if (value == null) return null
        return value.optString("en-US")
            .takeIf(String::isNotBlank)
            ?: value.keys().asSequence()
                .mapNotNull { key -> value.optString(key).takeIf(String::isNotBlank) }
                .firstOrNull()
    }

    private fun isTrustedInstallUrl(raw: String): Boolean =
        runCatching {
            val uri = URI(raw)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null &&
                (
                    uri.host.equals("addons.mozilla.org", ignoreCase = true) ||
                        uri.host?.endsWith(".addons.mozilla.org", ignoreCase = true) == true ||
                        uri.host.equals("addons.cdn.mozilla.net", ignoreCase = true) ||
                        uri.host?.endsWith(".addons.cdn.mozilla.net", ignoreCase = true) == true
                    )
        }.getOrDefault(false)

    private fun isHttpsUrl(raw: String): Boolean =
        runCatching {
            val uri = URI(raw)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }.getOrDefault(false)

    private companion object {
        const val SEARCH_ENDPOINT = "https://addons.mozilla.org/api/v5/addons/search/"
    }
}
