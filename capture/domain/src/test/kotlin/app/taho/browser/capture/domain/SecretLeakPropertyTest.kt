package app.taho.browser.capture.domain

import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse

class SecretLeakPropertyTest {
    @Test
    fun secretPlaintextNeverAppearsInSecretBearingTypeStrings() {
        val random = Random(9917)

        repeat(1_000) { index ->
            val secret = buildString {
                append("secret-")
                repeat(48) {
                    append(
                        "abcdefghijklmnopqrstuvwxyz0123456789"[
                            random.nextInt(36)
                        ],
                    )
                }
            }

            val raw = RawObservedValue.copyOf(secret.encodeToByteArray())
            val secretValue = SecretValue.copyOf(
                SecretCategory.CLIENT_SECRET,
                secret.encodeToByteArray(),
            )
            val finding = SecretFinding(
                id = "finding-" + index,
                location = SecretLocation.Query("api_key"),
                category = SecretCategory.API_KEY,
                confidence = Confidence.HIGH,
                evidence = "query parameter name 'api-key'",
            )
            val masked = SecretMasker.mask(finding)
            val normalized = RequestNormalizer.normalize(
                NormalizableRequest(
                    url = "https://example.test/api",
                    method = "GET",
                    headers = listOf(
                        NormalizedHeader(
                            name = "Authorization",
                            value = NormalizedHeaderValue.Protected(
                                SecretRef(
                                    id = "secret-ref-" + index,
                                    category = SecretCategory.BEARER_TOKEN,
                                ),
                            ),
                        ),
                    ),
                ),
            )
            val provenance = ProvenanceFactory.create(
                sourceVersion = "0.1.0",
                captureSessionId = CaptureSessionId("cap-" + index),
                tahoTabId = TahoTabId("tab-" + index),
                transactionId = TransactionId("tx-" + index),
                capturedAt = Instant.parse("2026-09-27T12:00:00Z"),
                originReportedAt = null,
                originalUrl = "https://example.test/api?api_key=" + secret,
                originalMethod = "GET",
                secretPolicyApplied = SecretPolicy.PARAMETERIZE,
                observation = ObservationSource.ENGINE,
            )
            val queued = QueuedCaptureEvent(
                id = "e-" + index,
                eventClass = CaptureEventClass.AUTHENTICATION,
                payload = raw,
            )

            val renderings = listOf(
                raw.toString(),
                secretValue.toString(),
                finding.toString(),
                masked.toString(),
                normalized.toString(),
                provenance.toString(),
                queued.toString(),
            )

            renderings.forEach { rendered ->
                assertFalse(
                    rendered.contains(secret),
                    "plaintext secret leaked from toString(): " + rendered,
                )
            }

            raw.close()
            secretValue.close()
        }
    }
}
