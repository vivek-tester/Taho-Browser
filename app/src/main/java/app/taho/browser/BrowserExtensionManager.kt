package app.taho.browser

import app.taho.browser.shell.ExtensionUi
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

enum class BrowserExtensionPromptKind {
    INSTALL,
    UPDATE,
    OPTIONAL_PERMISSION,
}

data class BrowserExtensionPermissionRequest(
    val kind: BrowserExtensionPromptKind,
    val extensionName: String,
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollectionPermissions: List<String>,
)

class BrowserExtensionManager(
    runtime: GeckoRuntime,
    private val onInventory: (List<ExtensionUi>) -> Unit,
    private val onPermissionRequest: (
        BrowserExtensionPermissionRequest,
        (Boolean) -> Unit,
    ) -> Unit,
) {
    private val controller = runtime.webExtensionController
    private val known = linkedMapOf<String, WebExtension>()

    init {
        controller.setPromptDelegate(
            object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<out String>,
                    origins: Array<out String>,
                    dataCollectionPermissions: Array<out String>,
                ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                    val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                    ask(
                        BrowserExtensionPromptKind.INSTALL,
                        extension,
                        permissions,
                        origins,
                        dataCollectionPermissions,
                    ) { allowed ->
                        result.complete(
                            WebExtension.PermissionPromptResponse(
                                allowed,
                                false,
                                false,
                            ),
                        )
                    }
                    return result
                }

                override fun onUpdatePrompt(
                    extension: WebExtension,
                    newPermissions: Array<out String>,
                    newOrigins: Array<out String>,
                    newDataCollectionPermissions: Array<out String>,
                ): GeckoResult<AllowOrDeny>? {
                    val result = GeckoResult<AllowOrDeny>()
                    ask(
                        BrowserExtensionPromptKind.UPDATE,
                        extension,
                        newPermissions,
                        newOrigins,
                        newDataCollectionPermissions,
                    ) { allowed ->
                        result.complete(if (allowed) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                    }
                    return result
                }

                override fun onOptionalPrompt(
                    extension: WebExtension,
                    permissions: Array<out String>,
                    origins: Array<out String>,
                    dataCollectionPermissions: Array<out String>,
                ): GeckoResult<AllowOrDeny>? {
                    val result = GeckoResult<AllowOrDeny>()
                    ask(
                        BrowserExtensionPromptKind.OPTIONAL_PERMISSION,
                        extension,
                        permissions,
                        origins,
                        dataCollectionPermissions,
                    ) { allowed ->
                        result.complete(if (allowed) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                    }
                    return result
                }
            },
        )
    }

    fun refresh(onComplete: ((Boolean) -> Unit)? = null) {
        controller.list().accept(
            { extensions ->
                known.clear()
                extensions.orEmpty().forEach { extension ->
                    known[extension.id] = extension
                }
                publish()
                onComplete?.invoke(true)
            },
            {
                onComplete?.invoke(false)
            },
        )
    }

    fun install(
        uri: String,
        onComplete: (Boolean, String?) -> Unit,
    ) {
        val safeUri = uri.trim()
        if (!safeUri.startsWith("https://")) {
            onComplete(false, "Only HTTPS extension package URLs are supported.")
            return
        }

        controller.install(
            safeUri,
            WebExtensionController.INSTALLATION_METHOD_MANAGER,
        ).accept(
            {
                refresh()
                onComplete(true, null)
            },
            { error ->
                onComplete(
                    false,
                    error?.javaClass?.simpleName ?: "Extension installation failed",
                )
            },
        )
    }

    fun setEnabled(id: String, enabled: Boolean, onComplete: (Boolean) -> Unit) {
        val extension = known[id] ?: run {
            onComplete(false)
            return
        }
        val operation = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        operation.accept(
            {
                refresh()
                onComplete(true)
            },
            { onComplete(false) },
        )
    }

    fun setAllowedInPrivate(id: String, allowed: Boolean, onComplete: (Boolean) -> Unit) {
        val extension = known[id] ?: run {
            onComplete(false)
            return
        }
        controller.setAllowedInPrivateBrowsing(extension, allowed).accept(
            {
                refresh()
                onComplete(true)
            },
            { onComplete(false) },
        )
    }

    fun update(id: String, onComplete: (Boolean, Boolean) -> Unit) {
        val extension = known[id] ?: run {
            onComplete(false, false)
            return
        }
        controller.update(extension).accept(
            { updated ->
                refresh()
                onComplete(true, updated != null)
            },
            { onComplete(false, false) },
        )
    }

    fun uninstall(id: String, onComplete: (Boolean) -> Unit) {
        val extension = known[id] ?: run {
            onComplete(false)
            return
        }
        if (extension.isBuiltIn) {
            onComplete(false)
            return
        }
        controller.uninstall(extension).accept(
            {
                refresh()
                onComplete(true)
            },
            { onComplete(false) },
        )
    }

    fun close() {
        controller.setPromptDelegate(null)
    }

    private fun ask(
        kind: BrowserExtensionPromptKind,
        extension: WebExtension,
        permissions: Array<out String>,
        origins: Array<out String>,
        dataCollectionPermissions: Array<out String>,
        callback: (Boolean) -> Unit,
    ) {
        onPermissionRequest(
            BrowserExtensionPermissionRequest(
                kind = kind,
                extensionName = extension.metaData.name
                    ?.takeIf(String::isNotBlank)
                    ?: extension.id,
                permissions = permissions.toList(),
                origins = origins.toList(),
                dataCollectionPermissions = dataCollectionPermissions.toList(),
            ),
            callback,
        )
    }

    private fun publish() {
        val visible = known.values
            .asSequence()
            .filterNot { it.id == INTERNAL_CAPTURE_EXTENSION_ID }
            .map { extension ->
                val meta = extension.metaData
                ExtensionUi(
                    id = extension.id,
                    name = meta.name?.takeIf(String::isNotBlank) ?: extension.id,
                    version = meta.version,
                    author = meta.creatorName?.takeIf(String::isNotBlank) ?: "Unknown",
                    description = meta.description?.orEmpty().orEmpty(),
                    isEnabled = meta.enabled,
                    allowedInPrivate = meta.allowedInPrivateBrowsing,
                    canBlockContent =
                        "webRequest" in meta.requiredPermissions ||
                            "webRequestBlocking" in meta.requiredPermissions,
                    permissions =
                        (meta.requiredPermissions.toList() + meta.requiredOrigins.toList())
                            .distinct(),
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()

        onInventory(visible)
    }

    private companion object {
        const val INTERNAL_CAPTURE_EXTENSION_ID = "taho-capture@browser.local"
    }
}
