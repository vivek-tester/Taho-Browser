package app.taho.browser.transfer.android

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.taho.browser.contract.ContractLimits
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface TransferArtifactClock {
    fun elapsedRealtimeMs(): Long
    fun bootId(): String?
}

internal class AndroidTransferArtifactClock(
    private val context: Context,
) : TransferArtifactClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun bootId(): String? {
        val bootCount = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.BOOT_COUNT,
            )
        }.getOrNull()
        if (bootCount != null) return "boot-count:$bootCount"

        return runCatching {
            File("/proc/sys/kernel/random/boot_id")
                .readText()
                .trim()
                .takeIf(String::isNotBlank)
                ?.let { "kernel:$it" }
        }.getOrNull()
    }
}

internal interface TransferArtifactCipher {
    fun encryptToFile(plaintext: ByteArray, destination: File)
    fun openDecrypted(file: File): InputStream
}

internal class AndroidKeystoreTransferArtifactCipher : TransferArtifactCipher {
    override fun encryptToFile(plaintext: ByteArray, destination: File) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size in 12..32)

        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        runCatching { temporary.delete() }

        BufferedOutputStream(FileOutputStream(temporary)).use { raw ->
            raw.write(FORMAT_VERSION)
            raw.write(iv.size)
            raw.write(iv)
            CipherOutputStream(raw, cipher).use { encrypted ->
                encrypted.write(plaintext)
            }
        }

        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Unable to commit transfer artifact")
        }
    }

    override fun openDecrypted(file: File): InputStream {
        require(file.isFile) { "Transfer artifact is missing" }
        require(
            file.length() <=
                ContractLimits.ARTIFACT_PLAINTEXT_BYTES + MAX_FORMAT_OVERHEAD_BYTES,
        ) { "Encrypted transfer artifact exceeds configured cap" }

        BufferedInputStream(FileInputStream(file)).use { raw ->
            val version = raw.read()
            val ivLength = raw.read()
            require(version == FORMAT_VERSION) { "Unsupported artifact format" }
            require(ivLength in 12..32) { "Invalid artifact IV" }

            val iv = ByteArray(ivLength)
            var offset = 0
            while (offset < iv.size) {
                val read = raw.read(iv, offset, iv.size - offset)
                require(read > 0) { "Truncated artifact IV" }
                offset += read
            }

            val ciphertext = raw.readBytes()
            require(ciphertext.size >= GCM_TAG_BYTES) {
                "Transfer artifact authentication tag is missing"
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, iv),
            )
            val plaintext = cipher.doFinal(ciphertext)
            require(
                plaintext.size.toLong() <=
                    ContractLimits.ARTIFACT_PLAINTEXT_BYTES,
            ) { "Decrypted transfer artifact exceeds configured cap" }
            return ByteArrayInputStream(plaintext)
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "taho_browser_transfer_artifact_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 1
        const val GCM_TAG_BYTES = 16
        const val MAX_FORMAT_OVERHEAD_BYTES = 2L + 32L + GCM_TAG_BYTES
    }
}

data class TransferArtifactHandle(
    val transferId: String,
    val targetPackage: String,
    val accessExpiresElapsedMs: Long,
    val cleanupAfterElapsedMs: Long,
)

internal data class TransferArtifactMetadata(
    val transferId: String,
    val targetPackage: String,
    val createdElapsedMs: Long,
    val accessExpiresElapsedMs: Long,
    val cleanupAfterElapsedMs: Long,
    val bootId: String,
    val consumed: Boolean,
)

internal enum class TransferArtifactClaimFailure {
    MISSING,
    EXPIRED,
    REBOOTED,
    WRONG_PACKAGE,
    ALREADY_CONSUMED,
    CORRUPT,
}

internal sealed interface TransferArtifactClaim {
    data class Granted(val metadata: TransferArtifactMetadata) : TransferArtifactClaim
    data class Denied(val reason: TransferArtifactClaimFailure) : TransferArtifactClaim
}

class SecureTransferArtifactStore internal constructor(
    context: Context,
    private val cipher: TransferArtifactCipher,
    private val clock: TransferArtifactClock,
    private val root: File,
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    constructor(context: Context) : this(
        context = context,
        cipher = AndroidKeystoreTransferArtifactCipher(),
        clock = AndroidTransferArtifactClock(context.applicationContext),
        root = File(context.applicationContext.noBackupFilesDir, ROOT_DIR),
    )

    fun create(
        transferId: String,
        targetPackage: String,
        plaintext: ByteArray,
    ): TransferArtifactHandle = synchronized(lock) {
        require(transferId.isNotBlank())
        require(targetPackage.isNotBlank())
        require(plaintext.size.toLong() <= ContractLimits.ARTIFACT_PLAINTEXT_BYTES) {
            "Transfer artifact exceeds configured cap"
        }

        val bootId = clock.bootId()
            ?: error("Secure transfer unavailable because boot identity is unavailable")
        val now = clock.elapsedRealtimeMs()
        require(now >= 0)

        root.mkdirs()
        deleteFiles(transferId)

        val artifact = artifactFile(transferId)
        cipher.encryptToFile(plaintext, artifact)
        val metadata = TransferArtifactMetadata(
            transferId = transferId,
            targetPackage = targetPackage,
            createdElapsedMs = now,
            accessExpiresElapsedMs = safeAdd(now, ACCESS_TTL_MS),
            cleanupAfterElapsedMs = safeAdd(now, ABANDONED_CLEANUP_MS),
            bootId = bootId,
            consumed = false,
        )
        try {
            writeMetadata(metadata)
        } catch (t: Throwable) {
            artifact.delete()
            throw t
        }

        TransferArtifactHandle(
            transferId = transferId,
            targetPackage = targetPackage,
            accessExpiresElapsedMs = metadata.accessExpiresElapsedMs,
            cleanupAfterElapsedMs = metadata.cleanupAfterElapsedMs,
        )
    }

    internal fun claim(
        transferId: String,
        callerPackages: Set<String>,
    ): TransferArtifactClaim = synchronized(lock) {
        val metadata = readMetadata(transferId)
            ?: return TransferArtifactClaim.Denied(TransferArtifactClaimFailure.MISSING)
        if (!artifactFile(transferId).isFile) {
            return TransferArtifactClaim.Denied(TransferArtifactClaimFailure.MISSING)
        }

        val bootId = clock.bootId()
        if (bootId == null || bootId != metadata.bootId) {
            return TransferArtifactClaim.Denied(TransferArtifactClaimFailure.REBOOTED)
        }

        val now = clock.elapsedRealtimeMs()
        if (now < metadata.createdElapsedMs || now >= metadata.accessExpiresElapsedMs) {
            return TransferArtifactClaim.Denied(TransferArtifactClaimFailure.EXPIRED)
        }
        if (metadata.targetPackage !in callerPackages) {
            return TransferArtifactClaim.Denied(TransferArtifactClaimFailure.WRONG_PACKAGE)
        }
        if (metadata.consumed) {
            return TransferArtifactClaim.Denied(TransferArtifactClaimFailure.ALREADY_CONSUMED)
        }

        val claimed = metadata.copy(consumed = true)
        return try {
            writeMetadata(claimed)
            TransferArtifactClaim.Granted(claimed)
        } catch (_: Throwable) {
            TransferArtifactClaim.Denied(TransferArtifactClaimFailure.CORRUPT)
        }
    }

    internal fun openDecrypted(transferId: String): InputStream =
        cipher.openDecrypted(artifactFile(transferId))

    fun settle(transferId: String) = synchronized(lock) {
        deleteFiles(transferId)
    }

    fun contains(transferId: String): Boolean = synchronized(lock) {
        artifactFile(transferId).isFile && metadataFile(transferId).isFile
    }

    fun sweep(): Int = synchronized(lock) {
        if (!root.isDirectory) return@synchronized 0
        val currentBoot = clock.bootId()
        val now = clock.elapsedRealtimeMs()
        var deleted = 0

        root.listFiles()
            .orEmpty()
            .filter { it.name.endsWith(METADATA_SUFFIX) }
            .forEach { metadataFile ->
                val transferId = metadataFile.name.removeSuffix(METADATA_SUFFIX)
                val metadata = readMetadata(transferId)
                val shouldDelete = metadata == null ||
                    currentBoot == null ||
                    currentBoot != metadata.bootId ||
                    now < metadata.createdElapsedMs ||
                    now >= metadata.cleanupAfterElapsedMs

                if (shouldDelete) {
                    deleteFiles(transferId)
                    deleted += 1
                }
            }

        root.listFiles()
            .orEmpty()
            .filter { it.name.endsWith(ARTIFACT_SUFFIX) }
            .forEach { artifact ->
                val transferId = artifact.name.removeSuffix(ARTIFACT_SUFFIX)
                if (!metadataFile(transferId).isFile) {
                    artifact.delete()
                }
            }

        deleted
    }

    internal fun metadataForTesting(transferId: String): TransferArtifactMetadata? =
        synchronized(lock) { readMetadata(transferId) }

    private fun writeMetadata(value: TransferArtifactMetadata) {
        root.mkdirs()
        val destination = metadataFile(value.transferId)
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        val json = JSONObject().apply {
            put("transferId", value.transferId)
            put("targetPackage", value.targetPackage)
            put("createdElapsedMs", value.createdElapsedMs)
            put("accessExpiresElapsedMs", value.accessExpiresElapsedMs)
            put("cleanupAfterElapsedMs", value.cleanupAfterElapsedMs)
            put("bootId", value.bootId)
            put("consumed", value.consumed)
        }.toString()
        temporary.writeText(json)
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Unable to commit transfer artifact metadata")
        }
    }

    private fun readMetadata(transferId: String): TransferArtifactMetadata? =
        runCatching {
            val json = JSONObject(metadataFile(transferId).readText())
            TransferArtifactMetadata(
                transferId = json.getString("transferId"),
                targetPackage = json.getString("targetPackage"),
                createdElapsedMs = json.getLong("createdElapsedMs"),
                accessExpiresElapsedMs = json.getLong("accessExpiresElapsedMs"),
                cleanupAfterElapsedMs = json.getLong("cleanupAfterElapsedMs"),
                bootId = json.getString("bootId"),
                consumed = json.getBoolean("consumed"),
            ).also {
                require(it.transferId == transferId)
                require(it.createdElapsedMs >= 0)
                require(it.accessExpiresElapsedMs >= it.createdElapsedMs)
                require(it.cleanupAfterElapsedMs >= it.accessExpiresElapsedMs)
            }
        }.getOrNull()

    private fun deleteFiles(transferId: String) {
        artifactFile(transferId).delete()
        metadataFile(transferId).delete()
        File(root, transferId + ARTIFACT_SUFFIX + ".tmp").delete()
        File(root, transferId + METADATA_SUFFIX + ".tmp").delete()
    }

    private fun artifactFile(transferId: String) =
        File(root, safeName(transferId) + ARTIFACT_SUFFIX)

    private fun metadataFile(transferId: String) =
        File(root, safeName(transferId) + METADATA_SUFFIX)

    private fun safeName(value: String): String {
        require(value.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) {
            "Invalid transfer artifact id"
        }
        return value
    }

    private fun safeAdd(value: Long, delta: Long): Long =
        if (Long.MAX_VALUE - value < delta) Long.MAX_VALUE else value + delta

    companion object {
        const val ACCESS_TTL_MS: Long = 15L * 60L * 1000L
        const val ABANDONED_CLEANUP_MS: Long = 60L * 60L * 1000L
        private const val ROOT_DIR = "transfer-artifacts"
        private const val ARTIFACT_SUFFIX = ".artifact"
        private const val METADATA_SUFFIX = ".json"
    }
}
