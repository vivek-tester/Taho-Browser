package app.taho.browser.spike

import app.taho.browser.observation.SpikeProbeEvent
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(result.contains("\"A\""))
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

    @Test
    fun initialLoadAndBackgroundTabAttributionProof() {
        val recorder = M1SpikeRecorder("156.0-test")

        // 1. Initial load for Tab A: session announcement at document_start
        recorder.record(
            SpikeProbeEvent.SessionAnnouncement(
                appTabId = "A",
                sessionIdentity = 101,
                senderMatchesRegisteredSession = true,
                javascriptTabId = 11,
                topLevel = true,
                token = null,
            ),
        )
        // 2. Initial load for Tab B (background tab)
        recorder.record(
            SpikeProbeEvent.SessionAnnouncement(
                appTabId = "B",
                sessionIdentity = 102,
                senderMatchesRegisteredSession = true,
                javascriptTabId = 12,
                topLevel = true,
                token = null,
            ),
        )

        // 3. Traffic generated in Tab A (foreground)
        recorder.record(
            SpikeProbeEvent.WebRequestObserved(
                phase = "onBeforeRequest",
                requestId = "req-a-1",
                webRequestTabId = 11,
                frameId = 0,
                documentId = "doc-a",
                resourceType = "main_frame",
                method = "GET",
                detailKeys = "documentId,frameId,requestId,tabId,type,url",
                requestBodyPresent = false,
                fixtureTag = "/page#A",
            ),
        )

        // 4. Traffic generated in Tab B (background fetch)
        recorder.record(
            SpikeProbeEvent.WebRequestObserved(
                phase = "onBeforeRequest",
                requestId = "req-b-1",
                webRequestTabId = 12,
                frameId = 0,
                documentId = "doc-b",
                resourceType = "xmlhttprequest",
                method = "GET",
                detailKeys = "documentId,frameId,requestId,tabId,type,url",
                requestBodyPresent = false,
                fixtureTag = "/api-bg#B",
            ),
        )

        val json = recorder.toJson()
        val attribution = json.getJSONObject("attribution")
        assertEquals(0, attribution.getInt("sessionMismatches"))
        assertEquals(0, attribution.getInt("topLevelMissingSession"))
        assertEquals(0, attribution.getInt("crossTabConflicts"))
        assertEquals(0, attribution.getInt("unstableJsTabBindings"))

        val webRequest = json.getJSONObject("webRequest")
        assertEquals(2, webRequest.getInt("events"))
        assertEquals(0, webRequest.getInt("unattributedEvents"))
    }

    @Test
    fun iframeServiceWorkerAndRedirectAttributionProof() {
        val recorder = M1SpikeRecorder("156.0-test")

        recorder.record(
            SpikeProbeEvent.SessionAnnouncement(
                appTabId = "A",
                sessionIdentity = 201,
                senderMatchesRegisteredSession = true,
                javascriptTabId = 21,
                topLevel = true,
                token = null,
            ),
        )

        // 1. Iframe request (frameId = 1) under Tab A maintains tab attribution
        recorder.record(
            SpikeProbeEvent.WebRequestObserved(
                phase = "onBeforeRequest",
                requestId = "req-iframe",
                webRequestTabId = 21,
                frameId = 1,
                documentId = "doc-iframe",
                resourceType = "sub_frame",
                method = "GET",
                detailKeys = "documentId,frameId,requestId,tabId,type,url",
                requestBodyPresent = false,
                fixtureTag = "/iframe#A",
            ),
        )

        // 2. Redirect sequence maintains Tab A attribution
        recorder.record(
            SpikeProbeEvent.WebRequestObserved(
                phase = "onBeforeRedirect",
                requestId = "req-redirect",
                webRequestTabId = 21,
                frameId = 0,
                documentId = "doc-redir",
                resourceType = "xmlhttprequest",
                method = "POST",
                detailKeys = "documentId,frameId,requestId,tabId,type,url",
                requestBodyPresent = true,
                fixtureTag = "/redirect#A",
            ),
        )

        // 3. Service Worker or worker fetch has tabId == -1 -> MUST be recorded as unattributed
        recorder.record(
            SpikeProbeEvent.WebRequestObserved(
                phase = "onBeforeRequest",
                requestId = "req-sw",
                webRequestTabId = -1,
                frameId = -1,
                documentId = null,
                resourceType = "xmlhttprequest",
                method = "GET",
                detailKeys = "requestId,tabId,type,url",
                requestBodyPresent = false,
                fixtureTag = null,
            ),
        )

        val json = recorder.toJson()
        val webRequest = json.getJSONObject("webRequest")
        assertEquals(3, webRequest.getInt("events"))
        assertEquals(1, webRequest.getInt("unattributedEvents"))

        val attribution = json.getJSONObject("attribution")
        assertEquals(0, attribution.getInt("crossTabConflicts"))
    }
}
