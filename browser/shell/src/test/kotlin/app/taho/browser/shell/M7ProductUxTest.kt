package app.taho.browser.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class M7ProductUxTest {
    private fun request(
        id: String,
        category: String,
        relevant: Boolean,
        sensitiveCount: Int = 0,
        completeness: M4CompletenessUi = M4CompletenessUi.COMPLETE,
    ) = M4CaptureRequestUiState(
        id = id,
        method = "POST",
        url = "https://api.example.test/v1/" + id,
        status = 200,
        durationMs = 10,
        category = category,
        requestBodyCompleteness = completeness,
        responseBodyCompleteness = M4CompletenessUi.UNAVAILABLE,
        sensitiveCount = sensitiveCount,
        relevanceCategory = category,
        relevantByDefault = relevant,
    )

    @Test
    fun relevantFilterNeverPromotesNoise() {
        val rows = listOf(
            request("api", "PRIMARY_API", true),
            request("static", "STATIC_RESOURCE", false),
            request("analytics", "ANALYTICS", false),
        )

        val filtered = M7CaptureUx.filtered(rows, M7CaptureFilterUi.RELEVANT)

        assertEquals(listOf("api"), filtered.map { it.id })
    }

    @Test
    fun allFilterKeepsNoiseVisibleForReadOnlyPresentation() {
        val rows = listOf(
            request("api", "PRIMARY_API", true),
            request("static", "STATIC_RESOURCE", false),
        )

        assertEquals(
            listOf("api", "static"),
            M7CaptureUx.filtered(rows, M7CaptureFilterUi.ALL).map { it.id },
        )
    }

    @Test
    fun authAndApiFiltersUsePersistedRelevanceCategories() {
        val rows = listOf(
            request("auth", "AUTHENTICATION", true),
            request("graphql", "GRAPHQL", true),
            request("telemetry", "TELEMETRY", false),
        )

        assertEquals(
            listOf("auth"),
            M7CaptureUx.filtered(rows, M7CaptureFilterUi.AUTH).map { it.id },
        )
        assertEquals(
            listOf("auth", "graphql"),
            M7CaptureUx.filtered(rows, M7CaptureFilterUi.API).map { it.id },
        )
    }

    @Test
    fun talkBackCopyNamesPartialBodiesWithoutClaimingCompleteness() {
        assertEquals(
            "Request body, partially captured",
            M7CaptureUx.bodyAnnouncement(M4CompletenessUi.PARTIAL),
        )
        assertEquals(
            "Request body, truncated",
            M7CaptureUx.bodyAnnouncement(M4CompletenessUi.TRUNCATED),
        )
    }

    @Test
    fun captureAnnouncementUsesRealCount() {
        assertEquals(
            "Capture active, 6 relevant requests",
            M7CaptureUx.captureAnnouncement(6),
        )
        assertEquals(
            "Capture active, 1 relevant request",
            M7CaptureUx.captureAnnouncement(1),
        )
    }

    @Test
    fun policyPreviewNeverInventsLiveSecret() {
        val protected = request(
            id = "secret",
            category = "AUTHENTICATION",
            relevant = true,
            sensitiveCount = 1,
        )

        assertEquals(
            "Authorization: Bearer {{AUTH_TOKEN}}",
            M7CaptureUx.policyPreview(protected, M4SecretPolicyUi.PARAMETERIZE),
        )
        assertEquals(
            "Authorization: Bearer ••••••••",
            M7CaptureUx.policyPreview(protected, M4SecretPolicyUi.MASK),
        )
        assertTrue(
            M7CaptureUx.policyPreview(protected, M4SecretPolicyUi.EXPLICIT)
                .contains("unavailable"),
        )
        assertFalse(protected.explicitPolicyAllowed)
    }

    @Test
    fun sensitiveHeaderProjectionRejectsRawValue() {
        val failed = runCatching {
            M4CaptureHeaderUiState(
                name = "Authorization",
                displayValue = "Bearer live-secret",
                sensitive = true,
            )
        }.isFailure

        assertTrue(failed)
    }
}
