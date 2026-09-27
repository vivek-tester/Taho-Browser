package app.taho.browser.capture.domain

enum class CaptureState { OBSERVING, CAPTURING, PAUSED, LIMITED, OFF, ERROR }

enum class Completeness { COMPLETE, PARTIAL, TRUNCATED, UNAVAILABLE, NOT_APPLICABLE }

data class RuntimeGeneration(val value: String)
data class BrowserTabId(val value: String)
data class RequestId(val value: String)

data class OriginIdentity(
    val runtimeGeneration: RuntimeGeneration,
    val browserTabId: BrowserTabId?,
    val requestId: RequestId,
)

data class TransactionSummary(
    val method: String,
    val host: String,
    val path: String,
    val statusCode: Int?,
    val durationMs: Long?,
    val requestBody: Completeness,
    val hasSensitiveData: Boolean,
)
