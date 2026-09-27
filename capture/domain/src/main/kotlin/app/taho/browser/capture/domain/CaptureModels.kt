package app.taho.browser.capture.domain

enum class CaptureState { OBSERVING, CAPTURING, PAUSED, LIMITED, OFF, ERROR }

enum class Completeness { COMPLETE, PARTIAL, TRUNCATED, UNAVAILABLE, NOT_APPLICABLE }

data class RuntimeGeneration(val value: String)
data class BrowserTabId(val value: String)
data class RequestId(val value: String)

data class RequestIdentity(
    val runtimeGeneration: RuntimeGeneration,
    val requestId: RequestId,
)

sealed interface Attribution {
    data object Unresolved : Attribution
    data class Resolved(val browserTabId: BrowserTabId) : Attribution
}

enum class BindingStatus { ATTACHED, DETACHED }

data class FieldEvidence(
    val completeness: Completeness,
    val capturedBytes: Long? = null,
    val declaredBytes: Long? = null,
    val capBytes: Long? = null,
) {
    init {
        require(capturedBytes == null || capturedBytes >= 0) { "capturedBytes must be non-negative" }
        require(declaredBytes == null || declaredBytes >= 0) { "declaredBytes must be non-negative" }
        require(capBytes == null || capBytes >= 0) { "capBytes must be non-negative" }
        if (completeness == Completeness.TRUNCATED) {
            require(capBytes != null) { "truncated evidence must record the cap that caused it" }
        }
    }
}

data class TransactionSummary(
    val method: String,
    val host: String,
    val path: String,
    val statusCode: Int?,
    val durationMs: Long?,
    val requestBody: Completeness,
    val hasSensitiveData: Boolean,
)
