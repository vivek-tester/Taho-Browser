package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretDetectorTest {
    @Test
    fun authorizationBearerIsClassifiedWithoutEvidenceLeak() {
        val token = "Bearer eyJhbGciOiJIUzI1NiJ9.abc.def"
        val field = RawObservedField.Header(
            name = "Authorization",
            value = raw(token),
        )

        val assessment = SecretDetector.assess("tx-1", listOf(field))
        field.value.close()

        val finding = assessment.findings.single()
        assertEquals(SecretCategory.BEARER_TOKEN, finding.category)
        assertEquals(Confidence.HIGH, finding.confidence)
        assertFalse(finding.evidence.contains("eyJ"))
        assertFalse(finding.toString().contains("abc"))
        assertEquals(SecretSeverity.HIGH, assessment.highestSeverity)
    }

    @Test
    fun cookieHeaderIsAlwaysSecretByName() {
        val field = RawObservedField.Header(
            name = "Cookie",
            value = raw("session=super-secret-cookie"),
        )

        val assessment = SecretDetector.assess("tx-2", listOf(field))
        field.value.close()

        assertEquals(SecretCategory.COOKIE, assessment.findings.single().category)
        assertEquals(SecretPolicy.MASK, SecretPolicyDefaults.forCategory(SecretCategory.COOKIE))
    }

    @Test
    fun requestIdsAndGenericXHeadersAreNotBlanketSecrets() {
        val requestId = RawObservedField.Header(
            "X-Request-ID",
            raw("7b541671-f5ad-4b11-a5ef-79aee6f53fb0"),
        )
        val generic = RawObservedField.Header(
            "X-Product-Version",
            raw("2026.09"),
        )

        val assessment = SecretDetector.assess(
            "tx-3",
            listOf(requestId, generic),
        )
        requestId.value.close()
        generic.value.close()

        assertTrue(assessment.findings.isEmpty())
        assertEquals(SecretSeverity.NONE, assessment.highestSeverity)
    }

    @Test
    fun queryTokenAndBodyPasswordAreHighConfidenceNameFindings() {
        val query = RawObservedField.Query(
            "access_token",
            raw("query-secret-value"),
        )
        val body = RawObservedField.BodyJson(
            path = "$.account.password",
            fieldName = "password",
            value = raw("correct horse battery staple"),
        )

        val assessment = SecretDetector.assess(
            "tx-4",
            listOf(query, body),
        )
        query.value.close()
        body.value.close()

        assertEquals(
            setOf(SecretCategory.QUERY_TOKEN, SecretCategory.PASSWORD),
            assessment.findings.map { it.category }.toSet(),
        )
        assertTrue(
            assessment.findings.all { it.confidence == Confidence.HIGH },
        )
        assertEquals(SecretSeverity.CRITICAL, assessment.highestSeverity)
    }

    @Test
    fun jwtShapeOnlyTriggersInAuthLikePosition() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature"
        val authish = RawObservedField.Header(
            "X-Auth-Blob",
            raw(jwt),
        )
        val unrelated = RawObservedField.Header(
            "X-Debug-Blob",
            raw(jwt),
        )

        val assessment = SecretDetector.assess(
            "tx-5",
            listOf(authish, unrelated),
        )
        authish.value.close()
        unrelated.value.close()

        val finding = assessment.findings.single()
        assertEquals(SecretCategory.JWT, finding.category)
        assertEquals(Confidence.LOW, finding.confidence)
    }

    @Test
    fun urlUserInfoCreatesFindingWithoutEmbeddingUrl() {
        val assessment = SecretDetector.assess(
            transactionKey = "tx-6",
            fields = emptyList(),
            hasUrlUserInfo = true,
        )

        val finding = assessment.findings.single()
        assertEquals(SecretLocation.UrlUserInfo, finding.location)
        assertEquals(SecretCategory.BASIC_AUTH, finding.category)
        assertFalse(finding.evidence.contains("@"))
    }

    @Test
    fun findingsAreStableForSameTransactionInput() {
        fun run(): SecretFinding {
            val field = RawObservedField.Header(
                "X-Api-Key",
                raw("AKIA1234567890ABCDEF"),
            )
            val finding = SecretDetector
                .assess("stable-tx", listOf(field))
                .findings
                .single()
            field.value.close()
            return finding
        }

        assertEquals(run().id, run().id)
    }

    @Test
    fun secretContainersNeverPrintPlaintext() {
        val plaintext = "dont-print-this"
        val raw = raw(plaintext)
        val secret = SecretValue.copyOf(
            SecretCategory.CLIENT_SECRET,
            plaintext.encodeToByteArray(),
        )

        assertFalse(raw.toString().contains(plaintext))
        assertFalse(secret.toString().contains(plaintext))

        raw.close()
        secret.close()
    }

    @Test
    fun maskerUsesCategoryOnlyAndNeverNeedsPlaintext() {
        val finding = SecretFinding(
            id = "finding-1",
            location = SecretLocation.Header("Authorization"),
            category = SecretCategory.BEARER_TOKEN,
            confidence = Confidence.HIGH,
            evidence = "header name 'authorization' with bearer scheme",
        )

        val masked = SecretMasker.mask(finding)

        assertEquals("Bearer ••••••••", masked.displayValue)
        assertEquals(SecretCategory.BEARER_TOKEN, masked.ref.category)
        assertEquals(
            SecretPolicy.PARAMETERIZE,
            SecretPolicyDefaults.forCategory(SecretCategory.BEARER_TOKEN),
        )
    }

    private fun raw(value: String): RawObservedValue =
        RawObservedValue.copyOf(value.encodeToByteArray())
}
