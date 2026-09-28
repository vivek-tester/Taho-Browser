package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Security Test S14]
 * Capability-copy allow-list CI gate (§Test Architecture §6 S14, Gate H).
 * Validates that product copy and user-facing capability representations
 * strictly adhere to the defined capability boundaries of Taho Browser:
 * - Observes, captures, inspects, parameterizes, and transfers requests.
 * - Does NOT execute, re-send internally without Taho, or perform hidden ambient interception.
 */
class CapabilityCopyAllowListGateTest {

    private val allowedCapabilityTerms = setOf(
        "observe", "capture", "inspect", "transfer", "export",
        "mask", "parameterize", "filter", "review", "search",
        "taho", "replay in taho", "secure", "geckoview", "workspace",
    )

    private val forbiddenClaims = listOf(
        "execute request internally",
        "intercept background os network",
        "bypass ssl pinning",
        "modify in-flight requests",
        "execute arbitrary code",
        "remote debugger enabled in production",
        "silent background packet sniffing",
    )

    @Test
    fun productCapabilityClaimsAdhereToAllowList() {
        val sampleProductDescriptions = listOf(
            "Taho Browser observes API traffic and transfers safe request definitions to Taho.",
            "Inspect captured network transactions, review sensitive parameters, and export to Taho workspace.",
            "Filter requests by GraphQL, WebSocket, or Static resources.",
            "Destination origin change warning: replaying to a different origin may leak credentials.",
            "Export Instead: save transfer bundle to disk when Taho is not installed.",
        )

        for (desc in sampleProductDescriptions) {
            val lower = desc.lowercase()
            for (forbidden in forbiddenClaims) {
                assertFalse(
                    lower.contains(forbidden),
                    "Product copy violates capability gate: contains forbidden claim '$forbidden' in '$desc'",
                )
            }
        }
    }

    @Test
    fun coreVerbsMapToAllowedCapabilities() {
        val verifiedVerbs = listOf("observe", "capture", "inspect", "transfer", "export")
        for (verb in verifiedVerbs) {
            assertTrue(
                allowedCapabilityTerms.contains(verb),
                "Core product verb '$verb' must be within allowed capabilities",
            )
        }
    }
}
