package app.taho.browser.observation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProductionObservationProtocolTest {
    @Test
    fun rejectsOversizeBeforeJsonParsing() {
        val raw = """{"type":"TX_ERROR","conn":"c","seq":1,"id":"e","reqId":"r","tabId":1,"error":""" +
            """ + "x".repeat(9000) + ""}"
        val result = ProductionObservationProtocol.parse(raw, ObservationLane.BULK)

        assertEquals(
            ObservationRejectReason.OVERSIZE,
            assertIs<ObservationParseResult.Rejected>(result).reason,
        )
    }

    @Test
    fun identityCannotArriveOnBulkLane() {
        val raw = """{"type":"TAB_REGISTER","extTabId":3,"url":"https://example.test/"}"""
        val result = ProductionObservationProtocol.parse(raw, ObservationLane.BULK)

        assertEquals(
            ObservationRejectReason.WRONG_LANE,
            assertIs<ObservationParseResult.Rejected>(result).reason,
        )
    }

    @Test
    fun parsesValidatedStartWithoutLeakingUrlInToString() {
        val raw = """{
          "type":"TX_START",
          "conn":"conn-1",
          "seq":2,
          "id":"event-1",
          "reqId":"request-1",
          "tabId":7,
          "frameId":0,
          "docId":"doc-1",
          "url":"https://api.example.test/v1/orders?token=secret",
          "method":"POST",
          "resourceType":"xmlhttprequest",
          "ts":123.5
        }""".trimIndent()

        val message = assertIs<ObservationParseResult.Accepted>(
            ProductionObservationProtocol.parse(raw, ObservationLane.BULK),
        ).message
        val start = assertIs<ProductionObservationMessage.TxStart>(message)

        assertEquals(7, start.extTabId)
        assertEquals("POST", start.method)
        assertFalse(start.toString().contains("token=secret"))
    }

    @Test
    fun headerDiagnosticNeverContainsHeaderValue() {
        val raw = """{
          "type":"TX_REQ_HEADERS",
          "conn":"conn-1",
          "seq":3,
          "id":"event-2",
          "reqId":"request-1",
          "tabId":7,
          "headers":[{"name":"Authorization","value":"Bearer live-secret"}]
        }""".trimIndent()

        val message = assertIs<ObservationParseResult.Accepted>(
            ProductionObservationProtocol.parse(raw, ObservationLane.BULK),
        ).message
        val headers = assertIs<ProductionObservationMessage.TxRequestHeaders>(message)

        assertEquals(1, headers.headers.size)
        assertFalse(headers.toString().contains("live-secret"))
        assertTrue(headers.toString().contains("Authorization"))
    }

    @Test
    fun rejectsControlCharactersInHeaders() {
        val raw = """{
          "type":"TX_REQ_HEADERS",
          "conn":"conn-1",
          "seq":3,
          "id":"event-2",
          "reqId":"request-1",
          "tabId":7,
          "headers":[{"name":"X-Test","value":"bad\nvalue"}]
        }""".trimIndent()

        val result = ProductionObservationProtocol.parse(raw, ObservationLane.BULK)
        assertEquals(
            ObservationRejectReason.INVALID_FIELD,
            assertIs<ObservationParseResult.Rejected>(result).reason,
        )
    }
}
