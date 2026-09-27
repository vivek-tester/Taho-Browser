package app.taho.browser.transfer.core

import app.taho.browser.contract.TransferErrorCode
import app.taho.browser.contract.TransferReceiptV1

enum class TransferState {
    NOT_STARTED,
    PREPARING,
    AWAITING_CONFIRMATION,
    TRANSFERRING,
    RECEIVED,
    FAILED,
    CANCELLED,
    EXPIRED,
}

data class TransferAttempt(
    val transferId: String,
    val state: TransferState = TransferState.NOT_STARTED,
    val receipt: TransferReceiptV1? = null,
    val errorCode: TransferErrorCode? = null,
)

sealed interface TransferLifecycleEvent {
    data object Prepare : TransferLifecycleEvent
    data object AwaitConfirmation : TransferLifecycleEvent
    data object ConfirmAndDispatch : TransferLifecycleEvent
    data class Receipt(val value: TransferReceiptV1) : TransferLifecycleEvent
    data class Fail(val code: TransferErrorCode) : TransferLifecycleEvent
    data object Cancel : TransferLifecycleEvent
    data object Expire : TransferLifecycleEvent
}

sealed interface TransferLifecycleResult {
    data class Applied(val attempt: TransferAttempt) : TransferLifecycleResult
    data class Rejected(val attempt: TransferAttempt, val reason: String) : TransferLifecycleResult
}

object TransferLifecycle {
    fun reduce(
        attempt: TransferAttempt,
        event: TransferLifecycleEvent,
    ): TransferLifecycleResult {
        if (attempt.state in TERMINAL) {
            return TransferLifecycleResult.Rejected(attempt, "transfer is terminal")
        }

        val next = when (event) {
            TransferLifecycleEvent.Prepare ->
                if (attempt.state == TransferState.NOT_STARTED) {
                    attempt.copy(state = TransferState.PREPARING)
                } else {
                    return invalid(attempt, event)
                }

            TransferLifecycleEvent.AwaitConfirmation ->
                if (attempt.state == TransferState.PREPARING) {
                    attempt.copy(state = TransferState.AWAITING_CONFIRMATION)
                } else {
                    return invalid(attempt, event)
                }

            TransferLifecycleEvent.ConfirmAndDispatch ->
                if (attempt.state == TransferState.AWAITING_CONFIRMATION) {
                    attempt.copy(state = TransferState.TRANSFERRING)
                } else {
                    return invalid(attempt, event)
                }

            is TransferLifecycleEvent.Receipt -> {
                if (attempt.state != TransferState.TRANSFERRING) {
                    return invalid(attempt, event)
                }
                if (event.value.transferId != attempt.transferId) {
                    return TransferLifecycleResult.Rejected(
                        attempt,
                        "receipt transferId mismatch",
                    )
                }
                attempt.copy(
                    state = TransferState.RECEIVED,
                    receipt = event.value,
                    errorCode = event.value.errorCode,
                )
            }

            is TransferLifecycleEvent.Fail ->
                attempt.copy(
                    state = TransferState.FAILED,
                    errorCode = event.code,
                )

            TransferLifecycleEvent.Cancel ->
                attempt.copy(
                    state = TransferState.CANCELLED,
                    errorCode = TransferErrorCode.TAHO_TRANSFER_CANCELLED,
                )

            TransferLifecycleEvent.Expire ->
                if (attempt.state == TransferState.TRANSFERRING) {
                    attempt.copy(
                        state = TransferState.EXPIRED,
                        errorCode = TransferErrorCode.TAHO_TRANSFER_URI_EXPIRED,
                    )
                } else {
                    return invalid(attempt, event)
                }
        }

        return TransferLifecycleResult.Applied(next)
    }

    private fun invalid(
        attempt: TransferAttempt,
        event: TransferLifecycleEvent,
    ) = TransferLifecycleResult.Rejected(
        attempt,
        "event " + event::class.simpleName + " is invalid from " + attempt.state,
    )

    private val TERMINAL = setOf(
        TransferState.RECEIVED,
        TransferState.FAILED,
        TransferState.CANCELLED,
        TransferState.EXPIRED,
    )
}
