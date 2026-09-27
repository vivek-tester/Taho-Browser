package app.taho.browser.observation

import android.os.Handler
import android.os.Looper
import app.taho.browser.capture.domain.CaptureState
import app.taho.browser.capture.domain.QueueOfferResult
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
    private val ingress = M4ObservationIngress()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var drainScheduled = false
    private var ingressLimited = false
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onObservation(event) }
            return
        }

        if (event is ProductionObservationEvent.Bulk) {
            when (val result = ingress.offer(event)) {
                is QueueOfferResult.Accepted -> {
                    if (result.evictedClass != null) ingressLimited = true
                }
                is QueueOfferResult.Rejected -> ingressLimited = true
            }
            scheduleDrain()
            if (ingressLimited) publish(CaptureState.LIMITED)
            return
        }

        assembler.accept(event)
        when (event) {
            ProductionObservationEvent.GateBlocked -> publish(CaptureState.OFF)
            is ProductionObservationEvent.ExtensionFailed -> publish(CaptureState.ERROR)
            is ProductionObservationEvent.Rejected -> publish(CaptureState.LIMITED)
            is ProductionObservationEvent.ExtensionReady,
            is ProductionObservationEvent.TabBound ->
                publish(derivedActiveState())
            is ProductionObservationEvent.Bulk -> error("handled above")
        }
    }

    private fun scheduleDrain() {
        if (drainScheduled) return
        drainScheduled = true
        mainHandler.post {
            drainScheduled = false
            while (true) {
                val event = ingress.poll() ?: break
                assembler.accept(event)
            }
            if (ingress.metrics().limited) ingressLimited = true
            publish(derivedActiveState())
        }
    }

    private fun derivedActiveState(): CaptureState =
        when {
            ingressLimited || assembler.isLimited() -> CaptureState.LIMITED
            assembler.allCompleted().isEmpty() -> CaptureState.OBSERVING
            else -> CaptureState.CAPTURING
        }

    private fun publish(state: CaptureState) {
        snapshot = M4CaptureRuntimeSnapshot(
            state = state,
            requests = assembler.allCompleted(),
        )
        listener?.invoke(snapshot)
    }
}
