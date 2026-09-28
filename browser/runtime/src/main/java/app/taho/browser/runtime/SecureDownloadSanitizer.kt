package app.taho.browser.runtime

import java.io.File

object SecureDownloadSanitizer {
    private const val MAX_FILENAME_LENGTH = 128
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]+")

    fun sanitizeFilename(candidate: String?, defaultName: String = "download"): String {
        if (candidate.isNullOrBlank()) return defaultName

        // Extract just the basename, stripping any directory structure / path traversal
        val nameOnly = candidate
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()

        // Replace illegal filesystem characters and control characters
        var sanitized = nameOnly.replace(ILLEGAL_CHARS, "_")
            .replace(Regex("\\.+"), ".") // Collapse consecutive dots (prevents ..)
            .trimStart('.') // Prevent hidden files

        if (sanitized.isBlank()) {
            sanitized = defaultName
        }

        if (sanitized.length > MAX_FILENAME_LENGTH) {
            val ext = sanitized.substringAfterLast('.', "")
            val base = sanitized.substringBeforeLast('.')
            sanitized = if (ext.isNotEmpty() && ext.length < 10) {
                base.take(MAX_FILENAME_LENGTH - ext.length - 1) + "." + ext
            } else {
                sanitized.take(MAX_FILENAME_LENGTH)
            }
        }

        return sanitized
    }

    fun getAppPrivateDownloadFile(filesDir: File, sanitizedFilename: String): File {
        val downloadDir = File(filesDir, "downloads")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        val target = File(downloadDir, sanitizedFilename)
        // Verify canonical path does not escape downloadDir (anti-path-traversal)
        val canonicalDir = downloadDir.canonicalFile
        val canonicalTarget = target.canonicalFile
        check(
            canonicalTarget.parentFile == canonicalDir ||
                canonicalTarget.toPath().startsWith(canonicalDir.toPath()),
        ) {
            "Path traversal detected for $sanitizedFilename"
        }
        return target
    }
}
