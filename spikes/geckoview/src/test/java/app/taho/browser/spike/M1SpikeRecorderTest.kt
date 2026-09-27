package app.taho.browser.spike

import app.taho.browser.observation.SpikeProbeEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class M1SpikeRecorderTest {
    @Test
    fun summaryNeverIncludesContentAnnouncementToken() {
        val recorder = M1SpikeRecorder("156.0-test")
        val secretToken = "token-that-must-not-be-exported"

        recorder.record(
            SpikeProbeEvent.SessionAnnouncement(
                appTabId = "A",
                sessionIdentity = 123,
                senderMatchesRegisteredSession = true,
                javascriptTabId = 7,
                topLevel = true,
                token = secretToken,
            ),
        )
        recorder.record(
            SpikeProbeEvent.BackgroundAnnouncement(
                javascriptTabId = 7,
                topLevel = true,
                token = secretToken,
            ),
        )

        val result = recorder.toJson().toString()

        assertFalse(result.contains(secretToken))
        assertTrue(result.contains("\\"A\\""))
    }

    @Test
    fun detectsCrossTabFixtureConflictFromSafeMarker() {
        val recorder = M1SpikeRecorder("156.0-test")

        recorder.record(
            SpikeProbeEvent.SessionAnnouncement(
                appTabId = "A",
                sessionIdentity = 1,
                senderMatchesRegisteredSession = true,
                javascriptTabId = 10,
                topLevel = true,
                token = null,
            ),
        )
        recorder.record(
            SpikeProbeEvent.SessionAnnouncement(
                appTabId = "B",
                sessionIdentity = 2,
                senderMatchesRegisteredSession = true,
                javascriptTabId = 20,
                topLevel = true,
                token = null,
            ),
        )
        recorder.record(
            SpikeProbeEvent.WebRequestObserved(
                phase = "onBeforeRequest",
                requestId = "r1",
                webRequestTabId = 20,
                frameId = 0,
                documentId = "d1",
                resourceType = "xmlhttprequest",
                method = "GET",
                detailKeys = "documentId,frameId,requestId,tabId,type,url",
                requestBodyPresent = false,
                fixtureTag = "/api-json#A",
            ),
        )

        val result = recorder.toJson()
            .getJSONObject("attribution")
            .getInt("crossTabConflicts")

        assertTrue(result > 0)
    }
}
