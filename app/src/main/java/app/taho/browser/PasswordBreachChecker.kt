package app.taho.browser

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Checks Pwned Passwords using its k-anonymity range API.
 *
 * Only the first five SHA-1 hex characters leave the device. The raw password
 * and complete hash are never logged or transmitted.
 */
class PasswordBreachChecker {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "taho-password-breach").apply { isDaemon = true }
        }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun check(password: String, callback: (Int?) -> Unit) {
        if (password.isEmpty()) {
            callback(0)
            return
        }

        executor.execute {
            val count = runCatching {
                val hash = sha1Hex(password)
                val prefix = hash.take(5)
                val suffix = hash.drop(5)

                val connection = URL(
                    "https://api.pwnedpasswords.com/range/$prefix",
                ).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("User-Agent", "Taho-Browser/0.1")
                    connection.setRequestProperty("Add-Padding", "true")
                    connection.setRequestProperty("Accept", "text/plain")

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        error("Unexpected breach service status")
                    }

                    connection.inputStream
                        .bufferedReader(StandardCharsets.UTF_8)
                        .useLines { lines ->
                            lines.mapNotNull { line ->
                                val separator = line.indexOf(':')
                                if (separator <= 0) return@mapNotNull null
                                val candidate = line.substring(0, separator).trim()
                                if (!candidate.equals(suffix, ignoreCase = true)) {
                                    return@mapNotNull null
                                }
                                line.substring(separator + 1).trim().toIntOrNull()
                            }.firstOrNull() ?: 0
                        }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()

            mainHandler.post { callback(count) }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun sha1Hex(password: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(password.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02X".format(byte.toInt() and 0xFF) }
}
