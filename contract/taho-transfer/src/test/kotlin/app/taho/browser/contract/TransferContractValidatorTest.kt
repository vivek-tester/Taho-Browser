package app.taho.browser.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferContractValidatorTest {
    @Test
    fun parameterizedSecretIsRedactedAndTransferableAfterReview() {
        val envelope = fixture(
            headers = listOf(
                TransferHeader(
                    name = "Authorization",
                    value = "{{AUTH_TOKEN}}",
                    redacted = true,
                    secretCategory = "BEARER_TOKEN",
                    policy = SecretPolicy.PARAMETERIZE,
                ),
            ),
        )

        assertTrue(
            TransferContractValidator.validate(envelope, encodedEnvelopeUtf8Bytes = 4096).isEmpty(),
        )
    }

    @Test
    fun unclassifiedContentBlocksTransferInsteadOfClaimingFalseSafety() {
        val envelope = fixture(
            security = fixtureSecurity(reviewRequired = true),
        )

        val violations = TransferContractValidator.validate(envelope, 4096)

        assertTrue(
            violations.any { it.code == ContractViolationCode.SECURITY_REVIEW_REQUIRED },
        )
    }

    @Test
    fun sourceDeclaredSizeIsEvidenceAndMayDisagreeWithCapturedBytes() {
        val content = "{\"ok\":true}"
        val envelope = fixture(
            body = TransferBody(
                representation = BodyRepresentation.JSON,
                contentType = "application/json",
                charset = "utf-8",
                encoding = BodyEncoding.UTF8,
                size = content.toByteArray().size.toLong(),
                declaredSize = 99_999,
                truncated = false,
                completeness = Completeness.COMPLETE,
                content = content,
            ),
        )

        assertTrue(TransferContractValidator.validate(envelope, 4096).isEmpty())
    }

    @Test
    fun actualBodyByteMismatchIsRejected() {
        val envelope = fixture(
            body = TransferBody(
                representation = BodyRepresentation.TEXT,
                contentType = "text/plain",
                charset = "utf-8",
                encoding = BodyEncoding.UTF8,
                size = 1,
                declaredSize = 1,
                truncated = false,
                completeness = Completeness.COMPLETE,
                content = "hello",
            ),
        )

        val violations = TransferContractValidator.validate(envelope, 4096)

        assertTrue(violations.any { it.code == ContractViolationCode.INVALID_BODY })
    }

    @Test
    fun controlCharactersInHeadersAreRejected() {
        val envelope = fixture(
            headers = listOf(
                TransferHeader(
                    name = "X-Test\r\nInjected",
                    value = "1",
                    redacted = false,
                ),
            ),
        )

        val violations = TransferContractValidator.validate(envelope, 4096)

        assertTrue(violations.any { it.code == ContractViolationCode.INVALID_HEADER })
    }

    @Test
    fun artifactCapUsesFinalSerializedEnvelopeBytes() {
        val violations = TransferContractValidator.validate(
            fixture(),
            ContractLimits.ARTIFACT_PLAINTEXT_BYTES + 1,
        )

        assertEquals(ContractViolationCode.ARTIFACT_CAP_EXCEEDED, violations.single().code)
    }

    private fun fixture(
        headers: List<TransferHeader> = emptyList(),
        body: TransferBody? = null,
        security: TransferSecurity = fixtureSecurity(reviewRequired = false),
    ) = RequestTransferV1(
        transferId = "01J8ZQ4M2K7X9V3B8N0P4R6T8Y",
        issuedAt = 1_758_912_345_678,
        source = TransferSource(
            appVersion = "0.1.0",
            engine = "geckoview-156",
            captureSessionId = "01J8ZQ4M2K7X9V3B8N0P4R6T8A",
            tabId = "01J8ZQ4M2K7X9V3B8N0P4R6T8B",
            observation = ObservationSource.ENGINE,
        ),
        request = TransferRequest(
            id = "01J8ZQ4M2K7X9V3B8N0P4R6T8C",
            method = "POST",
            url = "https://api.example.com/v1/orders",
            headers = headers,
            body = body,
        ),
        capture = TransferCapture(
            timestamp = 1_758_912_345_678,
            durationMs = 96,
            status = 201,
            statusText = "Created",
            initiator = "SCRIPT",
            timing = TransferTiming(ttfbMs = 71, totalMs = 96),
            completeness = CaptureCompleteness(
                requestUrl = Completeness.COMPLETE,
                requestHeaders = Completeness.COMPLETE,
                requestBody = if (body == null) Completeness.NOT_APPLICABLE else body.completeness,
                responseHeaders = Completeness.COMPLETE,
                responseBody = Completeness.UNAVAILABLE,
                timing = Completeness.COMPLETE,
                tlsInfo = Completeness.UNAVAILABLE,
            ),
        ),
        security = security,
        provenance = SafeProvenance(
            normalizerVersion = "norm/1.0.0",
            captureEngineVersion = "geckoview-156.0.20260921121718",
            capturedAt = 1_758_912_345_678,
            redirectCount = 0,
        ),
    )

    private fun fixtureSecurity(reviewRequired: Boolean) = TransferSecurity(
        secretPolicy = SecretPolicy.PARAMETERIZE,
        containsSensitiveData = false,
        secretCount = 0,
        fromPrivateSession = false,
        reviewRequired = reviewRequired,
    )
}
