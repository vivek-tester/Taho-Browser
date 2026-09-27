package app.taho.browser.spike

import app.taho.browser.observation.SpikeProbeEvent
import org.json.JSONArray
import org.json.JSONObject

class M1SpikeRecorder(
    private val geckoViewVersion: String,
) {
    private var ensureAttempts = 0
    private var extensionReadyCount = 0
    private var installFailures = 0
    private var webRequestAvailable: Boolean? = null
    private val registeredListeners = sortedSetOf<String>()
    private val failedListeners = sortedSetOf<String>()

    private var bulkPortConnects = 0
    private var bulkPortDisconnectRequests = 0
    private var bulkPortDisconnects = 0
    private var bulkHeartbeats = 0

    private var invalidMessages = 0
    private var sessionAnnouncements = 0
    private var sessionMismatches = 0
    private var topLevelMissingSession = 0
    private var backgroundAnnouncements = 0
    private val jsTabIdsByAppTab = linkedMapOf<String, MutableSet<Int>>()

    private var webRequestEvents = 0
    private var unattributedEvents = 0
    private var requestBodyObservedEvents = 0
    private val requestPhaseCounts = linkedMapOf<String, Int>()
    private val detailKeys = sortedSetOf<String>()
    private val resourceTypes = sortedSetOf<String>()
    private val methods = sortedSetOf<String>()
    private val webRequestTabIdsByFixture = linkedMapOf<String, MutableSet<Int>>()
    private val documentIdsByFixture = linkedMapOf<String, MutableSet<String>>()

    private val pageStarts = linkedMapOf<String, Int>()
    private val pageStops = linkedMapOf<String, Int>()
    private val crashes = linkedMapOf<String, Int>()
    private val kills = linkedMapOf<String, Int>()
    private val stateChanges = linkedMapOf<String, Int>()
    private val restoreRequests = linkedMapOf<String, Int>()

    private var securityCallbacks = 0
    private var securitySecureSeen = false
    private var securityCertificateSeen = false
    private val securityModes = sortedSetOf<Int>()
    private val mixedPassiveModes = sortedSetOf<Int>()
    private val mixedActiveModes = sortedSetOf<Int>()

    private var pageSizeBytes: Long? = null
    private var memoryClassMb: Int? = null
    private var lastPssKb: Int? = null
    private var maxPssKb: Int? = null
    private var lastMeasuredTabs: Int? = null

    @Synchronized
    fun record(event: SpikeProbeEvent) {
        when (event) {
            is SpikeProbeEvent.EnsureBuiltInAttempt -> {
                ensureAttempts = maxOf(ensureAttempts, event.attempt)
            }

            is SpikeProbeEvent.ExtensionReady -> {
                extensionReadyCount += 1
            }

            is SpikeProbeEvent.InstallFailed -> {
                installFailures += 1
            }

            is SpikeProbeEvent.BulkPortConnected -> {
                bulkPortConnects += 1
            }

            is SpikeProbeEvent.BulkPortDisconnectRequested -> {
                bulkPortDisconnectRequests += 1
            }

            is SpikeProbeEvent.BulkPortDisconnected -> {
                bulkPortDisconnects += 1
            }

            is SpikeProbeEvent.BulkHeartbeat -> {
                bulkHeartbeats += 1
            }

            is SpikeProbeEvent.WebRequestCapability -> {
                webRequestAvailable = event.available
                event.registeredListeners
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach(registeredListeners::add)
                event.failedListeners
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach(failedListeners::add)
            }

            is SpikeProbeEvent.SessionAnnouncement -> {
                sessionAnnouncements += 1
                if (!event.senderMatchesRegisteredSession) {
                    sessionMismatches += 1
                }
                if (event.topLevel && event.sessionIdentity == null) {
                    topLevelMissingSession += 1
                }
                event.javascriptTabId?.let { id ->
                    jsTabIdsByAppTab
                        .getOrPut(event.appTabId) { linkedSetOf() }
                        .add(id)
                }
            }

            is SpikeProbeEvent.BackgroundAnnouncement -> {
                backgroundAnnouncements += 1
            }

            is SpikeProbeEvent.WebRequestObserved -> {
                webRequestEvents += 1
                if (event.webRequestTabId == null || event.webRequestTabId == -1) {
                    unattributedEvents += 1
                }
                if (event.requestBodyPresent) {
                    requestBodyObservedEvents += 1
                }

                requestPhaseCounts.increment(event.phase)
                event.resourceType?.let(resourceTypes::add)
                event.method?.let(methods::add)
                event.detailKeys
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach(detailKeys::add)

                val fixture = event.fixtureTag
                val webTab = event.webRequestTabId
                if (fixture != null && webTab != null) {
                    webRequestTabIdsByFixture
                        .getOrPut(fixture) { linkedSetOf() }
                        .add(webTab)
                }

                val documentId = event.documentId
                if (fixture != null && documentId != null) {
                    documentIdsByFixture
                        .getOrPut(fixture) { linkedSetOf() }
                        .let { ids ->
                            if (ids.size < MAX_DOCUMENT_IDS_PER_FIXTURE) {
                                ids.add(documentId)
                            }
                        }
                }
            }

            is SpikeProbeEvent.InvalidMessage -> {
                invalidMessages += 1
            }
        }
    }

    @Synchronized
    fun recordPageStart(tabId: String) {
        pageStarts.increment(tabId)
    }

    @Synchronized
    fun recordPageStop(tabId: String) {
        pageStops.increment(tabId)
    }

    @Synchronized
    fun recordCrash(tabId: String) {
        crashes.increment(tabId)
    }

    @Synchronized
    fun recordKill(tabId: String) {
        kills.increment(tabId)
    }

    @Synchronized
    fun recordStateChange(tabId: String) {
        stateChanges.increment(tabId)
    }

    @Synchronized
    fun recordRestoreRequested(tabId: String) {
        restoreRequests.increment(tabId)
    }

    @Synchronized
    fun recordSecurity(
        isSecure: Boolean,
        certificatePresent: Boolean,
        securityMode: Int,
        mixedPassive: Int,
        mixedActive: Int,
    ) {
        securityCallbacks += 1
        securitySecureSeen = securitySecureSeen || isSecure
        securityCertificateSeen = securityCertificateSeen || certificatePresent
        securityModes += securityMode
        mixedPassiveModes += mixedPassive
        mixedActiveModes += mixedActive
    }

    @Synchronized
    fun recordPlatform(
        pageSizeBytes: Long,
        memoryClassMb: Int,
    ) {
        this.pageSizeBytes = pageSizeBytes
        this.memoryClassMb = memoryClassMb
    }

    @Synchronized
    fun recordMemory(
        totalPssKb: Int,
        measuredTabs: Int,
    ) {
        lastPssKb = totalPssKb
        maxPssKb = maxOf(maxPssKb ?: totalPssKb, totalPssKb)
        lastMeasuredTabs = measuredTabs
    }

    @Synchronized
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("schema", "taho.m1-feasibility-result")
        root.put("version", 1)
        root.put("geckoViewVersion", geckoViewVersion)

        root.put(
            "extension",
            JSONObject()
                .put("ensureAttempts", ensureAttempts)
                .put("readyCount", extensionReadyCount)
                .put("installFailures", installFailures)
                .put("bulkPortConnects", bulkPortConnects)
                .put("bulkPortDisconnectRequests", bulkPortDisconnectRequests)
                .put("bulkPortDisconnects", bulkPortDisconnects)
                .put("bulkHeartbeats", bulkHeartbeats),
        )

        root.put(
            "webRequest",
            JSONObject()
                .putNullable("available", webRequestAvailable)
                .put("registeredListeners", JSONArray(registeredListeners.toList()))
                .put("failedListeners", JSONArray(failedListeners.toList()))
                .put("events", webRequestEvents)
                .put("unattributedEvents", unattributedEvents)
                .put("requestBodyObservedEvents", requestBodyObservedEvents)
                .put("phaseCounts", requestPhaseCounts.toJsonObject())
                .put("detailKeys", JSONArray(detailKeys.toList()))
                .put("resourceTypes", JSONArray(resourceTypes.toList()))
                .put("methods", JSONArray(methods.toList()))
                .put("tabIdsByFixture", setMapToJson(webRequestTabIdsByFixture))
                .put("documentIdsByFixture", setMapToJson(documentIdsByFixture)),
        )

        root.put(
            "attribution",
            JSONObject()
                .put("sessionAnnouncements", sessionAnnouncements)
                .put("backgroundAnnouncements", backgroundAnnouncements)
                .put("sessionMismatches", sessionMismatches)
                .put("topLevelMissingSession", topLevelMissingSession)
                .put("jsTabIdsByAppTab", setMapToJson(jsTabIdsByAppTab))
                .put("crossTabConflicts", computeCrossTabConflicts())
                .put("unstableJsTabBindings", computeUnstableBindings()),
        )

        root.put(
            "lifecycle",
            JSONObject()
                .put("pageStarts", pageStarts.toJsonObject())
                .put("pageStops", pageStops.toJsonObject())
                .put("crashes", crashes.toJsonObject())
                .put("kills", kills.toJsonObject())
                .put("stateChanges", stateChanges.toJsonObject())
                .put("restoreRequests", restoreRequests.toJsonObject()),
        )

        root.put(
            "security",
            JSONObject()
                .put("callbacks", securityCallbacks)
                .put("secureSeen", securitySecureSeen)
                .put("certificateSeen", securityCertificateSeen)
                .put("securityModes", JSONArray(securityModes.toList()))
                .put("mixedPassiveModes", JSONArray(mixedPassiveModes.toList()))
                .put("mixedActiveModes", JSONArray(mixedActiveModes.toList())),
        )

        root.put(
            "platform",
            JSONObject()
                .putNullable("pageSizeBytes", pageSizeBytes)
                .putNullable("memoryClassMb", memoryClassMb)
                .putNullable("lastPssKb", lastPssKb)
                .putNullable("maxPssKb", maxPssKb)
                .putNullable("lastMeasuredTabs", lastMeasuredTabs),
        )

        root.put("invalidMessages", invalidMessages)
        return root
    }

    @Synchronized
    fun conciseSummary(): String =
        buildString {
            append("M1 ")
            append("webRequest=")
            append(webRequestAvailable ?: "?")
            append(" sessions=")
            append(sessionAnnouncements)
            append(" mismatches=")
            append(sessionMismatches)
            append(" crossTab=")
            append(computeCrossTabConflicts())
            append(" wrEvents=")
            append(webRequestEvents)
            append(" unattributed=")
            append(unattributedEvents)
            append(" portConnects=")
            append(bulkPortConnects)
        }

    private fun computeUnstableBindings(): Int =
        jsTabIdsByAppTab.values.count { ids -> ids.size > 1 }

    private fun computeCrossTabConflicts(): Int {
        var conflicts = 0
        webRequestTabIdsByFixture.forEach { (fixture, observedIds) ->
            val expectedAppTab = fixture.substringAfter('#', missingDelimiterValue = "")
            if (expectedAppTab.isBlank()) return@forEach
            val expectedIds = jsTabIdsByAppTab[expectedAppTab].orEmpty()
            if (expectedIds.isEmpty()) return@forEach

            conflicts += observedIds.count { observed ->
                observed != -1 && observed !in expectedIds
            }
        }
        return conflicts
    }

    private fun <T> setMapToJson(
        source: Map<String, Set<T>>,
    ): JSONObject =
        JSONObject().apply {
            source.forEach { (key, values) ->
                put(key, JSONArray(values.toList()))
            }
        }

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }

    private fun Map<String, Int>.toJsonObject(): JSONObject =
        JSONObject().apply {
            this@toJsonObject.forEach { (key, value) -> put(key, value) }
        }

    private fun JSONObject.putNullable(
        key: String,
        value: Any?,
    ): JSONObject {
        put(key, value ?: JSONObject.NULL)
        return this
    }

    companion object {
        private const val MAX_DOCUMENT_IDS_PER_FIXTURE = 32
    }
}
