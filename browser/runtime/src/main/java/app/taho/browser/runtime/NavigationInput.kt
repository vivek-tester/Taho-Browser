package app.taho.browser.runtime

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object NavigationInput {
    private const val DEFAULT_SEARCH_PREFIX = "https://www.google.com/search?q="

    fun resolve(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        if (input.equals("about:blank", ignoreCase = true)) {
            return "about:blank"
        }

        val explicitScheme = SCHEME.find(input)?.groupValues?.get(1)?.lowercase()
        if (explicitScheme != null) {
            return when (explicitScheme) {
                "http", "https" -> input
                else -> search(input)
            }
        }

        if (looksLikeHost(input)) {
            return "https://$input"
        }

        return search(input)
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

    private fun search(query: String): String {
        val encoded = URLEncoder
            .encode(query, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return DEFAULT_SEARCH_PREFIX + encoded
    }

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val IPV4 = Regex(
        """^(?:\d{1,3}\.){3}\d{1,3}$""",
    )
}
