package app.taho.browser.shell

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class TahoThemeContractTest {

    /**
     * Spec §2.3 states corner radii in dp. `Shape` exposes no radius accessor --
     * only `CornerSize`, which converts to px against a `Density` -- so the
     * contract is asserted as shape equality against the dp literal the spec
     * names. Comparing a `Dp` to a `RoundedCornerShape` directly, as this file
     * used to, never type-checks.
     */
    @Test
    fun panelsAreSharpAndControlsAreTwoDp() {
        assertEquals(RoundedCornerShape(0.dp), TahoSheetShape, "sheet corners must be sharp")
        assertEquals(RoundedCornerShape(0.dp), TahoCardShape, "card corners must be sharp")
        assertEquals(RoundedCornerShape(2.dp), TahoBlockShape, "blocks are machined at 2dp")
        assertEquals(RoundedCornerShape(2.dp), TahoNoteShape, "notes are machined at 2dp")
        assertEquals(RoundedCornerShape(4.dp), TahoBadgeShape)
        assertEquals(RoundedCornerShape(999.dp), TahoPillShape, "the pill is reserved for tags and progress")
    }

    @Test
    fun durationsAreShortened() {
        assertEquals(300, TahoDurationScreen)
        assertEquals(300, TahoDurationSheet)
        assertEquals(200, TahoDurationVeil)
    }
}