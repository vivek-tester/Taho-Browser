package app.taho.browser.capture.domain

import java.net.URI
import java.util.Locale

enum class RelevanceCategory {
    AUTHENTICATION,
    PRIMARY_API,
    BUSINESS_API,
    GRAPHQL,
    WEBSOCKET,
    PAGE_NAVIGATION,
    STATIC_RESOURCE,
    ANALYTICS,
    TELEMETRY,
}

data class Relevance(
    val category: RelevanceCategory,
    val isFirstParty: Boolean,
    val reason: String,
    val score: Int,
)

data class RelevanceInput(
    val url: String,
    val resourceType: String?,
    val method: String,
    val targetHost: String?,
    val contentType: String? = null,
)

object RelevanceClassifier {
    const val RULE_VERSION = "relevance/1.0.0"

    fun classify(input: RelevanceInput): Relevance {
        val host = hostOf(input.url)
        val firstParty = host != null && matchesHostBoundary(host, input.targetHost)
        val type = input.resourceType?.lowercase(Locale.ROOT)
        val content = input.contentType?.lowercase(Locale.ROOT).orEmpty()
        val path = runCatching { URI(input.url).path.lowercase(Locale.ROOT) }.getOrDefault("")

        val category = when {
            type == "main_frame" -> RelevanceCategory.PAGE_NAVIGATION
            type == "websocket" -> RelevanceCategory.WEBSOCKET
            content.contains("graphql") || path.contains("/graphql") ->
                RelevanceCategory.GRAPHQL
            isStatic(type, path) -> RelevanceCategory.STATIC_RESOURCE
            isTelemetry(host, path) -> RelevanceCategory.TELEMETRY
            isAnalytics(host, path) -> RelevanceCategory.ANALYTICS
            isAuthentication(path) -> RelevanceCategory.AUTHENTICATION
            type in API_TYPES && firstParty -> RelevanceCategory.PRIMARY_API
            type in API_TYPES -> RelevanceCategory.BUSINESS_API
            input.method.uppercase(Locale.ROOT) != "GET" ->
                if (firstParty) RelevanceCategory.PRIMARY_API else RelevanceCategory.BUSINESS_API
            else -> RelevanceCategory.BUSINESS_API
        }

        val score = when (category) {
            RelevanceCategory.AUTHENTICATION -> 100
            RelevanceCategory.PRIMARY_API -> 90
            RelevanceCategory.GRAPHQL -> 85
            RelevanceCategory.BUSINESS_API -> 80
            RelevanceCategory.WEBSOCKET -> 75
            RelevanceCategory.PAGE_NAVIGATION -> 50
            RelevanceCategory.STATIC_RESOURCE -> 20
            RelevanceCategory.ANALYTICS -> 10
            RelevanceCategory.TELEMETRY -> 5
        } + if (firstParty) 3 else 0

        return Relevance(
            category = category,
            isFirstParty = firstParty,
            reason = reason(category, firstParty),
            score = score,
        )
    }

    fun isRelevantByDefault(relevance: Relevance): Boolean =
        relevance.category !in setOf(
            RelevanceCategory.STATIC_RESOURCE,
            RelevanceCategory.ANALYTICS,
            RelevanceCategory.TELEMETRY,
        )

    fun matchesHostBoundary(host: String, targetHost: String?): Boolean {
        if (targetHost.isNullOrBlank()) return false
        val actual = host.trim('.').lowercase(Locale.ROOT)
        val target = targetHost.trim('.').lowercase(Locale.ROOT)
        return actual == target || actual.endsWith(".$target")
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull()

    private fun isStatic(type: String?, path: String): Boolean =
        type in STATIC_TYPES ||
            STATIC_SUFFIXES.any(path::endsWith)

    private fun isAuthentication(path: String): Boolean =
        AUTH_PATHS.any(path::contains)

    private fun isAnalytics(host: String?, path: String): Boolean =
        ANALYTICS_MARKERS.any { marker ->
            host?.contains(marker) == true || path.contains(marker)
        }

    private fun isTelemetry(host: String?, path: String): Boolean =
        TELEMETRY_MARKERS.any { marker ->
            host?.contains(marker) == true || path.contains(marker)
        }

    private fun reason(category: RelevanceCategory, firstParty: Boolean): String =
        when {
            category == RelevanceCategory.PAGE_NAVIGATION -> "top-level navigation"
            category == RelevanceCategory.STATIC_RESOURCE -> "static resource"
            category == RelevanceCategory.AUTHENTICATION -> "authentication endpoint"
            category == RelevanceCategory.GRAPHQL -> "GraphQL request"
            category == RelevanceCategory.WEBSOCKET -> "WebSocket handshake"
            category == RelevanceCategory.ANALYTICS -> "analytics traffic"
            category == RelevanceCategory.TELEMETRY -> "telemetry traffic"
            firstParty -> "matches workspace host"
            else -> "API-like request"
        }

    private val API_TYPES = setOf("xmlhttprequest", "fetch")
    private val STATIC_TYPES = setOf("image", "stylesheet", "font", "media", "script")
    private val STATIC_SUFFIXES = setOf(
        ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg",
        ".woff", ".woff2", ".ttf", ".ico", ".mp4", ".webm",
    )
    private val AUTH_PATHS = setOf(
        "/auth", "/login", "/logout", "/token", "/oauth", "/session",
    )
    private val ANALYTICS_MARKERS = setOf(
        "analytics", "google-analytics", "segment", "mixpanel", "amplitude",
    )
    private val TELEMETRY_MARKERS = setOf(
        "telemetry", "sentry", "datadog", "newrelic", "bugsnag",
    )
}
