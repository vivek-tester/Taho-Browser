package app.taho.browser.capture.domain

import java.net.URI
import java.time.Instant

data class CaptureSessionId(val value: String)
data class TahoTabId(val value: String)
data class TransactionId(val value: String)

enum class ObservationSource {
    ENGINE,
    PAGE,
}

data class Provenance(
    val sourceProduct: String,
    val sourceVersion: String,
    val captureSessionId: CaptureSessionId,
    val tahoTabId: TahoTabId?,
    val transactionId: TransactionId,
    val capturedAt: Instant,
    val originReportedAt: Instant?,
    val originalUrl: String,
    val originalMethod: String,
    val normalizerVersion: String,
    val secretPolicyApplied: SecretPolicy?,
    val observation: ObservationSource,
) {
    init {
        require(sourceProduct.isNotBlank()) { "sourceProduct must not be blank" }
        require(sourceVersion.isNotBlank()) { "sourceVersion must not be blank" }
        require(captureSessionId.value.isNotBlank()) { "captureSessionId must not be blank" }
        require(transactionId.value.isNotBlank()) { "transactionId must not be blank" }
        require(originalUrl.isNotBlank()) { "originalUrl must not be blank" }
        require(originalMethod.isNotBlank()) { "originalMethod must not be blank" }
        require(normalizerVersion.isNotBlank()) { "normalizerVersion must not be blank" }
    }

    /**
     * Safe for diagnostics/UI metadata. Userinfo is removed, query values are
     * replaced, and fragments are omitted. The immutable original remains in
     * provenance for encrypted persistence/explicit transfer policy.
     */
    fun redactedOriginalUrl(): String = ProvenanceUrlRedactor.redact(originalUrl)

    override fun toString(): String =
        "Provenance(" +
            "sourceProduct=" + sourceProduct +
            ", sourceVersion=" + sourceVersion +
            ", captureSessionId=" + captureSessionId +
            ", tahoTabId=" + tahoTabId +
            ", transactionId=" + transactionId +
            ", capturedAt=" + capturedAt +
            ", originReportedAt=" + originReportedAt +
            ", originalUrl=" + redactedOriginalUrl() +
            ", originalMethod=" + originalMethod +
            ", normalizerVersion=" + normalizerVersion +
            ", secretPolicyApplied=" + secretPolicyApplied +
            ", observation=" + observation +
            ")"
}

object ProvenanceFactory {
    const val SOURCE_PRODUCT: String = "taho-browser"

    fun create(
        sourceVersion: String,
        captureSessionId: CaptureSessionId,
        tahoTabId: TahoTabId?,
        transactionId: TransactionId,
        capturedAt: Instant,
        originReportedAt: Instant?,
        originalUrl: String,
        originalMethod: String,
        secretPolicyApplied: SecretPolicy?,
        observation: ObservationSource,
        normalizerVersion: String = RequestNormalizer.VERSION,
    ): Provenance =
        Provenance(
            sourceProduct = SOURCE_PRODUCT,
            sourceVersion = sourceVersion,
            captureSessionId = captureSessionId,
            tahoTabId = tahoTabId,
            transactionId = transactionId,
            capturedAt = capturedAt,
            originReportedAt = originReportedAt,
            originalUrl = originalUrl,
            originalMethod = originalMethod,
            normalizerVersion = normalizerVersion,
            secretPolicyApplied = secretPolicyApplied,
            observation = observation,
        )
}

object ProvenanceUrlRedactor {
    fun redact(raw: String): String =
        runCatching {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            if (scheme == null || host == null) return@runCatching "<redacted-url>"

            buildString {
                append(scheme)
                append("://")
                append(host.lowercase())
                if (uri.port >= 0) {
                    append(':')
                    append(uri.port)
                }

                val path = uri.rawPath
                if (!path.isNullOrEmpty()) append(path)

                val query = uri.rawQuery
                if (!query.isNullOrEmpty()) {
                    append('?')
                    append(
                        query
                            .split('&')
                            .joinToString("&") { pair ->
                                val key = pair.substringBefore('=')
                                if (key.isBlank()) "•••" else key + "=•••"
                            },
                    )
                }
            }
        }.getOrDefault("<redacted-url>")
}
