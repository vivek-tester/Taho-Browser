package app.taho.browser

import android.content.Context
import android.net.Uri
import app.taho.browser.shell.BrowserBackupRestoreSummary
import app.taho.browser.shell.BrowserBackupSnapshot
import app.taho.browser.shell.DownloadItemUi
import app.taho.browser.shell.TahoBrowserStateStore
import app.taho.browser.shell.TahoDownloadStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BrowserBackupResult(
    val success: Boolean,
    val message: String,
    val restoreSummary: BrowserBackupRestoreSummary? = null,
)

private data class BrowserBackupArchive(
    val archiveVersion: Int,
    val snapshot: BrowserBackupSnapshot,
    val downloads: List<BrowserBackupFile>,
    val offlinePages: List<BrowserBackupFile>,
) : Serializable

private data class BrowserBackupFile(
    val id: String,
    val fileName: String,
    val bytes: ByteArray,
) : Serializable

/**
 * Encrypted portable backup for Taho-owned browser records and app-private
 * download/offline files. Gecko-owned cookies/cache/IndexedDB are excluded by
 * architecture and are not copied from Gecko's internal profile directory.
 */
class BrowserBackupManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "taho-browser-backup").apply { isDaemon = true }
        }
    private val random = SecureRandom()

    fun exportTo(
        destination: Uri,
        passphrase: CharArray,
        callback: (BrowserBackupResult) -> Unit,
    ) {
        val passCopy = passphrase.copyOf()
        executor.execute {
            val result = runCatching {
                require(passCopy.size >= MIN_PASSPHRASE_LENGTH) {
                    "Backup passphrase must be at least $MIN_PASSPHRASE_LENGTH characters."
                }

                val snapshot = TahoBrowserStateStore.createBackupSnapshot()
                val archive = BrowserBackupArchive(
                    archiveVersion = ARCHIVE_VERSION,
                    snapshot = snapshot,
                    downloads = collectDownloads(snapshot.downloads),
                    offlinePages = collectOfflinePages(snapshot),
                )
                val clearBytes = serialize(archive)
                require(clearBytes.size <= MAX_CLEAR_ARCHIVE_BYTES) {
                    "Backup exceeds the supported size limit."
                }
                val encrypted = encrypt(clearBytes, passCopy)
                appContext.contentResolver.openOutputStream(destination, "w")
                    ?.buffered()
                    ?.use { it.write(encrypted) }
                    ?: error("Unable to open backup destination.")

                BrowserBackupResult(
                    success = true,
                    message = "Encrypted browser backup created.",
                )
            }.getOrElse { error ->
                BrowserBackupResult(
                    success = false,
                    message = error.message?.takeIf(String::isNotBlank)
                        ?: error.javaClass.simpleName,
                )
            }
            passCopy.fill('\u0000')
            appContext.mainExecutor.execute { callback(result) }
        }
    }

    fun restoreFrom(
        source: Uri,
        passphrase: CharArray,
        callback: (BrowserBackupResult) -> Unit,
    ) {
        val passCopy = passphrase.copyOf()
        executor.execute {
            val result = runCatching {
                require(passCopy.size >= MIN_PASSPHRASE_LENGTH) {
                    "Backup passphrase must be at least $MIN_PASSPHRASE_LENGTH characters."
                }

                val encrypted = appContext.contentResolver.openInputStream(source)
                    ?.buffered()
                    ?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_ENCRYPTED_ARCHIVE_BYTES) {
                                "Backup file exceeds the supported size limit."
                            }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                    ?: error("Unable to open backup file.")

                val clear = decrypt(encrypted, passCopy)
                val archive = deserialize(clear) as? BrowserBackupArchive
                    ?: error("Backup payload has an invalid type.")
                require(archive.archiveVersion == ARCHIVE_VERSION) {
                    "Unsupported backup archive version."
                }
                require(
                    archive.snapshot.schemaVersion ==
                        BrowserBackupSnapshot.CURRENT_SCHEMA_VERSION,
                ) {
                    "Unsupported browser-data schema version."
                }

                val restoredDownloads = restoreDownloads(
                    archive.snapshot.downloads,
                    archive.downloads,
                )
                restoreOfflinePages(archive.offlinePages)

                val restoredSnapshot = archive.snapshot.copy(
                    downloads = restoredDownloads,
                )
                val summary = TahoBrowserStateStore.restoreBackupSnapshot(restoredSnapshot)

                BrowserBackupResult(
                    success = true,
                    message = "Encrypted browser backup restored.",
                    restoreSummary = summary,
                )
            }.getOrElse { error ->
                BrowserBackupResult(
                    success = false,
                    message = when (error) {
                        is javax.crypto.AEADBadTagException ->
                            "Backup could not be decrypted. Check the passphrase."
                        else ->
                            error.message?.takeIf(String::isNotBlank)
                                ?: error.javaClass.simpleName
                    },
                )
            }
            passCopy.fill('\u0000')
            appContext.mainExecutor.execute { callback(result) }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun collectDownloads(items: List<DownloadItemUi>): List<BrowserBackupFile> =
        items.mapNotNull { item ->
            if (item.status != TahoDownloadStatus.COMPLETED) return@mapNotNull null
            val path = item.localPath ?: return@mapNotNull null
            val file = File(path)
            if (!isWithin(file, File(appContext.filesDir, "downloads")) || !file.isFile) {
                return@mapNotNull null
            }
            val bytes = readBounded(file, MAX_SINGLE_FILE_BYTES) ?: return@mapNotNull null
            BrowserBackupFile(item.id, file.name, bytes)
        }

    private fun collectOfflinePages(snapshot: BrowserBackupSnapshot): List<BrowserBackupFile> {
        val directory = File(appContext.filesDir, "offline_pages")
        return snapshot.offlinePages.mapNotNull { page ->
            val file = File(directory, page.id + ".pdf")
            if (!isWithin(file, directory) || !file.isFile) return@mapNotNull null
            val bytes = readBounded(file, MAX_SINGLE_FILE_BYTES) ?: return@mapNotNull null
            BrowserBackupFile(page.id, page.id + ".pdf", bytes)
        }
    }

    private fun restoreDownloads(
        metadata: List<DownloadItemUi>,
        files: List<BrowserBackupFile>,
    ): List<DownloadItemUi> {
        val directory = File(appContext.filesDir, "downloads").apply { mkdirs() }
        val byId = files.associateBy { it.id }

        return metadata.map { item ->
            val payload = byId[item.id]
            if (payload == null) {
                if (item.status == TahoDownloadStatus.COMPLETED) {
                    item.copy(
                        status = TahoDownloadStatus.FAILED,
                        localPath = null,
                    )
                } else {
                    item.copy(localPath = null)
                }
            } else {
                val safeName = app.taho.browser.runtime.SecureDownloadSanitizer
                    .sanitizeFilename(payload.fileName)
                val target = uniqueFile(directory, safeName)
                writeAtomically(target, payload.bytes)
                item.copy(
                    fileName = target.name,
                    bytesDownloaded = target.length(),
                    totalBytes = if (item.totalBytes > 0) item.totalBytes else target.length(),
                    status = TahoDownloadStatus.COMPLETED,
                    localPath = target.absolutePath,
                )
            }
        }
    }

    private fun restoreOfflinePages(files: List<BrowserBackupFile>) {
        val directory = File(appContext.filesDir, "offline_pages").apply { mkdirs() }
        files.forEach { payload ->
            val safeId = payload.id.takeIf { ID_PATTERN.matches(it) } ?: return@forEach
            val target = File(directory, safeId + ".pdf")
            writeAtomically(target, payload.bytes)
        }
    }

    private fun encrypt(clear: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val cipherText = cipher.doFinal(clear)

        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeInt(FILE_VERSION)
                data.writeInt(PBKDF2_ITERATIONS)
                data.writeInt(salt.size)
                data.write(salt)
                data.writeInt(iv.size)
                data.write(iv)
                data.writeInt(cipherText.size)
                data.write(cipherText)
            }
            output.toByteArray()
        }
    }

    private fun decrypt(encrypted: ByteArray, passphrase: CharArray): ByteArray {
        val input = DataInputStream(ByteArrayInputStream(encrypted))
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        require(magic.contentEquals(MAGIC)) { "Not a Taho Browser backup." }
        require(input.readInt() == FILE_VERSION) { "Unsupported encrypted backup version." }
        val iterations = input.readInt()
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Invalid backup key-derivation parameters."
        }
        val saltSize = input.readInt()
        require(saltSize in 16..64)
        val salt = ByteArray(saltSize).also(input::readFully)
        val ivSize = input.readInt()
        require(ivSize in 12..32)
        val iv = ByteArray(ivSize).also(input::readFully)
        val cipherSize = input.readInt()
        require(cipherSize in 1..MAX_ENCRYPTED_ARCHIVE_BYTES.toInt())
        val cipherText = ByteArray(cipherSize).also(input::readFully)
        require(input.read() == -1) { "Unexpected trailing backup data." }

        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return cipher.doFinal(cipherText)
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun serialize(value: Serializable): ByteArray =
        ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(value) }
            output.toByteArray()
        }

    private fun deserialize(bytes: ByteArray): Any? {
        require(bytes.size <= MAX_CLEAR_ARCHIVE_BYTES)
        return SafeBackupObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.readObject()
        }
    }

    private class SafeBackupObjectInputStream(
        input: java.io.InputStream,
    ) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            val name = desc.name
            require(isAllowedSerializedClass(name)) {
                "Backup contains an unsupported serialized type."
            }
            return super.resolveClass(desc)
        }

        private fun isAllowedSerializedClass(name: String): Boolean {
            if (name.startsWith("app.taho.browser.shell.")) return true
            if (name == "app.taho.browser.BrowserBackupArchive") return true
            if (name == "app.taho.browser.BrowserBackupFile") return true
            if (name.startsWith("[Lapp.taho.browser.shell.")) return true
            if (name == "[B" || name == "[I" || name == "[J" || name == "[Z") return true

            return name in SAFE_JAVA_TYPES
        }
    }

    private fun readBounded(file: File, maxBytes: Long): ByteArray? {
        if (file.length() > maxBytes) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        require(bytes.size.toLong() <= MAX_SINGLE_FILE_BYTES)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".restore-tmp")
        temp.outputStream().buffered().use { it.write(bytes) }
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Unable to restore browser file." }
        }
    }

    private fun uniqueFile(directory: File, requested: String): File {
        val direct = File(directory, requested)
        if (!direct.exists()) return direct
        val extension = requested.substringAfterLast('.', "")
        val base = if (extension.isBlank()) requested else requested.removeSuffix(".$extension")
        for (index in 1..9_999) {
            val name = if (extension.isBlank()) {
                "$base ($index)"
            } else {
                "$base ($index).$extension"
            }
            val next = File(directory, name)
            if (!next.exists()) return next
        }
        return File(directory, java.util.UUID.randomUUID().toString())
    }

    private fun isWithin(file: File, root: File): Boolean =
        runCatching {
            val canonicalRoot = root.canonicalFile
            val canonicalFile = file.canonicalFile
            canonicalFile.path == canonicalRoot.path ||
                canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
        }.getOrDefault(false)

    private companion object {
        const val ARCHIVE_VERSION = 1
        const val FILE_VERSION = 1
        const val MIN_PASSPHRASE_LENGTH = 10
        const val SALT_BYTES = 32
        const val IV_BYTES = 12
        const val PBKDF2_ITERATIONS = 210_000
        const val MIN_ACCEPTED_ITERATIONS = 100_000
        const val MAX_ACCEPTED_ITERATIONS = 2_000_000
        const val MAX_SINGLE_FILE_BYTES = 64L * 1024L * 1024L
        const val MAX_CLEAR_ARCHIVE_BYTES = 256 * 1024 * 1024
        const val MAX_ENCRYPTED_ARCHIVE_BYTES = 272L * 1024L * 1024L
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val MAGIC = "TAHO-BROWSER-BACKUP".toByteArray(Charsets.US_ASCII)
        val ID_PATTERN = Regex("""[A-Za-z0-9._-]{1,128}""")
        val SAFE_JAVA_TYPES = setOf(
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Boolean",
            "java.lang.Enum",
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.Collections\$EmptyList",
            "java.util.Collections\$SingletonList",
            "java.util.Collections\$UnmodifiableRandomAccessList",
            "java.util.Collections\$EmptySet",
            "java.util.Collections\$SingletonSet",
            "kotlin.collections.EmptyList",
            "kotlin.collections.EmptySet",
        )
    }
}
