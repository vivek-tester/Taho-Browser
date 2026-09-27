package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransactionReducerTest {
    private val identity = RequestIdentity(
        runtimeGeneration = RuntimeGeneration("runtime-1"),
        requestId = RequestId("request-1"),
    )

    @Test
    fun processDeathCanTruthfullyRecoverAStartedTransactionAsPartial() {
        val started = TransactionReducer.reduce(
            current = null,
            identity = identity,
            event = CaptureEvent.Start("e1"),
        )

        val recovered = TransactionReducer.reduce(
            current = started.state,
            identity = identity,
            event = CaptureEvent.RecoverInterrupted("e2", PartialReason.PROCESS_DIED),
        )

        assertEquals(TransactionState.PARTIAL, recovered.state.transaction.state)
        assertEquals("PROCESS_DIED", recovered.state.transaction.terminalReason)
    }

    @Test
    fun bodyTruncationDoesNotTerminateTheTransaction() {
        val started = TransactionReducer.reduce(
            null,
            identity,
            CaptureEvent.Start("e1"),
        )
        val truncated = TransactionReducer.reduce(
            started.state,
            identity,
            CaptureEvent.RequestBodyObserved(
                "e2",
                FieldEvidence(
                    completeness = Completeness.TRUNCATED,
                    capturedBytes = 8L * 1024 * 1024,
                    declaredBytes = 10L * 1024 * 1024,
                    capBytes = 8L * 1024 * 1024,
                ),
            ),
        )

        assertEquals(TransactionState.STARTED, truncated.state.transaction.state)

        val completed = TransactionReducer.reduce(
            truncated.state,
            identity,
            CaptureEvent.Completed("e3", statusCode = 201),
        )

        assertEquals(TransactionState.COMPLETED, completed.state.transaction.state)
        assertEquals(
            Completeness.TRUNCATED,
            completed.state.transaction.requestBody.completeness,
        )
    }

    @Test
    fun missingHeadersAreRepresentedInsteadOfDroppingTheResponse() {
        val provisional = TransactionReducer.reduce(
            current = null,
            identity = identity,
            event = CaptureEvent.ResponseStarted("e1"),
        )

        assertTrue(provisional.provisional)
        assertEquals(TransactionState.RESPONSE_STARTED, provisional.state.transaction.state)
        assertTrue(
            provisional.diagnostics.any {
                it.code == ReducerDiagnosticCode.MISSING_START
            },
        )
        assertTrue(
            provisional.diagnostics.any {
                it.code == ReducerDiagnosticCode.MISSING_HEADERS
            },
        )
    }

    @Test
    fun attributionResolvesOnceAndConflictingEvidenceDoesNotRewriteOwnership() {
        val started = TransactionReducer.reduce(
            null,
            identity,
            CaptureEvent.Start("e1"),
        )
        val resolved = TransactionReducer.reduce(
            started.state,
            identity,
            CaptureEvent.ResolveAttribution("e2", BrowserTabId("tab-a")),
        )
        val conflicting = TransactionReducer.reduce(
            resolved.state,
            identity,
            CaptureEvent.ResolveAttribution("e3", BrowserTabId("tab-b")),
        )

        val attribution = assertIs<Attribution.Resolved>(
            conflicting.state.transaction.attribution,
        )
        assertEquals(BrowserTabId("tab-a"), attribution.browserTabId)
        assertTrue(
            conflicting.diagnostics.any {
                it.code == ReducerDiagnosticCode.ATTRIBUTION_CONFLICT
            },
        )
    }

    @Test
    fun detachingLiveBindingDoesNotChangeOriginAttribution() {
        val started = TransactionReducer.reduce(
            null,
            identity,
            CaptureEvent.Start("e1"),
        )
        val resolved = TransactionReducer.reduce(
            started.state,
            identity,
            CaptureEvent.ResolveAttribution("e2", BrowserTabId("tab-a")),
        )
        val detached = TransactionReducer.reduce(
            resolved.state,
            identity,
            CaptureEvent.BindingChanged("e3", BindingStatus.DETACHED),
        )

        assertEquals(BindingStatus.DETACHED, detached.state.transaction.bindingStatus)
        assertEquals(resolved.state.transaction.attribution, detached.state.transaction.attribution)
    }

    @Test
    fun terminalOutcomeRejectsLateCaptureMutation() {
        val started = TransactionReducer.reduce(
            null,
            identity,
            CaptureEvent.Start("e1"),
        )
        val complete = TransactionReducer.reduce(
            started.state,
            identity,
            CaptureEvent.Completed("e2", 200),
        )
        val before = complete.state.transaction

        val late = TransactionReducer.reduce(
            complete.state,
            identity,
            CaptureEvent.RequestBodyObserved(
                "e3",
                FieldEvidence(Completeness.COMPLETE, capturedBytes = 10),
            ),
        )

        assertEquals(before, late.state.transaction)
        assertTrue(late.diagnostics.any { it.code == ReducerDiagnosticCode.LATE_EVENT })
    }

    @Test
    fun duplicateEventIsIdempotent() {
        val started = TransactionReducer.reduce(
            null,
            identity,
            CaptureEvent.Start("e1"),
        )
        val duplicate = TransactionReducer.reduce(
            started.state,
            identity,
            CaptureEvent.Start("e1"),
        )

        assertEquals(started.state, duplicate.state)
        assertTrue(
            duplicate.diagnostics.any {
                it.code == ReducerDiagnosticCode.DUPLICATE_EVENT
            },
        )
        assertFalse(duplicate.provisional)
    }
}
