package app.taho.browser.observation

import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.net.URI

enum class ProductionCaptureGate {
    BLOCKED_M1_DEVICE_EVIDENCE,
    ENABLED,
}

sealed interface ProductionObservationEvent {
    data object GateBlocked : ProductionObservationEvent
    data class ExtensionReady(val extensionId: String) : ProductionObservationEvent
    data class ExtensionFailed(val reason: String) : ProductionObservationEvent
    data class TabBound(
        val tahoTabId: String,
        val extTabId: Int,
        val isPrivate: Boolean,
    ) : ProductionObservationEvent
    data class Rejected(val reason: String) : ProductionObservationEvent
    data class Bulk(
        val tahoTabId: String?,
        val isPrivate: Boolean?,
        val message: ProductionObservationMessage,
        val sequenceGap: Boolean,
    ) : ProductionObservationEvent {
        override fun toString(): String =
            "Bulk(tahoTabId=$tahoTabId,isPrivate=$isPrivate,type=" + message.type +
                ",sequenceGap=$sequenceGap)"
    }
}

class ProductionObservationCoordinator(
    private val runtime: GeckoRuntime,
    private val gate: ProductionCaptureGate,
    private val sink: (ProductionObservationEvent) -> Unit,
) {
    private data class RegisteredSession(
        val tahoTabId: String,
        val session: GeckoSession,
        val committedUrl: () -> String?,
        val isPrivate: Boolean,
    )

    private val sessions = mutableListOf<RegisteredSession>()
    private val bindingByExtTab = mutableMapOf<Int, RegisteredSession>()
    private var extension: WebExtension? = null
    private var activeConnection: String? = null
    private var lastSequence: Long = 0

    private val backgroundDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            if (gate != ProductionCaptureGate.ENABLED) {
                port.disconnect()
                return
            }
            activeConnection = null
            lastSequence = 0

            port.setDelegate(
                object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        val raw = message as? String ?: run {
                            sink(ProductionObservationEvent.Rejected("bulk message was not a string"))
                            return
                        }
                        acceptBulk(raw)
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        activeConnection = null
                        lastSequence = 0
                    }
                },
            )
        }

        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            val raw = message as? String ?: run {
                sink(ProductionObservationEvent.Rejected("bulk message was not a string"))
                return null
            }
            acceptBulk(raw)
            return null
        }
    }

    fun start() {
        if (gate != ProductionCaptureGate.ENABLED) {
            sink(ProductionObservationEvent.GateBlocked)
            return
        }

        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept(
                { installed ->
                    if (installed == null) {
                        sink(ProductionObservationEvent.ExtensionFailed("extension unavailable"))
                    } else {
                        extension = installed
                        installed.setMessageDelegate(backgroundDelegate, BULK_NATIVE_APP)
                        sessions.forEach { attachIdentityDelegate(installed, it) }
                        sink(ProductionObservationEvent.ExtensionReady(installed.id))
                    }
                },
                { error ->
                    sink(
                        ProductionObservationEvent.ExtensionFailed(
                            error?.javaClass?.simpleName ?: "install failure",
                        ),
                    )
                },
            )
    }

    fun attachSession(
        tahoTabId: String,
        session: GeckoSession,
        committedUrl: () -> String?,
        isPrivate: Boolean,
    ) {
        if (gate != ProductionCaptureGate.ENABLED) return

        sessions.removeAll { it.session === session || it.tahoTabId == tahoTabId }
        bindingByExtTab.entries.removeAll { it.value.session === session }
        val registered = RegisteredSession(tahoTabId, session, committedUrl, isPrivate)
        sessions += registered
        extension?.let { attachIdentityDelegate(it, registered) }
    }

    fun detachSession(tahoTabId: String) {
        val removed = sessions.filter { it.tahoTabId == tahoTabId }.toSet()
        sessions.removeAll(removed)
        bindingByExtTab.entries.removeAll { it.value in removed }
    }

    private fun attachIdentityDelegate(
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
                    val raw = message as? String ?: return rejectIdentity("non-string identity")
                    val parsed = ProductionObservationProtocol.parse(
                        raw,
                        ObservationLane.IDENTITY,
                    )
                    val register = (parsed as? ObservationParseResult.Accepted)
                        ?.message as? ProductionObservationMessage.TabRegister
                        ?: return rejectIdentity("invalid TAB_REGISTER")

                    if (sender.session !== registered.session || !sender.isTopLevel()) {
                        return rejectIdentity("sender/session mismatch")
                    }

                    val committed = registered.committedUrl()
                    if (!sameOrigin(committed, register.url)) {
                        return rejectIdentity("origin mismatch")
                    }

                    val existing = bindingByExtTab[register.extTabId]
                    if (existing != null && existing.session !== registered.session) {
                        return rejectIdentity("extTabId already bound")
                    }

                    bindingByExtTab[register.extTabId] = registered
                    sink(
                        ProductionObservationEvent.TabBound(
                            tahoTabId = registered.tahoTabId,
                            extTabId = register.extTabId,
                            isPrivate = registered.isPrivate,
                        ),
                    )
                    return null
                }
            },
            IDENTITY_NATIVE_APP,
        )
    }

    private fun rejectIdentity(reason: String): GeckoResult<Any>? {
        sink(ProductionObservationEvent.Rejected(reason))
        return null
    }

    private fun acceptBulk(raw: String) {
        val parsed = ProductionObservationProtocol.parse(raw, ObservationLane.BULK)
        val message = (parsed as? ObservationParseResult.Accepted)?.message ?: run {
            val rejected = parsed as ObservationParseResult.Rejected
            sink(
                ProductionObservationEvent.Rejected(
                    "bulk " + rejected.reason.name + " type=" + (rejected.typeHint ?: "unknown"),
                ),
            )
            return
        }

        val meta = when (message) {
            is ProductionObservationMessage.Hello ->
                Triple(message.connectionId, message.sequence, null)
            is ProductionObservationMessage.TxStart ->
                Triple(message.connectionId, message.sequence, message.extTabId)
            is ProductionObservationMessage.TxRequestHeaders ->
                Triple(message.connectionId, message.sequence, message.extTabId)
            is ProductionObservationMessage.TxRequestBody ->
                Triple(message.connectionId, message.sequence, message.extTabId)
            is ProductionObservationMessage.TxResponseStart ->
                Triple(message.connectionId, message.sequence, message.extTabId)
            is ProductionObservationMessage.TxComplete ->
                Triple(message.connectionId, message.sequence, message.extTabId)
            is ProductionObservationMessage.TxError ->
                Triple(message.connectionId, message.sequence, message.extTabId)
            is ProductionObservationMessage.TabRegister -> return
        }

        val connection = meta.first
        val sequence = meta.second
        if (message is ProductionObservationMessage.Hello) {
            activeConnection = connection
            lastSequence = sequence
            sink(
                ProductionObservationEvent.Bulk(
                    tahoTabId = null,
                    isPrivate = null,
                    message = message,
                    sequenceGap = sequence != 1L,
                ),
            )
            return
        }

        if (activeConnection != connection || sequence <= lastSequence) {
            sink(ProductionObservationEvent.Rejected("connection/sequence regression"))
            return
        }

        val gap = sequence != lastSequence + 1
        lastSequence = sequence
        val binding = meta.third?.let { bindingByExtTab[it] }
        sink(
            ProductionObservationEvent.Bulk(
                tahoTabId = binding?.tahoTabId,
                isPrivate = binding?.isPrivate,
                message = message,
                sequenceGap = gap,
            ),
        )
    }

    private fun sameOrigin(expected: String?, actual: String): Boolean {
        val left = origin(expected) ?: return false
        val right = origin(actual) ?: return false
        return left == right
    }

    private fun origin(raw: String?): String? =
        runCatching {
            if (raw == null) return@runCatching null
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase() ?: return@runCatching null
            val host = uri.host?.lowercase() ?: return@runCatching null
            if (scheme != "http" && scheme != "https") return@runCatching null
            scheme + "://" + host + if (uri.port >= 0) ":" + uri.port else ""
        }.getOrNull()

    companion object {
        private const val EXTENSION_ID = "taho-capture@browser.local"
        private const val EXTENSION_LOCATION = "resource://android/assets/taho_capture/"
        private const val BULK_NATIVE_APP = "taho.capture.bulk"
        private const val IDENTITY_NATIVE_APP = "taho.identity"
    }
}
