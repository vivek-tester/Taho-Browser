package app.taho.browser

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.taho.browser.shell.OfflinePageUi
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OfflinePageManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "taho-offline-page").apply { isDaemon = true }
        }

    fun savePdf(
        title: String,
        url: String,
        stream: InputStream,
        onComplete: (OfflinePageUi?, String?) -> Unit,
    ) {
        executor.execute {
            val result = runCatching {
                stream.use { input ->
                    val id = UUID.randomUUID().toString()
                    val directory = File(appContext.filesDir, "offline_pages").apply { mkdirs() }
                    val file = File(directory, "$id.pdf")
                    file.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                    OfflinePageUi(
                        id = id,
                        title = title.ifBlank { url },
                        url = url,
                        sizeBytes = file.length(),
                    )
                }
            }

            result.fold(
                onSuccess = { page -> onComplete(page, null) },
                onFailure = { error ->
                    onComplete(
                        null,
                        error.javaClass.simpleName.ifBlank { "Offline snapshot failed" },
                    )
                },
            )
        }
    }

    fun open(page: OfflinePageUi): Boolean {
        val file = snapshotFile(page)
        if (!file.isFile) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                appContext,
                appContext.packageName + ".downloads",
                file,
            )
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    fun delete(page: OfflinePageUi): Boolean {
        return runCatching {
            val file = snapshotFile(page)
            !file.exists() || file.delete()
        }.getOrDefault(false)
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun snapshotFile(page: OfflinePageUi): File =
        File(File(appContext.filesDir, "offline_pages"), page.id + ".pdf")

}
