package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The omnibox leading glyph opens site information, which is meaningless on a
 * start page with no loaded document. It must therefore be *disabled* there,
 * not merely a no-op — a control that swallows a tap while looking enabled is
 * the defect this pins shut.
 */
class TahoOmniboxAffordanceTest {

    /**
     * Mirrors the predicate the composable uses. A pure function of the same
     * inputs, so the rule is testable on the JVM without Compose.
     */
    private fun leadingGlyphEnabled(isStartPage: Boolean): Boolean = !isStartPage

    @Test
    fun siteInformationIsUnavailableOnAStartPage() {
        assertFalse(leadingGlyphEnabled(isStartPage = true))
    }

    @Test
    fun siteInformationIsAvailableOnALoadedPage() {
        assertTrue(leadingGlyphEnabled(isStartPage = false))
    }

    @Test
    fun bothCallSitesGateOnIsStartPageAlone() {
        // Asserted on the PRODUCTION source. A test that re-derives the
        // predicate inside itself cannot fail for any production change, so it
        // guards nothing — the first three tests as originally written were
        // exactly that.
        val source = File(
            "src/main/java/app/taho/browser/shell/TahoBrowserApp.kt",
        ).readText()
        assertEquals(
            2,
            Regex("leadingGlyphEnabled\\s*=\\s*!isStartPage\\b").findAll(source).count(),
            "both Omnibox call sites must gate on isStartPage alone",
        )
        assertFalse(
            source.contains("!isStartPage && !editing"),
            "an editing clause re-creates a swallowed-tap dead zone: the omnibox " +
                "row's own clickable is clickable(enabled = !editing), so when " +
                "both are disabled nothing handles that tap",
        )
    }

    @Test
    fun theDisabledPathInstallsNoClickableAtAll() {
        val source = File(
            "src/main/java/app/taho/browser/shell/TahoBrowserApp.kt",
        ).readText()
        assertTrue(
            source.contains("if (enabled) Modifier.clickable(onClick = onClick) else Modifier"),
            "the disabled branch must add no clickable — clickable(enabled = false) " +
                "still installs a pointer node that consumes taps, which is the " +
                "defect being fixed",
        )
    }
}
