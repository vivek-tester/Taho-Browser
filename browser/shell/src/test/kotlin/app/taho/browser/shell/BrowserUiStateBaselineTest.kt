package app.taho.browser.shell

import app.taho.browser.capture.domain.CaptureState
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserUiStateBaselineTest {
    @Test
    fun browserStartsWithCaptureOffAndNoSyntheticRequestCount() {
        val state = BrowserUiState()

        assertEquals(CaptureState.OFF, state.captureState)
        assertEquals(0, state.relevantCount)
    }
}
