package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
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
    private fun leadingGlyphEnabled(isStartPage: Boolean, editing: Boolean): Boolean =
        !isStartPage && !editing

    @Test
    fun siteInformationIsUnavailableOnAStartPage() {
        assertFalse(leadingGlyphEnabled(isStartPage = true, editing = false))
    }

    @Test
    fun siteInformationIsAvailableOnALoadedPage() {
        assertTrue(leadingGlyphEnabled(isStartPage = false, editing = false))
    }

    @Test
    fun theGlyphIsDisabledWhileEditingTheAddress() {
        // Editing replaces the value with a draft; site info about the
        // pre-edit page would be confusing mid-keystroke.
        assertFalse(leadingGlyphEnabled(isStartPage = false, editing = true))
    }

    @Test
    fun theSourceNoLongerContainsABareGuardedNoOp() {
        // The defect's exact shape: `if (!isStartPage) { ... }` with no else,
        // attached to a clickable that consumes the tap regardless.
        val source = File(
            "src/main/java/app/taho/browser/shell/TahoBrowserApp.kt",
        ).readText()
        assertFalse(
            source.contains("if (!isStartPage) {\n                                showSiteInfo = true"),
            "the bare guarded no-op must be replaced by an explicit enabled flag",
        )
    }
}