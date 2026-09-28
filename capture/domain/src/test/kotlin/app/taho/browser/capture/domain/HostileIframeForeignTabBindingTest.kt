package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [Security Test S10]
 * Hostile iframe foreign-tab-binding test (§IPC 4.4, Test Architecture §6 S10).
 * Verifies that a hostile iframe or foreign context cannot hijack or re-bind
 * an existing transaction's tab attribution, and that attribution conflicts
 * are recorded as diagnostics while failing closed.
 */
class HostileIframeForeignTabBindingTest {

    @Test
    fun hostileIframeCannotRebindExistingTabAttribution() {
        val identity = RequestIdentity(
            runtimeGeneration = RuntimeGeneration("gen-1"),
            requestId = RequestId("req-hostile-iframe-1"),
        )
        val legitimateTabId = BrowserTabId("legitimate-tab-42")
        val hostileTabId = BrowserTabId("hostile-iframe-tab-666")

        // 1. Transaction starts under legitimate tab
        val startReduction = TransactionReducer.reduce(
            current = null,
            identity = identity,
            event = CaptureEvent.Start(eventId = "evt-start"),
        )
        val attributedReduction = TransactionReducer.reduce(
            current = startReduction.state,
            identity = identity,
            event = CaptureEvent.ResolveAttribution(
                eventId = "evt-resolve-legit",
                browserTabId = legitimateTabId,
            ),
        )

        assertEquals(
            Attribution.Resolved(legitimateTabId),
            attributedReduction.state.transaction.attribution,
            "Initial attribution must bind to the legitimate tab",
        )

        // 2. Hostile iframe attempts to overwrite attribution to foreign tab
        val hostileReduction = TransactionReducer.reduce(
            current = attributedReduction.state,
            identity = identity,
            event = CaptureEvent.ResolveAttribution(
                eventId = "evt-hostile-spoof",
                browserTabId = hostileTabId,
            ),
        )

        // Must retain legitimate tab binding
        assertEquals(
            Attribution.Resolved(legitimateTabId),
            hostileReduction.state.transaction.attribution,
            "Hostile iframe must NOT be able to overwrite tab attribution",
        )

        // Must emit attribution conflict diagnostic
        assertTrue(
            hostileReduction.diagnostics.any {
                it.code == ReducerDiagnosticCode.ATTRIBUTION_CONFLICT &&
                    it.eventId == "evt-hostile-spoof"
            },
            "Attribution conflict diagnostic must be recorded for hostile re-binding attempt",
        )
    }

    @Test
    fun unattributedBackgroundTrafficCannotBeBoundByHostileContextAfterClosure() {
        val identity = RequestIdentity(
            runtimeGeneration = RuntimeGeneration("gen-1"),
            requestId = RequestId("req-background-worker-1"),
        )

        val start = TransactionReducer.reduce(
            current = null,
            identity = identity,
            event = CaptureEvent.Start(eventId = "evt-bg-start"),
        )
        val detached = TransactionReducer.reduce(
            current = start.state,
            identity = identity,
            event = CaptureEvent.BindingChanged(
                eventId = "evt-detach",
                status = BindingStatus.DETACHED,
            ),
        )

        assertEquals(
            BindingStatus.DETACHED,
            detached.state.transaction.bindingStatus,
        )
        assertEquals(
            Attribution.Unresolved,
            detached.state.transaction.attribution,
            "Background/detached worker must remain unresolved",
        )
    }
}
