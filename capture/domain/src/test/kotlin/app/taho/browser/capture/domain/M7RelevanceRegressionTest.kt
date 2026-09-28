package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class M7RelevanceRegressionTest {
    @Test
    fun hostBoundaryRejectsSubstringLookalike() {
        assertFalse(
            RelevanceClassifier.matchesHostBoundary(
                host = "evil-example.com",
                targetHost = "example.com",
            ),
        )
    }

    @Test
    fun hostBoundaryAcceptsExactHostAndSubdomain() {
        assertTrue(
            RelevanceClassifier.matchesHostBoundary(
                host = "example.com",
                targetHost = "example.com",
            ),
        )
        assertTrue(
            RelevanceClassifier.matchesHostBoundary(
                host = "api.example.com",
                targetHost = "example.com",
            ),
        )
    }
}
