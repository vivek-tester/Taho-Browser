package app.taho.browser

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.taho.browser.runtime.SecureDownloadSanitizer
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
                    val base = SecureDownloadSanitizer.sanitizeFilename(
                        title.ifBlank { "offline-page" },
                    ).removeSuffix(".pdf")
                    val directory = File(appContext.filesDir, "offline_pages").apply { mkdirs() }
                    val file = uniqueFile(directory, "$base.pdf")
                    file.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                    OfflinePageUi(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { url },
                        url = url,
                        sizeBytes = file.length(),
                        localPath = file.absolutePath,
                        mimeType = "application/pdf",
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
        val path = page.localPath ?: return false
        val file = File(path)
        if (!file.isFile) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                appContext,
                appContext.packageName + ".downloads",
                file,
            )
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, page.mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    fun delete(page: OfflinePageUi): Boolean {
        val path = page.localPath ?: return true
        return runCatching {
            val file = File(path)
            !file.exists() || file.delete()
        }.getOrDefault(false)
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun uniqueFile(directory: File, candidate: String): File {
        val first = File(directory, candidate)
        if (!first.exists()) return first

        val ext = candidate.substringAfterLast('.', "")
        val base = if (ext.isBlank()) candidate else candidate.removeSuffix(".$ext")
        for (index in 1..9_999) {
            val name = if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext"
            val next = File(directory, name)
            if (!next.exists()) return next
        }
        return File(directory, UUID.randomUUID().toString() + ".pdf")
    }
}
