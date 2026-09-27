package app.taho.browser.spike

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.taho.browser.observation.AttributionSpikeProbe
import app.taho.browser.observation.SpikeProbeEvent
import app.taho.browser.runtime.BrowserRuntimeController
import app.taho.browser.runtime.BrowserSurfaceView
import app.taho.browser.runtime.GeckoRuntimeHolder

class SpikeActivity : Activity() {
    private lateinit var controller: BrowserRuntimeController
    private lateinit var surface: BrowserSurfaceView
    private lateinit var logView: TextView
    private lateinit var tabA: String
    private lateinit var tabB: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controller = BrowserRuntimeController(this)
        tabA = controller.snapshot().selectedTabId
        tabB = controller.createTab(privateMode = false)

        setContentView(buildUi())
        controller.selectTab(tabA)
        controller.bind(tabA, surface)

        val probe = AttributionSpikeProbe(
            runtime = GeckoRuntimeHolder.get(this),
            sink = { event -> runOnUiThread { append(event) } },
        )
        controller.forEachSession { tabId, session ->
            probe.attachSession(tabId, session)
        }
        probe.ensureInstalled()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 5, 5))
            setPadding(20, 20, 20, 20)
        }

        val url = EditText(this).apply {
            setText("https://example.com")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

        controls.addView(button("Load A") {
            controller.load(tabA, url.text.toString())
            controller.selectTab(tabA)
            controller.bind(tabA, surface)
        })
        controls.addView(button("Load B") {
            controller.load(tabB, url.text.toString())
            controller.selectTab(tabB)
            controller.bind(tabB, surface)
        })
        controls.addView(button("Tab A") {
            controller.selectTab(tabA)
            controller.bind(tabA, surface)
        })
        controls.addView(button("Tab B") {
            controller.selectTab(tabB)
            controller.bind(tabB, surface)
        })

        surface = BrowserSurfaceView(this)
        logView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 11f
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

        root.addView(url)
        root.addView(controls)
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

    private fun append(event: SpikeProbeEvent) {
        val safeLine = when (event) {
            is SpikeProbeEvent.ExtensionReady ->
                "extension ready id=" + event.extensionId
            is SpikeProbeEvent.InstallFailed ->
                "extension install failed: " + event.message
            is SpikeProbeEvent.SessionAnnouncement ->
                "session appTab=" + event.appTabId +
                    " identity=" + event.sessionIdentity +
                    " matches=" + event.senderMatchesRegisteredSession +
                    " jsTab=" + event.javascriptTabId +
                    " top=" + event.topLevel
            is SpikeProbeEvent.BackgroundAnnouncement ->
                "background jsTab=" + event.javascriptTabId +
                    " top=" + event.topLevel
            is SpikeProbeEvent.WebRequestObserved ->
                "webRequest phase=" + event.phase +
                    " request=" + event.requestId +
                    " tab=" + event.webRequestTabId +
                    " frame=" + event.frameId +
                    " doc=" + (event.documentId ?: "-") +
                    " type=" + event.resourceType +
                    " method=" + event.method
            is SpikeProbeEvent.InvalidMessage ->
                "invalid message on " + event.channel
        }
        logView.append(safeLine + "\n")
    }
}
