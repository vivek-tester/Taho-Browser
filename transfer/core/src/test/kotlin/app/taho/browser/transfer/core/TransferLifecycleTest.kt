package app.taho.browser.transfer.core

import app.taho.browser.contract.ReceiptResult
import app.taho.browser.contract.TransferReceiptV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransferLifecycleTest {
    @Test
    fun directLifecycleRequiresExplicitConfirmationBeforeDispatch() {
        var attempt = TransferAttempt("01J8ZQ4M2K7X9V3B8N0P4R6T8Y")
        attempt = applied(attempt, TransferLifecycleEvent.Prepare)
        attempt = applied(attempt, TransferLifecycleEvent.AwaitConfirmation)

        assertEquals(TransferState.AWAITING_CONFIRMATION, attempt.state)

        attempt = applied(attempt, TransferLifecycleEvent.ConfirmAndDispatch)
        assertEquals(TransferState.TRANSFERRING, attempt.state)

        attempt = applied(
            attempt,
            TransferLifecycleEvent.Receipt(
                TransferReceiptV1(
                    transferId = attempt.transferId,
                    result = ReceiptResult.IMPORTED,
                    requestId = "request-1",
                    importedAt = 2000L,
                    errorCode = null,
                ),
            ),
        )
        assertEquals(TransferState.RECEIVED, attempt.state)
    }

    @Test
    fun receiptForAnotherTransferIsRejected() {
        var attempt = TransferAttempt("01J8ZQ4M2K7X9V3B8N0P4R6T8Y")
        attempt = applied(attempt, TransferLifecycleEvent.Prepare)
        attempt = applied(attempt, TransferLifecycleEvent.AwaitConfirmation)
        attempt = applied(attempt, TransferLifecycleEvent.ConfirmAndDispatch)

        val result = TransferLifecycle.reduce(
            attempt,
            TransferLifecycleEvent.Receipt(
                TransferReceiptV1(
                    transferId = "01J8ZQ4M2K7X9V3B8N0P4R6T8Z",
                    result = ReceiptResult.IMPORTED,
                    requestId = "request-2",
                    importedAt = 2000L,
                    errorCode = null,
                ),
            ),
        )

        assertIs<TransferLifecycleResult.Rejected>(result)
        assertEquals(TransferState.TRANSFERRING, result.attempt.state)
    }

    @Test
    fun terminalStateCannotBeReopened() {
        var attempt = TransferAttempt("01J8ZQ4M2K7X9V3B8N0P4R6T8Y")
        attempt = applied(attempt, TransferLifecycleEvent.Prepare)
        attempt = applied(attempt, TransferLifecycleEvent.Cancel)

        val result = TransferLifecycle.reduce(attempt, TransferLifecycleEvent.Prepare)
        assertIs<TransferLifecycleResult.Rejected>(result)
        assertEquals(TransferState.CANCELLED, result.attempt.state)
    }

    private fun applied(
        attempt: TransferAttempt,
        event: TransferLifecycleEvent,
    ): TransferAttempt =
        assertIs<TransferLifecycleResult.Applied>(
            TransferLifecycle.reduce(attempt, event),
        ).attempt
}
