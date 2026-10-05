package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI
import java.text.DateFormat
import java.util.Date

enum class M7CaptureFilterUi {
    RELEVANT,
    ALL,
    AUTH,
    API,
    GRAPHQL,
    WEBSOCKET,
    STATIC_RESOURCE,
    ANALYTICS,
    TELEMETRY,
}

enum class M7InspectorTabUi {
    OVERVIEW,
    HEADERS,
    BODY,
    RESPONSE,
    TIMING,
}

internal object M7CaptureUx {
    private val STATIC_EXTENSIONS = setOf(
        ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".woff", ".woff2",
        ".ttf", ".eot", ".webp", ".mp4", ".webm", ".map",
    )

    private val ANALYTICS_PATTERNS = listOf(
        "google-analytics", "analytics", "segment.io", "mixpanel", "amplitude", "hotjar",
        "doubleclick", "clarity.ms", "stats", "pixel", "facebook.com/tr",
    )

    private val TELEMETRY_PATTERNS = listOf(
        "telemetry", "sentry.io", "bugsnag", "datadog", "crashlytics", "newrelic",
        "loggly", "rollbar", "metrics", "events",
    )

    fun filtered(
        requests: List<M4CaptureRequestUiState>,
        filter: M7CaptureFilterUi,
    ): List<M4CaptureRequestUiState> =
        when (filter) {
            M7CaptureFilterUi.RELEVANT -> requests.filter { it.relevantByDefault }
            M7CaptureFilterUi.ALL -> requests
            M7CaptureFilterUi.AUTH -> requests.filter {
                it.relevanceCategory.equals("AUTHENTICATION", ignoreCase = true) ||
                    it.category.contains("auth", ignoreCase = true)
            }
            M7CaptureFilterUi.API -> requests.filter {
                it.relevanceCategory.uppercase() in API_CATEGORIES
            }
            M7CaptureFilterUi.GRAPHQL -> requests.filter {
                it.relevanceCategory.equals("GRAPHQL", ignoreCase = true) ||
                    it.url.contains("/graphql", ignoreCase = true) ||
                    it.bodyRepresentation.equals("GRAPHQL", ignoreCase = true)
            }
            M7CaptureFilterUi.WEBSOCKET -> requests.filter {
                it.relevanceCategory.equals("WEBSOCKET", ignoreCase = true) ||
                    it.url.startsWith("ws://", ignoreCase = true) ||
                    it.url.startsWith("wss://", ignoreCase = true) ||
                    it.headers.any { h ->
                        h.name.equals("Upgrade", ignoreCase = true) &&
                            h.displayValue.contains("websocket", ignoreCase = true)
                    }
            }
            M7CaptureFilterUi.STATIC_RESOURCE -> requests.filter {
                it.relevanceCategory.equals("STATIC_RESOURCE", ignoreCase = true) ||
                    it.relevanceCategory.equals("STATIC", ignoreCase = true) ||
                    STATIC_EXTENSIONS.any { ext -> it.url.substringBefore('?').endsWith(ext, ignoreCase = true) }
            }
            M7CaptureFilterUi.ANALYTICS -> requests.filter {
                it.relevanceCategory.equals("ANALYTICS", ignoreCase = true) ||
                    ANALYTICS_PATTERNS.any { p -> it.url.contains(p, ignoreCase = true) }
            }
            M7CaptureFilterUi.TELEMETRY -> requests.filter {
                it.relevanceCategory.equals("TELEMETRY", ignoreCase = true) ||
                    TELEMETRY_PATTERNS.any { p -> it.url.contains(p, ignoreCase = true) }
            }
        }

    fun captureAnnouncement(relevantCount: Int): String =
        "Capture active, " + relevantCount.coerceAtLeast(0) +
            " relevant request" + if (relevantCount == 1) "" else "s"

    fun bodyAnnouncement(completeness: M4CompletenessUi): String =
        when (completeness) {
            M4CompletenessUi.COMPLETE -> "Request body, completely captured"
            M4CompletenessUi.PARTIAL -> "Request body, partially captured"
            M4CompletenessUi.TRUNCATED -> "Request body, truncated"
            M4CompletenessUi.UNAVAILABLE -> "Request body, not captured"
            M4CompletenessUi.NOT_APPLICABLE -> "Request body, not applicable"
        }

    fun policyPreview(
        request: M4CaptureRequestUiState,
        policy: M4SecretPolicyUi,
    ): String {
        if (request.sensitiveCount == 0) {
            return "No protected credential fields are included."
        }

        val protectedHeaders = request.headers.filter { it.sensitive }
        if (protectedHeaders.isEmpty()) {
            return when (policy) {
                M4SecretPolicyUi.PARAMETERIZE ->
                    "Protected request fields will be replaced with placeholders."
                M4SecretPolicyUi.MASK ->
                    "Protected request fields will be replaced with masked values."
                M4SecretPolicyUi.EXPLICIT ->
                    if (request.explicitPolicyAllowed) {
                        "A live credential will be included only after explicit consent."
                    } else {
                        "Explicit live credentials are unavailable for this capture."
                    }
            }
        }

        return protectedHeaders
            .take(3)
            .joinToString("\n") { header ->
                val effective = if (
                    header.secretCategory == "COOKIE" &&
                    policy != M4SecretPolicyUi.EXPLICIT
                ) {
                    M4SecretPolicyUi.MASK
                } else {
                    policy
                }

                val value = when (effective) {
                    M4SecretPolicyUi.PARAMETERIZE ->
                        parameterName(header.secretCategory)
                    M4SecretPolicyUi.MASK ->
                        header.displayValue
                    M4SecretPolicyUi.EXPLICIT ->
                        if (request.explicitPolicyAllowed) {
                            "<live value requires separate consent>"
                        } else {
                            "<explicit unavailable>"
                        }
                }
                header.name + ": " + value
            }
    }

    private fun parameterName(category: String?): String =
        when (category) {
            "API_KEY" -> "{{API_KEY}}"
            "CSRF_TOKEN" -> "{{CSRF_TOKEN}}"
            "SESSION_ID" -> "{{SESSION_ID}}"
            "CLIENT_SECRET" -> "{{CLIENT_SECRET}}"
            "PASSWORD" -> "{{PASSWORD}}"
            "QUERY_TOKEN" -> "{{QUERY_TOKEN}}"
            "COOKIE" -> "{{COOKIE}}"
            else -> "{{AUTH_TOKEN}}"
        }

    private val API_CATEGORIES = setOf(
        "AUTHENTICATION",
        "PRIMARY_API",
        "BUSINESS_API",
        "GRAPHQL",
        "WEBSOCKET",
    )
}

internal object M7SafeExport {
    fun curl(request: M4CaptureRequestUiState): String {
        val parts = mutableListOf(
            "curl",
            "-X",
            shellQuote(request.method),
            shellQuote(request.url),
        )
        request.headers.forEach { header ->
            parts += "-H"
            parts += shellQuote(header.name + ": " + header.displayValue)
        }

        val safeBody = request.safeBodyPreview?.takeIf {
            request.requestBodyCompleteness == M4CompletenessUi.COMPLETE &&
                !request.safeBodyPreviewTruncated
        }
        if (safeBody != null) {
            parts += "--data-raw"
            parts += shellQuote(safeBody)
        }

        val command = parts.joinToString(" ")
        return if (
            request.requestBodyCompleteness !in
            setOf(M4CompletenessUi.COMPLETE, M4CompletenessUi.NOT_APPLICABLE) ||
            request.safeBodyPreviewTruncated
        ) {
            command + "\n# Body omitted from masked export because the safe UI projection is incomplete."
        } else {
            command
        }
    }

    fun shareText(request: M4CaptureRequestUiState): String =
        buildString {
            append("Taho Browser masked request\n")
            append(curl(request))
            append("\n\nSensitive values are masked or parameterized.")
        }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}

@Composable
internal fun M7CaptureSummarySheet(
    requests: List<M4CaptureRequestUiState>,
    filter: M7CaptureFilterUi,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    listState: LazyListState,
    selectedId: String?,
    onFilterSelected: (M7CaptureFilterUi) -> Unit,
    onSelect: (String) -> Unit,
    onOpenCaptureSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val relevantCount = requests.count { it.relevantByDefault }
    val visible = filterByQuery(M7CaptureUx.filtered(requests, filter), searchQuery)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(.92f)
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Captured",
                    color = ink,
                    fontFamily = TahoBody,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Text(
                    text = relevantCount.toString() +
                        if (relevantCount == 1) " relevant request · filtered" else " relevant requests · filtered",
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                )
            }
            M7SheetHeaderAction(
                glyph = "⚙",
                description = "Capture settings",
                onClick = onOpenCaptureSettings,
            )
            M7SheetHeaderAction(
                glyph = "×",
                description = "Close captured requests",
                onClick = onClose,
            )
        }
        Spacer(Modifier.height(12.dp))
        M7CaptureSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            M7FilterChip("Relevant", M7CaptureFilterUi.RELEVANT, filter, onFilterSelected)
            M7FilterChip("All", M7CaptureFilterUi.ALL, filter, onFilterSelected)
            M7FilterChip("Auth", M7CaptureFilterUi.AUTH, filter, onFilterSelected)
            M7FilterChip("API", M7CaptureFilterUi.API, filter, onFilterSelected)
            M7FilterChip("GraphQL", M7CaptureFilterUi.GRAPHQL, filter, onFilterSelected)
            M7FilterChip("WebSocket", M7CaptureFilterUi.WEBSOCKET, filter, onFilterSelected)
            M7FilterChip("Static", M7CaptureFilterUi.STATIC_RESOURCE, filter, onFilterSelected)
            M7FilterChip("Analytics", M7CaptureFilterUi.ANALYTICS, filter, onFilterSelected)
            M7FilterChip("Telemetry", M7CaptureFilterUi.TELEMETRY, filter, onFilterSelected)
        }
        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            val searching = searchQuery.isNotBlank()
            val emptyTitle = when {
                searching -> "No requests match \"" + searchQuery.trim() + "\""
                filter == M7CaptureFilterUi.RELEVANT -> "No relevant requests yet."
                filter == M7CaptureFilterUi.ALL -> "No captured requests yet."
                filter == M7CaptureFilterUi.AUTH -> "No authentication requests in this capture."
                filter == M7CaptureFilterUi.API -> "No API requests in this capture."
                filter == M7CaptureFilterUi.GRAPHQL -> "No GraphQL requests in this capture."
                filter == M7CaptureFilterUi.WEBSOCKET -> "No WebSocket streams in this capture."
                filter == M7CaptureFilterUi.STATIC_RESOURCE -> "No static resource requests in this capture."
                filter == M7CaptureFilterUi.ANALYTICS -> "No analytics requests in this capture."
                filter == M7CaptureFilterUi.TELEMETRY -> "No telemetry requests in this capture."
                else -> "No matching requests in this capture."
            }
            val emptyDetail = when {
                searching -> "Search runs on host, path, method, status, request ID and content-type."
                filter == M7CaptureFilterUi.RELEVANT ->
                    "Continue browsing and Taho will surface API activity here."
                else -> "Change the filter or continue browsing."
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp),
            ) {
                Text(
                    text = emptyTitle,
                    color = ink,
                    fontFamily = TahoMono,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = emptyDetail,
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { request ->
                    val inspectable = filter != M7CaptureFilterUi.ALL || request.relevantByDefault
                    M7SummaryRow(
                        request = request,
                        inspectable = inspectable,
                        selected = request.id == selectedId,
                        onClick = { if (inspectable) onSelect(request.id) },
                    )
                }
            }
        }

        if (filter == M7CaptureFilterUi.ALL) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "view all — includes noise · read-only",
                color = mute,
                fontFamily = TahoMono,
                fontSize = 9.sp,
            )
        }
    }
}

/**
 * The M7 sheet-header action: a square, glyph-only control carrying a
 * description so TalkBack names it.
 *
 * Lifted out of the capture summary header's Close `×` rather than authored a
 * second time, so the capture-settings entry point is the same affordance the
 * reader's eye already knows. 48dp, per the plan's minimum touch target.
 *
 * Emoji stay `Text` glyphs here on purpose — the TahoIcon migration is a
 * separate queued task and must not be smuggled in with a reachability fix.
 */
@Composable
private fun M7SheetHeaderAction(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = mute, fontSize = 18.sp)
    }
}

@Composable
private fun M7FilterChip(
    label: String,
    value: M7CaptureFilterUi,
    selectedValue: M7CaptureFilterUi,
    onSelect: (M7CaptureFilterUi) -> Unit,
) {
    val active = value == selectedValue
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(TahoPillShape)
            .background(if (active) amber.copy(alpha = .13f) else Color.White.copy(alpha = .025f))
            .border(
                1.dp,
                if (active) amber.copy(alpha = .45f) else Color.White.copy(alpha = .08f),
                TahoPillShape,
            )
            .semantics {
                selected = active
                role = Role.Button
                contentDescription = label + ", filter" + if (active) ", selected" else ""
            }
            .clickable(onClick = { onSelect(value) })
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) amberHover else mute,
            fontFamily = TahoMono,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun M7SummaryRow(
    request: M4CaptureRequestUiState,
    inspectable: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val parsed = runCatching { URI(request.url) }.getOrNull()
    val path = parsed?.rawPath?.takeIf { it.isNotBlank() } ?: "/"
    val bodyFlag = when (request.requestBodyCompleteness) {
        M4CompletenessUi.PARTIAL -> "△ body partial"
        M4CompletenessUi.TRUNCATED -> "△ body truncated"
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .alpha(if (inspectable) 1f else .42f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = .028f))
            .border(
                1.dp,
                if (selected) amber.copy(alpha = .5f) else Color.White.copy(alpha = .08f),
                RoundedCornerShape(16.dp),
            )
            .semantics {
                if (inspectable) role = Role.Button
                contentDescription = buildString {
                    append(request.method)
                    append(" ")
                    append(path)
                    request.status?.let { append(", status ").append(it) }
                    append(", ")
                    append(request.relevanceCategory.replace('_', ' ').lowercase())
                    if (request.sensitiveCount > 0) append(", credential detected")
                    if (bodyFlag != null) append(", ").append(bodyFlag.removePrefix("△ "))
                }
            }
            .clickable(enabled = inspectable, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            M7MethodBadge(request.method)
            Spacer(Modifier.width(8.dp))
            Text(
                text = path,
                modifier = Modifier.weight(1f),
                color = ink,
                fontFamily = TahoMono,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            request.status?.let {
                Text(
                    text = it.toString(),
                    color = if (it in 200..399) ok else TahoWarn,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                )
            }
            request.durationMs?.let {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = it.toString() + " ms",
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            M7CategoryChip(request.relevanceCategory)
            if (request.sensitiveCount > 0) {
                Text(
                    text = "⚑ credential detected",
                    color = TahoWarn,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                )
            }
            if (bodyFlag != null) {
                Text(
                    text = bodyFlag,
                    color = info,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                )
            }
            request.transactionState
                ?.takeIf { it in setOf("FAILED", "CANCELLED", "PARTIAL") }
                ?.let {
                    Text(
                        text = "△ " + it.lowercase(),
                        color = TahoWarn,
                        fontFamily = TahoMono,
                        fontSize = 9.sp,
                    )
                }
        }
    }
}

@Composable
internal fun M7RequestInspectorSheet(
    request: M4CaptureRequestUiState,
    onBack: () -> Unit,
    onCopyCurl: () -> Unit,
    onShare: () -> Unit,
    onSendToTaho: () -> Unit,
    onReplay: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onToggleWorkspace: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable(request.id) { mutableStateOf(M7InspectorTabUi.OVERVIEW) }
    val parsed = runCatching { URI(request.url) }.getOrNull()
    val path = parsed?.rawPath?.takeIf { it.isNotBlank() } ?: "/"
    val host = parsed?.host ?: "unknown host"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(.92f)
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            M7MethodBadge(request.method)
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = path,
                    color = ink,
                    fontFamily = TahoMono,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(host)
                        request.status?.let { append(" · ").append(it) }
                        request.durationMs?.let { append(" · ").append(it).append(" ms") }
                    },
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onToggleWorkspace != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .semantics {
                            role = Role.Button
                            contentDescription = "Open full-screen technical workspace"
                        }
                        .clickable(onClick = onToggleWorkspace),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⛶", color = amberHover, fontSize = 16.sp)
                }
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Close request inspector"
                    }
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = mute, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            M7InspectorTabUi.entries.forEach { tab ->
                M7InspectorTabChip(tab, selectedTab) { selectedTab = it }
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                when (selectedTab) {
                    M7InspectorTabUi.OVERVIEW -> M7Overview(request, host)
                    M7InspectorTabUi.HEADERS -> M7Headers(request)
                    M7InspectorTabUi.BODY -> M7Body(request)
                    M7InspectorTabUi.RESPONSE -> M7Response(request)
                    M7InspectorTabUi.TIMING -> M7Timing(request)
                }
            }
            item { M7Provenance(request) }
        }

        request.transferBlockedReason?.let { reason ->
            Spacer(Modifier.height(8.dp))
            M7HonestyNote(reason, warning = true)
        }

        if (onReplay != null || onDelete != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onReplay != null) {
                    M7MiniButton(
                        label = "Replay",
                        modifier = Modifier.weight(1f),
                        onClick = onReplay,
                    )
                }
                if (onDelete != null) {
                    M7MiniButton(
                        label = "Delete",
                        modifier = Modifier.weight(1f),
                        onClick = onDelete,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            M7MiniButton(
                label = "Copy cURL",
                modifier = Modifier.weight(.78f),
                onClick = onCopyCurl,
            )
            M7MiniButton(
                label = "Share",
                modifier = Modifier.weight(.60f),
                onClick = onShare,
            )
            M7PrimaryButton(
                label = "Send to Taho",
                enabled = request.transferBlockedReason == null,
                modifier = Modifier.weight(1.38f),
                onClick = onSendToTaho,
            )
        }
    }
}

@Composable
private fun M7InspectorTabChip(
    tab: M7InspectorTabUi,
    selectedTab: M7InspectorTabUi,
    onSelect: (M7InspectorTabUi) -> Unit,
) {
    val active = tab == selectedTab
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) amber.copy(alpha = .10f) else Color.Transparent)
            .semantics { selected = active; role = Role.Button }
            .clickable { onSelect(tab) }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tab.name.uppercase(),
            color = if (active) amberHover else mute,
            fontFamily = TahoMono,
            fontSize = 10.sp,
        )
    }
}

/**
 * Spec §4.3 JSON palette: keys `info`, string values `amberHover`, numbers
 * `body` — applied over the `ink` base the caller sets on the Text. Numbers go
 * monochrome on purpose: a third chromatic tier on a value dump is noise, and
 * the count of tokens the syntax highlighter may spend is deliberately small.
 * Safe on non-JSON text (returns as-is).
 */
private fun highlightJson(source: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = source.length
    append(source) // base text; spans below recolor the slices
    while (i < n) {
        val c = source[i]
        when {
            c == '"' -> {
                val close = source.indexOf('"', i + 1)
                if (close < 0) break
                // a key is a string followed (after whitespace) by ':'
                var j = close + 1
                while (j < n && source[j].isWhitespace()) j++
                val isKey = j < n && source[j] == ':'
                addStyle(
                    SpanStyle(color = if (isKey) info else amberHover),
                    i,
                    close + 1,
                )
                i = close + 1
            }
            c.isDigit() || ((c == '-' || c == '+') && i + 1 < n && source[i + 1].isDigit()) -> {
                var j = i
                while (j < n && (source[j].isDigit() || source[j] in ".eE+-")) {
                    if (source[j] in "+-" && j > i && source[j - 1] !in "eE") break
                    j++
                }
                addStyle(SpanStyle(color = body), i, j)
                i = j
            }
            else -> i++
        }
    }
}

@Composable
private fun M7Overview(request: M4CaptureRequestUiState, host: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        M7KeyValue("Method · Host", request.method + " · " + host)
        M7KeyValue(
            "Status · Duration",
            (request.status?.toString() ?: "—") + " · " +
                (request.durationMs?.let { it.toString() + " ms" } ?: "— unavailable"),
        )
        M7KeyValue("Category", request.relevanceCategory.replace('_', ' ').lowercase())
        M7Evidence("Request URL", request.requestUrlCompleteness)
        M7Evidence("Request headers", request.requestHeadersCompleteness)
        M7Evidence("Request body", request.requestBodyCompleteness)
        M7Evidence("Response headers", request.responseHeadersCompleteness)
        M7Evidence("Response body", request.responseBodyCompleteness)
        M7Evidence("Timing", request.timingCompleteness)
        M7Evidence("TLS", request.tlsCompleteness)

        Spacer(Modifier.height(8.dp))
        if (request.responseBodyCompleteness == M4CompletenessUi.UNAVAILABLE) {
            M7HonestyNote(
                "Response bodies are unavailable on this GeckoView capture path. " +
                    "The request itself can still be reviewed using the evidence that was actually captured.",
            )
        }
        if (request.requestBodyCompleteness in setOf(M4CompletenessUi.PARTIAL, M4CompletenessUi.TRUNCATED)) {
            M7HonestyNote(
                "Request body was partially captured. Transfer remains blocked when the captured body is not safely reproducible.",
                warning = true,
            )
        }
    }
}

@Composable
private fun M7Headers(request: M4CaptureRequestUiState) {
    if (request.headers.isEmpty()) {
        M7HonestyNote("Header details are unavailable for this recovered capture.")
        return
    }
    Column {
        request.headers.forEach { header ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (header.sensitive) {
                            contentDescription =
                                header.name.replaceFirstChar(Char::uppercase) +
                                    " header, sensitive value hidden"
                        }
                    }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = header.name,
                    modifier = Modifier.weight(.42f),
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = header.displayValue + if (header.sensitive) "  sensitive" else "",
                    modifier = Modifier.weight(.58f),
                    color = if (header.sensitive) TahoWarn else ink,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        M7HonestyNote("Browser-managed transport headers are normalized before handoff.")
    }
}

@Composable
private fun M7Body(request: M4CaptureRequestUiState) {
    Column(
        modifier = Modifier.semantics {
            contentDescription = M7CaptureUx.bodyAnnouncement(request.requestBodyCompleteness)
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        M7Evidence("Request body", request.requestBodyCompleteness)
        request.bodyRepresentation?.let { M7KeyValue("Representation", it.lowercase()) }
        if (request.requestBodyCapturedBytes != null) {
            val captured = request.requestBodyCapturedBytes
            val declared = request.requestBodyDeclaredBytes
            M7KeyValue(
                "Captured",
                captured.toString() + " bytes" +
                    (declared?.let { " of " + it + " bytes" } ?: ""),
            )
        }

        val preview = request.safeBodyPreview
        if (preview != null) {
            Text(
                text = highlightJson(preview),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(Color.White.copy(alpha = .03f))
                    .border(1.dp, hairline, TahoBlockShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                color = ink,
                fontFamily = TahoMono,
                fontSize = 10.sp,
                lineHeight = 17.sp,
            )
            if (request.safeBodyPreviewTruncated) {
                M7HonestyNote(
                    "Preview limited to 64 KiB for UI performance. Capture completeness and transfer bytes are unchanged.",
                )
            }
        } else {
            val copy = when (request.requestBodyCompleteness) {
                M4CompletenessUi.NOT_APPLICABLE -> "No request body."
                M4CompletenessUi.UNAVAILABLE -> "Request body was not captured."
                else -> request.bodyLimitation
                    ?: "Raw body content is not present in this safe UI projection."
            }
            M7HonestyNote(copy, warning = request.requestBodyCompleteness != M4CompletenessUi.NOT_APPLICABLE)
        }
    }
}

@Composable
private fun M7Response(request: M4CaptureRequestUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        M7KeyValue("Status", request.status?.toString() ?: "— unavailable")
        M7Evidence("Response headers", request.responseHeadersCompleteness)
        M7Evidence("Response body", request.responseBodyCompleteness)
        M7HonestyNote(
            "Response metadata may be available; response evidence is optional for Browser → Taho handoff.",
        )
    }
}

@Composable
private fun M7Timing(request: M4CaptureRequestUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        M7KeyValue("Queued", "— unavailable")
        M7KeyValue("DNS + TLS", "— unavailable")
        M7KeyValue("Request sent", "— unavailable")
        M7KeyValue("Waiting (TTFB)", "— unavailable")
        M7KeyValue("Content download", "— unavailable")
        M7KeyValue("Total", request.durationMs?.let { it.toString() + " ms" } ?: "— unavailable")
        M7Evidence("Timing completeness", request.timingCompleteness)
        M7HonestyNote("Only measured timing fields are shown; missing phases are never inferred.")
    }
}

@Composable
private fun M7Provenance(request: M4CaptureRequestUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = .02f))
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "provenance",
            color = mute,
            fontFamily = TahoMono,
            fontSize = 9.sp,
        )
        M7KeyValue(
            "source",
            request.sourceProduct + " · " + (request.sourceVersion ?: "version unavailable"),
        )
        M7KeyValue("capture session", request.captureSessionId ?: "— unavailable")
        M7KeyValue("tab", request.tabId ?: "— unresolved")
        M7KeyValue(
            "captured",
            request.capturedAtEpochMs?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it))
            } ?: "— unavailable",
        )
        M7KeyValue(
            "completeness",
            "url " + request.requestUrlCompleteness.name.lowercase() +
                " · req headers " + request.requestHeadersCompleteness.name.lowercase() +
                " · req body " + request.requestBodyCompleteness.name.lowercase() +
                " · resp headers " + request.responseHeadersCompleteness.name.lowercase() +
                " · resp body " + request.responseBodyCompleteness.name.lowercase(),
        )
        request.captureEngineVersion?.let { M7KeyValue("engine", it) }
        request.normalizerVersion?.let { M7KeyValue("normalizer", it) }
        M7KeyValue("redirects", request.redirectCount.toString())
    }
}

@Composable
internal fun M7SendConfirmationSheet(
    request: M4CaptureRequestUiState,
    selectedPolicy: M4SecretPolicyUi,
    onPolicySelected: (M4SecretPolicyUi) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    isTahoInstalled: Boolean = true,
    onInstallTaho: () -> Unit = {},
    onExportInstead: () -> Unit = {},
) {
    val parsed = runCatching { URI(request.url) }.getOrNull()
    val host = parsed?.host ?: "request"
    val path = parsed?.rawPath?.takeIf { it.isNotBlank() } ?: "/"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Send to Taho?",
                    color = ink,
                    fontFamily = TahoBody,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Text(
                    text = request.method + " " + host + path,
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .semantics { role = Role.Button; contentDescription = "Close Send to Taho confirmation" }
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = mute, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        M7TransferLine("✓", "URL · Method · Query — included", ok)
        M7TransferLine(
            if (request.requestBodyCompleteness == M4CompletenessUi.COMPLETE ||
                request.requestBodyCompleteness == M4CompletenessUi.NOT_APPLICABLE
            ) "✓" else "△",
            when (request.requestBodyCompleteness) {
                M4CompletenessUi.NOT_APPLICABLE -> "Request body — not applicable"
                M4CompletenessUi.COMPLETE -> "Request body — included"
                M4CompletenessUi.PARTIAL -> "Request body — partially captured"
                M4CompletenessUi.TRUNCATED -> "Request body — truncated"
                M4CompletenessUi.UNAVAILABLE -> "Request body — unavailable"
            },
            if (request.requestBodyCompleteness == M4CompletenessUi.COMPLETE ||
                request.requestBodyCompleteness == M4CompletenessUi.NOT_APPLICABLE
            ) ok else TahoWarn,
        )
        M7TransferLine("✓", "Non-sensitive headers — normalized", ok)
        if (request.sensitiveCount > 0) {
            M7TransferLine("⛨", "Authorization and other detected secrets — protected below", TahoWarn)
        }

        if (request.fromPrivateSession) {
            Spacer(Modifier.height(10.dp))
            M7HonestyNote(
                "This request came from a private session. Sending it to Taho may persist the request there.",
                warning = true,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Credential policy",
            color = mute,
            fontFamily = TahoMono,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            M7PolicyButton("Parameterize", M4SecretPolicyUi.PARAMETERIZE, selectedPolicy, true, onPolicySelected)
            M7PolicyButton("Mask", M4SecretPolicyUi.MASK, selectedPolicy, true, onPolicySelected)
            M7PolicyButton(
                "Explicit",
                M4SecretPolicyUi.EXPLICIT,
                selectedPolicy,
                request.explicitPolicyAllowed,
                onPolicySelected,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = M7CaptureUx.policyPreview(request, selectedPolicy),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = .025f))
                .border(
                    1.dp,
                    if (selectedPolicy == M4SecretPolicyUi.EXPLICIT) TahoWarn.copy(alpha = .5f)
                    else Color.White.copy(alpha = .08f),
                    RoundedCornerShape(14.dp),
                )
                .padding(12.dp),
            color = if (selectedPolicy == M4SecretPolicyUi.EXPLICIT) TahoWarn else amberHover,
            fontFamily = TahoMono,
            fontSize = 9.sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = when (selectedPolicy) {
                M4SecretPolicyUi.PARAMETERIZE ->
                    "Protected fields use placeholders; cookie values remain masked by the Browser policy default."
                M4SecretPolicyUi.MASK ->
                    "Masked — the imported request is not executable as-is."
                M4SecretPolicyUi.EXPLICIT ->
                    if (request.explicitPolicyAllowed) {
                        "It may persist in Taho. This transfers a live credential."
                    } else {
                        "No consent-safe live secret source is retained."
                    }
            },
            color = mute,
            fontFamily = TahoMono,
            fontSize = 9.sp,
        )

        Spacer(Modifier.height(18.dp))
        if (!isTahoInstalled) {
            M7HonestyNote(
                "Taho API Testing is not installed on this device. You can install it from the app store or export this request directly.",
                warning = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                M7PrimaryButton(
                    label = "Install Taho",
                    showArrow = true,
                    modifier = Modifier.weight(1f),
                    onClick = onInstallTaho,
                )
                M7SecondaryButton(
                    label = "Export Instead",
                    modifier = Modifier.weight(1f),
                    onClick = onExportInstead,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                M7PrimaryButton(
                    label = "Send to Taho",
                    showArrow = false,
                    enabled = request.transferBlockedReason == null &&
                        (selectedPolicy != M4SecretPolicyUi.EXPLICIT || request.explicitPolicyAllowed),
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
                M7SecondaryButton("Cancel", Modifier.weight(1f), onCancel)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = "Import ≠ execute. Taho opens an unsaved request; no network request runs during import.",
            color = mute,
            fontFamily = TahoMono,
            fontSize = 9.sp,
        )
    }
}

/**
 * Browser and capture settings — the only UI for retention mode and for
 * deleting captures, so it has to stay reachable from the capture surface.
 *
 * Renders sheet *content*, not a sheet: no scrim, no drag handle, no
 * dismissal of its own until [onDismiss] was added. That parameter is
 * deliberately without a default, so a future call site cannot compose this
 * with no way out.
 */
@Composable
internal fun M7SettingsSheet(
    captureCapabilityNote: String?,
    retentionMode: String = "Session only",
    onRetentionModeChanged: (String) -> Unit = {},
    onClearCaptureData: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmingClear by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Settings",
                    color = ink,
                    fontFamily = TahoBody,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Text(
                    text = "Browser and capture",
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                )
            }
            M7SheetHeaderAction(
                glyph = "×",
                description = "Close settings",
                onClick = onDismiss,
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Capture retention",
                    color = ink,
                    fontFamily = TahoMono,
                    fontSize = 11.sp,
                )
                Text(
                    text = if (retentionMode == "Keep until deleted") {
                        "Workspace mode: records are kept until deleted."
                    } else {
                        "Ephemeral: records are cleared when tabs close."
                    },
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (retentionMode == "Session only") amber.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .clickable { onRetentionModeChanged("Session only") }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text("Session", color = if (retentionMode == "Session only") amberHover else mute, fontSize = 10.sp, fontFamily = TahoMono)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (retentionMode == "Keep until deleted") amber.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .clickable { onRetentionModeChanged("Keep until deleted") }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text("Keep until deleted", color = if (retentionMode == "Keep until deleted") amberHover else mute, fontSize = 10.sp, fontFamily = TahoMono)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        M7KeyValue("Default secret policy", "Parameterize")
        M7KeyValue("Response bodies", "Unavailable on current capture path")
        M7KeyValue("Reduced motion", "Follows Android animation scale")

        captureCapabilityNote?.let {
            Spacer(Modifier.height(10.dp))
            M7HonestyNote(it, warning = true)
        }

        Spacer(Modifier.height(18.dp))
        if (!confirmingClear) {
            M7SecondaryButton(
                label = "Clear capture data",
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmingClear = true },
            )
        } else {
            Text(
                text = "Delete captured requests?",
                color = ink,
                fontFamily = TahoMono,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Your websites and login sessions will remain.",
                color = mute,
                fontFamily = TahoMono,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                M7SecondaryButton("Cancel", Modifier.weight(1f)) { confirmingClear = false }
                M7PrimaryButton(
                    label = "Delete Capture",
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        confirmingClear = false
                        onClearCaptureData()
                    },
                )
            }
        }
    }
}

@Composable
internal fun M7TechnicalWorkspaceView(
    request: M4CaptureRequestUiState,
    onClose: () -> Unit,
    onCopyCurl: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas)
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TECHNICAL WORKSPACE",
                    color = amberHover,
                    fontFamily = TahoMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
                Text(
                    text = request.method + " " + request.url,
                    color = ink,
                    fontFamily = TahoMono,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = mute, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            M7SecondaryButton("Copy cURL", Modifier.weight(1f), onCopyCurl)
            M7SecondaryButton("Export / Share", Modifier.weight(1f), onShare)
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                M7RawSection(
                    title = "METRICS & PROVENANCE",
                    entries = listOf(
                        "Request ID" to request.id,
                        "Status" to "${request.status ?: 0}",
                        "Duration" to "${request.durationMs ?: 0} ms",
                        "Initiator" to (request.observationSource ?: "network"),
                        "Redirects" to "${request.redirectCount}",
                        "Private Session" to "${request.fromPrivateSession}",
                    ),
                )
            }
            item {
                M7RawSection(
                    title = "REQUEST HEADERS",
                    entries = request.headers.map { it.name to it.displayValue },
                )
            }
            item {
                M7RawSection(
                    title = "COMPLETENESS AUDIT",
                    entries = listOf(
                        "URL" to request.requestUrlCompleteness.name,
                        "Headers" to request.requestHeadersCompleteness.name,
                        "Body" to request.requestBodyCompleteness.name,
                        "Response Headers" to request.responseHeadersCompleteness.name,
                        "Response Body" to request.responseBodyCompleteness.name,
                        "Timing" to request.timingCompleteness.name,
                        "TLS Info" to request.tlsCompleteness.name,
                    ),
                )
            }
            if (!request.safeBodyPreview.isNullOrBlank()) {
                item {
                    M7RawBlock(
                        title = "REQUEST BODY PAYLOAD",
                        content = request.safeBodyPreview,
                    )
                }
            }
        }
    }
}

@Composable
private fun M7RawSection(title: String, entries: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            text = title,
            color = mute,
            fontFamily = TahoMono,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(8.dp))
        entries.forEach { (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = key,
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(0.4f),
                )
                Text(
                    text = value,
                    color = ink,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(0.6f),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun M7RawBlock(title: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            text = title,
            color = mute,
            fontFamily = TahoMono,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = content,
            color = amberHover,
            fontFamily = TahoMono,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun M7PolicyButton(
    label: String,
    value: M4SecretPolicyUi,
    selectedValue: M4SecretPolicyUi,
    enabled: Boolean,
    onSelect: (M4SecretPolicyUi) -> Unit,
) {
    val active = value == selectedValue
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) amber.copy(alpha = .16f) else Color.White.copy(alpha = .03f))
            .border(
                1.dp,
                if (active) amber.copy(alpha = .55f) else Color.White.copy(alpha = .10f),
                RoundedCornerShape(999.dp),
            )
            .semantics { selected = active; role = Role.Button }
            .clickable(enabled = enabled, onClick = { onSelect(value) })
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> mute
                active -> amberHover
                else -> mute
            },
            fontFamily = TahoMono,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun M7Evidence(label: String, completeness: M4CompletenessUi) {
    val value = when (completeness) {
        M4CompletenessUi.COMPLETE -> "✓ complete"
        M4CompletenessUi.PARTIAL -> "△ partial"
        M4CompletenessUi.TRUNCATED -> "△ truncated"
        M4CompletenessUi.UNAVAILABLE -> "— not captured"
        M4CompletenessUi.NOT_APPLICABLE -> "— not applicable"
    }
    val color = when (completeness) {
        M4CompletenessUi.COMPLETE -> ok
        M4CompletenessUi.PARTIAL, M4CompletenessUi.TRUNCATED -> TahoWarn
        else -> mute
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = mute,
            fontFamily = TahoMono,
            fontSize = 10.sp,
        )
        Text(
            text = value,
            color = color,
            fontFamily = TahoMono,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun M7KeyValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(.42f),
            color = mute,
            fontFamily = TahoMono,
            fontSize = 9.sp,
        )
        Text(
            text = value,
            modifier = Modifier.weight(.58f),
            color = ink,
            fontFamily = TahoMono,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun M7HonestyNote(text: String, warning: Boolean = false) {
    Text(
        text = "ⓘ " + text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(TahoNoteShape)
            .background(Color.White.copy(alpha = .03f))
            .border(1.dp, hairline, TahoNoteShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = if (warning) TahoWarn else mute,
        fontFamily = TahoMono,
        fontSize = 9.5.sp,
        lineHeight = 15.sp,
    )
}

@Composable
private fun M7TransferLine(glyph: String, copy: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(glyph, color = color, fontFamily = TahoMono, fontSize = 10.sp)
        Spacer(Modifier.width(7.dp))
        Text(copy, color = color, fontFamily = TahoMono, fontSize = 10.sp)
    }
}

@Composable
private fun M7MethodBadge(method: String) {
    val upper = method.uppercase()
    val background = when (upper) {
        "POST" -> amber.copy(alpha = .15f)
        "DELETE" -> danger.copy(alpha = .14f)
        else -> Color.White.copy(alpha = .07f)
    }
    val foreground = when (upper) {
        "POST" -> amberHover
        "DELETE" -> danger
        else -> charcoal
    }

    Box(
        modifier = Modifier
            .clip(TahoBadgeShape)
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = upper,
            color = foreground,
            fontFamily = TahoMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.5.sp,
            letterSpacing = .4.sp,
        )
    }
}

@Composable
private fun M7CategoryChip(category: String) {
    val upper = category.uppercase()
    val accent = when (upper) {
        "AUTHENTICATION" -> TahoWarn
        "PRIMARY_API" -> amberHover
        "STATIC_RESOURCE", "ANALYTICS", "TELEMETRY" -> mute
        else -> charcoal
    }
    val borderAccent = when (upper) {
        "AUTHENTICATION" -> TahoWarn.copy(alpha = .4f)
        "PRIMARY_API" -> amber.copy(alpha = .4f)
        "STATIC_RESOURCE", "ANALYTICS", "TELEMETRY" -> hairline
        else -> hairline
    }
    val label = upper
        .lowercase()
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    Box(
        modifier = Modifier
            .clip(TahoPillShape)
            .border(1.dp, borderAccent, TahoPillShape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = accent,
            fontFamily = TahoMono,
            fontSize = 9.5.sp,
        )
    }
}

@Composable
private fun M7MiniButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .tahoPressScale(interaction, target = .97f)
            .clip(TahoPillShape)
            .background(bone)
            .border(1.dp, hairline, TahoPillShape)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = mute,
            fontFamily = TahoMono,
            fontSize = 9.5.sp,
            maxLines = 1,
        )
    }
}

/**
 * Prototype "button-in-button" primary: gold pill, body-text label, and the
 * trailing arrow nested inside its own dark circular wrapper (spec §6).
 */
@Composable
internal fun M7PrimaryButton(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .tahoPressScale(interaction, target = .96f)
            .clip(TahoPillShape)
            .background(if (enabled) amber else Color.White.copy(alpha = .04f))
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                color = if (enabled) canvas else mute,
                fontFamily = TahoBody,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
            )
            if (showArrow) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(TahoPillShape)
                        .background(Color.Black.copy(alpha = .15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↗",
                        color = if (enabled) canvas else mute,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun M7SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .tahoPressScale(interaction, target = .97f)
            .clip(TahoPillShape)
            .background(bone)
            .border(1.dp, hairline, TahoPillShape)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ink,
            fontFamily = TahoBody,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}
