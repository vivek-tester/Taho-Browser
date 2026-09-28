package app.taho.browser.observation

import app.taho.browser.capture.domain.DurableBodyRepresentation
import app.taho.browser.capture.domain.DurableTransaction
import app.taho.browser.capture.domain.SecretCategory
import app.taho.browser.capture.domain.SecretPolicy
import app.taho.browser.transfer.core.M4PreparationResult
import app.taho.browser.transfer.core.M4TransferPreparer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M4InMemoryCaptureAssemblerTest {
    @Test
    fun completesSafeJsonPostAndParameterizesAuthorization() {
        val assembler = M4InMemoryCaptureAssembler(
            appVersion = "0.1.0",
            engineVersion = "geckoview-test",
            captureSessionId = "capture-1",
            transferIdFactory = { "01J8ZQ4M2K7X9V3B8N0P4R6T8Y" },
        )

        val body = """{"item":"123","qty":2}""".toByteArray()
        accept(
            assembler,
            ProductionObservationMessage.TxStart(
                "conn", 2, "e1", "r1", 7, 0, "doc",
                "https://api.example.test/v1/orders?expand=items",
                "POST", "xmlhttprequest", 1000.0,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestHeaders(
                "conn", 3, "e2", "r1", 7,
                listOf(
                    ProductionObservationMessage.HeaderValue(
                        "Content-Type",
                        "application/json",
                    ),
                    ProductionObservationMessage.HeaderValue(
                        "Authorization",
                        "Bearer live-secret-token",
                    ),
                ),
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestBody(
                "conn", 4, "e3", "r1", 7, body,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxResponseStart(
                "conn", 5, "e4", "r1", 7, 201, "Created", emptyList(), 1005.0,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxComplete(
                "conn", 6, "e5", "r1", 7, 1010.0,
            ),
        )

        val captured = assembler.requestsForTab("tab-a").single()
        assertFalse(captured.reviewRequired)
        assertEquals(1, captured.secretAssessment.findings.size)
        assertEquals(
            SecretCategory.BEARER_TOKEN,
            captured.secretAssessment.findings.single().category,
        )

        val prepared = assertIs<M4PreparationResult.Prepared>(
            M4TransferPreparer.prepare(captured, SecretPolicy.PARAMETERIZE),
        ).value
        assertEquals(
            "{{AUTH_TOKEN}}",
            prepared.envelope.request.headers
                .first { it.name == "Authorization" }
                .value,
        )
        assertFalse(prepared.encodedJson.contains("live-secret-token"))
    }

    @Test
    fun secretInsideJsonBodyFailsClosedBeforeTransfer() {
        val assembler = M4InMemoryCaptureAssembler(
            appVersion = "0.1.0",
            engineVersion = "geckoview-test",
            captureSessionId = "capture-1",
            transferIdFactory = { "01J8ZQ4M2K7X9V3B8N0P4R6T8Y" },
        )

        accept(
            assembler,
            ProductionObservationMessage.TxStart(
                "conn", 2, "e1", "r1", 7, 0, null,
                "https://api.example.test/login",
                "POST", "xmlhttprequest", 1000.0,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestHeaders(
                "conn", 3, "e2", "r1", 7,
                listOf(
                    ProductionObservationMessage.HeaderValue(
                        "Content-Type",
                        "application/json",
                    ),
                ),
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestBody(
                "conn", 4, "e3", "r1", 7,
                """{"password":"live-password"}""".toByteArray(),
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxComplete(
                "conn", 5, "e4", "r1", 7, 1010.0,
            ),
        )

        val captured = assembler.requestsForTab("tab-a").single()
        assertTrue(captured.reviewRequired)
        assertEquals(null, captured.body)
        assertFalse(captured.toString().contains("live-password"))
        val blocked = assertIs<M4PreparationResult.Blocked>(
            M4TransferPreparer.prepare(captured),
        )
        assertEquals(
            app.taho.browser.transfer.core.M4PreparationBlock.REVIEW_REQUIRED,
            blocked.reason,
        )
    }

    @Test
    fun unresolvedAttributionNeverAppearsInPerTabResults() {
        val assembler = M4InMemoryCaptureAssembler(
            appVersion = "0.1.0",
            engineVersion = "geckoview-test",
            captureSessionId = "capture-1",
        )

        assembler.accept(
            ProductionObservationEvent.Bulk(
                tahoTabId = null,
                isPrivate = null,
                message = ProductionObservationMessage.TxStart(
                    "conn", 2, "e1", "r1", 99, 0, null,
                    "https://api.example.test/data",
                    "GET", "xmlhttprequest", 1000.0,
                ),
                sequenceGap = false,
            ),
        )
        assembler.accept(
            ProductionObservationEvent.Bulk(
                tahoTabId = null,
                isPrivate = null,
                message = ProductionObservationMessage.TxComplete(
                    "conn", 3, "e2", "r1", 99, 1002.0,
                ),
                sequenceGap = false,
            ),
        )

        assertTrue(assembler.allCompleted().isEmpty())
        assertTrue(assembler.isLimited())
    }

    @Test
    fun redirectChainAndSafeFormAreRetainedAndTransferable() {
        var durable: DurableTransaction? = null
        val assembler = M4InMemoryCaptureAssembler(
            appVersion = "0.1.0",
            engineVersion = "geckoview-test",
            captureSessionId = "capture-1",
            transferIdFactory = { "01J8ZQ4M2K7X9V3B8N0P4R6T8Y" },
            onDurableRecord = { durable = it },
        )

        accept(
            assembler,
            ProductionObservationMessage.TxStart(
                "conn", 2, "e1", "r1", 7, 0, null,
                "https://api.example.test/v1/submit",
                "POST", "xmlhttprequest", 1000.0,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestHeaders(
                "conn", 3, "e2", "r1", 7,
                listOf(
                    ProductionObservationMessage.HeaderValue(
                        "Content-Type",
                        "application/x-www-form-urlencoded",
                    ),
                ),
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestBody(
                connectionId = "conn",
                sequence = 4,
                eventId = "e3",
                requestId = "r1",
                extTabId = 7,
                formData = mapOf("name" to listOf("widget")),
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRedirect(
                "conn", 5, "e4", "r1", 7, 302,
                "https://api.example.test/v2/submit?next=1",
                1004.0,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxComplete(
                "conn", 6, "e5", "r1", 7, 1010.0,
            ),
        )

        val captured = assembler.requestsForTab("tab-a").single()
        assertEquals(1, captured.redirectCount)
        assertEquals(DurableBodyRepresentation.FORM.name, captured.bodyRepresentation?.name)
        assertFalse(captured.reviewRequired)
        assertEquals(null, captured.bodyLimitation)
        assertEquals(
            app.taho.browser.contract.BodyRepresentation.FORM,
            captured.body?.representation,
        )
        assertEquals("name=widget", captured.body?.content)

        val stored = requireNotNull(durable)
        try {
            assertEquals(1, stored.redirects.size)
            assertEquals(302, stored.redirects.single().statusCode)
            assertEquals(
                "https://api.example.test/v2/submit",
                stored.url,
            )
            assertEquals(
                DurableBodyRepresentation.FORM,
                stored.requestBody?.representation,
            )
        } finally {
            stored.close()
        }
    }

    @Test
    fun unresolvedRequestIsPersistableAsUnresolvedButNeverLiveTransferable() {
        var durable: DurableTransaction? = null
        val assembler = M4InMemoryCaptureAssembler(
            appVersion = "0.1.0",
            engineVersion = "geckoview-test",
            captureSessionId = "capture-1",
            onDurableRecord = { durable = it },
        )

        assembler.accept(
            ProductionObservationEvent.Bulk(
                tahoTabId = null,
                isPrivate = false,
                targetHost = "example.test",
                message = ProductionObservationMessage.TxStart(
                    "conn", 2, "e1", "r-unresolved", 77, 0, null,
                    "https://api.example.test/data",
                    "GET", "xmlhttprequest", 1000.0,
                ),
                sequenceGap = false,
            ),
        )
        assembler.accept(
            ProductionObservationEvent.Bulk(
                tahoTabId = null,
                isPrivate = false,
                targetHost = "example.test",
                message = ProductionObservationMessage.TxComplete(
                    "conn", 3, "e2", "r-unresolved", 77, 1001.0,
                ),
                sequenceGap = false,
            ),
        )

        assertTrue(assembler.allCompleted().isEmpty())
        val stored = requireNotNull(durable)
        try {
            assertEquals(null, stored.tahoTabId)
            assertEquals("UNRESOLVED", stored.attribution)
        } finally {
            stored.close()
        }
    }


    @Test
    fun jsonGraphqlBodyMapsToGraphqlTransferRepresentation() {
        val assembler = M4InMemoryCaptureAssembler(
            appVersion = "0.1.0",
            engineVersion = "geckoview-test",
            captureSessionId = "capture-graphql",
        )
        val body =
            """{"query":"query Viewer { viewer { id } }","variables":{"limit":3}}"""
                .encodeToByteArray()

        accept(
            assembler,
            ProductionObservationMessage.TxStart(
                "conn-graphql",
                2,
                "graphql-start",
                "graphql-request",
                7,
                0,
                null,
                "https://api.example.test/graphql",
                "POST",
                "xmlhttprequest",
                1000.0,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestHeaders(
                "conn-graphql",
                3,
                "graphql-headers",
                "graphql-request",
                7,
                listOf(
                    ProductionObservationMessage.HeaderValue(
                        "Content-Type",
                        "application/json",
                    ),
                ),
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestBody(
                "conn-graphql",
                4,
                "graphql-body",
                "graphql-request",
                7,
                body,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxComplete(
                "conn-graphql",
                5,
                "graphql-complete",
                "graphql-request",
                7,
                1010.0,
            ),
        )

        val captured = assembler.requestsForTab("tab-a").single()
        assertFalse(captured.reviewRequired)
        assertEquals(
            app.taho.browser.contract.BodyRepresentation.GRAPHQL,
            captured.body?.representation,
        )
        assertEquals(body.decodeToString(), captured.body?.content)
    }

    @Test
    fun fiveMiBJsonBodyReassemblesAndPreparesForArtifactTransport() {
        val assembler = M4InMemoryCaptureAssembler(
            appVersion = "0.1.0",
            engineVersion = "geckoview-test",
            captureSessionId = "capture-large",
            transferIdFactory = { "01J8ZQ4M2K7X9V3B8N0P4R6T8Y" },
        )
        val json = "{\"blob\":\"" +
            "a".repeat(5 * 1024 * 1024) +
            "\"}"
        val bytes = json.encodeToByteArray()

        accept(
            assembler,
            ProductionObservationMessage.TxStart(
                "conn-large",
                2,
                "large-start",
                "large-request",
                7,
                0,
                null,
                "https://api.example.test/v1/large",
                "POST",
                "xmlhttprequest",
                1000.0,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestHeaders(
                "conn-large",
                3,
                "large-headers",
                "large-request",
                7,
                listOf(
                    ProductionObservationMessage.HeaderValue(
                        "Content-Type",
                        "application/json",
                    ),
                ),
            ),
        )

        val chunkSize = 160 * 1024
        val chunkCount = (bytes.size + chunkSize - 1) / chunkSize
        var sequence = 4L
        repeat(chunkCount) { index ->
            val start = index * chunkSize
            val end = minOf(bytes.size, start + chunkSize)
            accept(
                assembler,
                ProductionObservationMessage.TxRequestBody(
                    connectionId = "conn-large",
                    sequence = sequence++,
                    eventId = "large-body-$index",
                    requestId = "large-request",
                    extTabId = 7,
                    bytes = bytes.copyOfRange(start, end),
                    chunkIndex = index,
                    chunkCount = chunkCount,
                    isFinal = index == chunkCount - 1,
                    observedTotalBytes = bytes.size.toLong(),
                    truncated = false,
                ),
            )
        }
        accept(
            assembler,
            ProductionObservationMessage.TxComplete(
                "conn-large",
                sequence,
                "large-complete",
                "large-request",
                7,
                1010.0,
            ),
        )

        val captured = assembler.requestsForTab("tab-a").single()
        assertFalse(captured.reviewRequired)
        assertEquals(
            app.taho.browser.contract.Completeness.COMPLETE,
            captured.completeness.requestBody,
        )
        assertEquals(bytes.size.toLong(), captured.body?.size)

        val prepared = assertIs<M4PreparationResult.Prepared>(
            M4TransferPreparer.prepare(captured),
        ).value
        assertTrue(
            prepared.encodedUtf8Bytes.toLong() >
                app.taho.browser.contract.ContractLimits.DIRECT_ENVELOPE_UTF8_BYTES,
        )
        assertTrue(
            prepared.encodedUtf8Bytes.toLong() <
                app.taho.browser.contract.ContractLimits.ARTIFACT_PLAINTEXT_BYTES,
        )
    }

    @Test
    fun missingBodyChunkBecomesPartialAndTransferBlocked() {
        val assembler = M4InMemoryCaptureAssembler(
            appVersion = "0.1.0",
            engineVersion = "geckoview-test",
            captureSessionId = "capture-partial",
        )
        accept(
            assembler,
            ProductionObservationMessage.TxStart(
                "conn-partial",
                2,
                "partial-start",
                "partial-request",
                7,
                0,
                null,
                "https://api.example.test/v1/partial",
                "POST",
                "xmlhttprequest",
                1000.0,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestHeaders(
                "conn-partial",
                3,
                "partial-headers",
                "partial-request",
                7,
                listOf(
                    ProductionObservationMessage.HeaderValue(
                        "Content-Type",
                        "application/json",
                    ),
                ),
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestBody(
                connectionId = "conn-partial",
                sequence = 4,
                eventId = "partial-body-0",
                requestId = "partial-request",
                extTabId = 7,
                bytes = "{\"blob\":\"".encodeToByteArray(),
                chunkIndex = 0,
                chunkCount = 3,
                isFinal = false,
                observedTotalBytes = 100,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxRequestBody(
                connectionId = "conn-partial",
                sequence = 5,
                eventId = "partial-body-2",
                requestId = "partial-request",
                extTabId = 7,
                bytes = "\"}".encodeToByteArray(),
                chunkIndex = 2,
                chunkCount = 3,
                isFinal = true,
                observedTotalBytes = 100,
            ),
        )
        accept(
            assembler,
            ProductionObservationMessage.TxComplete(
                "conn-partial",
                6,
                "partial-complete",
                "partial-request",
                7,
                1010.0,
            ),
        )

        val captured = assembler.requestsForTab("tab-a").single()
        assertTrue(captured.reviewRequired)
        assertEquals(
            app.taho.browser.contract.Completeness.PARTIAL,
            captured.completeness.requestBody,
        )
        assertEquals(null, captured.body)
        assertTrue(
            captured.bodyLimitation?.contains("incomplete", ignoreCase = true) == true,
        )
    }



    private fun accept(
        assembler: M4InMemoryCaptureAssembler,
        message: ProductionObservationMessage,
    ) {
        assembler.accept(
            ProductionObservationEvent.Bulk(
                tahoTabId = "tab-a",
                isPrivate = false,
                message = message,
                sequenceGap = false,
            ),
        )
    }
}
