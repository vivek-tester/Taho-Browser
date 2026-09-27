package app.taho.browser.capture.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoundedEventQueuePropertyTest {
    @Test
    fun lowAndMediumCannotConsumeSixtyPercentReservedFloor() {
        val queue = BoundedEventQueue<Int>(
            capacity = 10,
            reservedHighFraction = 0.60,
        )

        repeat(4) { index ->
            assertIs<QueueOfferResult.Accepted>(
                queue.offer(event(index, CaptureEventClass.STATIC_RESOURCE)),
            )
        }

        val rejected = queue.offer(
            event(99, CaptureEventClass.STATIC_RESOURCE),
        )
        assertEquals(
            QueueDropReason.RESERVED_FOR_HIGH,
            assertIs<QueueOfferResult.Rejected>(rejected).reason,
        )

        repeat(6) { index ->
            assertIs<QueueOfferResult.Accepted>(
                queue.offer(event(100 + index, CaptureEventClass.PRIMARY_API)),
            )
        }

        assertEquals(10, queue.size)
        assertEquals(6, queue.reservedHighSlots)
        assertEquals(4, queue.metrics().nonHighSize)
    }

    @Test
    fun higherPriorityAdmissionEvictsLowestEligibleClass() {
        val queue = BoundedEventQueue<Int>(
            capacity = 10,
            reservedHighFraction = 0.60,
        )

        repeat(4) { index ->
            queue.offer(event(index, CaptureEventClass.TELEMETRY))
        }
        repeat(6) { index ->
            queue.offer(event(10 + index, CaptureEventClass.PRIMARY_API))
        }

        val admitted = queue.offer(
            event(500, CaptureEventClass.AUTHENTICATION),
        )
        val accepted = assertIs<QueueOfferResult.Accepted>(admitted)

        assertEquals(CaptureEventClass.TELEMETRY, accepted.evictedClass)
        assertEquals(10, queue.size)
        assertEquals(
            1L,
            queue.metrics().dropsByClass.getValue(CaptureEventClass.TELEMETRY),
        )
        assertTrue(queue.metrics().limited)
    }

    @Test
    fun mediumCanDisplaceLowestPriorityWithinGeneralPool() {
        val queue = BoundedEventQueue<Int>(
            capacity = 10,
            reservedHighFraction = 0.60,
        )

        repeat(4) { index ->
            queue.offer(event(index, CaptureEventClass.TELEMETRY))
        }

        val medium = assertIs<QueueOfferResult.Accepted>(
            queue.offer(event(10, CaptureEventClass.PAGE_NAVIGATION)),
        )
        assertEquals(CaptureEventClass.TELEMETRY, medium.evictedClass)
        assertEquals(4, queue.metrics().nonHighSize)
    }

    @Test
    fun lowerPriorityCannotDisplaceMedium() {
        val queue = BoundedEventQueue<Int>(
            capacity = 10,
            reservedHighFraction = 0.60,
        )

        repeat(4) { index ->
            queue.offer(event(index, CaptureEventClass.PAGE_NAVIGATION))
        }

        val low = queue.offer(event(11, CaptureEventClass.STATIC_RESOURCE))
        assertEquals(
            QueueDropReason.RESERVED_FOR_HIGH,
            assertIs<QueueOfferResult.Rejected>(low).reason,
        )
        assertEquals(4, queue.metrics().nonHighSize)
    }

    @Test
    fun highOnlySaturationIsExplicitAndNeverOverflowsCapacity() {
        val queue = BoundedEventQueue<Int>(
            capacity = 3,
            reservedHighFraction = 0.60,
        )

        repeat(3) { index ->
            queue.offer(event(index, CaptureEventClass.AUTHENTICATION))
        }

        val result = queue.offer(
            event(99, CaptureEventClass.PRIMARY_API),
        )

        assertEquals(
            QueueDropReason.HIGH_PRIORITY_SATURATED,
            assertIs<QueueOfferResult.Rejected>(result).reason,
        )
        assertEquals(3, queue.size)
        assertEquals(1L, queue.metrics().highPrioritySaturationCount)
        assertTrue(queue.metrics().limited)
    }

    @Test
    fun queueNeverExceedsCapacityUnderRandomOverload() {
        val random = Random(20260927)
        val queue = BoundedEventQueue<Int>(
            capacity = 64,
            reservedHighFraction = 0.60,
        )

        repeat(25_000) { index ->
            if (random.nextInt(100) < 30) {
                queue.poll()
            } else {
                val eventClass = CaptureEventClass.entries[
                    random.nextInt(CaptureEventClass.entries.size)
                ]
                queue.offer(event(index, eventClass))
            }

            val metrics = queue.metrics()
            assertTrue(queue.size <= queue.capacity)
            assertTrue(metrics.nonHighSize <= queue.generalSlots)
            assertTrue(metrics.size >= 0)
        }
    }

    @Test
    fun pollPreservesGlobalFifoForEventsThatRemainQueued() {
        val queue = BoundedEventQueue<Int>(
            capacity = 10,
            reservedHighFraction = 0.60,
        )

        queue.offer(event(1, CaptureEventClass.AUTHENTICATION))
        queue.offer(event(2, CaptureEventClass.TELEMETRY))
        queue.offer(event(3, CaptureEventClass.PRIMARY_API))
        queue.offer(event(4, CaptureEventClass.PAGE_NAVIGATION))

        val ids = buildList {
            while (queue.size > 0) {
                add(assertNotNull(queue.poll()).id)
            }
        }

        assertEquals(listOf("e-1", "e-2", "e-3", "e-4"), ids)
    }

    @Test
    fun queuedEventStringNeverPrintsPayload() {
        val secret = "payload-secret-that-must-not-log"
        val queued = QueuedCaptureEvent(
            id = "e-secret",
            eventClass = CaptureEventClass.AUTHENTICATION,
            payload = secret,
        )

        assertTrue(!queued.toString().contains(secret))
    }

    private fun event(
        id: Int,
        eventClass: CaptureEventClass,
    ): QueuedCaptureEvent<Int> =
        QueuedCaptureEvent(
            id = "e-" + id,
            eventClass = eventClass,
            payload = id,
        )
}
