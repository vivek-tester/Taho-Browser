package app.taho.browser.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncProtocolTest {
    @Test
    fun rejectsWeakAccountInputs() {
        assertFalse(SyncInputValidation.validEmail("not-an-email"))
        assertFalse(SyncInputValidation.validPassword("short"))
        assertFalse(SyncInputValidation.validDeviceName(""))
    }

    @Test
    fun acceptsBoundedOpaquePayloads() {
        assertTrue(SyncInputValidation.validOpaqueBase64("YWJjZA==", 32))
        assertFalse(SyncInputValidation.validOpaqueBase64("!".repeat(20), 32))
    }
}
