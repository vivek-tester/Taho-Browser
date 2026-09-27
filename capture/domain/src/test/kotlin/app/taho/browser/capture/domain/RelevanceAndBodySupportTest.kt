package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelevanceAndBodySupportTest {
    @Test
    fun mainFrameIsNeverClassifiedAsApi() {
        val relevance = RelevanceClassifier.classify(
            RelevanceInput(
                url = "https://api.example.com/v1/users",
                resourceType = "main_frame",
                method = "GET",
                targetHost = "example.com",
            ),
        )
        assertEquals(RelevanceCategory.PAGE_NAVIGATION, relevance.category)
    }

    @Test
    fun firstPartyUsesHostnameBoundariesNotContains() {
        assertTrue(RelevanceClassifier.matchesHostBoundary("api.example.com", "example.com"))
        assertFalse(RelevanceClassifier.matchesHostBoundary("evil-example.com", "example.com"))
    }

    @Test
    fun staticTrafficIsNotRelevantByDefault() {
        val relevance = RelevanceClassifier.classify(
            RelevanceInput(
                url = "https://example.com/app.js",
                resourceType = "script",
                method = "GET",
                targetHost = "example.com",
            ),
        )
        assertEquals(RelevanceCategory.STATIC_RESOURCE, relevance.category)
        assertFalse(RelevanceClassifier.isRelevantByDefault(relevance))
    }

    @Test
    fun graphqlJsonIsDetected() {
        val result = RequestBodySupport.classify(
            transactionKey = "tx-1",
            contentType = "application/json",
            bytes = """{"query":"query { viewer { id } }","variables":{}}""".encodeToByteArray(),
        )
        assertEquals(DurableBodyRepresentation.GRAPHQL, result.representation)
        assertEquals(Completeness.COMPLETE, result.completeness)
        assertEquals(BodySupportLimitation.NONE, result.limitation)
    }

    @Test
    fun urlEncodedFormWithPasswordFailsClosed() {
        val result = RequestBodySupport.classify(
            transactionKey = "tx-2",
            contentType = "application/x-www-form-urlencoded",
            bytes = "username=a&password=secret".encodeToByteArray(),
        )
        assertEquals(DurableBodyRepresentation.FORM, result.representation)
        assertEquals(BodySupportLimitation.SECRET_REVIEW_REQUIRED, result.limitation)
        assertNull(result.transferSafeText)
        assertTrue(result.secretAssessment.findings.isNotEmpty())
    }

    @Test
    fun multipartIsExplicitlyLimitedInsteadOfPretendingEmpty() {
        val result = RequestBodySupport.classify(
            transactionKey = "tx-3",
            contentType = "multipart/form-data; boundary=x",
            bytes = "--x\r\ncontent\r\n--x--".encodeToByteArray(),
        )
        assertEquals(DurableBodyRepresentation.MULTIPART, result.representation)
        assertEquals(Completeness.COMPLETE, result.completeness)
        assertEquals(
            BodySupportLimitation.MULTIPART_PARTS_UNAVAILABLE,
            result.limitation,
        )
        assertNull(result.transferSafeText)
    }

    @Test
    fun opaqueBinaryIsHonest() {
        val result = RequestBodySupport.classify(
            transactionKey = "tx-4",
            contentType = "application/octet-stream",
            bytes = byteArrayOf(0, 1, 2, 3),
        )
        assertEquals(DurableBodyRepresentation.BINARY, result.representation)
        assertEquals(BodySupportLimitation.OPAQUE_BINARY, result.limitation)
        assertEquals(4, result.capturedSize)
    }

    @Test
    fun missingBodyIsUnavailableNotEmptyComplete() {
        val result = RequestBodySupport.classify(
            transactionKey = "tx-5",
            contentType = "application/json",
            bytes = null,
        )
        assertEquals(Completeness.UNAVAILABLE, result.completeness)
        assertEquals(BodySupportLimitation.BODY_NOT_OBSERVED, result.limitation)
    }
}
