package app.taho.browser.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TahoThemeContractTest {

    @Test
    fun panelsAreSharpAndControlsAreTwoDp() {
        assertEquals(0.dp, TahoSheetShape)
        assertEquals(0.dp, TahoCardShape)
        assertEquals(2.dp, TahoBlockShape)
        assertEquals(2.dp, TahoNoteShape)
        assertEquals(4.dp, TahoBadgeShape)
        assertEquals(999.dp, TahoPillShape)
    }

    @Test
    fun durationsAreShortened() {
        assertEquals(300, TahoDurationScreen)
        assertEquals(300, TahoDurationSheet)
        assertEquals(200, TahoDurationVeil)
    }
}