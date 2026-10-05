package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Captured request data must be reviewable AND erasable. `M7SettingsSheet` is
 * the only UI for retention mode and for deleting captures; before this fix it
 * had no call sites at all, so both were unreachable.
 *
 * These assert on production source rather than on composed output. The defect
 * is reachability, and the only questions that can fail here are "does a call
 * site exist", "does it hand over the live handlers" and "can the back gesture
 * dismiss it" — none of which a re-derived copy of the rule could answer.
 */
class TahoCaptureSettingsReachabilityTest {

    private val productUx = File("src/main/java/app/taho/browser/shell/M7ProductUx.kt")
    private val browserApp = File("src/main/java/app/taho/browser/shell/TahoBrowserApp.kt")

    /**
     * The plan's original predicate — `composed || fromApp` — was satisfied by
     * the composable's own declaration line, so it could never fail. A call
     * site is the only thing that makes retention and deletion reachable.
     */
    @Test
    fun theCaptureSettingsSheetIsActuallyComposed() {
        assertTrue(
            browserApp.readText().contains("M7SettingsSheet("),
            "M7SettingsSheet must have a call site, or retention and deletion stay unreachable",
        )
    }

    @Test
    fun theSummarySheetOffersAnEntryPointToThem() {
        val summary = productUx.readText()
            .substringAfter("M7CaptureSummarySheet(")
            .take(4000)
        assertTrue(
            summary.contains("onOpenCaptureSettings") || summary.contains("M7SettingsSheet("),
            "the capture summary needs a control that opens capture settings",
        )
    }

    /**
     * Stronger than "the parameter name appears somewhere in the file": both
     * handlers reached TahoBrowserApp's signature long ago and dead-ended
     * there, so only the call site proves they now reach the UI.
     */
    @Test
    fun theShellForwardsBothHandlersIntoThatCallSite() {
        val call = browserApp.readText().substringAfter("M7SettingsSheet(").take(700)
        assertTrue(
            call.contains("onRetentionModeChanged = onRetentionModeChanged") &&
                call.contains("onClearCaptureData = onClearCaptureData"),
            "both live handlers must be passed to the settings sheet",
        )
        assertTrue(
            call.contains("captureCapabilityNote = state.captureCapabilityNote") &&
                call.contains("retentionMode = state.retentionMode"),
            "the sheet must be given the state it renders, not its own defaults",
        )
    }

    /**
     * A flag registered in only one of the two places strands the sheet: the
     * predicate decides whether the handler is installed at all, and the
     * dispatch decides what it does. Both are asserted separately.
     */
    @Test
    fun theBackHandlerEnablesAndDispatchesForTheNewFlag() {
        val app = browserApp.readText()
        assertTrue(
            Regex("enabled\\s*=\\s*[^)]*showCaptureSettings").containsMatchIn(app),
            "showCaptureSettings must join the BackHandler's canHandleBack predicate — " +
                "a flag missing from it never installs the handler",
        )
        assertTrue(
            Regex("showCaptureSettings\\s*->\\s*showCaptureSettings\\s*=\\s*false").containsMatchIn(app),
            "the dispatch branch must clear the flag, or the back gesture cannot dismiss the sheet",
        )
    }
}