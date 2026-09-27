package app.taho.browser.capture.domain

private const val EVENT_WINDOW_SIZE = 64

enum class TransactionState {
    STARTED,
    HEADERS_CAPTURED,
    RESPONSE_STARTED,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED ||
            this == PARTIAL ||
            this == FAILED ||
            this == CANCELLED
}

enum class PartialReason {
    PROCESS_DIED,
    OBSERVATION_ENDED,
    MISSED_EVENTS,
}

enum class ReducerDiagnosticCode {
    MISSING_START,
    MISSING_HEADERS,
    MISSING_RESPONSE_START,
    DUPLICATE_EVENT,
    DUPLICATE_START,
    LATE_EVENT,
    ATTRIBUTION_CONFLICT,
}

data class ReducerDiagnostic(
    val code: ReducerDiagnosticCode,
    val eventId: String,
)

data class TransactionRecord(
    val identity: RequestIdentity,
    val state: TransactionState = TransactionState.STARTED,
    val attribution: Attribution = Attribution.Unresolved,
    val bindingStatus: BindingStatus = BindingStatus.ATTACHED,
    val requestHeaders: Completeness = Completeness.UNAVAILABLE,
    val requestBody: FieldEvidence = FieldEvidence(Completeness.UNAVAILABLE),
    val responseHeaders: Completeness = Completeness.UNAVAILABLE,
    val responseBody: FieldEvidence = FieldEvidence(Completeness.UNAVAILABLE),
    val statusCode: Int? = null,
    val terminalReason: String? = null,
)

data class ReductionState(
    val transaction: TransactionRecord,
    val recentEventIds: List<String> = emptyList(),
)

data class Reduction(
    val state: ReductionState,
    val diagnostics: List<ReducerDiagnostic> = emptyList(),
    val provisional: Boolean = false,
)

sealed interface CaptureEvent {
    val eventId: String

    data class Start(override val eventId: String) : CaptureEvent

    data class HeadersCaptured(
        override val eventId: String,
        val completeness: Completeness = Completeness.COMPLETE,
    ) : CaptureEvent

    data class RequestBodyObserved(
        override val eventId: String,
        val evidence: FieldEvidence,
    ) : CaptureEvent

    data class ResponseStarted(override val eventId: String) : CaptureEvent

    data class ResponseHeadersObserved(
        override val eventId: String,
        val completeness: Completeness = Completeness.COMPLETE,
    ) : CaptureEvent

    data class ResponseBodyObserved(
        override val eventId: String,
        val evidence: FieldEvidence,
    ) : CaptureEvent

    data class Completed(
        override val eventId: String,
        val statusCode: Int?,
    ) : CaptureEvent

    data class Failed(
        override val eventId: String,
        val reason: String,
    ) : CaptureEvent

    data class Cancelled(
        override val eventId: String,
        val reason: String,
    ) : CaptureEvent

    data class RecoverInterrupted(
        override val eventId: String,
        val reason: PartialReason,
    ) : CaptureEvent

    data class ResolveAttribution(
        override val eventId: String,
        val browserTabId: BrowserTabId,
    ) : CaptureEvent

    data class BindingChanged(
        override val eventId: String,
        val status: BindingStatus,
    ) : CaptureEvent
}

object TransactionReducer {
    fun reduce(
        current: ReductionState?,
        identity: RequestIdentity,
        event: CaptureEvent,
    ): Reduction {
        if (current != null) {
            require(current.transaction.identity == identity) {
                "Reducer state belongs to a different request identity"
            }
            if (event.eventId in current.recentEventIds) {
                return Reduction(
                    state = current,
                    diagnostics = listOf(
                        ReducerDiagnostic(ReducerDiagnosticCode.DUPLICATE_EVENT, event.eventId),
                    ),
                )
            }
        }

        val provisional = current == null && event !is CaptureEvent.Start
        var tx = current?.transaction ?: TransactionRecord(identity)
        val diagnostics = mutableListOf<ReducerDiagnostic>()

        if (provisional) {
            diagnostics += ReducerDiagnostic(ReducerDiagnosticCode.MISSING_START, event.eventId)
        }

        when (event) {
            is CaptureEvent.ResolveAttribution -> {
                tx = when (val attribution = tx.attribution) {
                    Attribution.Unresolved -> tx.copy(
                        attribution = Attribution.Resolved(event.browserTabId),
                    )
                    is Attribution.Resolved -> {
                        if (attribution.browserTabId != event.browserTabId) {
                            diagnostics += ReducerDiagnostic(
                                ReducerDiagnosticCode.ATTRIBUTION_CONFLICT,
                                event.eventId,
                            )
                        }
                        tx
                    }
                }
                return consumed(current, tx, event, diagnostics, provisional)
            }

            is CaptureEvent.BindingChanged -> {
                tx = tx.copy(bindingStatus = event.status)
                return consumed(current, tx, event, diagnostics, provisional)
            }

            else -> Unit
        }

        if (tx.state.isTerminal) {
            diagnostics += ReducerDiagnostic(ReducerDiagnosticCode.LATE_EVENT, event.eventId)
            return Reduction(
                state = current ?: ReductionState(tx),
                diagnostics = diagnostics,
                provisional = provisional,
            )
        }

        tx = when (event) {
            is CaptureEvent.Start -> {
                if (current != null) {
                    diagnostics += ReducerDiagnostic(
                        ReducerDiagnosticCode.DUPLICATE_START,
                        event.eventId,
                    )
                }
                tx
            }

            is CaptureEvent.HeadersCaptured -> when (tx.state) {
                TransactionState.STARTED -> tx.copy(
                    state = TransactionState.HEADERS_CAPTURED,
                    requestHeaders = event.completeness,
                )
                TransactionState.HEADERS_CAPTURED,
                TransactionState.RESPONSE_STARTED -> tx.copy(
                    requestHeaders = event.completeness,
                )
                else -> tx
            }

            is CaptureEvent.RequestBodyObserved -> tx.copy(requestBody = event.evidence)

            is CaptureEvent.ResponseStarted -> when (tx.state) {
                TransactionState.STARTED -> {
                    diagnostics += ReducerDiagnostic(
                        ReducerDiagnosticCode.MISSING_HEADERS,
                        event.eventId,
                    )
                    tx.copy(state = TransactionState.RESPONSE_STARTED)
                }
                TransactionState.HEADERS_CAPTURED -> tx.copy(
                    state = TransactionState.RESPONSE_STARTED,
                )
                TransactionState.RESPONSE_STARTED -> tx
                else -> tx
            }

            is CaptureEvent.ResponseHeadersObserved -> {
                if (tx.state == TransactionState.STARTED) {
                    diagnostics += ReducerDiagnostic(
                        ReducerDiagnosticCode.MISSING_HEADERS,
                        event.eventId,
                    )
                }
                tx.copy(
                    state = TransactionState.RESPONSE_STARTED,
                    responseHeaders = event.completeness,
                )
            }

            is CaptureEvent.ResponseBodyObserved -> tx.copy(responseBody = event.evidence)

            is CaptureEvent.Completed -> {
                when (tx.state) {
                    TransactionState.STARTED -> {
                        diagnostics += ReducerDiagnostic(
                            ReducerDiagnosticCode.MISSING_HEADERS,
                            event.eventId,
                        )
                        diagnostics += ReducerDiagnostic(
                            ReducerDiagnosticCode.MISSING_RESPONSE_START,
                            event.eventId,
                        )
                    }
                    TransactionState.HEADERS_CAPTURED -> diagnostics += ReducerDiagnostic(
                        ReducerDiagnosticCode.MISSING_RESPONSE_START,
                        event.eventId,
                    )
                    else -> Unit
                }
                tx.copy(
                    state = TransactionState.COMPLETED,
                    statusCode = event.statusCode,
                )
            }

            is CaptureEvent.Failed -> tx.copy(
                state = TransactionState.FAILED,
                terminalReason = event.reason,
            )

            is CaptureEvent.Cancelled -> tx.copy(
                state = TransactionState.CANCELLED,
                terminalReason = event.reason,
            )

            is CaptureEvent.RecoverInterrupted -> tx.copy(
                state = TransactionState.PARTIAL,
                terminalReason = event.reason.name,
            )

            is CaptureEvent.ResolveAttribution,
            is CaptureEvent.BindingChanged -> error("handled above")
        }

        if (event is CaptureEvent.Start && current != null) {
            return Reduction(
                state = current,
                diagnostics = diagnostics,
                provisional = provisional,
            )
        }

        return consumed(current, tx, event, diagnostics, provisional)
    }

    private fun consumed(
        previous: ReductionState?,
        transaction: TransactionRecord,
        event: CaptureEvent,
        diagnostics: List<ReducerDiagnostic>,
        provisional: Boolean,
    ): Reduction {
        val ids = ((previous?.recentEventIds ?: emptyList()) + event.eventId)
            .takeLast(EVENT_WINDOW_SIZE)
        return Reduction(
            state = ReductionState(transaction = transaction, recentEventIds = ids),
            diagnostics = diagnostics,
            provisional = provisional,
        )
    }
}
