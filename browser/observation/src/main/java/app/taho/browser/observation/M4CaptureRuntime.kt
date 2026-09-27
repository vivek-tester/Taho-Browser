package app.taho.browser.observation

import app.taho.browser.capture.domain.CaptureState
import app.taho.browser.capture.domain.SecretPolicy
import app.taho.browser.transfer.core.M4CapturedRequest
import app.taho.browser.transfer.core.M4DisplayRequest
import app.taho.browser.transfer.core.M4PreparationResult
import app.taho.browser.transfer.core.M4TransferPreparer
import app.taho.browser.transfer.core.UlidGenerator
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

data class ObservedBrowserSession(
    val tahoTabId: String,
    val session: GeckoSession,
    val isPrivate: Boolean,
    val committedUrl: () -> String?,
)

data class M4CaptureRuntimeSnapshot(
    val state: CaptureState,
    val requests: List<M4CapturedRequest>,
)

class M4CaptureRuntime(
    runtime: GeckoRuntime,
    gate: ProductionCaptureGate,
    appVersion: String,
    engineVersion: String,
) {
    private val assembler = M4InMemoryCaptureAssembler(
        appVersion = appVersion,
        engineVersion = engineVersion,
        captureSessionId = UlidGenerator.next(),
    )
    private val coordinator = ProductionObservationCoordinator(
        runtime = runtime,
        gate = gate,
        sink = ::onObservation,
    )
    private val trackedSessions = mutableMapOf<String, ObservedBrowserSession>()
    private var listener: ((M4CaptureRuntimeSnapshot) -> Unit)? = null
    private var snapshot = M4CaptureRuntimeSnapshot(
        state = CaptureState.OFF,
        requests = emptyList(),
    )

    fun snapshot(): M4CaptureRuntimeSnapshot = snapshot

    fun setListener(listener: ((M4CaptureRuntimeSnapshot) -> Unit)?) {
        this.listener = listener
        listener?.invoke(snapshot)
    }

    fun start() {
        coordinator.start()
    }

    fun syncSessions(sessions: List<ObservedBrowserSession>) {
        val incoming = sessions.associateBy { it.tahoTabId }

        trackedSessions.toMap().forEach { (tabId, old) ->
            val next = incoming[tabId]
            if (next == null || next.session !== old.session) {
                coordinator.detachSession(tabId)
                trackedSessions.remove(tabId)
            }
        }

        sessions.forEach { next ->
            val old = trackedSessions[next.tahoTabId]
            if (
                old == null ||
                old.session !== next.session ||
                old.isPrivate != next.isPrivate
            ) {
                coordinator.attachSession(
                    tahoTabId = next.tahoTabId,
                    session = next.session,
                    committedUrl = next.committedUrl,
                    isPrivate = next.isPrivate,
                )
                trackedSessions[next.tahoTabId] = next
            }
        }
    }

    fun display(transferId: String): M4DisplayRequest? =
        snapshot.requests
            .firstOrNull { it.transferId == transferId }
            ?.let(M4TransferPreparer::projectDisplay)

    fun prepare(
        transferId: String,
        policy: SecretPolicy,
    ): M4PreparationResult? =
        snapshot.requests
            .firstOrNull { it.transferId == transferId }
            ?.let { request ->
                M4TransferPreparer.prepare(
                    input = request,
                    requestedPolicy = policy,
                )
            }

    private fun onObservation(event: ProductionObservationEvent) {
        assembler.accept(event)

        val nextState = when (event) {
            ProductionObservationEvent.GateBlocked -> CaptureState.OFF
            is ProductionObservationEvent.ExtensionFailed -> CaptureState.ERROR
            is ProductionObservationEvent.Rejected -> CaptureState.LIMITED
            is ProductionObservationEvent.ExtensionReady,
            is ProductionObservationEvent.TabBound -> {
                if (assembler.isLimited()) CaptureState.LIMITED
                else if (assembler.allCompleted().isEmpty()) {
                    CaptureState.OBSERVING
                } else {
                    CaptureState.CAPTURING
                }
            }

            is ProductionObservationEvent.Bulk -> {
                if (assembler.isLimited()) CaptureState.LIMITED
                else if (assembler.allCompleted().isEmpty()) {
                    CaptureState.OBSERVING
                } else {
                    CaptureState.CAPTURING
                }
            }
        }

        snapshot = M4CaptureRuntimeSnapshot(
            state = nextState,
            requests = assembler.allCompleted(),
        )
        listener?.invoke(snapshot)
    }
}
