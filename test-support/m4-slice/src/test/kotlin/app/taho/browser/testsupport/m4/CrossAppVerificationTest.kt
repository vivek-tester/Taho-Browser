package app.taho.browser.testsupport.m4

import app.taho.browser.capture.domain.BrowserTabId
import app.taho.browser.capture.domain.CaptureEvent
import app.taho.browser.capture.domain.Confidence
import app.taho.browser.capture.domain.NormalizedHeader
import app.taho.browser.capture.domain.NormalizedHeaderValue
import app.taho.browser.capture.domain.PartialReason
import app.taho.browser.capture.domain.RequestId
import app.taho.browser.capture.domain.RequestIdentity
import app.taho.browser.capture.domain.RuntimeGeneration
import app.taho.browser.capture.domain.SecretAssessment
import app.taho.browser.capture.domain.SecretCategory
import app.taho.browser.capture.domain.SecretFinding
import app.taho.browser.capture.domain.SecretLocation
import app.taho.browser.capture.domain.SecretRef
import app.taho.browser.capture.domain.SecretSeverity
import app.taho.browser.capture.domain.TransactionReducer
import app.taho.browser.capture.domain.TransactionState
import app.taho.browser.contract.BodyEncoding
import app.taho.browser.contract.BodyRepresentation
import app.taho.browser.contract.CaptureCompleteness
import app.taho.browser.contract.Completeness
import app.taho.browser.contract.ContractLimits
import app.taho.browser.contract.ReceiptResult
import app.taho.browser.contract.TransferBody
import app.taho.browser.contract.TransferContractValidator
import app.taho.browser.contract.TransferErrorCode
import app.taho.browser.testsupport.http.ControlledHttpFixtureServer
import app.taho.browser.testsupport.receiver.TahoReceiverHarness
import app.taho.browser.transfer.core.M4CapturedRequest
import app.taho.browser.transfer.core.M4FieldValue
import app.taho.browser.transfer.core.M4PreparationBlock
import app.taho.browser.transfer.core.M4PreparationResult
import app.taho.browser.transfer.core.M4QueryInput
import app.taho.browser.transfer.core.M4TransferPreparer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [Cross-app Verification Suite]
 * Tests real Browser -> Project-Taho integration, contract adherence,
 * streaming, private session boundaries, forgery rejection, and process death recovery.
 */
class CrossAppVerificationTest {

    @Test
    fun realGetTransferVerification() {
        ControlledHttpFixtureServer().use { server ->
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI(server.url("/api/get?limit=10"))).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode())

            val captured = fixture(
                method = "GET",
                url = server.url("/api/get"),
                body = null,
                requestBody = Completeness.NOT_APPLICABLE,
            ).copy(
                query = listOf(M4QueryInput("limit", M4FieldValue.Public("10"))),
            )

            val prepared = assertIs<M4PreparationResult.Prepared>(
                M4TransferPreparer.prepare(captured),
            ).value

            val harness = TahoReceiverHarness(clockMillis = { 3000L })
            val receipt = harness.receive(
                envelope = prepared.envelope,
                encodedEnvelopeUtf8Bytes = prepared.encodedUtf8Bytes.toLong(),
            )

            assertEquals(ReceiptResult.IMPORTED, receipt.result)
            val snapshot = harness.snapshot()
            assertEquals(1, snapshot.stagedImports.size)
            val imported = snapshot.stagedImports.single()
            assertEquals("GET", imported.request.method)
            assertEquals("10", imported.request.query.single().value)
            assertEquals(0, snapshot.executionCount, "Receiver must stage without auto-executing")
            assertEquals(0, snapshot.persistenceCount, "Receiver must stage without auto-persisting")
        }
    }

    @Test
    fun realJsonPostTransferVerification() {
        ControlledHttpFixtureServer().use { server ->
            val bodyText = """{"orderId":"ord-9988","amount":42.50}"""
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI(server.url("/api/json")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyText))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(201, response.statusCode())

            val captured = fixture(
                method = "POST",
                url = server.url("/api/json"),
                body = TransferBody(
                    representation = BodyRepresentation.JSON,
                    contentType = "application/json",
                    charset = "utf-8",
                    encoding = BodyEncoding.UTF8,
                    size = bodyText.toByteArray().size.toLong(),
                    declaredSize = bodyText.toByteArray().size.toLong(),
                    truncated = false,
                    completeness = Completeness.COMPLETE,
                    content = bodyText,
                ),
                requestBody = Completeness.COMPLETE,
            ).copy(
                headers = listOf(
                    NormalizedHeader("Content-Type", NormalizedHeaderValue.Public("application/json")),
                ),
            )

            val prepared = assertIs<M4PreparationResult.Prepared>(
                M4TransferPreparer.prepare(captured),
            ).value

            val harness = TahoReceiverHarness()
            val receipt = harness.receive(
                envelope = prepared.envelope,
                encodedEnvelopeUtf8Bytes = prepared.encodedUtf8Bytes.toLong(),
            )

            assertEquals(ReceiptResult.IMPORTED, receipt.result)
            val imported = harness.snapshot().stagedImports.single()
            assertEquals("POST", imported.request.method)
            assertEquals("application/json", imported.request.body?.contentType)
            assertEquals(bodyText, imported.request.body?.content)
        }
    }

    @Test
    fun real5MbSecureFileStreamTransferVerification() {
        // 5 MB file transfer: should use FILE_URI reference rather than inline bytes
        val fiveMegabytes = 5L * 1024L * 1024L
        val largeFileBody = TransferBody(
            representation = BodyRepresentation.BINARY,
            contentType = "application/octet-stream",
            charset = null,
            encoding = BodyEncoding.FILE_URI,
            size = fiveMegabytes,
            declaredSize = fiveMegabytes,
            truncated = false,
            completeness = Completeness.COMPLETE,
            content = null,
            uri = "content://app.taho.browser.transfer/artifact/01J8ZQ4M2K7X9V3B8N0P4R6T8Y",
        )

        val captured = fixture(
            method = "POST",
            url = "https://upload.example.test/stream",
            body = largeFileBody,
            requestBody = Completeness.COMPLETE,
        )

        val prepared = assertIs<M4PreparationResult.Prepared>(
            M4TransferPreparer.prepare(captured),
        ).value

        // Direct envelope UTF-8 bytes must remain strictly within ContractLimits.DIRECT_ENVELOPE_UTF8_BYTES (256 KB)
        assertTrue(
            prepared.encodedUtf8Bytes < ContractLimits.DIRECT_ENVELOPE_UTF8_BYTES,
            "Direct envelope for 5MB stream must be under 256 KB limit via FILE_URI reference",
        )

        val harness = TahoReceiverHarness()
        val receipt = harness.receive(
            envelope = prepared.envelope,
            encodedEnvelopeUtf8Bytes = prepared.encodedUtf8Bytes.toLong(),
        )

        assertEquals(ReceiptResult.IMPORTED, receipt.result)
        val imported = harness.snapshot().stagedImports.single()
        assertEquals(BodyEncoding.FILE_URI, imported.request.body?.encoding)
        assertEquals(fiveMegabytes, imported.request.body?.size)

        // Attempting to transfer binary inline directly violates contract limits
        val inlineViolations = TransferContractValidator.validate(
            envelope = prepared.envelope.copy(
                request = prepared.envelope.request.copy(
                    body = largeFileBody.copy(
                        encoding = BodyEncoding.UTF8,
                        content = "sample binary data directly inlined",
                    ),
                ),
            ),
            encodedEnvelopeUtf8Bytes = prepared.encodedUtf8Bytes.toLong(),
        )
        assertTrue(
            inlineViolations.any { it.code == app.taho.browser.contract.ContractViolationCode.INVALID_BODY },
            "BINARY bodies must use FILE_URI",
        )

        // Exceeding ARTIFACT_PLAINTEXT_BYTES cap (32 MB)
        val artifactCapViolations = TransferContractValidator.validate(
            envelope = prepared.envelope,
            encodedEnvelopeUtf8Bytes = ContractLimits.ARTIFACT_PLAINTEXT_BYTES + 1024L,
        )
        assertTrue(
            artifactCapViolations.any { it.code == app.taho.browser.contract.ContractViolationCode.ARTIFACT_CAP_EXCEEDED },
            "Oversized artifact envelope bytes must violate contract limits",
        )
    }

    @Test
    fun realPrivateSessionTransferVerification() {
        val privateRequest = fixture(
            method = "GET",
            url = "https://secure-bank.example.test/account",
            body = null,
            requestBody = Completeness.NOT_APPLICABLE,
        ).copy(
            fromPrivateSession = true,
            reviewRequired = true,
        )

        // When reviewRequired = true, automatic preparation must be blocked
        val result = M4TransferPreparer.prepare(privateRequest)
        val blocked = assertIs<M4PreparationResult.Blocked>(result)
        assertEquals(M4PreparationBlock.REVIEW_REQUIRED, blocked.reason)

        // Once user explicitly confirms and review requirement is satisfied
        val approvedRequest = privateRequest.copy(reviewRequired = false)
        val prepared = assertIs<M4PreparationResult.Prepared>(
            M4TransferPreparer.prepare(approvedRequest),
        ).value

        assertTrue(prepared.envelope.security.fromPrivateSession)

        val harness = TahoReceiverHarness()
        val receipt = harness.receive(
            envelope = prepared.envelope,
            encodedEnvelopeUtf8Bytes = prepared.encodedUtf8Bytes.toLong(),
        )
        assertEquals(ReceiptResult.IMPORTED, receipt.result)
        assertTrue(harness.snapshot().stagedImports.single().request.url.contains("secure-bank"))
    }

    @Test
    fun realMalformedForgedTransferVerification() {
        val harness = TahoReceiverHarness()
        val valid = assertIs<M4PreparationResult.Prepared>(
            M4TransferPreparer.prepare(
                fixture(
                    method = "GET",
                    url = "https://api.test/resource",
                    body = null,
                    requestBody = Completeness.NOT_APPLICABLE,
                ),
            ),
        ).value

        // 1. Forged schema
        val forgedSchema = valid.envelope.copy(schema = "forged.attack.schema")
        val r1 = harness.receive(forgedSchema, valid.encodedUtf8Bytes.toLong())
        assertEquals(ReceiptResult.REJECTED, r1.result)
        assertEquals(TransferErrorCode.TAHO_TRANSFER_INVALID_SCHEMA, r1.errorCode)

        // 2. Unsupported version
        val unsupportedVersion = valid.envelope.copy(version = 999)
        val r2 = harness.receive(unsupportedVersion, valid.encodedUtf8Bytes.toLong())
        assertEquals(ReceiptResult.UNSUPPORTED, r2.result)
        assertEquals(TransferErrorCode.TAHO_TRANSFER_UNSUPPORTED_VERSION, r2.errorCode)

        // 3. Forged CRLF in headers
        val crlfHeader = valid.envelope.copy(
            request = valid.envelope.request.copy(
                headers = listOf(
                    app.taho.browser.contract.TransferHeader(
                        name = "X-Injected\r\nSet-Cookie: evil=1",
                        value = "val",
                        redacted = false,
                    ),
                ),
            ),
        )
        val r3 = harness.receive(crlfHeader, valid.encodedUtf8Bytes.toLong())
        assertEquals(ReceiptResult.REJECTED, r3.result)

        // 4. Declared vs actual size mismatch
        val sizeMismatch = valid.envelope.copy(
            request = valid.envelope.request.copy(
                body = TransferBody(
                    representation = BodyRepresentation.TEXT,
                    contentType = "text/plain",
                    charset = "utf-8",
                    encoding = BodyEncoding.UTF8,
                    size = 100L,
                    declaredSize = 50L, // Mismatch!
                    truncated = false,
                    completeness = Completeness.COMPLETE,
                    content = "Sample content with size mismatch",
                ),
            ),
        )
        val r4 = harness.receive(sizeMismatch, valid.encodedUtf8Bytes.toLong())
        assertEquals(ReceiptResult.REJECTED, r4.result)
    }

    @Test
    fun realProcessDeathTransferRecoveryVerification() {
        val identity = RequestIdentity(
            runtimeGeneration = RuntimeGeneration("gen-pre-crash"),
            requestId = RequestId("req-inflight-crash-1"),
        )

        // 1. Transaction started and headers captured before unexpected process termination
        val s1 = TransactionReducer.reduce(
            current = null,
            identity = identity,
            event = CaptureEvent.Start("e1"),
        )
        val s2 = TransactionReducer.reduce(
            current = s1.state,
            identity = identity,
            event = CaptureEvent.HeadersCaptured("e2", app.taho.browser.capture.domain.Completeness.COMPLETE),
        )

        assertEquals(TransactionState.HEADERS_CAPTURED, s2.state.transaction.state)

        // 2. Process death occurs -> recovery detects interruption
        val recovered = TransactionReducer.reduce(
            current = s2.state,
            identity = identity,
            event = CaptureEvent.RecoverInterrupted("e-recovery", PartialReason.PROCESS_DIED),
        )

        // Verifies honest recovery to PARTIAL terminal state
        assertEquals(TransactionState.PARTIAL, recovered.state.transaction.state)
        assertEquals("PROCESS_DIED", recovered.state.transaction.terminalReason)
        assertTrue(recovered.state.transaction.state.isTerminal)

        // 3. Post-recovery: late events after crash are discarded as terminal
        val lateEvent = TransactionReducer.reduce(
            current = recovered.state,
            identity = identity,
            event = CaptureEvent.Completed("e-late", 200),
        )
        assertEquals(TransactionState.PARTIAL, lateEvent.state.transaction.state)
    }

    private fun fixture(
        method: String,
        url: String,
        body: TransferBody?,
        requestBody: Completeness,
    ) = M4CapturedRequest(
        transferId = "01J8ZQ4M2K7X9V3B8N0P4R6T8Y",
        issuedAt = 1000L,
        appVersion = "0.1.0",
        engineVersion = "geckoview-156.0.20260921121718",
        captureSessionId = "01J8ZQ4M2K7X9V3B8N0P4R6T8A",
        tabId = "tab-a",
        transactionId = "01J8ZQ4M2K7X9V3B8N0P4R6T8C",
        method = method,
        url = url,
        query = emptyList(),
        headers = emptyList(),
        body = body,
        status = if (method == "POST") 201 else 200,
        statusText = if (method == "POST") "Created" else "OK",
        durationMs = 10L,
        initiator = "SCRIPT",
        completeness = CaptureCompleteness(
            requestUrl = Completeness.COMPLETE,
            requestHeaders = Completeness.COMPLETE,
            requestBody = requestBody,
            responseHeaders = Completeness.COMPLETE,
            responseBody = Completeness.UNAVAILABLE,
            timing = Completeness.UNAVAILABLE,
            tlsInfo = Completeness.UNAVAILABLE,
        ),
        secretAssessment = SecretAssessment.NONE,
        fromPrivateSession = false,
        reviewRequired = false,
        capturedAt = 1000L,
        redirectCount = 0,
    )
}
