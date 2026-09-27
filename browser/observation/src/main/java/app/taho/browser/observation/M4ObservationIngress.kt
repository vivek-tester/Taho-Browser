package app.taho.browser.observation

import app.taho.browser.capture.domain.BoundedEventQueue
import app.taho.browser.capture.domain.CaptureBudgets
import app.taho.browser.capture.domain.CaptureEventClass
import app.taho.browser.capture.domain.QueueMetrics
import app.taho.browser.capture.domain.QueueOfferResult
import app.taho.browser.capture.domain.QueuedCaptureEvent

class M4ObservationIngress(
    capacity: Int = CaptureBudgets().parsedQueueDepth,
) {
    private val queue = BoundedEventQueue<ProductionObservationEvent.Bulk>(
        capacity = capacity,
    )

    fun offer(event: ProductionObservationEvent.Bulk): QueueOfferResult =
        queue.offer(
            QueuedCaptureEvent(
                id = eventId(event.message),
                eventClass = eventClass(event.message),
                payload = event,
            ),
        )

    fun poll(): ProductionObservationEvent.Bulk? =
        queue.poll()?.payload

    fun metrics(): QueueMetrics = queue.metrics()

    private fun eventClass(message: ProductionObservationMessage): CaptureEventClass =
        when (message) {
            is ProductionObservationMessage.Hello ->
                CaptureEventClass.PAGE_NAVIGATION

            is ProductionObservationMessage.TxRequestHeaders ->
                if (message.headers.any(::isAuthenticationHeader)) {
                    CaptureEventClass.AUTHENTICATION
                } else {
                    CaptureEventClass.PRIMARY_API
                }

            is ProductionObservationMessage.TxStart,
            is ProductionObservationMessage.TxRequestBody ->
                CaptureEventClass.PRIMARY_API

            is ProductionObservationMessage.TxRedirect,
            is ProductionObservationMessage.TxResponseStart,
            is ProductionObservationMessage.TxComplete,
            is ProductionObservationMessage.TxError ->
                CaptureEventClass.RESPONSE_METADATA

            is ProductionObservationMessage.TabRegister ->
                CaptureEventClass.PAGE_NAVIGATION
        }

    private fun eventId(message: ProductionObservationMessage): String =
        when (message) {
            is ProductionObservationMessage.Hello ->
                "hello-" + message.connectionId + "-" + message.sequence
            is ProductionObservationMessage.TxStart -> message.eventId
            is ProductionObservationMessage.TxRequestHeaders -> message.eventId
            is ProductionObservationMessage.TxRequestBody -> message.eventId
            is ProductionObservationMessage.TxRedirect -> message.eventId
            is ProductionObservationMessage.TxResponseStart -> message.eventId
            is ProductionObservationMessage.TxComplete -> message.eventId
            is ProductionObservationMessage.TxError -> message.eventId
            is ProductionObservationMessage.TabRegister ->
                "tab-" + message.extTabId + "-" + message.url.hashCode()
        }

    private fun isAuthenticationHeader(
        header: ProductionObservationMessage.HeaderValue,
    ): Boolean {
        val normalized = header.name.trim().lowercase()
        return normalized == "authorization" ||
            normalized == "proxy-authorization" ||
            normalized == "cookie" ||
            normalized == "x-api-key" ||
            normalized == "api-key" ||
            normalized == "x-auth-token" ||
            normalized == "x-access-token" ||
            normalized == "x-session-id"
    }
}
