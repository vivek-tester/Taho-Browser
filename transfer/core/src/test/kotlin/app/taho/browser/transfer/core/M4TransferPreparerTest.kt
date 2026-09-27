package app.taho.browser.transfer.core

import app.taho.browser.capture.domain.Confidence
import app.taho.browser.capture.domain.NormalizedHeader
import app.taho.browser.capture.domain.NormalizedHeaderValue
import app.taho.browser.capture.domain.SecretAssessment
import app.taho.browser.capture.domain.SecretCategory
import app.taho.browser.capture.domain.SecretFinding
import app.taho.browser.capture.domain.SecretLocation
import app.taho.browser.capture.domain.SecretPolicy
import app.taho.browser.capture.domain.SecretRef
import app.taho.browser.capture.domain.SecretSeverity
import app.taho.browser.contract.BodyEncoding
import app.taho.browser.contract.BodyRepresentation
import app.taho.browser.contract.CaptureCompleteness
import app.taho.browser.contract.Completeness
import app.taho.browser.contract.TransferBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M4TransferPreparerTest {
    @Test
    fun parameterizesAuthorizationMasksCookieAndStripsTransportHeaders() {
        val auth = SecretFinding(
            id = "auth-ref",
            location = SecretLocation.Header("Authorization"),
            category = SecretCategory.BEARER_TOKEN,
            confidence = Confidence.HIGH,
            evidence = "header name 'authorization' with bearer scheme",
        )
        val cookie = SecretFinding(
            id = "cookie-ref",
            location = SecretLocation.Header("Cookie"),
            category = SecretCategory.COOKIE,
            confidence = Confidence.HIGH,
            evidence = "header name 'cookie'",
        )
        val result = M4TransferPreparer.prepare(
            input = base().copy(
                headers = listOf(
                    NormalizedHeader("Host", NormalizedHeaderValue.Public("api.test")),
                    NormalizedHeader(
                        "Authorization",
                        NormalizedHeaderValue.Protected(
                            SecretRef(auth.id, auth.category),
                        ),
                    ),
                    NormalizedHeader(
                        "Cookie",
                        NormalizedHeaderValue.Protected(
                            SecretRef(cookie.id, cookie.category),
                        ),
                    ),
                    NormalizedHeader("X-Request-ID", NormalizedHeaderValue.Public("trace-1")),
                ),
                secretAssessment = SecretAssessment(
                    findings = listOf(auth, cookie),
                    highestSeverity = SecretSeverity.HIGH,
                ),
            ),
        )

        val prepared = assertIs<M4PreparationResult.Prepared>(result).value
        assertFalse(prepared.envelope.request.headers.any { it.name.equals("Host", true) })
        assertEquals(
            "{{AUTH_TOKEN}}",
            prepared.envelope.request.headers.first { it.name == "Authorization" }.value,
        )
        assertEquals(
            "••••••••",
            prepared.envelope.request.headers.first { it.name == "Cookie" }.value,
        )
        assertTrue(prepared.envelope.request.headers.first { it.name == "Cookie" }.redacted)
        assertEquals(
            "Bearer ••••••••",
            prepared.display.headers.first { it.name == "Authorization" }.displayValue,
        )
        assertTrue(prepared.encodedUtf8Bytes > 0)
    }

    @Test
    fun jsonPostBodyAndQuerySurvivePreparation() {
        val bodyText = """{"item":"123","qty":2}"""
        val result = M4TransferPreparer.prepare(
            base().copy(
                method = "POST",
                query = listOf(M4QueryInput("expand", M4FieldValue.Public("items"))),
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
                completeness = completeness(requestBody = Completeness.COMPLETE),
            ),
        )

        val prepared = assertIs<M4PreparationResult.Prepared>(result).value
        assertEquals("POST", prepared.envelope.request.method)
        assertEquals("items", prepared.envelope.request.query.single().value)
        assertEquals(bodyText, prepared.envelope.request.body?.content)
    }

    @Test
    fun unresolvedSecurityReviewFailsClosed() {
        val result = M4TransferPreparer.prepare(base().copy(reviewRequired = true))
        assertEquals(
            M4PreparationBlock.REVIEW_REQUIRED,
            assertIs<M4PreparationResult.Blocked>(result).reason,
        )
    }

    @Test
    fun explicitPolicyHasNoFallbackToMaskedOrCapturedPlaintext() {
        val finding = SecretFinding(
            id = "auth-ref",
            location = SecretLocation.Header("Authorization"),
            category = SecretCategory.AUTHORIZATION,
            confidence = Confidence.HIGH,
            evidence = "header name 'authorization'",
        )
        val result = M4TransferPreparer.prepare(
            input = base().copy(
                headers = listOf(
                    NormalizedHeader(
                        "Authorization",
                        NormalizedHeaderValue.Protected(
                            SecretRef(finding.id, finding.category),
                        ),
                    ),
                ),
                secretAssessment = SecretAssessment(
                    listOf(finding),
                    SecretSeverity.HIGH,
                ),
            ),
            requestedPolicy = SecretPolicy.EXPLICIT,
        )

        assertEquals(
            M4PreparationBlock.EXPLICIT_SECRET_UNAVAILABLE,
            assertIs<M4PreparationResult.Blocked>(result).reason,
        )
    }

    private fun base() = M4CapturedRequest(
        transferId = "01J8ZQ4M2K7X9V3B8N0P4R6T8Y",
        issuedAt = 1000L,
        appVersion = "0.1.0",
        engineVersion = "geckoview-156.0.20260921121718",
        captureSessionId = "01J8ZQ4M2K7X9V3B8N0P4R6T8A",
        tabId = "tab-a",
        transactionId = "01J8ZQ4M2K7X9V3B8N0P4R6T8C",
        method = "GET",
        url = "https://api.example.test/v1/items",
        query = emptyList(),
        headers = listOf(
            NormalizedHeader("Accept", NormalizedHeaderValue.Public("application/json")),
        ),
        body = null,
        status = 200,
        statusText = "OK",
        durationMs = 12L,
        initiator = "SCRIPT",
        completeness = completeness(Completeness.NOT_APPLICABLE),
        secretAssessment = SecretAssessment.NONE,
        fromPrivateSession = false,
        reviewRequired = false,
        capturedAt = 1000L,
        redirectCount = 0,
    )

    private fun completeness(requestBody: Completeness) = CaptureCompleteness(
        requestUrl = Completeness.COMPLETE,
        requestHeaders = Completeness.COMPLETE,
        requestBody = requestBody,
        responseHeaders = Completeness.COMPLETE,
        responseBody = Completeness.UNAVAILABLE,
        timing = Completeness.UNAVAILABLE,
        tlsInfo = Completeness.UNAVAILABLE,
    )
}
