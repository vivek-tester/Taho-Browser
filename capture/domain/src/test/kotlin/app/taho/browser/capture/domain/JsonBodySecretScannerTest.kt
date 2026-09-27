package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonBodySecretScannerTest {
    @Test
    fun detectsNestedTokenWithoutPuttingValueInEvidence() {
        val secret = "live-json-secret-123456"
        val result = JsonBodySecretScanner.scan(
            transactionKey = "tx-json-1",
            rawJson = """{"profile":{"token":"$secret","name":"test"}}""",
        )

        assertEquals(1, result.assessment.findings.size)
        val finding = result.assessment.findings.single()
        assertEquals(SecretCategory.QUERY_TOKEN, finding.category)
        assertEquals(SecretLocation.BodyJsonPath("$.profile.token"), finding.location)
        assertFalse(finding.evidence.contains(secret))
        assertFalse(result.requiresReview)
    }

    @Test
    fun malformedJsonFailsClosed() {
        val result = JsonBodySecretScanner.scan(
            transactionKey = "tx-json-2",
            rawJson = """{"token":""",
        )

        assertTrue(result.requiresReview)
        assertEquals(SecretAssessment.NONE, result.assessment)
    }

    @Test
    fun sensitiveObjectContainerRequiresReview() {
        val result = JsonBodySecretScanner.scan(
            transactionKey = "tx-json-3",
            rawJson = """{"secret":{"nested":"value"}}""",
        )

        assertTrue(result.requiresReview)
    }

    @Test
    fun credentialShapedValueUnderUnknownNameFailsClosed() {
        val result = JsonBodySecretScanner.scan(
            transactionKey = "tx-json-shape",
            rawJson = """{"credential":"abcDEF1234567890_abcDEF1234567890_abcDEF12"}""",
        )

        assertTrue(result.requiresReview)
    }

    @Test
    fun ordinaryJsonDoesNotRequireReview() {
        val result = JsonBodySecretScanner.scan(
            transactionKey = "tx-json-4",
            rawJson = """{"item":"123","qty":2}""",
        )

        assertFalse(result.requiresReview)
        assertTrue(result.assessment.findings.isEmpty())
    }
}
