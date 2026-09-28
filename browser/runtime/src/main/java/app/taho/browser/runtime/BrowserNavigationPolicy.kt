package app.taho.browser.runtime

import java.net.URI

enum class BrowserNavigationDisposition {
    ALLOW_IN_BROWSER,
    OPEN_NEW_TAB,
    REQUEST_EXTERNAL_APP,
    DENY,
}

data class BrowserNavigationDecision(
    val disposition: BrowserNavigationDisposition,
    val scheme: String?,
)

object BrowserNavigationPolicy {
    private const val MAX_ORIGIN_LENGTH = 160

    private val internalSchemes = setOf("http", "https", "about")
    private val externalSchemes = setOf("mailto", "tel", "sms", "geo", "intent")
    private val schemePattern = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

    fun decide(
        uri: String,
        targetNewWindow: Boolean,
        hasUserGesture: Boolean,
        isRedirect: Boolean,
        externalRequestPending: Boolean,
    ): BrowserNavigationDecision {
        val scheme = schemeOf(uri)

        if (scheme in internalSchemes) {
            val disposition = if (targetNewWindow) {
                if (hasUserGesture) {
                    BrowserNavigationDisposition.OPEN_NEW_TAB
                } else {
                    BrowserNavigationDisposition.DENY
                }
            } else {
                BrowserNavigationDisposition.ALLOW_IN_BROWSER
            }
            return BrowserNavigationDecision(disposition, scheme)
        }

        if (scheme in externalSchemes) {
            val allowed =
                hasUserGesture &&
                    !isRedirect &&
                    !externalRequestPending
            return BrowserNavigationDecision(
                disposition = if (allowed) {
                    BrowserNavigationDisposition.REQUEST_EXTERNAL_APP
                } else {
                    BrowserNavigationDisposition.DENY
                },
                scheme = scheme,
            )
        }

        return BrowserNavigationDecision(
            disposition = BrowserNavigationDisposition.DENY,
            scheme = scheme,
        )
    }

    fun displayOrigin(raw: String): String {
        val parsed = runCatching { URI(raw) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val host = parsed?.host

        if ((scheme == "http" || scheme == "https") && !host.isNullOrBlank()) {
            return buildString {
                append(scheme)
                append("://")
                append(host)
                if (parsed.port >= 0) {
                    append(':')
                    append(parsed.port)
                }
            }.take(MAX_ORIGIN_LENGTH)
        }

        return "Unknown origin"
    }

    private fun schemeOf(uri: String): String? =
        schemePattern.find(uri.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
}
