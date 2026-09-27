package app.taho.browser.shell

import kotlin.test.Test
import kotlin.test.assertFailsWith

class M4CaptureUiModelTest {
    @Test
    fun sensitiveHeaderRejectsPlaintextDisplayValue() {
        assertFailsWith<IllegalArgumentException> {
            M4CaptureHeaderUiState(
                name = "Authorization",
                displayValue = "Bearer live-token",
                sensitive = true,
            )
        }
    }

    @Test
    fun sensitiveHeaderAcceptsMaskedOrParameterizedValue() {
        M4CaptureHeaderUiState(
            name = "Authorization",
            displayValue = "Bearer ••••••••",
            sensitive = true,
        )
        M4CaptureHeaderUiState(
            name = "X-Api-Key",
            displayValue = "{{API_KEY}}",
            sensitive = true,
        )
    }
}
