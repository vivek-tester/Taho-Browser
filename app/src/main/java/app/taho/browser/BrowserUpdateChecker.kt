package app.taho.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
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
                BrowserReleaseApi.check(appContext)
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
}
