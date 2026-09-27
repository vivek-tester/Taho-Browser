package app.taho.browser.capture.domain

import java.util.Arrays

enum class CaptureSessionKind { EPHEMERAL, WORKSPACE, PRIVATE }
enum class CaptureSessionLifecycle { ACTIVE, PAUSED, CLOSED }
enum class RetentionPolicy { SESSION_ONLY, KEEP_UNTIL_DELETED, PRIVATE }

enum class DurableTransactionState {
    STARTED,
    COMPLETED,
    PARTIAL,
    FAILED,
}

enum class DurableBodyRepresentation {
    TEXT,
    JSON,
    FORM,
    MULTIPART,
    GRAPHQL,
    BINARY,
}

enum class DurableBodyEncoding {
    UTF8,
    BASE64,
    BINARY,
}

enum class StorageDegradationReason {
    KEY_INVALIDATED,
    LOW_STORAGE,
    BODY_FILE_MISSING,
    KEY_ROTATION,
    CORRUPT_RECORD,
    DATABASE_CORRUPT,
}

class SensitivePayload private constructor(bytes: ByteArray) : AutoCloseable {
    private val bytes = bytes.copyOf()
    private var closed = false

    val size: Int get() = bytes.size

    fun copyBytesForPersistence(): ByteArray {
        check(!closed) { "SensitivePayload has been cleared" }
        return bytes.copyOf()
    }

    override fun close() {
        if (closed) return
        Arrays.fill(bytes, 0)
        closed = true
    }

    override fun toString(): String = "SensitivePayload(size=$size, redacted)"

    companion object {
        fun copyOf(bytes: ByteArray): SensitivePayload = SensitivePayload(bytes)
        fun utf8(value: String): SensitivePayload = SensitivePayload(value.encodeToByteArray())
    }
}

data class DurableCaptureSession(
    val id: String,
    val kind: CaptureSessionKind,
    val lifecycle: CaptureSessionLifecycle,
    val label: String? = null,
    val targetHost: String? = null,
    val retention: RetentionPolicy,
    val createdAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(createdAtEpochMs >= 0)
        require(closedAtEpochMs == null || closedAtEpochMs >= createdAtEpochMs)
        if (kind == CaptureSessionKind.PRIVATE) {
            require(retention == RetentionPolicy.PRIVATE)
        }
    }
}

data class DurableQueryValue(
    val name: String,
    val displayValue: String,
    val redacted: Boolean,
)

data class DurableHeaderValue(
    val name: String,
    val displayValue: String,
    val redacted: Boolean,
)

data class DurableRedirectHop(
    val statusCode: Int,
    val fromUrl: String,
    val toUrl: String,
    val atEpochMs: Long?,
) {
    init {
        require(statusCode in 300..399)
        require(fromUrl.startsWith("http://") || fromUrl.startsWith("https://"))
        require(toUrl.startsWith("http://") || toUrl.startsWith("https://"))
        require(atEpochMs == null || atEpochMs >= 0)
    }
}

class DurableBody(
    val representation: DurableBodyRepresentation,
    val encoding: DurableBodyEncoding,
    val contentType: String?,
    val charset: String?,
    val declaredSize: Long?,
    val capturedSize: Long,
    val truncated: Boolean,
    val completeness: Completeness,
    val payload: SensitivePayload?,
    val limitation: String? = null,
) : AutoCloseable {
    init {
        require(declaredSize == null || declaredSize >= 0)
        require(capturedSize >= 0)
        if (truncated) require(completeness == Completeness.TRUNCATED)
        // PRIVATE retention may keep complete metadata while deliberately
        // omitting the secret-bearing payload at write time.
    }

    override fun close() {
        payload?.close()
    }

    override fun toString(): String =
        "DurableBody(representation=$representation,encoding=$encoding," +
            "capturedSize=$capturedSize,truncated=$truncated,completeness=$completeness," +
            "payload=<redacted>,limitation=$limitation)"
}

class DurableTransaction(
    val id: String,
    val captureSessionId: String,
    val tahoTabId: String?,
    val attribution: String,
    val extTabId: Int?,
    val engineRequestId: String?,
    val method: String,
    val url: String,
    val query: List<DurableQueryValue>,
    val requestHeaders: List<DurableHeaderValue>,
    val encryptedRequestHeadersSource: SensitivePayload?,
    val requestBody: DurableBody?,
    val status: Int?,
    val statusText: String?,
    val responseHeaders: List<DurableHeaderValue>,
    val encryptedResponseHeadersSource: SensitivePayload?,
    val responseBody: DurableBody?,
    val redirects: List<DurableRedirectHop> = emptyList(),
    val state: DurableTransactionState,
    val relevance: Relevance,
    val observationSource: ObservationSource,
    val provenanceJson: String,
    val normalizerVersion: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val isPrivate: Boolean,
) : AutoCloseable {
    init {
        require(id.isNotBlank())
        require(captureSessionId.isNotBlank())
        require(method.isNotBlank())
        require(url.startsWith("https://") || url.startsWith("http://"))
        require(createdAtEpochMs >= 0)
        require(updatedAtEpochMs >= createdAtEpochMs)
        if (isPrivate) {
            require(encryptedRequestHeadersSource == null)
            require(encryptedResponseHeadersSource == null)
            require(requestBody?.payload == null)
            require(responseBody?.payload == null)
        }
    }

    override fun close() {
        encryptedRequestHeadersSource?.close()
        encryptedResponseHeadersSource?.close()
        requestBody?.close()
        responseBody?.close()
    }

    override fun toString(): String =
        "DurableTransaction(id=$id,captureSessionId=$captureSessionId,tahoTabId=$tahoTabId," +
            "method=$method,url=<redacted>,state=$state,relevance=$relevance," +
            "requestHeaders=<redacted>,responseHeaders=<redacted>,bodies=<redacted>)"
}

data class CaptureQuery(
    val search: String? = null,
    val tabId: String? = null,
    val relevance: Set<RelevanceCategory> = emptySet(),
    val methods: Set<String> = emptySet(),
    val states: Set<DurableTransactionState> = emptySet(),
    val limit: Int = 200,
) {
    init {
        require(limit in 1..2_000)
    }
}

data class DurableTransactionSummary(
    val id: String,
    val captureSessionId: String,
    val tahoTabId: String?,
    val method: String,
    val url: String,
    val status: Int?,
    val state: DurableTransactionState,
    val relevance: Relevance,
    val requestBodyCompleteness: Completeness,
    val responseBodyCompleteness: Completeness,
    val requestBodyRepresentation: DurableBodyRepresentation? = null,
    val bodyLimitation: String? = null,
    val createdAtEpochMs: Long,
    val degradedReason: StorageDegradationReason? = null,
)

data class RetentionSweepResult(
    val deletedSessions: Int,
    val deletedTransactions: Int,
    val deletedBodies: Int,
)

sealed interface CaptureRepositoryResult<out T> {
    data class Success<T>(val value: T) : CaptureRepositoryResult<T>
    data class Degraded(
        val reason: StorageDegradationReason,
        val detail: String? = null,
    ) : CaptureRepositoryResult<Nothing>
}

interface CaptureRepository {
    fun upsertSession(session: DurableCaptureSession): CaptureRepositoryResult<Unit>
    fun commit(transaction: DurableTransaction): CaptureRepositoryResult<Unit>
    fun list(query: CaptureQuery): CaptureRepositoryResult<List<DurableTransactionSummary>>
    fun markInterruptedPartial(nowEpochMs: Long): CaptureRepositoryResult<Int>
    fun closeAbandonedActiveSessions(
        currentSessionId: String,
        nowEpochMs: Long,
    ): CaptureRepositoryResult<Int>
    fun closeSession(sessionId: String, closedAtEpochMs: Long): CaptureRepositoryResult<Unit>
    fun clearCaptureData(): CaptureRepositoryResult<Unit>
    fun sweepRetention(nowEpochMs: Long): CaptureRepositoryResult<RetentionSweepResult>
}
