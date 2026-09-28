package app.taho.browser.sync

import java.io.Serializable

object TahoSyncProtocol {
    const val VERSION = 1
    const val AUTH_HEADER = "Authorization"
    const val BEARER_PREFIX = "Bearer "
    const val MAX_OPAQUE_STATE_BYTES = 8 * 1024 * 1024
    const val MAX_SEND_TAB_BYTES = 64 * 1024
    const val MAX_DEVICE_NAME_CHARS = 80
}

data class SyncAccountSession(
    val accountId: String,
    val token: String,
    val syncSaltBase64: String,
) : Serializable

data class SyncDevice(
    val id: String,
    val name: String,
    val type: String,
    val lastSeenEpochMs: Long,
) : Serializable

data class OpaqueSyncState(
    val revision: Long,
    val updatedAtEpochMs: Long,
    val ciphertextBase64: String,
) : Serializable

data class SendTabEnvelope(
    val id: String,
    val fromDeviceId: String,
    val targetDeviceId: String,
    val createdAtEpochMs: Long,
    val ciphertextBase64: String,
) : Serializable

object SyncInputValidation {
    fun validEmail(raw: String): Boolean {
        val value = raw.trim()
        return value.length in 3..254 &&
            '@' in value &&
            value.substringAfter('@').contains('.')
    }

    fun validPassword(raw: CharSequence): Boolean =
        raw.length in 10..256

    fun validDeviceName(raw: String): Boolean =
        raw.trim().length in 1..TahoSyncProtocol.MAX_DEVICE_NAME_CHARS

    fun validOpaqueBase64(raw: String, maxDecodedBytes: Int): Boolean {
        if (raw.isBlank()) return false
        val estimated = (raw.length.toLong() * 3L) / 4L
        return estimated <= maxDecodedBytes &&
            raw.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_' }
    }
}
