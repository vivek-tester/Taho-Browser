package app.taho.browser.capture.domain

import java.util.ArrayDeque
import kotlin.math.ceil

enum class EventPriority(val rank: Int) {
    P0_CRITICAL(0),
    P1_HIGH(1),
    P2_MEDIUM(2),
    P3_LOW(3),
    P4_LOWEST(4);

    val isReservedHigh: Boolean
        get() = this == P0_CRITICAL || this == P1_HIGH
}

enum class CaptureEventClass(val priority: EventPriority) {
    AUTHENTICATION(EventPriority.P0_CRITICAL),
    PRIMARY_API(EventPriority.P1_HIGH),
    BUSINESS_API(EventPriority.P1_HIGH),
    GRAPHQL(EventPriority.P1_HIGH),
    WS_HANDSHAKE(EventPriority.P1_HIGH),
    RESPONSE_METADATA(EventPriority.P2_MEDIUM),
    PAGE_NAVIGATION(EventPriority.P2_MEDIUM),
    STATIC_RESOURCE(EventPriority.P3_LOW),
    ANALYTICS(EventPriority.P4_LOWEST),
    TELEMETRY(EventPriority.P4_LOWEST),
}

class QueuedCaptureEvent<T>(
    val id: String,
    val eventClass: CaptureEventClass,
    val payload: T,
) {
    init {
        require(id.isNotBlank()) { "event id must not be blank" }
    }

    override fun toString(): String =
        "QueuedCaptureEvent(id=$id, eventClass=$eventClass, payload=<redacted>)"
}

enum class QueueDropReason {
    RESERVED_FOR_HIGH,
    FULL,
    HIGH_PRIORITY_SATURATED,
}

sealed interface QueueOfferResult {
    data class Accepted(
        val evictedClass: CaptureEventClass? = null,
    ) : QueueOfferResult

    data class Rejected(
        val reason: QueueDropReason,
    ) : QueueOfferResult
}

data class QueueMetrics(
    val size: Int,
    val capacity: Int,
    val reservedHighSlots: Int,
    val nonHighSize: Int,
    val dropsByClass: Map<CaptureEventClass, Long>,
    val highPrioritySaturationCount: Long,
) {
    val totalDrops: Long
        get() = dropsByClass.values.sum()

    val limited: Boolean
        get() = totalDrops > 0 || highPrioritySaturationCount > 0
}

/**
 * A bounded priority-aware queue with global FIFO polling for all events that
 * remain admitted.
 *
 * P0/P1 receive a 60% reserved floor. P2-P4 cannot consume that floor.
 * When a more important event arrives and the eligible area is full, the
 * newest event from the lowest eligible priority lane is evicted and counted.
 *
 * A queue saturated entirely by P0/P1 cannot satisfy both "never block" and
 * "never drop high priority" with a hard depth ceiling. That condition is
 * surfaced explicitly as HIGH_PRIORITY_SATURATED for upstream body degradation
 * / flow-control handling instead of silently violating either guarantee.
 */
class BoundedEventQueue<T>(
    val capacity: Int = CaptureBudgets().parsedQueueDepth,
    reservedHighFraction: Double = 0.60,
) {
    private data class Entry<T>(
        val sequence: Long,
        val event: QueuedCaptureEvent<T>,
    )

    private val lanes: Map<EventPriority, ArrayDeque<Entry<T>>> =
        EventPriority.entries.associateWith { ArrayDeque() }

    private val drops = CaptureEventClass.entries
        .associateWith { 0L }
        .toMutableMap()

    val reservedHighSlots: Int
    val generalSlots: Int

    private var sequence: Long = 0
    private var currentSize: Int = 0
    private var currentNonHighSize: Int = 0
    private var highPrioritySaturationCount: Long = 0

    init {
        require(capacity > 0) { "capacity must be positive" }
        require(reservedHighFraction in 0.0..1.0) {
            "reservedHighFraction must be within 0.0..1.0"
        }

        reservedHighSlots = ceil(capacity * reservedHighFraction)
            .toInt()
            .coerceIn(0, capacity)
        generalSlots = capacity - reservedHighSlots
    }

    val size: Int
        get() = currentSize

    fun offer(event: QueuedCaptureEvent<T>): QueueOfferResult {
        val priority = event.eventClass.priority

        if (priority.isReservedHigh) {
            if (currentSize < capacity) {
                enqueue(event)
                return QueueOfferResult.Accepted()
            }

            val evicted = evictLowestNonHigh()
            if (evicted != null) {
                recordDrop(evicted.event.eventClass)
                enqueue(event)
                return QueueOfferResult.Accepted(
                    evictedClass = evicted.event.eventClass,
                )
            }

            highPrioritySaturationCount += 1
            recordDrop(event.eventClass)
            return QueueOfferResult.Rejected(
                QueueDropReason.HIGH_PRIORITY_SATURATED,
            )
        }

        if (currentNonHighSize < generalSlots && currentSize < capacity) {
            enqueue(event)
            return QueueOfferResult.Accepted()
        }

        val evicted = evictStrictlyLowerPriority(priority)
        if (evicted != null) {
            recordDrop(evicted.event.eventClass)
            enqueue(event)
            return QueueOfferResult.Accepted(
                evictedClass = evicted.event.eventClass,
            )
        }

        recordDrop(event.eventClass)
        val reason = if (currentSize >= capacity) {
            QueueDropReason.FULL
        } else {
            QueueDropReason.RESERVED_FOR_HIGH
        }
        return QueueOfferResult.Rejected(reason)
    }

    fun poll(): QueuedCaptureEvent<T>? {
        val next = lanes.values
            .mapNotNull { lane -> lane.peekFirst() }
            .minByOrNull { entry -> entry.sequence }
            ?: return null

        val lane = lane(next.event.eventClass.priority)
        val removed = lane.removeFirst()
        currentSize -= 1
        if (!removed.event.eventClass.priority.isReservedHigh) {
            currentNonHighSize -= 1
        }
        return removed.event
    }

    fun peek(): QueuedCaptureEvent<T>? =
        lanes.values
            .mapNotNull { lane -> lane.peekFirst() }
            .minByOrNull { entry -> entry.sequence }
            ?.event

    fun metrics(): QueueMetrics =
        QueueMetrics(
            size = currentSize,
            capacity = capacity,
            reservedHighSlots = reservedHighSlots,
            nonHighSize = currentNonHighSize,
            dropsByClass = drops.toMap(),
            highPrioritySaturationCount = highPrioritySaturationCount,
        )

    private fun enqueue(event: QueuedCaptureEvent<T>) {
        check(currentSize < capacity) { "queue capacity exceeded" }
        if (!event.eventClass.priority.isReservedHigh) {
            check(currentNonHighSize < generalSlots || generalSlots == 0) {
                "non-high reservation boundary exceeded"
            }
        }

        lane(event.eventClass.priority).addLast(
            Entry(sequence = sequence++, event = event),
        )
        currentSize += 1
        if (!event.eventClass.priority.isReservedHigh) {
            currentNonHighSize += 1
        }
    }

    private fun evictLowestNonHigh(): Entry<T>? =
        listOf(
            EventPriority.P4_LOWEST,
            EventPriority.P3_LOW,
            EventPriority.P2_MEDIUM,
        ).firstNotNullOfOrNull { priority ->
            lane(priority).pollLast()
        }?.also {
            currentSize -= 1
            currentNonHighSize -= 1
        }

    private fun evictStrictlyLowerPriority(
        incoming: EventPriority,
    ): Entry<T>? {
        val candidates = EventPriority.entries
            .filter { priority ->
                !priority.isReservedHigh &&
                    priority.rank > incoming.rank
            }
            .sortedByDescending { it.rank }

        return candidates.firstNotNullOfOrNull { priority ->
            lane(priority).pollLast()
        }?.also {
            currentSize -= 1
            currentNonHighSize -= 1
        }
    }

    private fun recordDrop(eventClass: CaptureEventClass) {
        drops[eventClass] = drops.getValue(eventClass) + 1
    }

    private fun lane(priority: EventPriority): ArrayDeque<Entry<T>> =
        lanes.getValue(priority)
}
