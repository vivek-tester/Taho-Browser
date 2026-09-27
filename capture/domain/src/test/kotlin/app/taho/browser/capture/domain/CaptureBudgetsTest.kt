package app.taho.browser.capture.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CaptureBudgetsTest {
    @Test
    fun defaultsMatchArchitectureBudgets() {
        val budgets = CaptureBudgets()

        assertEquals(64L * 1024L, budgets.inlineBodyCiphertextBytes)
        assertEquals(8L * 1024L * 1024L, budgets.maxBodyBytes)
        assertEquals(512, budgets.unparsedQueueDepth)
        assertEquals(2_048, budgets.parsedQueueDepth)
        assertEquals(4_000, budgets.portReadRatePerSecond)
        assertEquals(2_000, budgets.maxStreamFrames)
        assertEquals(50_000, budgets.maxTransactionsPerSession)
        assertEquals(250_000, budgets.maxTransactionsTotal)
        assertEquals(512L * 1024L * 1024L, budgets.captureDbSoftBytes)
        assertEquals(32L * 1024L * 1024L, budgets.transferArtifactBytes)
    }

    @Test
    fun messageCapsMatchIpcCatalogue() {
        assertEquals(4 * 1024, IngressMessageType.TAB_REGISTER.payloadCapBytes)
        assertEquals(1 * 1024, IngressMessageType.TAB_TEARDOWN.payloadCapBytes)
        assertEquals(32 * 1024, IngressMessageType.TX_START.payloadCapBytes)
        assertEquals(32 * 1024, IngressMessageType.TX_REQ_HEADERS.payloadCapBytes)
        assertEquals(256 * 1024, IngressMessageType.TX_REQ_BODY.payloadCapBytes)
        assertEquals(16 * 1024, IngressMessageType.TX_RESP_START.payloadCapBytes)
        assertEquals(256 * 1024, IngressMessageType.TX_RESP_BODY.payloadCapBytes)
        assertEquals(8 * 1024, IngressMessageType.TX_COMPLETE.payloadCapBytes)
        assertEquals(1 * 1024, IngressMessageType.HEARTBEAT.payloadCapBytes)
    }

    @Test
    fun oversizeMessageIsRejectedWhole() {
        val cap = IngressMessageType.TX_REQ_BODY.payloadCapBytes

        assertIs<PayloadBudgetVerdict.Accept>(
            PayloadBudgetGate.check(IngressMessageType.TX_REQ_BODY, cap),
        )

        val rejected = PayloadBudgetGate.check(
            IngressMessageType.TX_REQ_BODY,
            cap + 1,
        )
        assertIs<PayloadBudgetVerdict.Oversize>(rejected)
        assertEquals(cap + 1, rejected.actualBytes)
        assertEquals(cap, rejected.capBytes)
    }

    @Test
    fun bodyTrackerNeverPartiallyAcceptsChunkPastCeiling() {
        val tracker = BodyBudgetTracker(capBytes = 10)

        assertEquals(
            BodyBudgetVerdict.Accepted(6),
            tracker.offerChunk(6),
        )

        val truncated = tracker.offerChunk(5)
        assertIs<BodyBudgetVerdict.Truncated>(truncated)
        assertEquals(6, tracker.capturedBytes)
        assertTrue(tracker.truncated)

        val later = tracker.offerChunk(1)
        assertIs<BodyBudgetVerdict.AlreadyTruncated>(later)
        assertEquals(6, tracker.capturedBytes)
    }
}
