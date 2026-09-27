package app.taho.browser.testsupport.receiver

import app.taho.browser.contract.CaptureCompleteness
import app.taho.browser.contract.Completeness
import app.taho.browser.contract.ObservationSource
import app.taho.browser.contract.ReceiptResult
import app.taho.browser.contract.RequestTransferV1
import app.taho.browser.contract.SafeProvenance
import app.taho.browser.contract.SecretPolicy
import app.taho.browser.contract.TransferCapture
import app.taho.browser.contract.TransferErrorCode
import app.taho.browser.contract.TransferRequest
import app.taho.browser.contract.TransferSecurity
import app.taho.browser.contract.TransferSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TahoReceiverHarnessTest {
    @Test
    fun validTransferStagesOnlyUnsavedUnexecutedImport() {
        val harness = TahoReceiverHarness(clockMillis = { 1234L })
        val receipt = harness.receive(fixture(), encodedEnvelopeUtf8Bytes = 4096)
        val snapshot = harness.snapshot()

        assertEquals(ReceiptResult.IMPORTED, receipt.result)
        assertNotNull(receipt.requestId)
        assertEquals(1, snapshot.stagedImports.size)
        assertEquals(0, snapshot.executionCount)
        assertEquals(0, snapshot.persistenceCount)
    }

    @Test
    fun duplicateTransferIsIdempotent() {
        val harness = TahoReceiverHarness(clockMillis = { 1234L })
        val envelope = fixture()
        val first = harness.receive(envelope, 4096)
        val duplicate = harness.receive(envelope, 4096)

        assertEquals(ReceiptResult.IMPORTED, first.result)
        assertEquals(ReceiptResult.DUPLICATE, duplicate.result)
        assertEquals(first.requestId, duplicate.requestId)
        assertEquals(1, harness.snapshot().stagedImports.size)
    }

    @Test
    fun invalidSchemaIsRejectedWithoutRequest() {
        val harness = TahoReceiverHarness()
        val receipt = harness.receive(
            fixture().copy(schema = "not.taho.request-transfer"),
            4096,
        )
        assertEquals(ReceiptResult.REJECTED, receipt.result)
        assertEquals(TransferErrorCode.TAHO_TRANSFER_INVALID_SCHEMA, receipt.errorCode)
        assertTrue(harness.snapshot().stagedImports.isEmpty())
    }

    @Test
    fun unresolvedSecurityReviewFailsClosed() {
        val harness = TahoReceiverHarness()
        val base = fixture()
        val receipt = harness.receive(
            base.copy(security = base.security.copy(reviewRequired = true)),
            4096,
        )
        assertEquals(ReceiptResult.REJECTED, receipt.result)
        assertEquals(TransferErrorCode.TAHO_TRANSFER_IMPORT_FAILED, receipt.errorCode)
        assertTrue(harness.snapshot().stagedImports.isEmpty())
    }

    private fun fixture() = RequestTransferV1(
        transferId = "01J8ZQ4M2K7X9V3B8N0P4R6T8Y",
        issuedAt = 1000L,
        source = TransferSource(
            appVersion = "0.1.0",
            engine = "geckoview-156",
            captureSessionId = "01J8ZQ4M2K7X9V3B8N0P4R6T8A",
            tabId = "01J8ZQ4M2K7X9V3B8N0P4R6T8B",
            observation = ObservationSource.ENGINE,
        ),
        request = TransferRequest(
            id = "01J8ZQ4M2K7X9V3B8N0P4R6T8C",
            method = "GET",
            url = "https://api.example.test/v1/items",
        ),
        capture = TransferCapture(
            timestamp = 1000L,
            durationMs = 12L,
            status = 200,
            statusText = "OK",
            initiator = "SCRIPT",
            timing = null,
            completeness = CaptureCompleteness(
                requestUrl = Completeness.COMPLETE,
                requestHeaders = Completeness.COMPLETE,
                requestBody = Completeness.NOT_APPLICABLE,
                responseHeaders = Completeness.COMPLETE,
                responseBody = Completeness.UNAVAILABLE,
                timing = Completeness.UNAVAILABLE,
                tlsInfo = Completeness.UNAVAILABLE,
            ),
        ),
        security = TransferSecurity(
            secretPolicy = SecretPolicy.PARAMETERIZE,
            containsSensitiveData = false,
            secretCount = 0,
            fromPrivateSession = false,
            reviewRequired = false,
        ),
        provenance = SafeProvenance(
            normalizerVersion = "request/1.0.0",
            captureEngineVersion = "geckoview-156.0.20260921121718",
            capturedAt = 1000L,
            redirectCount = 0,
        ),
    )
}
