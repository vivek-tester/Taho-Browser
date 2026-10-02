package app.taho.browser.observation

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.util.UUID

data class MobileDevToolsResult(
    val command: String,
    val payloadJson: String? = null,
    val error: String? = null,
)

class MobileDevToolsRuntime(
    private val runtime: GeckoRuntime,
    private val gate: ProductionCaptureGate,
) {
    private data class RegisteredSession(
        val tahoTabId: String,
        val session: GeckoSession,
    )

    private data class PendingRequest(
        val tabId: String,
        val command: String,
        val callback: (MobileDevToolsResult) -> Unit,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessions = mutableMapOf<String, RegisteredSession>()
    private val portByTab = mutableMapOf<String, WebExtension.Port>()
    private val pending = mutableMapOf<String, PendingRequest>()
    private var extension: WebExtension? = null
    private var enabled = false
    private var listener: ((Set<String>) -> Unit)? = null

    fun setListener(listener: ((Set<String>) -> Unit)?) {
        this.listener = listener
        notifyConnections()
    }

    fun connectedTabs(): Set<String> =
        if (enabled) portByTab.keys.toSet() else emptySet()

    fun setEnabled(value: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setEnabled(value) }
            return
        }

        if (gate != ProductionCaptureGate.ENABLED) {
            enabled = false
            failAll("Developer tools are unavailable because the capture capability gate is closed.")
            notifyConnections()
            return
        }

        if (enabled == value) {
            if (value) ensureExtension()
            notifyConnections()
            return
        }

        enabled = value
        if (enabled) {
            ensureExtension()
        } else {
            failAll("Developer tools were disabled.")
        }
        notifyConnections()
    }

    fun syncSessions(observed: List<ObservedBrowserSession>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { syncSessions(observed) }
            return
        }

        val incoming = observed.associateBy { it.tahoTabId }
        sessions.toMap().forEach { (tabId, old) ->
            val next = incoming[tabId]
            if (next == null || next.session !== old.session) {
                detachSession(tabId)
            }
        }

        observed.forEach { next ->
            val old = sessions[next.tahoTabId]
            if (old == null || old.session !== next.session) {
                sessions[next.tahoTabId] = RegisteredSession(next.tahoTabId, next.session)
                extension?.let { attachDelegate(it, sessions.getValue(next.tahoTabId)) }
            }
        }
    }

    fun request(
        tabId: String,
        command: String,
        argument: String? = null,
        callback: (MobileDevToolsResult) -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { request(tabId, command, argument, callback) }
            return
        }

        if (!enabled) {
            callback(MobileDevToolsResult(command = command, error = "Developer tools are disabled."))
            return
        }
        if (command !in ALLOWED_COMMANDS) {
            callback(MobileDevToolsResult(command = command, error = "Unsupported developer-tools command."))
            return
        }

        val port = portByTab[tabId]
        if (port == null) {
            callback(
                MobileDevToolsResult(
                    command = command,
                    error = "Inspector is not attached to this page yet. Reload the page once and try again.",
                ),
            )
            return
        }

        val requestId = UUID.randomUUID().toString()
        pending[requestId] = PendingRequest(tabId, command, callback)
        val message = JSONObject()
            .put("type", "DEVTOOLS_COMMAND")
            .put("id", requestId)
            .put("command", command)
        argument
            ?.take(MAX_ARGUMENT_CHARS)
            ?.let { message.put("argument", it) }

        runCatching {
            port.postMessage(message)
        }.onFailure { error ->
            pending.remove(requestId)
            callback(
                MobileDevToolsResult(
                    command = command,
                    error = error.javaClass.simpleName.ifBlank { "Unable to send inspector command." },
                ),
            )
            return
        }

        mainHandler.postDelayed(
            {
                val timedOut = pending.remove(requestId) ?: return@postDelayed
                timedOut.callback(
                    MobileDevToolsResult(
                        command = timedOut.command,
                        error = "Inspector response timed out.",
                    ),
                )
            },
            REQUEST_TIMEOUT_MS,
        )
    }

    fun close() {
        failAll("Developer tools closed.")
        portByTab.values.toSet().forEach { port ->
            runCatching { port.disconnect() }
        }
        portByTab.clear()
        sessions.clear()
        listener = null
    }

    private fun ensureExtension() {
        extension?.let { installed ->
            sessions.values.forEach { attachDelegate(installed, it) }
            notifyConnections()
            return
        }

        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept(
                { installed ->
                    if (installed == null) {
                        failAll("Developer-tools extension is unavailable.")
                        notifyConnections()
                    } else {
                        extension = installed
                        sessions.values.forEach { attachDelegate(installed, it) }
                    }
                },
                { error ->
                    failAll(error?.javaClass?.simpleName ?: "Developer-tools extension failed to load.")
                    notifyConnections()
                },
            )
    }

    private fun attachDelegate(
        extension: WebExtension,
        registered: RegisteredSession,
    ) {
        registered.session.webExtensionController.setMessageDelegate(
            extension,
            object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    if (!enabled) {
                        port.disconnect()
                        return
                    }

                    val previous = portByTab.put(registered.tahoTabId, port)
                    if (previous != null && previous !== port) {
                        runCatching { previous.disconnect() }
                    }

                    port.setDelegate(
                        object : WebExtension.PortDelegate {
                            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                handlePortMessage(registered.tahoTabId, message)
                            }

                            override fun onDisconnect(port: WebExtension.Port) {
                                if (portByTab[registered.tahoTabId] === port) {
                                    portByTab.remove(registered.tahoTabId)
                                    failPendingForTab(
                                        registered.tahoTabId,
                                        "Inspector disconnected from this page.",
                                    )
                                    notifyConnections()
                                }
                            }
                        },
                    )
                    notifyConnections()
                }
            },
            DEVTOOLS_NATIVE_APP,
        )
    }

    private fun detachSession(tabId: String) {
        sessions.remove(tabId)
        portByTab.remove(tabId)?.let { port ->
            runCatching { port.disconnect() }
        }
        failPendingForTab(tabId, "Tab closed before the inspector response arrived.")
        notifyConnections()
    }

    private fun handlePortMessage(tabId: String, message: Any) {
        val raw = message as? String ?: return
        val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (parsed.optString("type")) {
            "DEVTOOLS_READY" -> {
                notifyConnections()
            }

            "DEVTOOLS_RESPONSE" -> {
                val id = parsed.optString("id")
                val request = pending.remove(id) ?: return
                if (request.tabId != tabId) {
                    request.callback(
                        MobileDevToolsResult(
                            command = request.command,
                            error = "Inspector response came from a different tab.",
                        ),
                    )
                    return
                }

                val error = parsed.optString("error").takeIf { it.isNotBlank() }
                val rawPayload = parsed.opt("payload")
                val payload = when (rawPayload) {
                    null, JSONObject.NULL -> null
                    else -> rawPayload.toString()
                }
                request.callback(
                    MobileDevToolsResult(
                        command = request.command,
                        payloadJson = payload,
                        error = error,
                    ),
                )
            }
        }
    }

    private fun failPendingForTab(tabId: String, reason: String) {
        val ids = pending
            .filterValues { it.tabId == tabId }
            .keys
            .toList()
        ids.forEach { id ->
            pending.remove(id)?.let { request ->
                request.callback(
                    MobileDevToolsResult(
                        command = request.command,
                        error = reason,
                    ),
                )
            }
        }
    }

    private fun failAll(reason: String) {
        val requests = pending.values.toList()
        pending.clear()
        requests.forEach { request ->
            request.callback(
                MobileDevToolsResult(
                    command = request.command,
                    error = reason,
                ),
            )
        }
    }

    private fun notifyConnections() {
        val snapshot = connectedTabs()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener?.invoke(snapshot)
        } else {
            mainHandler.post { listener?.invoke(snapshot) }
        }
    }

    companion object {
        private const val EXTENSION_ID = "taho-capture@browser.local"
        private const val EXTENSION_LOCATION = "resource://android/assets/taho_capture/"
        private const val DEVTOOLS_NATIVE_APP = "taho.devtools"
        private const val MAX_ARGUMENT_CHARS = 32 * 1024
        private const val REQUEST_TIMEOUT_MS = 6_000L

        private val ALLOWED_COMMANDS = setOf(
            "ELEMENTS",
            "CONSOLE_INFO",
            "CONSOLE_EVAL",
            "SOURCES",
            "PERFORMANCE",
            "MEMORY",
            "APPLICATION",
            "SECURITY",
            "LIGHTHOUSE",
            "RECORDER",
            "ISSUES",
            "RENDERING",
            "SENSORS",
            "COVERAGE",
            "CHANGES",
            "ANIMATIONS",
        )
    }
}
