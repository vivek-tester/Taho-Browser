package app.taho.browser

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal object BrowserReleaseApi {
    fun check(context: Context): BrowserUpdateCheckResult {
        val current = currentVersion(context)
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Taho-Browser/" + current)

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                BrowserUpdateCheckResult(
                    latestVersion = null,
                    releaseUrl = null,
                    updateAvailable = false,
                    error = "Release service returned HTTP " + connection.responseCode + ".",
                )
            } else {
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
                    .takeIf { isTrustedReleaseUrl(it) }

                BrowserUpdateCheckResult(
                    latestVersion = latest,
                    releaseUrl = releaseUrl,
                    updateAvailable =
                        latest != null && compareVersions(latest, current) > 0,
                )
            }
        } finally {
            connection.disconnect()
        }
    }

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

    private fun currentVersion(context: Context): String =
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
                .removePrefix("v")
        }.getOrDefault("0")

    private fun numericParts(raw: String): List<Int> =
        Regex("""\d+""")
            .findAll(raw)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
            .ifEmpty { listOf(0) }

    private fun isTrustedReleaseUrl(raw: String): Boolean =
        runCatching {
            val uri = java.net.URI(raw)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("github.com", ignoreCase = true) &&
                uri.path.startsWith("/vivek-tester/Taho-Browser/")
        }.getOrDefault(false)

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/vivek-tester/Taho-Browser/releases/latest"
}
