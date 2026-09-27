package app.taho.browser.capture.domain

import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProvenanceTest {
    @Test
    fun provenanceRecordsNormalizerVersionAndObservationSource() {
        val provenance = fixture(
            url = "https://example.test/api",
            observation = ObservationSource.ENGINE,
        )

        assertEquals("taho-browser", provenance.sourceProduct)
        assertEquals(RequestNormalizer.VERSION, provenance.normalizerVersion)
        assertEquals(ObservationSource.ENGINE, provenance.observation)
    }

    @Test
    fun diagnosticStringRedactsUserInfoQueryValuesAndFragment() {
        val secret = "super-secret-token"
        val provenance = fixture(
            url = "https://alice:password@example.test/api?api_key=$secret&empty=#fragment",
            observation = ObservationSource.ENGINE,
        )

        val rendered = provenance.toString()

        assertFalse(rendered.contains(secret))
        assertFalse(rendered.contains("password"))
        assertFalse(rendered.contains("alice"))
        assertFalse(rendered.contains("#fragment"))
        assertTrue(rendered.contains("api_key=•••"))
        assertTrue(rendered.contains("empty=•••"))
    }

    @Test
    fun equalCaptureFactsProduceEqualProvenanceRegardlessOfArrivalOrder() {
        val facts = linkedMapOf(
            "url" to "https://api.example.test/v1/items",
            "method" to "POST",
            "sourceVersion" to "0.1.0",
        )
        val random = Random(42)

        repeat(500) {
            val shuffled = facts.entries.shuffled(random).associate { it.key to it.value }

            val left = fixture(
                url = facts.getValue("url"),
                method = facts.getValue("method"),
                sourceVersion = facts.getValue("sourceVersion"),
            )
            val right = fixture(
                url = shuffled.getValue("url"),
                method = shuffled.getValue("method"),
                sourceVersion = shuffled.getValue("sourceVersion"),
            )

            assertEquals(left, right)
            assertEquals(left.hashCode(), right.hashCode())
        }
    }

    private fun fixture(
        url: String,
        method: String = "GET",
        sourceVersion: String = "0.1.0",
        observation: ObservationSource = ObservationSource.PAGE,
    ): Provenance =
        ProvenanceFactory.create(
            sourceVersion = sourceVersion,
            captureSessionId = CaptureSessionId("cap-1"),
            tahoTabId = TahoTabId("tab-1"),
            transactionId = TransactionId("tx-1"),
            capturedAt = Instant.parse("2026-09-27T12:00:00Z"),
            originReportedAt = Instant.parse("2026-09-27T11:59:59Z"),
            originalUrl = url,
            originalMethod = method,
            secretPolicyApplied = null,
            observation = observation,
        )
}
