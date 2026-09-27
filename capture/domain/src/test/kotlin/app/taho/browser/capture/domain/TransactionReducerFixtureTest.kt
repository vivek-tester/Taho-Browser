package app.taho.browser.capture.domain

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TransactionReducerFixtureTest {
    private val identity = RequestIdentity(
        runtimeGeneration = RuntimeGeneration("fixture-runtime"),
        requestId = RequestId("fixture-request"),
    )

    @Test fun missingStartFixture() = assertFixture("missing-start.json")
    @Test fun reorderedFixture() = assertFixture("reordered-response-before-headers.json")
    @Test fun duplicateFixture() = assertFixture("duplicate-event.json")
    @Test fun recoveryFixture() = assertFixture("process-death-recovery.json")

    private fun assertFixture(name: String) {
        val fixture = JSONObject(loadFixture(name))
        val events = fixture.getJSONArray("events")
        val diagnostics = mutableListOf<String>()
        var state: ReductionState? = null
        var provisionalObserved = false

        repeat(events.length()) { index ->
            val reduction = TransactionReducer.reduce(
                current = state,
                identity = identity,
                event = parseEvent(events.getJSONObject(index)),
            )
            state = reduction.state
            provisionalObserved = provisionalObserved || reduction.provisional
            diagnostics += reduction.diagnostics.map { it.code.name }
        }

        val expected = fixture.getJSONObject("expected")
        val final = assertNotNull(state).transaction
        assertEquals(expected.getString("state"), final.state.name)
        assertEquals(expected.getBoolean("provisionalObserved"), provisionalObserved)

        val expectedDiagnostics = expected.getJSONArray("diagnostics")
        val expectedNames = buildSet {
            repeat(expectedDiagnostics.length()) {
                add(expectedDiagnostics.getString(it))
            }
        }
        assertEquals(expectedNames, diagnostics.toSet())

        if (expected.has("terminalReason")) {
            assertEquals(expected.getString("terminalReason"), final.terminalReason)
        }
        if (expected.has("requestHeaders")) {
            assertEquals(expected.getString("requestHeaders"), final.requestHeaders.name)
        }
    }

    private fun parseEvent(json: JSONObject): CaptureEvent =
        when (json.getString("type")) {
            "Start" -> CaptureEvent.Start(json.getString("id"))
            "HeadersCaptured" -> CaptureEvent.HeadersCaptured(
                eventId = json.getString("id"),
                completeness = Completeness.valueOf(
                    json.optString("completeness", Completeness.COMPLETE.name),
                ),
            )
            "ResponseStarted" -> CaptureEvent.ResponseStarted(json.getString("id"))
            "Completed" -> CaptureEvent.Completed(
                eventId = json.getString("id"),
                statusCode = if (json.has("statusCode")) json.getInt("statusCode") else null,
            )
            "RecoverInterrupted" -> CaptureEvent.RecoverInterrupted(
                eventId = json.getString("id"),
                reason = PartialReason.valueOf(json.getString("reason")),
            )
            else -> error("Unknown fixture event type: " + json.getString("type"))
        }

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("reducer/" + name)
            ?: error("Missing reducer fixture: " + name)
        return stream.bufferedReader().use { it.readText() }
    }
}
