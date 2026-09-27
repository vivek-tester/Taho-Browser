package app.taho.browser.capture.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestNormalizerPropertyTest {
    @Test
    fun stripsExactTransportHeadersAndSecPrefixes() {
        val headers = listOf(
            header("Host"),
            header("Content-Length"),
            header("Connection"),
            header("Keep-Alive"),
            header("Proxy-Connection"),
            header("TE"),
            header("Trailer"),
            header("Transfer-Encoding"),
            header("Upgrade"),
            header("Sec-Fetch-Site"),
            header("Sec-CH-UA"),
            header("Sec-CH-UA-Platform"),
            header("Accept"),
            header("X-Request-ID"),
            header("X-New-App-Header"),
            header("DNT"),
        )

        val normalized = RequestNormalizer.normalizeHeaders(headers)
        val names = normalized.map { it.name }

        assertEquals(
            listOf("Accept", "X-Request-ID", "X-New-App-Header", "DNT"),
            names,
        )
    }

    @Test
    fun unrecognisedHeadersAreRetainedByDefault() {
        val names = listOf(
            "X-Future-Feature",
            "X-Experimental-App-Header",
            "Content-Type",
            "Authorization",
            "X-API-Key",
            "X-CSRF-Token",
        )

        val normalized = RequestNormalizer.normalizeHeaders(names.map(::header))

        assertEquals(names, normalized.map { it.name })
    }

    @Test
    fun normalizationIsIdempotentAcrossRandomHeaderSets() {
        val random = Random(20260927)
        repeat(2_000) {
            val headers = buildList {
                repeat(random.nextInt(0, 40)) {
                    add(
                        header(
                            if (random.nextBoolean()) {
                                KNOWN[random.nextInt(KNOWN.size)]
                            } else {
                                "X-Random-" + random.nextInt().toUInt().toString(16)
                            },
                        ),
                    )
                }
            }

            val once = RequestNormalizer.normalize(
                NormalizableRequest(
                    url = "https://example.test/api?token=secret",
                    method = "POST",
                    headers = headers,
                ),
            )
            val twice = RequestNormalizer.normalize(once)

            assertEquals(once, twice)
            assertEquals(RequestNormalizer.VERSION, twice.normalizerVersion)
        }
    }

    @Test
    fun protectedHeaderValueNeverContainsPlaintext() {
        val token = "do-not-leak-this-token"
        val header = NormalizedHeader(
            name = "Authorization",
            value = NormalizedHeaderValue.Protected(
                SecretRef("s-1", SecretCategory.BEARER_TOKEN),
            ),
        )

        val request = RequestNormalizer.normalize(
            NormalizableRequest(
                url = "https://example.test/",
                method = "GET",
                headers = listOf(header),
            ),
        )

        assertFalse(request.toString().contains(token))
        assertTrue(request.toString().contains("BEARER_TOKEN"))
    }

    private fun header(name: String): NormalizedHeader =
        NormalizedHeader(
            name = name,
            value = NormalizedHeaderValue.Public("value"),
        )

    companion object {
        private val KNOWN = listOf(
            "Host",
            "Content-Length",
            "Connection",
            "Keep-Alive",
            "Proxy-Connection",
            "TE",
            "Trailer",
            "Transfer-Encoding",
            "Upgrade",
            "Sec-Fetch-Mode",
            "Sec-CH-UA",
            "Sec-CH-UA-Mobile",
            "Accept",
            "Content-Type",
            "DNT",
        )
    }
}
