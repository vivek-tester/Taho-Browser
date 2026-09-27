package app.taho.browser.capture.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionReducerPropertyTest {
    @Test
    fun randomLateEventSequencesNeverChangeATerminalOutcome() {
        repeat(250) { seed ->
            val random = Random(seed)
            val identity = RequestIdentity(
                RuntimeGeneration("runtime-$seed"),
                RequestId("request-$seed"),
            )

            var state = TransactionReducer.reduce(
                null,
                identity,
                CaptureEvent.Start("start"),
            ).state

            val terminalEvent = when (random.nextInt(4)) {
                0 -> CaptureEvent.Completed("terminal", 200)
                1 -> CaptureEvent.Failed("terminal", "network")
                2 -> CaptureEvent.Cancelled("terminal", "user")
                else -> CaptureEvent.RecoverInterrupted(
                    "terminal",
                    PartialReason.PROCESS_DIED,
                )
            }
            state = TransactionReducer.reduce(
                state,
                identity,
                terminalEvent,
            ).state

            val terminal = state.transaction.state

            repeat(40) { index ->
                val event = when (random.nextInt(5)) {
                    0 -> CaptureEvent.HeadersCaptured("late-h-$index")
                    1 -> CaptureEvent.ResponseStarted("late-r-$index")
                    2 -> CaptureEvent.RequestBodyObserved(
                        "late-b-$index",
                        FieldEvidence(
                            completeness = Completeness.PARTIAL,
                            capturedBytes = index.toLong(),
                        ),
                    )
                    3 -> CaptureEvent.Completed("late-c-$index", 204)
                    else -> CaptureEvent.Failed("late-f-$index", "late")
                }
                state = TransactionReducer.reduce(state, identity, event).state
                assertEquals(terminal, state.transaction.state)
            }
        }
    }
}
