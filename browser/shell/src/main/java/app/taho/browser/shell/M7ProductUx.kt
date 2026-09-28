package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontFamily
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
}

enum class M7InspectorTabUi {
    OVERVIEW,
    HEADERS,
    BODY,
    RESPONSE,
    TIMING,
}

internal object M7CaptureUx {
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

@Composable
internal fun M7CaptureSummarySheet(
    requests: List<M4CaptureRequestUiState>,
    filter: M7CaptureFilterUi,
    listState: LazyListState,
    selectedId: String?,
    onFilterSelected: (M7CaptureFilterUi) -> Unit,
    onSelect: (String) -> Unit,
) {
    val relevantCount = requests.count { it.relevantByDefault }
    val visible = M7CaptureUx.filtered(requests, filter)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(.92f)
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
    ) {
        Text("Captured", color = TahoText, fontSize = 19.sp)
        Text(
            text = relevantCount.toString() +
                if (relevantCount == 1) " relevant request · filtered" else " relevant requests · filtered",
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
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
        }
        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp),
            ) {
                Text(
                    text = "No relevant requests yet.",
                    color = TahoText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Continue browsing and Taho will surface API activity here.",
                    color = TahoFaint,
                    fontFamily = FontFamily.Monospace,
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
                color = TahoFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }
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
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) TahoGold.copy(alpha = .14f) else Color.White.copy(alpha = .025f))
            .border(
                1.dp,
                if (active) TahoGold.copy(alpha = .5f) else Color.White.copy(alpha = .09f),
                RoundedCornerShape(999.dp),
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
            color = if (active) TahoGoldHi else TahoMuted,
            fontFamily = FontFamily.Monospace,
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
    val host = parsed?.host ?: "unknown host"
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
                if (selected) TahoGold.copy(alpha = .5f) else Color.White.copy(alpha = .08f),
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
            Text(
                text = request.method,
                color = TahoGoldHi,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = path,
                modifier = Modifier.weight(1f),
                color = TahoText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            request.status?.let {
                Text(
                    text = it.toString(),
                    color = if (it in 200..399) TahoOk else TahoWarn,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
            request.durationMs?.let {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = it.toString() + " ms",
                    color = TahoFaint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = host + " · " + request.relevanceCategory.replace('_', ' ').lowercase(),
            color = TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (request.sensitiveCount > 0 || bodyFlag != null || request.transactionState in setOf("FAILED", "CANCELLED", "PARTIAL")) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = buildString {
                    if (request.sensitiveCount > 0) append("⚑ credential detected")
                    if (bodyFlag != null) {
                        if (isNotEmpty()) append(" · ")
                        append(bodyFlag)
                    }
                    request.transactionState
                        ?.takeIf { it in setOf("FAILED", "CANCELLED", "PARTIAL") }
                        ?.let {
                            if (isNotEmpty()) append(" · ")
                            append("△ ").append(it.lowercase())
                        }
                },
                color = TahoWarn,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
internal fun M7RequestInspectorSheet(
    request: M4CaptureRequestUiState,
    onBack: () -> Unit,
    onSendToTaho: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .semantics { role = Role.Button; contentDescription = "Back to captured requests" }
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = TahoGoldHi, fontSize = 20.sp)
            }
            Spacer(Modifier.width(5.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.method + "  " + path,
                    color = TahoText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(host)
                        request.status?.let { append(" · ").append(it) }
                        request.durationMs?.let { append(" · ").append(it).append(" ms") }
                    },
                    color = TahoFaint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 2,
                )
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

        Spacer(Modifier.height(10.dp))
        M7PrimaryButton(
            label = "Send to Taho ↗",
            enabled = request.transferBlockedReason == null,
            onClick = onSendToTaho,
        )
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
            .background(if (active) TahoGold.copy(alpha = .13f) else Color.Transparent)
            .semantics { selected = active; role = Role.Button }
            .clickable { onSelect(tab) }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tab.name.lowercase().replaceFirstChar(Char::uppercase),
            color = if (active) TahoGoldHi else TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
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
        M7Evidence("Request URL", M4CompletenessUi.COMPLETE)
        M7Evidence(
            "Request headers",
            if (request.headers.isEmpty()) M4CompletenessUi.UNAVAILABLE else M4CompletenessUi.COMPLETE,
        )
        M7Evidence("Request body", request.requestBodyCompleteness)
        M7Evidence("Response body", request.responseBodyCompleteness)
        M7Evidence("Timing", if (request.durationMs == null) M4CompletenessUi.UNAVAILABLE else M4CompletenessUi.PARTIAL)
        M7Evidence("TLS", M4CompletenessUi.UNAVAILABLE)

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
                            contentDescription = header.name + " header, sensitive value hidden"
                        }
                    }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = header.name,
                    modifier = Modifier.weight(.42f),
                    color = TahoFaint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = header.displayValue + if (header.sensitive) "  sensitive" else "",
                    modifier = Modifier.weight(.58f),
                    color = if (header.sensitive) TahoWarn else TahoText,
                    fontFamily = FontFamily.Monospace,
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
                text = preview,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = .025f))
                    .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                color = TahoText,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
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
            color = TahoFaint,
            fontFamily = FontFamily.Monospace,
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
            "request body " + request.requestBodyCompleteness.name.lowercase() +
                " · response body " + request.responseBodyCompleteness.name.lowercase(),
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
        Text("Send to Taho?", color = TahoText, fontSize = 19.sp)
        Text(
            text = request.method + " " + host + path,
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))

        M7TransferLine("✓", "URL · Method · Query — included", TahoOk)
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
            ) TahoOk else TahoWarn,
        )
        M7TransferLine("✓", "Non-sensitive headers — normalized", TahoOk)
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
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
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
            color = if (selectedPolicy == M4SecretPolicyUi.EXPLICIT) TahoWarn else TahoGoldHi,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = when (selectedPolicy) {
                M4SecretPolicyUi.PARAMETERIZE ->
                    "Taho will receive placeholders for protected fields."
                M4SecretPolicyUi.MASK ->
                    "Masked — the imported request is not executable as-is."
                M4SecretPolicyUi.EXPLICIT ->
                    if (request.explicitPolicyAllowed) {
                        "It may persist in Taho. This transfers a live credential."
                    } else {
                        "No consent-safe live secret source is retained."
                    }
            },
            color = TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            M7SecondaryButton("Cancel", Modifier.weight(1f), onCancel)
            M7PrimaryButton(
                label = "Send to Taho",
                enabled = request.transferBlockedReason == null &&
                    (selectedPolicy != M4SecretPolicyUi.EXPLICIT || request.explicitPolicyAllowed),
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = "Import ≠ execute. Taho opens an unsaved request; no network request runs during import.",
            color = TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
    }
}

@Composable
internal fun M7SettingsSheet(
    captureCapabilityNote: String?,
    onClearCaptureData: () -> Unit,
) {
    var confirmingClear by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
    ) {
        Text("Settings", color = TahoText, fontSize = 19.sp)
        Text(
            text = "Browser and capture",
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(16.dp))

        M7KeyValue("Capture retention", "Session only")
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
                color = TahoText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Your websites and login sessions will remain.",
                color = TahoFaint,
                fontFamily = FontFamily.Monospace,
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
            .background(if (active) TahoGold.copy(alpha = .16f) else Color.White.copy(alpha = .03f))
            .border(
                1.dp,
                if (active) TahoGold.copy(alpha = .55f) else Color.White.copy(alpha = .10f),
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
                !enabled -> TahoFaint
                active -> TahoGoldHi
                else -> TahoMuted
            },
            fontFamily = FontFamily.Monospace,
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
        M4CompletenessUi.COMPLETE -> TahoOk
        M4CompletenessUi.PARTIAL, M4CompletenessUi.TRUNCATED -> TahoWarn
        else -> TahoFaint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Text(
            text = value,
            color = color,
            fontFamily = FontFamily.Monospace,
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
            color = TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
        Text(
            text = value,
            modifier = Modifier.weight(.58f),
            color = TahoText,
            fontFamily = FontFamily.Monospace,
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = .025f))
            .padding(10.dp),
        color = if (warning) TahoWarn else TahoMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
    )
}

@Composable
private fun M7TransferLine(glyph: String, copy: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(glyph, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Spacer(Modifier.width(7.dp))
        Text(copy, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun M7PrimaryButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) TahoGold else Color.White.copy(alpha = .04f))
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) TahoBg else TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun M7SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = .035f))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(999.dp))
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = TahoText,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}
