package app.taho.browser.runtime

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object NavigationInput {
    private const val DEFAULT_SEARCH_TEMPLATE = "https://www.google.com/search?q=%s"

    fun resolve(
        raw: String,
        searchUrlTemplate: String = DEFAULT_SEARCH_TEMPLATE,
    ): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        if (input.equals("about:blank", ignoreCase = true)) {
            return "about:blank"
        }

        val explicitScheme = SCHEME.find(input)?.groupValues?.get(1)?.lowercase()
        if (explicitScheme != null) {
            return when {
                explicitScheme == "http" || explicitScheme == "https" -> input
                explicitScheme == "localhost" && LOCALHOST_WITH_PORT.matches(input) ->
                    "https://$input"
                else -> search(input, searchUrlTemplate)
            }
        }

        if (looksLikeHost(input)) {
            return "https://$input"
        }

        return search(input, searchUrlTemplate)
    }

    private fun looksLikeHost(input: String): Boolean {
        if (input.any(Char::isWhitespace)) return false

        val hostCandidate = input.substringBefore('/').substringBefore('?')
        if (hostCandidate.equals("localhost", ignoreCase = true)) return true
        if (hostCandidate.startsWith("localhost:", ignoreCase = true)) return true
        if (IPV4.matches(hostCandidate.substringBefore(':'))) return true

        return hostCandidate.contains('.') &&
            !hostCandidate.startsWith('.') &&
            !hostCandidate.endsWith('.')
    }

    private fun search(query: String, template: String): String {
        val encoded = URLEncoder
            .encode(query, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val safeTemplate = template
            .takeIf { it.startsWith("https://") && it.contains("%s") }
            ?: DEFAULT_SEARCH_TEMPLATE
        return safeTemplate.replace("%s", encoded)
    }

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val LOCALHOST_WITH_PORT = Regex(
        """^localhost:\d+(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val IPV4 = Regex(
        """^(?:\d{1,3}\.){3}\d{1,3}$""",
    )
}
