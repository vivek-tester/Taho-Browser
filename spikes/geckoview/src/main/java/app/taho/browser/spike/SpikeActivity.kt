package app.taho.browser.spike

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Color
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.taho.browser.observation.AttributionSpikeProbe
import app.taho.browser.observation.SpikeProbeEvent
import app.taho.browser.runtime.BrowserSurfaceView
import app.taho.browser.runtime.GeckoRuntimeHolder
import java.io.File
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

class SpikeActivity : Activity() {
    private data class SpikeTab(
        val id: String,
        val isPrivate: Boolean,
        val session: GeckoSession,
        var sessionState: GeckoSession.SessionState? = null,
    )

    private lateinit var runtime: GeckoRuntime
    private lateinit var fixture: SpikeFixtureServer
    private lateinit var probe: AttributionSpikeProbe
    private lateinit var recorder: M1SpikeRecorder
    private lateinit var surface: BrowserSurfaceView
    private lateinit var summaryView: TextView
    private lateinit var logView: TextView

    private val tabs = linkedMapOf<String, SpikeTab>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var selectedTabId: String = "A"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fixture = SpikeFixtureServer().also { it.start() }
        runtime = GeckoRuntimeHolder.get(this)
        recorder = M1SpikeRecorder(BuildConfig.GECKOVIEW_VERSION)
        probe = AttributionSpikeProbe(
            runtime = runtime,
            sink = { event ->
                recorder.record(event)
                runOnUiThread {
                    appendProbeEvent(event)
                    updateSummary()
                }
            },
        )

        setContentView(buildUi())
        recordPlatform()
        createBaseTabs()
        selectTab("A")
        probe.ensureInstalled()

        appendLine("M1 fixture ready on loopback port " + fixture.port)
        appendLine("GeckoView pin " + BuildConfig.GECKOVIEW_VERSION)
        appendLine("No request URLs, headers, bodies, cookies, or credentials are written to this log.")
    }

    override fun onStop() {
        saveNormalSessionStates()
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        tabs.values.forEach { tab ->
            runCatching {
                tab.session.setFocused(false)
                tab.session.setActive(false)
                tab.session.close()
            }
        }
        tabs.clear()
        fixture.close()
        super.onDestroy()
    }

    private fun createBaseTabs() {
        createTab(
            id = "A",
            privateMode = false,
            restored = loadSavedState(PREF_STATE_A),
        )
        createTab(
            id = "B",
            privateMode = false,
            restored = loadSavedState(PREF_STATE_B),
        )
        createTab(
            id = "P",
            privateMode = true,
            restored = null,
        )
    }

    private fun createTab(
        id: String,
        privateMode: Boolean,
        restored: GeckoSession.SessionState? = null,
    ): SpikeTab {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .build()
        val session = GeckoSession(settings)
        val tab = SpikeTab(
            id = id,
            isPrivate = privateMode,
            session = session,
            sessionState = restored,
        )
        tabs[id] = tab

        session.setProgressDelegate(
            object : GeckoSession.ProgressDelegate {
                override fun onPageStart(
                    session: GeckoSession,
                    url: String,
                ) {
                    recorder.recordPageStart(id)
                    appendLine("page-start tab=$id tag=" + fixture.safeTagForUrl(url))
                    updateSummary()
                }

                override fun onPageStop(
                    session: GeckoSession,
                    success: Boolean,
                ) {
                    recorder.recordPageStop(id)
                    appendLine("page-stop tab=$id success=$success")
                    updateSummary()
                }

                override fun onSessionStateChange(
                    session: GeckoSession,
                    sessionState: GeckoSession.SessionState,
                ) {
                    tab.sessionState = sessionState
                    recorder.recordStateChange(id)
                    updateSummary()
                }

                override fun onSecurityChange(
                    session: GeckoSession,
                    securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
                ) {
                    recorder.recordSecurity(
                        isSecure = securityInfo.isSecure,
                        certificatePresent = securityInfo.certificate != null,
                        securityMode = securityInfo.securityMode,
                        mixedPassive = securityInfo.mixedModePassive,
                        mixedActive = securityInfo.mixedModeActive,
                    )
                    appendLine(
                        "security tab=$id secure=" + securityInfo.isSecure +
                            " cert=" + (securityInfo.certificate != null) +
                            " mode=" + securityInfo.securityMode,
                    )
                    updateSummary()
                }
            },
        )

        session.setContentDelegate(
            object : GeckoSession.ContentDelegate {
                override fun onCrash(session: GeckoSession) {
                    recorder.recordCrash(id)
                    appendLine("content-crash tab=$id")
                    updateSummary()
                }

                override fun onKill(session: GeckoSession) {
                    recorder.recordKill(id)
                    appendLine("content-kill tab=$id")
                    updateSummary()
                }
            },
        )

        session.open(runtime)
        probe.attachSession(id, session)

        if (restored != null && !privateMode) {
            recorder.recordRestoreRequested(id)
            session.restoreState(restored)
            appendLine("restore-requested tab=$id")
        }

        session.setActive(false)
        session.setFocused(false)
        return tab
    }

    private fun selectTab(id: String) {
        val selected = tabs[id] ?: return
        tabs.values.forEach { tab ->
            val active = tab.id == id
            runCatching {
                tab.session.setActive(active)
                tab.session.setFocused(active)
            }
        }
        selectedTabId = id
        surface.bind(selected.session)
        appendLine("selected tab=$id private=" + selected.isPrivate)
    }

    private fun loadScenario(id: String) {
        val tab = tabs[id] ?: return
        tab.session.loadUri(fixture.pageUrl(id))
        selectTab(id)
    }

    private fun loadRedirect(id: String) {
        val tab = tabs[id] ?: return
        tab.session.loadUri(fixture.redirectUrl(id))
        selectTab(id)
    }

    private fun startEightTabScenario() {
        for (index in 1..5) {
            val id = "X$index"
            val tab = tabs[id] ?: createTab(
                id = id,
                privateMode = false,
                restored = null,
            )
            tab.session.loadUri(fixture.pageUrl(id))
            tab.session.setActive(false)
            tab.session.setFocused(false)
        }

        tabs["A"]?.session?.loadUri(fixture.pageUrl("A"))
        tabs["B"]?.session?.loadUri(fixture.pageUrl("B"))
        tabs["P"]?.session?.loadUri(fixture.pageUrl("P"))
        selectTab("A")

        appendLine("8-tab load started")
        mainHandler.postDelayed(
            {
                recordMemorySnapshot()
                appendLine("8-tab memory snapshot recorded")
                updateSummary()
            },
            MEMORY_SAMPLE_DELAY_MS,
        )
    }

    private fun recordPlatform() {
        val pageSize = runCatching {
            Os.sysconf(OsConstants._SC_PAGESIZE)
        }.getOrDefault(-1L)
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        recorder.recordPlatform(
            pageSizeBytes = pageSize,
            memoryClassMb = activityManager.memoryClass,
        )
        appendLine(
            "platform pageSize=$pageSize memoryClassMb=" + activityManager.memoryClass,
        )
    }

    private fun recordMemorySnapshot() {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        recorder.recordMemory(
            totalPssKb = info.totalPss,
            measuredTabs = tabs.size,
        )
        appendLine("memory totalPssKb=" + info.totalPss + " tabs=" + tabs.size)
    }

    private fun saveNormalSessionStates() {
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
        saveState(editor, PREF_STATE_A, tabs["A"]?.sessionState)
        saveState(editor, PREF_STATE_B, tabs["B"]?.sessionState)
        editor.remove(PREF_STATE_PRIVATE)
        editor.commit()
    }

    private fun saveState(
        editor: android.content.SharedPreferences.Editor,
        key: String,
        state: GeckoSession.SessionState?,
    ) {
        val serialized = state?.toString()
        if (serialized == null) {
            editor.remove(key)
        } else {
            editor.putString(key, serialized)
        }
    }

    private fun loadSavedState(key: String): GeckoSession.SessionState? {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(key, null)
            ?: return null
        return runCatching {
            GeckoSession.SessionState.fromString(raw)
        }.getOrNull()
    }

    private fun persistAndKillForRestoreProbe() {
        saveNormalSessionStates()
        appendLine("normal states persisted; private state deliberately excluded")
        File(filesDir, LAST_SUMMARY_FILE).writeText(recorder.toJson().toString(2))
        mainHandler.postDelayed(
            {
                Process.killProcess(Process.myPid())
            },
            250,
        )
    }

    private fun exportSummary() {
        val file = File(filesDir, LAST_SUMMARY_FILE)
        file.writeText(recorder.toJson().toString(2))
        appendLine("summary-written " + file.absolutePath)
        updateSummary()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 5))
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            text = "Taho M1 GeckoView feasibility suite"
        }
        summaryView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 11f
            text = "M1 starting…"
        }

        root.addView(title)
        root.addView(summaryView)

        root.addView(
            row(
                button("Load A") { loadScenario("A") },
                button("Load B") { loadScenario("B") },
                button("Load P") { loadScenario("P") },
                button("8 Tabs") { startEightTabScenario() },
            ),
        )
        root.addView(
            row(
                button("Tab A") { selectTab("A") },
                button("Tab B") { selectTab("B") },
                button("Tab P") { selectTab("P") },
                button("Memory") { recordMemorySnapshot(); updateSummary() },
            ),
        )
        root.addView(
            row(
                button("Redirect A") { loadRedirect("A") },
                button("Redirect B") { loadRedirect("B") },
                button("Ensure Ext") { probe.ensureInstalled() },
                button("Drop Port") {
                    val disconnected = probe.forceDisconnectBulkPort()
                    appendLine("drop-port requested=$disconnected")
                },
            ),
        )
        root.addView(
            row(
                button("Crash Content") {
                    tabs[selectedTabId]?.session?.loadUri("about:crashcontent")
                    appendLine("about:crashcontent requested tab=$selectedTabId")
                },
                button("Save+Kill") { persistAndKillForRestoreProbe() },
                button("Export") { exportSummary() },
                button("Clear Log") { logView.text = "" },
            ),
        )

        surface = BrowserSurfaceView(this)
        logView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 10f
            setTextIsSelectable(true)
        }
        val logScroll = ScrollView(this).apply {
            addView(
                logView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        root.addView(
            surface,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                3f,
            ),
        )
        root.addView(
            logScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                2f,
            ),
        )
        return root
    }

    private fun row(vararg children: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            children.forEach { child ->
                addView(
                    child,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
            }
        }

    private fun button(
        label: String,
        action: () -> Unit,
    ): Button =
        Button(this).apply {
            text = label
            textSize = 10f
            setOnClickListener { action() }
        }

    private fun appendProbeEvent(event: SpikeProbeEvent) {
        val line = when (event) {
            is SpikeProbeEvent.EnsureBuiltInAttempt ->
                "ensureBuiltIn attempt=" + event.attempt

            is SpikeProbeEvent.ExtensionReady ->
                "extension-ready"

            is SpikeProbeEvent.InstallFailed ->
                "extension-failed " + event.message.take(120)

            is SpikeProbeEvent.BulkPortConnected ->
                "bulk-port-connected ordinal=" + event.ordinal

            is SpikeProbeEvent.BulkPortDisconnectRequested ->
                "bulk-port-disconnect-requested ordinal=" + event.ordinal

            is SpikeProbeEvent.BulkPortDisconnected ->
                "bulk-port-disconnected ordinal=" + event.ordinal

            is SpikeProbeEvent.BulkHeartbeat ->
                "bulk-heartbeat ordinal=" + event.ordinal + " seq=" + event.sequence

            is SpikeProbeEvent.WebRequestCapability ->
                "webRequest available=" + event.available +
                    " listeners=" + event.registeredListeners +
                    " failed=" + event.failedListeners

            is SpikeProbeEvent.SessionAnnouncement ->
                "session appTab=" + event.appTabId +
                    " matches=" + event.senderMatchesRegisteredSession +
                    " jsTab=" + event.javascriptTabId +
                    " top=" + event.topLevel

            is SpikeProbeEvent.BackgroundAnnouncement ->
                "background jsTab=" + event.javascriptTabId +
                    " top=" + event.topLevel

            is SpikeProbeEvent.WebRequestObserved ->
                "webRequest phase=" + event.phase +
                    " tab=" + event.webRequestTabId +
                    " frame=" + event.frameId +
                    " type=" + event.resourceType +
                    " method=" + event.method +
                    " bodyField=" + event.requestBodyPresent +
                    " fixture=" + (event.fixtureTag ?: "-")

            is SpikeProbeEvent.InvalidMessage ->
                "invalid-message channel=" + event.channel
        }
        appendLine(line)
    }

    private fun appendLine(line: String) {
        if (!::logView.isInitialized) return
        logView.append(line + "\n")
    }

    private fun updateSummary() {
        if (::summaryView.isInitialized) {
            summaryView.text = recorder.conciseSummary()
        }
    }

    companion object {
        private const val PREFS_NAME = "taho_m1_spike"
        private const val PREF_STATE_A = "normal_a"
        private const val PREF_STATE_B = "normal_b"
        private const val PREF_STATE_PRIVATE = "private_p"
        private const val LAST_SUMMARY_FILE = "m1-spike-result.json"
        private const val MEMORY_SAMPLE_DELAY_MS = 5_000L
    }
}
