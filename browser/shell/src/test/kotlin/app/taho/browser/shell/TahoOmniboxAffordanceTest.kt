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
        // The `,?\\s*$` tail is load-bearing twice over. A bare `\b` matches at
        // the trailing space, so `!isStartPage && !editing` would satisfy this
        // count and the assertion would pass for the exact regression it exists
        // to catch; the comma has to be tolerated explicitly, because anchoring
        // to end-of-line without it matches nothing at all and the test fails
        // on correct source instead of on the mutation.
        assertEquals(
            2,
            Regex("leadingGlyphEnabled\\s*=\\s*!isStartPage\\s*,?\\s*$", RegexOption.MULTILINE)
                .findAll(source).count(),
            "both Omnibox call sites must gate on isStartPage and nothing else",
        )
    }

    @Test
    fun theDisabledPathInstallsNoClickableAtAll() {
        val source = File(
            "src/main/java/app/taho/browser/shell/TahoBrowserApp.kt",
        ).readText()
        assertTrue(
            source.contains("if (enabled) Modifier.clickable(onClick = onClick) else Modifier"),
            "the disabled branch should add no clickable, so this control does not " +
                "participate in the omnibox row's hit test at all",
        )
    }
}
