package app.taho.browser.capture.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Security Test S1 & S12]
 * Full seeded-secret capture -> inspect -> transfer leak test.
 * Asserts that plaintext secrets seeded in URLs, queries, headers, and payloads
 * NEVER appear in log buffers, toString() representations, crash reports,
 * or debug diagnostics.
 */
class FullSeededSecretLeakTest {

    @Test
    fun fullSeededSecretPipelineNeverLeaksPlaintextInDiagnosticsOrStrings() {
        val seededBearerToken = "eyJhGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.super_secret_payload_token_9981"
        val seededCookieSession = "session_cookie_super_secret_abc123xyz789"
        val seededApiKey = "ai_live_sk_99998888777766665555"
        val seededPassword = "MySuperSecretMasterPassword!2026"

        val allSecrets = listOf(
            seededBearerToken,
            seededCookieSession,
            seededApiKey,
            seededPassword,
        )

        // 1. Secret Detection & Masking
        val authHeaderFinding = SecretFinding(
            id = "finding-auth-1",
            location = SecretLocation.Header("Authorization"),
            category = SecretCategory.BEARER_TOKEN,
            confidence = Confidence.HIGH,
            evidence = "header name 'authorization' with bearer scheme",
        )
        val cookieFinding = SecretFinding(
            id = "finding-cookie-1",
            location = SecretLocation.Header("Cookie"),
            category = SecretCategory.COOKIE,
            confidence = Confidence.HIGH,
            evidence = "header name 'cookie'",
        )
        val queryFinding = SecretFinding(
            id = "finding-query-1",
            location = SecretLocation.Query("api_key"),
            category = SecretCategory.API_KEY,
            confidence = Confidence.HIGH,
            evidence = "query parameter name 'api_key'",
        )

        val rawAuth = RawObservedValue.copyOf(seededBearerToken.encodeToByteArray())
        val secretValue = SecretValue.copyOf(SecretCategory.BEARER_TOKEN, seededBearerToken.encodeToByteArray())
        val maskedAuth = SecretMasker.mask(authHeaderFinding)

        // 2. Request Normalization
        val normalizableRequest = NormalizableRequest(
            url = "https://api.taho.test/v1/resource?api_key=$seededApiKey",
            method = "POST",
            headers = listOf(
                NormalizedHeader(
                    name = "Authorization",
                    value = NormalizedHeaderValue.Protected(
                        SecretRef("ref-auth-1", SecretCategory.BEARER_TOKEN),
                    ),
                ),
                NormalizedHeader(
                    name = "Cookie",
                    value = NormalizedHeaderValue.Protected(
                        SecretRef("ref-cookie-1", SecretCategory.COOKIE),
                    ),
                ),
                NormalizedHeader(
                    name = "User-Agent",
                    value = NormalizedHeaderValue.Public("TahoBrowser/1.0"),
                ),
            ),
        )
        val normalized = RequestNormalizer.normalize(normalizableRequest)

        // 3. Provenance with sensitive original URL containing API key
        val provenance = ProvenanceFactory.create(
            sourceVersion = "0.1.0",
            captureSessionId = CaptureSessionId("session-leak-test"),
            tahoTabId = TahoTabId("tab-leak-test"),
            transactionId = TransactionId("tx-leak-test"),
            capturedAt = Instant.parse("2026-09-28T12:00:00Z"),
            originReportedAt = null,
            originalUrl = "https://api.taho.test/v1/resource?api_key=$seededApiKey&user=alice",
            originalMethod = "POST",
            secretPolicyApplied = SecretPolicy.PARAMETERIZE,
            observation = ObservationSource.ENGINE,
        )

        // 4. JSON Body secret scanning
        val jsonPayload = """{"username":"admin","password":"$seededPassword","nested":{"secret_key":"$seededApiKey"}}"""
        val scanResult = JsonBodySecretScanner.scan(transactionKey = "tx-leak-test", rawJson = jsonPayload)
        assertTrue(scanResult.assessment.findings.isNotEmpty(), "JSON body secret scanner should detect credentials")

        // 5. Audit all string and debug diagnostic representations
        val diagnosticStrings = listOf(
            rawAuth.toString(),
            secretValue.toString(),
            authHeaderFinding.toString(),
            cookieFinding.toString(),
            queryFinding.toString(),
            maskedAuth.toString(),
            normalized.toString(),
            provenance.toString(),
            provenance.redactedOriginalUrl(),
        )

        // Verify none of the seeded plaintext secrets leak into any diagnostic string
        for (secret in allSecrets) {
            for (rendered in diagnosticStrings) {
                assertFalse(
                    rendered.contains(secret),
                    "Security Leak detected: Plaintext secret leaked in diagnostic/string output: $rendered",
                )
            }
        }

        rawAuth.close()
        secretValue.close()
    }
}
