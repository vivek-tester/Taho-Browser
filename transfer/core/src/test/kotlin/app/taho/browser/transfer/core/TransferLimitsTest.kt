package app.taho.browser.transfer.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferLimitsTest {
    @Test
    fun directBudgetBoundaryIsExplicit() {
        assertFalse(
            TransferLimits.requiresLargePayloadPath(
                TransferLimits.DIRECT_TRANSFER_BYTES.toLong(),
            ),
        )
        assertTrue(
            TransferLimits.requiresLargePayloadPath(
                TransferLimits.DIRECT_TRANSFER_BYTES.toLong() + 1,
            ),
        )
    }
}
