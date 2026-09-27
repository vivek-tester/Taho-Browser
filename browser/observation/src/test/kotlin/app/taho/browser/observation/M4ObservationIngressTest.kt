package app.taho.browser.observation

import app.taho.browser.capture.domain.QueueOfferResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M4ObservationIngressTest {
    @Test
    fun queueIsBoundedAndReportsDrops() {
        val ingress = M4ObservationIngress(capacity = 3)

        assertIs<QueueOfferResult.Accepted>(ingress.offer(response("e1", 2)))
        assertIs<QueueOfferResult.Rejected>(ingress.offer(response("e2", 3)))
        assertTrue(ingress.metrics().limited)
        assertTrue(ingress.metrics().size <= 3)
    }

    @Test
    fun authenticationHeaderReceivesReservedHighPriority() {
        val ingress = M4ObservationIngress(capacity = 3)

        ingress.offer(response("low", 2))
        val auth = bulk(
            ProductionObservationMessage.TxRequestHeaders(
                connectionId = "conn",
                sequence = 3,
                eventId = "auth",
                requestId = "request",
                extTabId = 1,
                headers = listOf(
                    ProductionObservationMessage.HeaderValue(
                        "Authorization",
                        "Bearer secret",
                    ),
                ),
            ),
        )

        assertIs<QueueOfferResult.Accepted>(ingress.offer(auth))
        assertEquals("e1", (ingress.poll()?.message as? ProductionObservationMessage.TxResponseStart)?.eventId)
        assertEquals(
            "auth",
            (ingress.poll()?.message as? ProductionObservationMessage.TxRequestHeaders)?.eventId,
        )
    }

    private fun response(eventId: String, sequence: Long) =
        bulk(
            ProductionObservationMessage.TxResponseStart(
                connectionId = "conn",
                sequence = sequence,
                eventId = eventId,
                requestId = "request",
                extTabId = 1,
                statusCode = 200,
                statusText = "OK",
                headers = emptyList(),
                reportedAt = null,
            ),
        )

    private fun bulk(message: ProductionObservationMessage) =
        ProductionObservationEvent.Bulk(
            tahoTabId = "tab",
            isPrivate = false,
            message = message,
            sequenceGap = false,
        )
}
