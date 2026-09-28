package app.taho.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class BrowserUpdateCheckResult(
    val latestVersion: String?,
    val releaseUrl: String?,
    val updateAvailable: Boolean,
    val error: String? = null,
)

class BrowserUpdateChecker(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "taho-update-check").apply { isDaemon = true }
        }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun check(callback: (BrowserUpdateCheckResult) -> Unit) {
        executor.execute {
            val result = runCatching {
                val current = currentVersion()
                val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "Taho-Browser/" + current)

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        return@runCatching BrowserUpdateCheckResult(
                            latestVersion = null,
                            releaseUrl = null,
                            updateAvailable = false,
                            error = "Release service returned HTTP " + connection.responseCode + ".",
                        )
                    }

                    val body = connection.inputStream
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { it.readText() }
                    val json = JSONObject(body)
                    val latest = json.optString("tag_name")
                        .trim()
                        .removePrefix("v")
                        .takeIf(String::isNotBlank)
                    val releaseUrl = json.optString("html_url")
                        .trim()
                        .takeIf { it.startsWith("https://github.com/") }

                    BrowserUpdateCheckResult(
                        latestVersion = latest,
                        releaseUrl = releaseUrl,
                        updateAvailable =
                            latest != null && compareVersions(latest, current) > 0,
                    )
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { error ->
                BrowserUpdateCheckResult(
                    latestVersion = null,
                    releaseUrl = null,
                    updateAvailable = false,
                    error = error.message
                        ?.takeIf(String::isNotBlank)
                        ?: error.javaClass.simpleName,
                )
            }

            mainHandler.post { callback(result) }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun currentVersion(): String =
        runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
                .orEmpty()
                .removePrefix("v")
        }.getOrDefault("0")

    internal fun compareVersions(left: String, right: String): Int {
        val a = numericParts(left)
        val b = numericParts(right)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun numericParts(raw: String): List<Int> =
        Regex("""\d+""")
            .findAll(raw)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
            .ifEmpty { listOf(0) }

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/vivek-tester/Taho-Browser/releases/latest"
    }
}
