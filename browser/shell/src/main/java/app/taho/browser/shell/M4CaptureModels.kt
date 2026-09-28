package app.taho.browser.shell

/**
 * Safe UI projection models for captured requests. UI receives only these
 * masked summaries — it cannot render a secret it was never given
 * (Data Model invariant I1).
 */

enum class M4SecretPolicyUi {
    PARAMETERIZE,
    MASK,
    EXPLICIT,
}

/** PRD §66 transfer status model, projected for UI. */
enum class M7TransferPhaseUi {
    NOT_STARTED,
    PREPARING,
    TRANSFERRING,
    RECEIVED,
    FAILED,
}

enum class M4CompletenessUi {
    COMPLETE,
    PARTIAL,
    TRUNCATED,
    UNAVAILABLE,
    NOT_APPLICABLE,
}

data class M4CaptureHeaderUiState(
    val name: String,
    val displayValue: String,
    val sensitive: Boolean,
    val secretCategory: String? = null,
) {
    init {
        require(name.isNotBlank()) { "header name must not be blank" }
        if (sensitive) {
            require(
                displayValue.contains("•") ||
                    displayValue.startsWith("{{"),
            ) {
                "Sensitive UI headers must already be masked or parameterized."
            }
        }
    }
}

data class M4CaptureRequestUiState(
    val id: String,
    val method: String,
    val url: String,
    val status: Int?,
    val durationMs: Long?,
    val category: String = "API",
    val headers: List<M4CaptureHeaderUiState> = emptyList(),
    val requestUrlCompleteness: M4CompletenessUi = M4CompletenessUi.COMPLETE,
    val requestHeadersCompleteness: M4CompletenessUi = M4CompletenessUi.UNAVAILABLE,
    val requestBodyCompleteness: M4CompletenessUi,
    val responseHeadersCompleteness: M4CompletenessUi = M4CompletenessUi.UNAVAILABLE,
    val responseBodyCompleteness: M4CompletenessUi,
    val timingCompleteness: M4CompletenessUi = M4CompletenessUi.UNAVAILABLE,
    val tlsCompleteness: M4CompletenessUi = M4CompletenessUi.UNAVAILABLE,
    val bodyRepresentation: String? = null,
    val bodyLimitation: String? = null,
    val sensitiveCount: Int = 0,
    val fromPrivateSession: Boolean = false,
    val transferBlockedReason: String? = null,
    val explicitPolicyAllowed: Boolean = false,
    val relevanceCategory: String = category,
    val relevantByDefault: Boolean = true,
    val captureSessionId: String? = null,
    val tabId: String? = null,
    val capturedAtEpochMs: Long? = null,
    val sourceProduct: String = "taho-browser",
    val sourceVersion: String? = null,
    val captureEngineVersion: String? = null,
    val normalizerVersion: String? = null,
    val observationSource: String? = null,
    val redirectCount: Int = 0,
    val transactionState: String? = null,
    val requestBodyCapturedBytes: Long? = null,
    val requestBodyDeclaredBytes: Long? = null,
    val safeBodyPreview: String? = null,
    val safeBodyPreviewTruncated: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "request id must not be blank" }
        require(method.isNotBlank()) { "method must not be blank" }
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "capture UI requires an absolute HTTP(S) URL"
        }
        require(sensitiveCount >= 0) { "sensitiveCount must be non-negative" }
    }
}
