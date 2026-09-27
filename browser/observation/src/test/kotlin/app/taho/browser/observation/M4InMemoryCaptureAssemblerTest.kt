package app.taho.browser.observation

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
