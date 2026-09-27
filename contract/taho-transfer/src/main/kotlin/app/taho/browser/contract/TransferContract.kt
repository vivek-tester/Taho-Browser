package app.taho.browser.contract

const val TAHO_REQUEST_TRANSFER_SCHEMA = "taho.request-transfer"
const val TAHO_REQUEST_TRANSFER_VERSION = 1

enum class CredentialPolicy {
    PARAMETERIZE,
    MASK,
    EXPLICIT,
}

enum class ValueProtection {
    INCLUDED,
    PARAMETERIZED,
    MASKED,
    OMITTED,
    EXPLICIT,
}

data class TransferHeader(
    val name: String,
    val value: String,
    val protection: ValueProtection,
)

data class TransferBody(
    val mediaType: String?,
    val content: ByteArray,
    val complete: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is TransferBody &&
            mediaType == other.mediaType &&
            content.contentEquals(other.content) &&
            complete == other.complete

    override fun hashCode(): Int =
        31 * (31 * (mediaType?.hashCode() ?: 0) + content.contentHashCode()) +
            complete.hashCode()
}

data class SafeProvenance(
    val sourceProduct: String,
    val sourceVersion: String,
    val captureSessionId: String,
    val tabId: String?,
    val capturedAtEpochMs: Long,
    val completeness: String,
)

data class RequestTransferV1(
    val transferId: String,
    val method: String,
    val url: String,
    val headers: List<TransferHeader>,
    val body: TransferBody?,
    val credentialPolicy: CredentialPolicy,
    val provenance: SafeProvenance,
    val schema: String = TAHO_REQUEST_TRANSFER_SCHEMA,
    val version: Int = TAHO_REQUEST_TRANSFER_VERSION,
)

enum class ReceiptStatus {
    RECEIVED,
    REJECTED,
    DUPLICATE,
    EXPIRED,
}

data class TransferReceiptV1(
    val transferId: String,
    val status: ReceiptStatus,
    val importedRequestId: String? = null,
    val message: String? = null,
)
