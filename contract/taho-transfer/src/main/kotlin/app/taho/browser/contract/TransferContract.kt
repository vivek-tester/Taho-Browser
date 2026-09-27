package app.taho.browser.contract

const val TAHO_REQUEST_TRANSFER_SCHEMA = "taho.request-transfer"
const val TAHO_REQUEST_TRANSFER_VERSION = 1
const val TAHO_REQUEST_TRANSFER_MIN_READER_VERSION = 1

object ContractLimits {
    const val DIRECT_ENVELOPE_UTF8_BYTES: Long = 256L * 1024L
    const val CAPTURED_BODY_BYTES: Long = 8L * 1024L * 1024L
    const val ARTIFACT_PLAINTEXT_BYTES: Long = 32L * 1024L * 1024L
}

enum class ObservationSource { ENGINE, PAGE }
enum class SecretPolicy { PARAMETERIZE, MASK, EXPLICIT }
enum class BodyRepresentation { TEXT, JSON, FORM, MULTIPART, GRAPHQL, BINARY }
enum class BodyEncoding { UTF8, BASE64, FILE_URI }
enum class Completeness { COMPLETE, PARTIAL, TRUNCATED, UNAVAILABLE, NOT_APPLICABLE }

data class TransferSource(
    val product: String = "taho-browser",
    val appVersion: String,
    val engine: String,
    val captureSessionId: String,
    val tabId: String?,
    val observation: ObservationSource,
)

data class TransferQueryParameter(
    val name: String,
    val value: String,
    val redacted: Boolean,
    val secretCategory: String? = null,
    val policy: SecretPolicy? = null,
)

data class TransferHeader(
    val name: String,
    val value: String,
    val redacted: Boolean,
    val secretCategory: String? = null,
    val policy: SecretPolicy? = null,
)

data class MultipartPart(
    val name: String,
    val filename: String? = null,
    val contentType: String? = null,
    val encoding: BodyEncoding,
    val value: String? = null,
    val uri: String? = null,
    val size: Long,
    val truncated: Boolean,
)

data class TransferBody(
    val representation: BodyRepresentation,
    val contentType: String?,
    val charset: String?,
    val encoding: BodyEncoding,
    val size: Long,
    val declaredSize: Long?,
    val truncated: Boolean,
    val completeness: Completeness,
    val content: String? = null,
    val uri: String? = null,
    val parts: List<MultipartPart> = emptyList(),
)

data class TransferRequest(
    val id: String,
    val method: String,
    val url: String,
    val query: List<TransferQueryParameter> = emptyList(),
    val headers: List<TransferHeader> = emptyList(),
    val body: TransferBody? = null,
)

data class TransferTiming(
    val ttfbMs: Long? = null,
    val totalMs: Long? = null,
)

data class CaptureCompleteness(
    val requestUrl: Completeness,
    val requestHeaders: Completeness,
    val requestBody: Completeness,
    val responseHeaders: Completeness,
    val responseBody: Completeness,
    val timing: Completeness,
    val tlsInfo: Completeness,
)

data class TransferCapture(
    val timestamp: Long,
    val durationMs: Long?,
    val status: Int?,
    val statusText: String?,
    val initiator: String?,
    val timing: TransferTiming?,
    val completeness: CaptureCompleteness,
)

data class SecretFinding(
    val location: String,
    val category: String,
    val policy: SecretPolicy,
)

data class TransferSecurity(
    val secretPolicy: SecretPolicy,
    val containsSensitiveData: Boolean,
    val secretCount: Int,
    val fromPrivateSession: Boolean,
    val reviewRequired: Boolean,
    val findings: List<SecretFinding> = emptyList(),
)

data class SafeProvenance(
    val normalizerVersion: String,
    val captureEngineVersion: String,
    val capturedAt: Long,
    val redirectCount: Int,
)

data class RequestTransferV1(
    val transferId: String,
    val issuedAt: Long,
    val source: TransferSource,
    val request: TransferRequest,
    val capture: TransferCapture,
    val security: TransferSecurity,
    val provenance: SafeProvenance,
    val schema: String = TAHO_REQUEST_TRANSFER_SCHEMA,
    val version: Int = TAHO_REQUEST_TRANSFER_VERSION,
    val minimumReaderVersion: Int = TAHO_REQUEST_TRANSFER_MIN_READER_VERSION,
)

enum class ReceiptResult { IMPORTED, REJECTED, DUPLICATE, UNSUPPORTED, ERROR }

enum class TransferErrorCode {
    TAHO_TRANSFER_UNSUPPORTED_VERSION,
    TAHO_TRANSFER_INVALID_SCHEMA,
    TAHO_TRANSFER_TOO_LARGE,
    TAHO_TRANSFER_ACCESS_DENIED,
    TAHO_TRANSFER_TARGET_UNAVAILABLE,
    TAHO_TRANSFER_URI_EXPIRED,
    TAHO_TRANSFER_IMPORT_FAILED,
    TAHO_TRANSFER_CANCELLED,
}

data class TransferReceiptV1(
    val transferId: String,
    val result: ReceiptResult,
    val requestId: String?,
    val importedAt: Long?,
    val errorCode: TransferErrorCode?,
)
