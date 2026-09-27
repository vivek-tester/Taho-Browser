package app.taho.browser.testsupport.m4

import app.taho.browser.capture.domain.NormalizedHeader
import app.taho.browser.capture.domain.NormalizedHeaderValue
import app.taho.browser.capture.domain.SecretAssessment
import app.taho.browser.contract.BodyEncoding
import app.taho.browser.contract.BodyRepresentation
import app.taho.browser.contract.CaptureCompleteness
import app.taho.browser.contract.Completeness
import app.taho.browser.contract.ReceiptResult
import app.taho.browser.contract.TransferBody
import app.taho.browser.testsupport.http.ControlledHttpFixtureServer
import app.taho.browser.testsupport.receiver.TahoReceiverHarness
import app.taho.browser.transfer.core.M4CapturedRequest
import app.taho.browser.transfer.core.M4FieldValue
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

class M4ContractSliceTest {
    @Test
    fun realLoopbackGetPreparesAndImportsWithoutExecution() {
        ControlledHttpFixtureServer().use { server ->
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI(server.url("/api/get"))).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode())

            val prepared = assertIs<M4PreparationResult.Prepared>(
                M4TransferPreparer.prepare(
                    fixture(
                        method = "GET",
                        url = server.url("/api/get"),
                        body = null,
                        requestBody = Completeness.NOT_APPLICABLE,
                    ),
                ),
            ).value

            val receiver = TahoReceiverHarness(clockMillis = { 2000L })
            val receipt = receiver.receive(
                prepared.envelope,
                prepared.encodedUtf8Bytes.toLong(),
            )

            assertEquals(ReceiptResult.IMPORTED, receipt.result)
            val imported = receiver.snapshot().stagedImports.single()
            assertEquals("GET", imported.request.method)
            assertEquals(server.url("/api/get"), imported.request.url)
            assertEquals(0, receiver.snapshot().executionCount)
            assertEquals(0, receiver.snapshot().persistenceCount)
        }
    }

    @Test
    fun realLoopbackJsonPostRetainsQueryHeadersBodyAndProvenance() {
        ControlledHttpFixtureServer().use { server ->
            val bodyText = """{"name":"m4-fixture"}"""
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI(server.url("/api/json?tab=A")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyText))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(201, response.statusCode())

            val body = TransferBody(
                representation = BodyRepresentation.JSON,
                contentType = "application/json",
                charset = "utf-8",
                encoding = BodyEncoding.UTF8,
                size = bodyText.toByteArray().size.toLong(),
                declaredSize = bodyText.toByteArray().size.toLong(),
                truncated = false,
                completeness = Completeness.COMPLETE,
                content = bodyText,
            )
            val captured = fixture(
                method = "POST",
                url = server.url("/api/json"),
                body = body,
                requestBody = Completeness.COMPLETE,
            ).copy(
                query = listOf(M4QueryInput("tab", M4FieldValue.Public("A"))),
                headers = listOf(
                    NormalizedHeader(
                        "Content-Type",
                        NormalizedHeaderValue.Public("application/json"),
                    ),
                    NormalizedHeader(
                        "X-Request-ID",
                        NormalizedHeaderValue.Public("m4-contract-slice"),
                    ),
                ),
            )
            val prepared = assertIs<M4PreparationResult.Prepared>(
                M4TransferPreparer.prepare(captured),
            ).value
            val receiver = TahoReceiverHarness(clockMillis = { 2000L })
            val receipt = receiver.receive(
                prepared.envelope,
                prepared.encodedUtf8Bytes.toLong(),
            )

            assertEquals(ReceiptResult.IMPORTED, receipt.result)
            val imported = receiver.snapshot().stagedImports.single()
            assertEquals("POST", imported.request.method)
            assertEquals("A", imported.request.query.single().value)
            assertEquals("application/json", imported.request.body?.contentType)
            assertEquals(bodyText, imported.request.body?.content)
            assertEquals(
                "request/1.0.0",
                imported.provenance.normalizerVersion,
            )
            assertEquals(0, receiver.snapshot().executionCount)
        }
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
