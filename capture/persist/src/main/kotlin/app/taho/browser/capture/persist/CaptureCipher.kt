package app.taho.browser.capture.persist

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedCaptureBytes(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

class CaptureKeyInvalidatedException(cause: Throwable? = null) :
    IllegalStateException("capture encryption key is invalid", cause)

interface CaptureCipher {
    fun encrypt(plaintext: ByteArray): EncryptedCaptureBytes
    fun decrypt(value: EncryptedCaptureBytes): ByteArray
}

class AndroidKeystoreCaptureCipher(
    private val alias: String = "taho_capture_master_v1",
) : CaptureCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedCaptureBytes =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            EncryptedCaptureBytes(
                iv = cipher.iv.copyOf(),
                ciphertext = cipher.doFinal(plaintext),
            )
        } catch (error: Throwable) {
            throw mapKeyError(error)
        }

    override fun decrypt(value: EncryptedCaptureBytes): ByteArray =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, value.iv),
            )
            cipher.doFinal(value.ciphertext)
        } catch (error: Throwable) {
            throw mapKeyError(error)
        }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun mapKeyError(error: Throwable): Throwable =
        when (error) {
            is AEADBadTagException,
            is UnrecoverableKeyException -> CaptureKeyInvalidatedException(error)
            else -> error
        }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
