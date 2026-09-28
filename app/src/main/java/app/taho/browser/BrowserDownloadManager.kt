package app.taho.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.taho.browser.runtime.BrowserDownloadResponse
import app.taho.browser.runtime.SecureDownloadSanitizer
import app.taho.browser.shell.DownloadItemUi
import app.taho.browser.shell.TahoDownloadStatus
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns GeckoView external-response bodies after BrowserRuntimeController hands
 * them off. Progress is based on bytes actually written to app-private storage.
 *
 * Pause/resume is real for a live response stream. It intentionally does not
 * pretend to support process-death/range resumption because Gecko's external
 * response stream may depend on authenticated browser state.
 */
class BrowserDownloadManager(
    context: Context,
    private val onUpdate: (DownloadItemUi) -> Unit,
) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService =
        Executors.newCachedThreadPool { task ->
            Thread(task, "taho-download").apply { isDaemon = true }
        }
    private val tasks = ConcurrentHashMap<String, DownloadTask>()

    fun accept(response: BrowserDownloadResponse) {
        val body = response.body
        val id = UUID.randomUUID().toString()
        val fileName = uniqueFileName(suggestedFileName(response))
        val finalFile = SecureDownloadSanitizer.getAppPrivateDownloadFile(
            appContext.filesDir,
            fileName,
        )
        val partFile = File(finalFile.parentFile, finalFile.name + ".part-" + id.take(8))
        val totalBytes = contentLength(response.headers)
        val mimeType = response.headers["content-type"]
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "application/octet-stream"

        val task = DownloadTask(
            id = id,
            uri = response.uri,
            body = body,
            partFile = partFile,
            finalFile = finalFile,
            mimeType = mimeType,
            totalBytes = totalBytes,
        )
        tasks[id] = task
        publish(task, TahoDownloadStatus.DOWNLOADING)
        executor.execute { stream(task) }
    }

    fun pauseResume(id: String) {
        val task = tasks[id] ?: return
        synchronized(task.pauseLock) {
            if (task.cancelled || task.finished) return
            task.paused = !task.paused
            if (!task.paused) task.pauseLock.notifyAll()
            publish(
                task,
                if (task.paused) TahoDownloadStatus.PAUSED
                else TahoDownloadStatus.DOWNLOADING,
            )
        }
    }

    fun cancel(id: String) {
        val task = tasks[id] ?: return
        synchronized(task.pauseLock) {
            if (task.finished) return
            task.cancelled = true
            task.paused = false
            task.pauseLock.notifyAll()
        }
        runCatching { task.body.close() }
        publish(task, TahoDownloadStatus.CANCELLED)
    }

    fun delete(item: DownloadItemUi): Boolean {
        cancel(item.id)
        tasks.remove(item.id)
        val path = item.localPath ?: return true
        return runCatching {
            val file = File(path)
            if (!file.exists()) true else file.delete()
        }.getOrDefault(false)
    }

    fun open(item: DownloadItemUi): Boolean {
        if (item.status != TahoDownloadStatus.COMPLETED) return false
        val path = item.localPath ?: return false
        val file = File(path)
        if (!file.isFile) return false

        return runCatching {
            val uri = FileProvider.getUriForFile(
                appContext,
                appContext.packageName + ".downloads",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, item.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun cancelAllActive() {
        tasks.keys.toList().forEach(::cancel)
    }

    fun close() {
        tasks.values.forEach { task ->
            synchronized(task.pauseLock) {
                task.cancelled = true
                task.paused = false
                task.pauseLock.notifyAll()
            }
            runCatching { task.body.close() }
        }
        tasks.clear()
        executor.shutdownNow()
    }

    private fun stream(task: DownloadTask) {
        try {
            task.partFile.parentFile?.mkdirs()
            task.body.use { input ->
                task.partFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        synchronized(task.pauseLock) {
                            while (task.paused && !task.cancelled) {
                                task.pauseLock.wait()
                            }
                            if (task.cancelled) return@use
                        }

                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        task.bytesDownloaded += read.toLong()
                        publish(
                            task,
                            if (task.paused) TahoDownloadStatus.PAUSED
                            else TahoDownloadStatus.DOWNLOADING,
                        )
                    }
                    output.flush()
                }
            }

            if (task.cancelled) {
                task.partFile.delete()
                publish(task, TahoDownloadStatus.CANCELLED)
                return
            }

            if (task.finalFile.exists()) task.finalFile.delete()
            check(task.partFile.renameTo(task.finalFile)) {
                "Unable to commit downloaded file"
            }
            task.finished = true
            publish(task, TahoDownloadStatus.COMPLETED, localPath = task.finalFile.absolutePath)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            task.partFile.delete()
            if (!task.cancelled) publish(task, TahoDownloadStatus.FAILED)
        } catch (_: Throwable) {
            task.partFile.delete()
            if (task.cancelled) {
                publish(task, TahoDownloadStatus.CANCELLED)
            } else {
                publish(task, TahoDownloadStatus.FAILED)
            }
        } finally {
            runCatching { task.body.close() }
            if (task.finished || task.cancelled) tasks.remove(task.id)
        }
    }

    private fun publish(
        task: DownloadTask,
        status: TahoDownloadStatus,
        localPath: String? = if (task.finished) task.finalFile.absolutePath else null,
    ) {
        onUpdate(
            DownloadItemUi(
                id = task.id,
                fileName = task.finalFile.name,
                url = task.uri,
                bytesDownloaded = task.bytesDownloaded,
                totalBytes = task.totalBytes,
                status = status,
                localPath = localPath,
                mimeType = task.mimeType,
            ),
        )
    }

    private fun suggestedFileName(response: BrowserDownloadResponse): String {
        val disposition = response.headers["content-disposition"].orEmpty()
        val encoded = FILENAME_STAR.find(disposition)?.groupValues?.getOrNull(1)
        val decoded = encoded?.let {
            runCatching {
                URLDecoder.decode(it, StandardCharsets.UTF_8.name())
            }.getOrNull()
        }
        val quoted = FILENAME_QUOTED.find(disposition)?.groupValues?.getOrNull(1)
        val plain = FILENAME_PLAIN.find(disposition)?.groupValues?.getOrNull(1)

        val fromHeader = decoded ?: quoted ?: plain
        val fromUri = runCatching {
            Uri.parse(response.uri).lastPathSegment
        }.getOrNull()

        return SecureDownloadSanitizer.sanitizeFilename(fromHeader ?: fromUri)
    }

    private fun uniqueFileName(candidate: String): String {
        val safe = SecureDownloadSanitizer.sanitizeFilename(candidate)
        val directory = File(appContext.filesDir, "downloads").apply { mkdirs() }
        if (!File(directory, safe).exists()) return safe

        val extension = safe.substringAfterLast('.', "")
        val base = if (extension.isBlank()) safe else safe.removeSuffix(".$extension")
        for (index in 1..9_999) {
            val next = if (extension.isBlank()) "$base ($index)" else "$base ($index).$extension"
            if (!File(directory, next).exists()) return next
        }
        return UUID.randomUUID().toString() + if (extension.isBlank()) "" else ".$extension"
    }

    private fun contentLength(headers: Map<String, String>): Long =
        headers["content-length"]?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    private data class DownloadTask(
        val id: String,
        val uri: String,
        val body: java.io.InputStream,
        val partFile: File,
        val finalFile: File,
        val mimeType: String,
        val totalBytes: Long,
        val pauseLock: Object = Object(),
        @Volatile var paused: Boolean = false,
        @Volatile var cancelled: Boolean = false,
        @Volatile var finished: Boolean = false,
        @Volatile var bytesDownloaded: Long = 0L,
    )

    private companion object {
        val FILENAME_STAR = Regex("""filename\*\s*=\s*(?:UTF-8'')?([^;]+)""", RegexOption.IGNORE_CASE)
        val FILENAME_QUOTED = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        val FILENAME_PLAIN = Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
    }
}
