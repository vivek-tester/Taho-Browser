package app.taho.browser.observation

import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

sealed interface SpikeProbeEvent {
    data class EnsureBuiltInAttempt(val attempt: Int) : SpikeProbeEvent
    data class ExtensionReady(val extensionId: String) : SpikeProbeEvent
    data class InstallFailed(val message: String) : SpikeProbeEvent

    data class BulkPortConnected(val ordinal: Int) : SpikeProbeEvent
    data class BulkPortDisconnectRequested(val ordinal: Int) : SpikeProbeEvent
    data class BulkPortDisconnected(val ordinal: Int) : SpikeProbeEvent
    data class BulkHeartbeat(
        val ordinal: Int,
        val sequence: Long?,
    ) : SpikeProbeEvent

    data class WebRequestCapability(
        val available: Boolean,
        val registeredListeners: String,
        val failedListeners: String,
    ) : SpikeProbeEvent

    data class SessionAnnouncement(
        val appTabId: String,
        val sessionIdentity: Int?,
        val senderMatchesRegisteredSession: Boolean,
        val javascriptTabId: Int?,
        val topLevel: Boolean,
        val token: String?,
    ) : SpikeProbeEvent

    data class BackgroundAnnouncement(
        val javascriptTabId: Int?,
        val topLevel: Boolean,
        val token: String?,
    ) : SpikeProbeEvent

    data class WebRequestObserved(
        val phase: String,
        val requestId: String?,
        val webRequestTabId: Int?,
        val frameId: Int?,
        val documentId: String?,
        val resourceType: String?,
        val method: String?,
        val detailKeys: String,
        val requestBodyPresent: Boolean,
        val fixtureTag: String?,
    ) : SpikeProbeEvent

    data class InvalidMessage(val channel: String) : SpikeProbeEvent
}

class AttributionSpikeProbe(
    private val runtime: GeckoRuntime,
    private val sink: (SpikeProbeEvent) -> Unit,
) {
    private data class RegisteredSession(
        val appTabId: String,
        val session: GeckoSession,
    )

    private val sessions = mutableListOf<RegisteredSession>()
    private var extension: WebExtension? = null
    private var activePort: WebExtension.Port? = null
    private var activePortOrdinal: Int = 0
    private var ensureAttempts: Int = 0

    private val backgroundDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            return handleBackgroundMessage(message, channel = "background-one-off")
        }

        override fun onConnect(port: WebExtension.Port) {
            activePortOrdinal += 1
            val ordinal = activePortOrdinal
            activePort = port
            sink(SpikeProbeEvent.BulkPortConnected(ordinal))

            port.setDelegate(
                object : WebExtension.PortDelegate {
                    override fun onPortMessage(
                        message: Any,
                        port: WebExtension.Port,
                    ) {
                        handleBackgroundMessage(
                            message = message,
                            channel = "background-port",
                            portOrdinal = ordinal,
                        )
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        if (activePort === port) {
                            activePort = null
                        }
                        sink(SpikeProbeEvent.BulkPortDisconnected(ordinal))
                    }
                },
            )
        }
    }

    fun ensureInstalled() {
        ensureAttempts += 1
        sink(SpikeProbeEvent.EnsureBuiltInAttempt(ensureAttempts))

        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept(
                { installed ->
                    if (installed == null) {
                        sink(SpikeProbeEvent.InstallFailed("ensureBuiltIn returned no extension"))
                    } else {
                        extension = installed
                        installed.setMessageDelegate(backgroundDelegate, NATIVE_APP)
                        sessions.forEach { attachDelegate(installed, it) }
                        sink(SpikeProbeEvent.ExtensionReady(installed.id))
                    }
                },
                { error ->
                    sink(
                        SpikeProbeEvent.InstallFailed(
                            error?.message ?: error?.javaClass?.simpleName ?: "unknown error",
                        ),
                    )
                },
            )
    }

    fun attachSession(appTabId: String, session: GeckoSession) {
        sessions.removeAll { it.session === session || it.appTabId == appTabId }
        val registered = RegisteredSession(appTabId, session)
        sessions += registered
        extension?.let { attachDelegate(it, registered) }
    }

    /**
     * SPIKE-12 helper. The extension is expected to reconnect automatically.
     * The forced disconnect is explicit and recorded; no product code depends on it.
     */
    fun forceDisconnectBulkPort(): Boolean {
        val port = activePort ?: return false
        val ordinal = activePortOrdinal
        activePort = null
        sink(SpikeProbeEvent.BulkPortDisconnectRequested(ordinal))
        port.disconnect()
        return true
    }

    private fun attachDelegate(
        extension: WebExtension,
        registered: RegisteredSession,
    ) {
        registered.session.webExtensionController.setMessageDelegate(
            extension,
            object : WebExtension.MessageDelegate {
                override fun onMessage(
                    nativeApp: String,
                    message: Any,
                    sender: WebExtension.MessageSender,
                ): GeckoResult<Any>? {
                    val json = message as? JSONObject ?: return invalid("session")
                    if (json.optString("kind") != "session_announce") {
                        return invalid("session")
                    }

                    val senderSession = sender.session
                    sink(
                        SpikeProbeEvent.SessionAnnouncement(
                            appTabId = registered.appTabId,
                            sessionIdentity = senderSession?.let { System.identityHashCode(it) },
                            senderMatchesRegisteredSession = senderSession === registered.session,
                            javascriptTabId = json.optNullableInt("jsTabId"),
                            topLevel = sender.isTopLevel(),
                            token = json.optNullableString("token"),
                        ),
                    )
                    return null
                }
            },
            NATIVE_APP,
        )
    }

    private fun handleBackgroundMessage(
        message: Any,
        channel: String,
        portOrdinal: Int? = null,
    ): GeckoResult<Any>? {
        val json = message as? JSONObject ?: return invalid(channel)

        when (json.optString("kind")) {
            "bulk_heartbeat" -> sink(
                SpikeProbeEvent.BulkHeartbeat(
                    ordinal = portOrdinal ?: activePortOrdinal,
                    sequence = json.optNullableLong("seq"),
                ),
            )

            "web_request_capability" -> sink(
                SpikeProbeEvent.WebRequestCapability(
                    available = json.optBoolean("available", false),
                    registeredListeners = json.optString("listeners", ""),
                    failedListeners = json.optString("failed", ""),
                ),
            )

            "background_announce" -> sink(
                SpikeProbeEvent.BackgroundAnnouncement(
                    javascriptTabId = json.optNullableInt("jsTabId"),
                    topLevel = json.optBoolean("topLevel", false),
                    token = json.optNullableString("token"),
                ),
            )

            "web_request" -> sink(
                SpikeProbeEvent.WebRequestObserved(
                    phase = json.optString("phase", "unknown"),
                    requestId = json.optNullableString("requestId"),
                    webRequestTabId = json.optNullableInt("tabId"),
                    frameId = json.optNullableInt("frameId"),
                    documentId = json.optNullableString("documentId"),
                    resourceType = json.optNullableString("resourceType"),
                    method = json.optNullableString("method"),
                    detailKeys = json.optString("detailKeys", ""),
                    requestBodyPresent = json.optBoolean("requestBodyPresent", false),
                    fixtureTag = json.optNullableString("fixtureTag"),
                ),
            )

            else -> return invalid(channel)
        }
        return null
    }

    private fun invalid(channel: String): GeckoResult<Any>? {
        sink(SpikeProbeEvent.InvalidMessage(channel))
        return null
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    companion object {
        private const val EXTENSION_ID = "taho-attribution-spike@browser.local"
        private const val EXTENSION_LOCATION =
            "resource://android/assets/taho_attribution_spike/"
        private const val NATIVE_APP = "taho.observation"
    }
}
