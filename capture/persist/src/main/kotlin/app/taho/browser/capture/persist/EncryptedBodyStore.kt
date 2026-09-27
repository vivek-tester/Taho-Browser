package app.taho.browser.capture.persist

import android.content.Context
import app.taho.browser.capture.domain.StorageDegradationReason
import java.io.File
import java.nio.ByteBuffer

sealed interface StoredBodyPayload {
    data class Inline(val encrypted: EncryptedCaptureBytes) : StoredBodyPayload
    data class FileBacked(val storageRef: String) : StoredBodyPayload
    data object None : StoredBodyPayload
}

sealed interface BodyStoreResult<out T> {
    data class Success<T>(val value: T) : BodyStoreResult<T>
    data class Degraded(val reason: StorageDegradationReason) : BodyStoreResult<Nothing>
}

class EncryptedBodyStore(
    context: Context,
    private val cipher: CaptureCipher,
    private val inlineLimitBytes: Int = 64 * 1024,
) {
    private val root = File(context.filesDir, "capture-bodies").apply { mkdirs() }

    fun write(id: String, bytes: ByteArray?): BodyStoreResult<StoredBodyPayload> {
        if (bytes == null) return BodyStoreResult.Success(StoredBodyPayload.None)
        if (bytes.size <= inlineLimitBytes) {
            return encrypted(bytes) { StoredBodyPayload.Inline(it) }
        }

        if (root.usableSpace in 1 until (bytes.size.toLong() * 2L)) {
            return BodyStoreResult.Degraded(StorageDegradationReason.LOW_STORAGE)
        }

        return encrypted(bytes) { encrypted ->
            val file = File(root, safeName(id) + ".bin")
            val buffer = ByteBuffer.allocate(
                Int.SIZE_BYTES + encrypted.iv.size + encrypted.ciphertext.size,
            )
            buffer.putInt(encrypted.iv.size)
            buffer.put(encrypted.iv)
            buffer.put(encrypted.ciphertext)
            file.writeBytes(buffer.array())
            StoredBodyPayload.FileBacked(file.name)
        }
    }

    fun read(
        inlineIv: ByteArray?,
        inlineCiphertext: ByteArray?,
        storageRef: String?,
    ): BodyStoreResult<ByteArray?> {
        if (storageRef != null) {
            val file = File(root, safeName(storageRef))
            if (!file.exists()) {
                return BodyStoreResult.Degraded(StorageDegradationReason.BODY_FILE_MISSING)
            }
            return try {
                val raw = file.readBytes()
                val buffer = ByteBuffer.wrap(raw)
                val ivSize = buffer.int
                if (ivSize <= 0 || ivSize > 64 || buffer.remaining() <= ivSize) {
                    return BodyStoreResult.Degraded(StorageDegradationReason.CORRUPT_RECORD)
                }
                val iv = ByteArray(ivSize).also(buffer::get)
                val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
                BodyStoreResult.Success(
                    cipher.decrypt(EncryptedCaptureBytes(iv, ciphertext)),
                )
            } catch (_: CaptureKeyInvalidatedException) {
                BodyStoreResult.Degraded(StorageDegradationReason.KEY_INVALIDATED)
            } catch (_: Throwable) {
                BodyStoreResult.Degraded(StorageDegradationReason.CORRUPT_RECORD)
            }
        }

        if (inlineIv == null && inlineCiphertext == null) {
            return BodyStoreResult.Success(null)
        }
        if (inlineIv == null || inlineCiphertext == null) {
            return BodyStoreResult.Degraded(StorageDegradationReason.CORRUPT_RECORD)
        }
        return try {
            BodyStoreResult.Success(
                cipher.decrypt(EncryptedCaptureBytes(inlineIv, inlineCiphertext)),
            )
        } catch (_: CaptureKeyInvalidatedException) {
            BodyStoreResult.Degraded(StorageDegradationReason.KEY_INVALIDATED)
        } catch (_: Throwable) {
            BodyStoreResult.Degraded(StorageDegradationReason.CORRUPT_RECORD)
        }
    }

    fun delete(storageRef: String?) {
        if (storageRef == null) return
        File(root, safeName(storageRef)).delete()
    }

    fun clear() {
        root.listFiles()?.forEach(File::delete)
    }

    fun sweepKnown(knownRefs: Set<String>): Int {
        var deleted = 0
        root.listFiles()?.forEach { file ->
            if (file.name !in knownRefs && file.delete()) deleted += 1
        }
        return deleted
    }

    private inline fun <T> encrypted(
        bytes: ByteArray,
        transform: (EncryptedCaptureBytes) -> T,
    ): BodyStoreResult<T> =
        try {
            BodyStoreResult.Success(transform(cipher.encrypt(bytes)))
        } catch (_: CaptureKeyInvalidatedException) {
            BodyStoreResult.Degraded(StorageDegradationReason.KEY_INVALIDATED)
        } catch (_: Throwable) {
            BodyStoreResult.Degraded(StorageDegradationReason.CORRUPT_RECORD)
        }

    private fun safeName(value: String): String =
        value.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
}
