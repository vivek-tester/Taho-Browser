package app.taho.browser.observation

import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

sealed interface SpikeProbeEvent {
    data class ExtensionReady(val extensionId: String) : SpikeProbeEvent
    data class InstallFailed(val message: String) : SpikeProbeEvent
    data class WebRequestCapability(
        val available: Boolean,
        val registeredListeners: String,
    ) : SpikeProbeEvent

    data class SessionAnnouncement(
        val appTabId: String,
        val sessionIdentity: Int?,
        val senderMatchesRegisteredSession: Boolean,
        val javascriptTabId: Int?,
        val topLevel: Boolean,
    ) : SpikeProbeEvent

    data class BackgroundAnnouncement(
        val javascriptTabId: Int?,
        val topLevel: Boolean,
    ) : SpikeProbeEvent

    data class WebRequestObserved(
        val phase: String,
        val requestId: String?,
        val webRequestTabId: Int?,
        val frameId: Int?,
        val documentId: String?,
        val resourceType: String?,
        val method: String?,
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

    private val backgroundDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            val json = message as? JSONObject ?: return invalid("background")

            when (json.optString("kind")) {
                "web_request_capability" -> sink(
                    SpikeProbeEvent.WebRequestCapability(
                        available = json.optBoolean("available", false),
                        registeredListeners = json.optString("listeners", ""),
                    ),
                )

                "background_announce" -> sink(
                    SpikeProbeEvent.BackgroundAnnouncement(
                        javascriptTabId = json.optNullableInt("jsTabId"),
                        topLevel = json.optBoolean("topLevel", false),
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
                    ),
                )

                else -> return invalid("background")
            }
            return null
        }
    }

    fun ensureInstalled() {
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
        sessions.removeAll { it.session === session }
        val registered = RegisteredSession(appTabId, session)
        sessions += registered
        extension?.let { attachDelegate(it, registered) }
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
                        ),
                    )
                    return null
                }
            },
            NATIVE_APP,
        )
    }

    private fun invalid(channel: String): GeckoResult<Any>? {
        sink(SpikeProbeEvent.InvalidMessage(channel))
        return null
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    companion object {
        private const val EXTENSION_ID = "taho-attribution-spike@browser.local"
        private const val EXTENSION_LOCATION =
            "resource://android/assets/taho_attribution_spike/"
        private const val NATIVE_APP = "taho.observation"
    }
}
