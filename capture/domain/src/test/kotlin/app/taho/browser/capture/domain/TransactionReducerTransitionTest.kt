package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransactionReducerTransitionTest {
    private val identity = RequestIdentity(
        runtimeGeneration = RuntimeGeneration("runtime"),
        requestId = RequestId("request"),
    )

    @Test
    fun everyNonTerminalOutcomeCanBeRecoveredAsPartial() {
        val states = listOf(
            stateAfter(CaptureEvent.Start("start")),
            stateAfter(
                CaptureEvent.Start("start"),
                CaptureEvent.HeadersCaptured("headers"),
            ),
            stateAfter(
                CaptureEvent.Start("start"),
                CaptureEvent.HeadersCaptured("headers"),
                CaptureEvent.ResponseStarted("response"),
            ),
        )

        states.forEachIndexed { index, state ->
            val reduced = TransactionReducer.reduce(
                state,
                identity,
                CaptureEvent.RecoverInterrupted(
                    eventId = "recover-$index",
                    reason = PartialReason.PROCESS_DIED,
                ),
            )
            assertEquals(TransactionState.PARTIAL, reduced.state.transaction.state)
            assertEquals("PROCESS_DIED", reduced.state.transaction.terminalReason)
        }
    }

    @Test
    fun allTerminalOutcomesKeepTheirOutcomeWhenLateTrafficArrives() {
        val terminalStates = listOf(
            terminal(CaptureEvent.Completed("done", 200)),
            terminal(CaptureEvent.Failed("failed", "network")),
            terminal(CaptureEvent.Cancelled("cancelled", "user")),
            terminal(
                CaptureEvent.RecoverInterrupted(
                    "partial",
                    PartialReason.OBSERVATION_ENDED,
                ),
            ),
        )

        terminalStates.forEachIndexed { index, state ->
            val before = state.transaction
            val reduced = TransactionReducer.reduce(
                state,
                identity,
                CaptureEvent.ResponseBodyObserved(
                    "late-$index",
                    FieldEvidence(
                        completeness = Completeness.COMPLETE,
                        capturedBytes = 1,
                    ),
                ),
            )

            assertEquals(before, reduced.state.transaction)
            assertTrue(
                reduced.diagnostics.any {
                    it.code == ReducerDiagnosticCode.LATE_EVENT
                },
            )
        }
    }

    @Test
    fun truncatedEvidenceRequiresTheCapThatCausedIt() {
        assertFailsWith<IllegalArgumentException> {
            FieldEvidence(
                completeness = Completeness.TRUNCATED,
                capturedBytes = 10,
                declaredBytes = 20,
                capBytes = null,
            )
        }
    }

    @Test
    fun reducerRejectsStateFromAnotherRuntimeScopedRequest() {
        val state = stateAfter(CaptureEvent.Start("start"))
        val other = RequestIdentity(
            runtimeGeneration = RuntimeGeneration("runtime-2"),
            requestId = RequestId("request"),
        )

        assertFailsWith<IllegalArgumentException> {
            TransactionReducer.reduce(
                state,
                other,
                CaptureEvent.HeadersCaptured("headers"),
            )
        }
    }

    @Test
    fun boundedDedupWindowDoesNotGrowWithoutLimit() {
        var state = stateAfter(CaptureEvent.Start("start"))

        repeat(200) { index ->
            state = TransactionReducer.reduce(
                state,
                identity,
                CaptureEvent.RequestBodyObserved(
                    eventId = "body-$index",
                    evidence = FieldEvidence(
                        completeness = Completeness.PARTIAL,
                        capturedBytes = index.toLong(),
                    ),
                ),
            ).state
        }

        assertEquals(64, state.recentEventIds.size)
        assertEquals("body-199", state.recentEventIds.last())
    }

    private fun stateAfter(vararg events: CaptureEvent): ReductionState {
        var state: ReductionState? = null
        events.forEach { event ->
            state = TransactionReducer.reduce(state, identity, event).state
        }
        return requireNotNull(state)
    }

    private fun terminal(event: CaptureEvent): ReductionState =
        stateAfter(CaptureEvent.Start("start-${event.eventId}"), event)
}
