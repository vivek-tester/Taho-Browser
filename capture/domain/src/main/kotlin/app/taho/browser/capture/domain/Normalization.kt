package app.taho.browser.capture.domain

import java.util.Locale

sealed interface NormalizedHeaderValue {
    data class Public(val value: String) : NormalizedHeaderValue

    data class Protected(val ref: SecretRef) : NormalizedHeaderValue {
        override fun toString(): String =
            "Protected(ref=" + ref.id + ", category=" + ref.category + ")"
    }
}

data class NormalizedHeader(
    val name: String,
    val value: NormalizedHeaderValue,
)

data class NormalizableRequest(
    val url: String,
    val method: String,
    val headers: List<NormalizedHeader>,
)

data class NormalizedRequest(
    val url: String,
    val method: String,
    val headers: List<NormalizedHeader>,
    val normalizerVersion: String,
)

/**
 * Pure versioned request normalizer.
 *
 * Secret detection/classification happens before this boundary. This component
 * only accepts public values or opaque SecretRef values and never raw secrets.
 */
object RequestNormalizer {
    const val VERSION: String = "request/1.0.0"

    fun normalize(input: NormalizableRequest): NormalizedRequest =
        NormalizedRequest(
            url = input.url,
            method = input.method,
            headers = normalizeHeaders(input.headers),
            normalizerVersion = VERSION,
        )

    fun normalize(input: NormalizedRequest): NormalizedRequest {
        if (
            input.normalizerVersion == VERSION &&
            input.headers.none { shouldStrip(it.name) }
        ) {
            return input
        }

        return input.copy(
            headers = normalizeHeaders(input.headers),
            normalizerVersion = VERSION,
        )
    }

    fun normalizeHeaders(headers: List<NormalizedHeader>): List<NormalizedHeader> =
        headers.filterNot { shouldStrip(it.name) }

    fun shouldStrip(name: String): Boolean {
        val normalized = name.trim().lowercase(Locale.ROOT)
        if (normalized in EXACT_TRANSPORT_HEADERS) return true
        if (normalized.startsWith("sec-fetch-")) return true
        if (normalized == "sec-ch-ua") return true
        if (normalized.startsWith("sec-ch-ua-")) return true

        // The source architecture marks DNT removal as optional. Version 1
        // deliberately retains DNT so this rule set has one deterministic meaning.
        return false
    }

    private val EXACT_TRANSPORT_HEADERS = setOf(
        "host",
        "content-length",
        "connection",
        "keep-alive",
        "proxy-connection",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
    )
}
