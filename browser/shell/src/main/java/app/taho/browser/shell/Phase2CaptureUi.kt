package app.taho.browser.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.taho.browser.capture.domain.CaptureState
import java.net.URI

private val P2Bg = Color(0xFF07111B)
private val P2Surface = Color(0xFF101C27)
private val P2SurfaceHi = Color(0xFF162533)
private val P2Hairline = Color.White.copy(alpha = .10f)
private val P2Text = Color(0xFFF5F7FA)
private val P2Muted = Color(0xFF9DA9B4)
private val P2Blue = Color(0xFF1677FF)
private val P2Cyan = Color(0xFF21D4FD)
private val P2Green = Color(0xFF39D98A)
private val P2Orange = Color(0xFFFF9F43)
private val P2Red = Color(0xFFFF5C67)

internal enum class Phase2MethodFilter { ALL, GET, POST, OTHER }
internal enum class Phase2StatusFilter { ALL, SUCCESS, REDIRECT, CLIENT_ERROR, SERVER_ERROR, FAILED }
internal enum class Phase2ExportFormat { JSON, HAR, CSV, PCAP }

@Composable
internal fun Phase2PacketCaptureScreen(
    enabled: Boolean,
    captureState: CaptureState,
    requests: List<M4CaptureRequestUiState>,
    relevantCount: Int,
    captureInBackground: Boolean,
    floatingEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    onFloatingChange: (Boolean) -> Unit,
    onViewCaptured: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Phase2Screen("Packet Capture", onBack, trailing = {
        Phase2IconButton("⚙", "Capture settings", onOpenSettings)
    }) {
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P2Surface)
                .border(1.dp, if (enabled) P2Blue.copy(alpha = .58f) else P2Hairline, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(P2Blue.copy(alpha = .13f))
                        .border(1.dp, P2Cyan.copy(alpha = .38f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("⌁", color = if (enabled) P2Cyan else P2Muted, fontSize = 22.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Capture network packets", color = P2Text, fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (enabled) "Monitor, inspect and analyze network requests and responses in real time."
                        else "Turn on capture to inspect live browser network activity.",
                        color = P2Muted, fontFamily = TahoBody, fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P2SurfaceHi)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Packet Capture", color = P2Text, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(if (enabled) "On" else "Off", color = if (enabled) P2Green else P2Muted, fontFamily = TahoBody, fontSize = 11.sp)
                }
                Phase2Switch(enabled, onEnabledChange)
            }
        }

        if (enabled) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P2Surface)
                    .border(1.dp, P2Hairline, RoundedCornerShape(18.dp)).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Captured Packets", color = P2Text, fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("${requests.size} total · $relevantCount relevant", color = P2Muted, fontFamily = TahoBody, fontSize = 11.sp)
                    }
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (captureState == CaptureState.ERROR) P2Red else P2Green))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when (captureState) {
                            CaptureState.CAPTURING -> "Live"
                            CaptureState.OBSERVING -> "Active"
                            CaptureState.PAUSED -> "Paused"
                            CaptureState.LIMITED -> "Limited"
                            CaptureState.ERROR -> "Error"
                            CaptureState.OFF -> "Off"
                        },
                        color = if (captureState == CaptureState.ERROR) P2Red else P2Green,
                        fontFamily = TahoBody, fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Phase2ActivityGraph(requests.size)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Phase2StatCard("Requests", requests.size.toString(), Modifier.weight(1f))
                    Phase2StatCard("Responses", requests.count { it.status != null }.toString(), Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Phase2ToggleRow("Capture in background", "Keep capture active while Taho is backgrounded", captureInBackground, onBackgroundChange)
        Phase2ToggleRow("Show floating character", "Display live capture count over the page", floatingEnabled, onFloatingChange)
        Spacer(Modifier.weight(1f))
        Phase2PrimaryButton(
            if (requests.isEmpty()) "No Captured Packets Yet" else "View Captured Packets",
            enabled = requests.isNotEmpty(),
            onClick = onViewCaptured,
        )
    }
}

@Composable
internal fun Phase2CaptureQuickPanel(
    requests: List<M4CaptureRequestUiState>,
    onOpenFull: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Captured Packets", color = TahoText, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("${requests.size} packets", color = TahoMuted, fontFamily = TahoBody, fontSize = 11.sp)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(P2Green))
            Spacer(Modifier.width(5.dp))
            Text("Live", color = P2Green, fontFamily = TahoBody, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Phase2IconButton("×", "Close", onDismiss)
        }
        Spacer(Modifier.height(8.dp))
        requests.take(4).forEach { Phase2CompactPacketRow(it) }
        if (requests.isEmpty()) {
            Text(
                "No captured packets yet. Browse or reload a page while capture is enabled.",
                color = TahoMuted, fontFamily = TahoBody, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase2SecondaryButton("Open Full View", Modifier.weight(1f), onClick = onOpenFull)
            Phase2DangerButton("Stop Capture", Modifier.weight(1f), onStop)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Phase2CapturedPacketsScreen(
    requests: List<M4CaptureRequestUiState>,
    currentHost: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    methodFilter: Phase2MethodFilter,
    statusFilter: Phase2StatusFilter,
    domainFilter: String,
    thirdPartyOnly: Boolean,
    failedOnly: Boolean,
    webSocketOnly: Boolean,
    onMethodFilterChange: (Phase2MethodFilter) -> Unit,
    onInspect: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenExport: (List<M4CaptureRequestUiState>) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
    onSendSingleToTaho: (String) -> Unit,
    onBack: () -> Unit,
) {
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val visible = remember(requests, searchQuery, methodFilter, statusFilter, domainFilter, thirdPartyOnly, failedOnly, webSocketOnly, currentHost) {
        requests.filter { r ->
            val method = r.method.uppercase()
            val host = runCatching { URI(r.url).host.orEmpty() }.getOrDefault("")
            val search = searchQuery.isBlank() || r.url.contains(searchQuery, true) ||
                r.method.contains(searchQuery, true) || r.status?.toString()?.contains(searchQuery) == true
            val methodOk = when (methodFilter) {
                Phase2MethodFilter.ALL -> true
                Phase2MethodFilter.GET -> method == "GET"
                Phase2MethodFilter.POST -> method == "POST"
                Phase2MethodFilter.OTHER -> method !in setOf("GET", "POST")
            }
            val status = r.status
            val statusOk = when (statusFilter) {
                Phase2StatusFilter.ALL -> true
                Phase2StatusFilter.SUCCESS -> status != null && status in 200..299
                Phase2StatusFilter.REDIRECT -> status != null && status in 300..399
                Phase2StatusFilter.CLIENT_ERROR -> status != null && status in 400..499
                Phase2StatusFilter.SERVER_ERROR -> status != null && status >= 500
                Phase2StatusFilter.FAILED -> status == null || status >= 400
            }
            val domainOk = domainFilter.isBlank() || host.contains(domainFilter, true)
            val thirdPartyOk = !thirdPartyOnly || (currentHost != null && host.isNotBlank() && !sameSiteHost(host, currentHost))
            val failedOk = !failedOnly || status == null || status >= 400
            val wsOk = !webSocketOnly || r.category.contains("websocket", true) || r.relevanceCategory.contains("websocket", true)
            search && methodOk && statusOk && domainOk && thirdPartyOk && failedOk && wsOk
        }
    }

    Phase2Screen(
        if (selectionMode) "${selectedIds.size} selected" else "Captured Packets",
        onBack = { if (selectionMode) selectedIds = emptySet() else onBack() },
        trailing = { if (!selectionMode) Phase2IconButton("⇧", "Export packets") { onOpenExport(visible) } },
    ) {
        Phase2SearchField(searchQuery, onSearchQueryChange, onOpenFilters)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Phase2MethodFilter.entries.forEach { option ->
                Phase2FilterChip(option.name.lowercase().replaceFirstChar { it.uppercase() }, option == methodFilter) {
                    onMethodFilterChange(option)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (visible.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No matching packets", color = P2Text, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(5.dp))
                Text("Change filters or continue browsing.", color = P2Muted, fontFamily = TahoBody, fontSize = 12.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(visible, key = { it.id }) { request ->
                    Phase2PacketRow(
                        request, selectionMode, request.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (request.id in selectedIds) selectedIds - request.id else selectedIds + request.id
                            } else onInspect(request.id)
                        },
                        onLongClick = { selectedIds = selectedIds + request.id },
                    )
                }
            }
        }

        if (selectionMode) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Phase2SecondaryButton("Send to Taho", Modifier.weight(1.2f), enabled = selectedIds.size == 1) {
                    onSendSingleToTaho(selectedIds.first())
                }
                Phase2SecondaryButton("Export", Modifier.weight(.9f)) {
                    onOpenExport(requests.filter { it.id in selectedIds })
                }
                Phase2DangerButton("Delete", Modifier.weight(.9f)) {
                    onDeleteSelected(selectedIds)
                    selectedIds = emptySet()
                }
            }
            if (selectedIds.size > 1) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "Taho's current transfer contract imports one request at a time. Multi-selection can be exported or deleted.",
                    color = P2Muted, fontFamily = TahoBody, fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
internal fun Phase2PacketDetailsScreen(
    request: M4CaptureRequestUiState,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSendToTaho: () -> Unit,
) {
    var tab by rememberSaveable(request.id) { mutableStateOf("Overview") }
    val host = runCatching { URI(request.url).host.orEmpty() }.getOrDefault("")
    val path = runCatching { URI(request.url).rawPath.ifBlank { "/" } }.getOrDefault("/")
    Phase2Screen("Packet Details", onBack, trailing = { Phase2IconButton("↗", "Share request", onShare) }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P2Surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Phase2MethodBadge(request.method)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(host + path, color = P2Text, fontFamily = TahoBody, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(request.durationMs?.let { "$it ms" } ?: "request details", color = P2Muted, fontFamily = TahoBody, fontSize = 10.sp)
            }
            request.status?.let {
                Text(it.toString() + if (it in 200..299) " OK" else "", color = if (it in 200..399) P2Green else P2Orange,
                    fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Overview", "Request", "Response", "Headers").forEach { option ->
                Phase2FilterChip(option, option == tab) { tab = option }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (tab) {
                "Overview" -> {
                    Phase2KeyValue("URL", request.url)
                    Phase2KeyValue("Method", request.method)
                    Phase2KeyValue("Status Code", request.status?.toString() ?: "Unavailable")
                    Phase2KeyValue("Content Type", request.headers.firstOrNull { it.name.equals("content-type", true) }?.displayValue ?: "Unavailable")
                    Phase2KeyValue("Size", request.requestBodyCapturedBytes?.let(::phase2Bytes) ?: "Unavailable")
                    Phase2KeyValue("Time", request.durationMs?.let { "$it ms" } ?: "Unavailable")
                    Phase2KeyValue("Category", request.relevanceCategory)
                }
                "Request" -> {
                    Phase2CodeBlock(buildString {
                        append(request.method).append(" ").append(path).append("\n")
                        request.headers.forEach { append(it.name).append(": ").append(it.displayValue).append("\n") }
                        request.safeBodyPreview?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                    })
                    if (request.requestBodyCompleteness !in setOf(M4CompletenessUi.COMPLETE, M4CompletenessUi.NOT_APPLICABLE)) {
                        Spacer(Modifier.height(8.dp))
                        Phase2InfoNote("Request body: " + request.requestBodyCompleteness.name.lowercase().replace('_', ' '))
                    }
                }
                "Response" -> {
                    Phase2KeyValue("Status", request.status?.toString() ?: "Unavailable")
                    Phase2KeyValue("Response headers", request.responseHeadersCompleteness.name.lowercase().replace('_', ' '))
                    Phase2KeyValue("Response body", request.responseBodyCompleteness.name.lowercase().replace('_', ' '))
                    if (request.responseBodyCompleteness == M4CompletenessUi.UNAVAILABLE) {
                        Phase2InfoNote("Response body data is unavailable on the current GeckoView capture path; Taho does not fabricate it.")
                    }
                }
                else -> {
                    if (request.headers.isEmpty()) Phase2InfoNote("No request headers were captured.")
                    else request.headers.forEach { Phase2KeyValue(it.name, it.displayValue) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase2SecondaryButton("Copy", Modifier.weight(.65f), onClick = onCopy)
            Phase2SecondaryButton("Share", Modifier.weight(.65f), onClick = onShare)
            Phase2PrimaryButton("Send to Taho", enabled = request.transferBlockedReason == null, modifier = Modifier.weight(1.35f), onClick = onSendToTaho)
        }
    }
}

@Composable
internal fun Phase2CaptureFiltersScreen(
    methodFilter: Phase2MethodFilter,
    statusFilter: Phase2StatusFilter,
    domainFilter: String,
    thirdPartyOnly: Boolean,
    failedOnly: Boolean,
    webSocketOnly: Boolean,
    onMethodChange: (Phase2MethodFilter) -> Unit,
    onStatusChange: (Phase2StatusFilter) -> Unit,
    onDomainChange: (String) -> Unit,
    onThirdPartyChange: (Boolean) -> Unit,
    onFailedChange: (Boolean) -> Unit,
    onWebSocketChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
) {
    Phase2Screen("Filters", onBack, trailing = {
        Text("Reset", color = P2Blue, fontFamily = TahoBody, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onReset).padding(8.dp))
    }) {
        Phase2SectionTitle("Request Type")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Phase2MethodFilter.entries.forEach { Phase2FilterChip(it.name, it == methodFilter) { onMethodChange(it) } }
        }
        Spacer(Modifier.height(16.dp))
        Phase2SectionTitle("Status Code")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Phase2StatusFilter.entries.forEach { Phase2FilterChip(phase2StatusLabel(it), it == statusFilter) { onStatusChange(it) } }
        }
        Spacer(Modifier.height(16.dp))
        Phase2SectionTitle("Domain")
        BasicTextField(
            value = domainFilter, onValueChange = onDomainChange,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P2Surface)
                .border(1.dp, P2Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
            singleLine = true,
            textStyle = TextStyle(color = P2Text, fontFamily = TahoBody, fontSize = 13.sp),
            cursorBrush = SolidColor(P2Blue),
            decorationBox = { field ->
                Box {
                    if (domainFilter.isBlank()) Text("e.g. google.com", color = P2Muted, fontFamily = TahoBody, fontSize = 13.sp)
                    field()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Phase2ToggleRow("Show only third-party requests", null, thirdPartyOnly, onThirdPartyChange)
        Phase2ToggleRow("Show only failed requests", null, failedOnly, onFailedChange)
        Phase2ToggleRow("Show WebSocket traffic", null, webSocketOnly, onWebSocketChange)
        Spacer(Modifier.weight(1f))
        Phase2PrimaryButton("Apply Filters", onClick = onApply)
    }
}

@Composable
internal fun Phase2ExportScreen(
    requests: List<M4CaptureRequestUiState>,
    onExport: (String) -> Unit,
    onBack: () -> Unit,
) {
    var format by rememberSaveable { mutableStateOf(Phase2ExportFormat.JSON) }
    Phase2Screen("Export", onBack) {
        Phase2SectionTitle("Select Format")
        Phase2ExportFormat.entries.forEach { option ->
            val enabled = option != Phase2ExportFormat.PCAP
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (format == option) P2Blue.copy(alpha = .13f) else P2Surface)
                    .border(1.dp, if (format == option) P2Blue else P2Hairline, RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { format = option }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(when (option) {
                    Phase2ExportFormat.JSON -> "{}"
                    Phase2ExportFormat.HAR -> "▣"
                    Phase2ExportFormat.CSV -> "≡"
                    Phase2ExportFormat.PCAP -> "◇"
                }, color = if (enabled) P2Text else P2Muted, fontFamily = TahoMono, modifier = Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(option.name, color = if (enabled) P2Text else P2Muted, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(when (option) {
                        Phase2ExportFormat.JSON -> "Masked request summaries"
                        Phase2ExportFormat.HAR -> "HTTP Archive-compatible summary"
                        Phase2ExportFormat.CSV -> "Spreadsheet-friendly summary"
                        Phase2ExportFormat.PCAP -> "Unavailable: GeckoView does not expose raw packets"
                    }, color = P2Muted, fontFamily = TahoBody, fontSize = 10.sp)
                }
                if (format == option) Text("✓", color = P2Blue, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${requests.size} packet${if (requests.size == 1) "" else "s"} selected for export", color = P2Muted, fontFamily = TahoBody, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Phase2PrimaryButton("Export ${requests.size} Packets", enabled = requests.isNotEmpty() && format != Phase2ExportFormat.PCAP) {
            onExport(phase2ExportText(requests, format))
        }
    }
}

@Composable
internal fun Phase2CaptureSettingsScreen(
    enabled: Boolean,
    captureInBackground: Boolean,
    floatingEnabled: Boolean,
    animationStyle: String,
    defaultPosition: String,
    capabilityNote: String?,
    onEnabledChange: (Boolean) -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    onFloatingChange: (Boolean) -> Unit,
    onAnimationStyleChange: (String) -> Unit,
    onDefaultPositionChange: (String) -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    Phase2Screen("Capture Settings", onBack) {
        Phase2ToggleRow("Enable packet capture", null, enabled, onEnabledChange)
        Phase2ToggleRow("Capture in background", "Capture while Taho remains alive in the background", captureInBackground, onBackgroundChange)
        Phase2ValueRow("Capture type", "All traffic")
        Phase2CapabilityRow("Include request body", "Captured when GeckoView provides reproducible request bytes")
        Phase2CapabilityRow("Include response body", "Unavailable on current GeckoView capture path")
        Phase2CapabilityRow("Capture WebSocket frames", "Displayed when the capture engine reports frames")
        Phase2ToggleRow("Show floating character", null, floatingEnabled, onFloatingChange)
        Phase2ValueRow("Animation style", animationStyle) { onAnimationStyleChange(if (animationStyle == "Subtle") "Minimal" else "Subtle") }
        Phase2ValueRow("Default position", defaultPosition) { onDefaultPositionChange(if (defaultPosition == "Right side") "Left side" else "Right side") }
        Phase2ValueRow("Storage limit", "512 MiB soft limit")
        capabilityNote?.let {
            Spacer(Modifier.height(10.dp))
            Phase2InfoNote(it)
        }
        Spacer(Modifier.height(12.dp))
        Phase2SecondaryButton("Export packets", Modifier.fillMaxWidth(), onClick = onExport)
        Spacer(Modifier.height(8.dp))
        Phase2DangerButton("Clear captured data", Modifier.fillMaxWidth(), onClear)
    }
}

@Composable
internal fun Phase2PrivacyToolsMenu(
    captureEnabled: Boolean,
    httpsOnlyEnabled: Boolean,
    trackingProtectionEnabled: Boolean,
    onPacketCapture: () -> Unit,
    onClearBrowsingData: () -> Unit,
    onSitePermissions: () -> Unit,
    onTrackerProtection: () -> Unit,
    onHttpsOnlyChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Phase2IconButton("←", "Back", onBack)
            Spacer(Modifier.width(4.dp))
            Text("Privacy tools", color = TahoText, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TahoHairlineStrong))
        Phase2PrivacyMenuRow("⌁", "Packet capture", if (captureEnabled) "On" else "Off", onPacketCapture)
        Phase2PrivacyMenuRow("⌫", "Clear browsing data", null, onClearBrowsingData)
        Phase2PrivacyMenuRow("◇", "Site permissions", null, onSitePermissions)
        Phase2PrivacyMenuRow("◎", "Tracker protection", if (trackingProtectionEnabled) "On" else "Off", onTrackerProtection)
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▣", color = TahoText.copy(alpha = .8f), modifier = Modifier.width(30.dp), fontSize = 16.sp)
            Text("HTTPS only mode", color = TahoText, fontFamily = TahoBody, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = httpsOnlyEnabled, onCheckedChange = onHttpsOnlyChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = P2Blue,
                    uncheckedThumbColor = TahoMuted, uncheckedTrackColor = TahoSurfaceControl,
                ),
            )
        }
    }
}

@Composable
private fun Phase2PrivacyMenuRow(glyph: String, label: String, trailing: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(glyph, color = TahoText.copy(alpha = .8f), modifier = Modifier.width(30.dp), fontSize = 16.sp)
        Text(label, color = TahoText, fontFamily = TahoBody, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = TahoMuted, fontFamily = TahoBody, fontSize = 12.sp) }
    }
}

@Composable
private fun Phase2Screen(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(P2Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            Phase2IconButton("←", "Back", onBack)
            Spacer(Modifier.width(5.dp))
            Text(title, color = P2Text, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Phase2IconButton(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick).semantics {
            role = Role.Button
            contentDescription = description
        },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = P2Text, fontSize = 18.sp) }
}

@Composable
private fun Phase2Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = P2Blue,
            uncheckedThumbColor = P2Muted, uncheckedTrackColor = P2SurfaceHi, uncheckedBorderColor = P2Hairline,
        ),
    )
}

@Composable
private fun Phase2ToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P2Text, fontFamily = TahoBody, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = P2Muted, fontFamily = TahoBody, fontSize = 10.sp)
            }
        }
        Phase2Switch(checked, onCheckedChange)
    }
}

@Composable
private fun Phase2ValueRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(enabled = onClick != null) { onClick?.invoke() }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = P2Text, fontFamily = TahoBody, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = P2Muted, fontFamily = TahoBody, fontSize = 11.sp)
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Text("›", color = P2Muted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun Phase2CapabilityRow(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(title, color = P2Text, fontFamily = TahoBody, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        Text(detail, color = P2Muted, fontFamily = TahoBody, fontSize = 10.sp)
    }
}

@Composable
private fun Phase2ActivityGraph(count: Int) {
    Canvas(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)).background(P2SurfaceHi)) {
        var previous = Offset(0f, size.height * .65f)
        for (i in 1 until 18) {
            val x = size.width * i / 17f
            val seed = ((count + i * 17) % 13).toFloat() / 13f
            val current = Offset(x, size.height * (.25f + .55f * seed))
            drawLine(P2Cyan, previous, current, strokeWidth = 1.8.dp.toPx())
            previous = current
        }
    }
}

@Composable
private fun Phase2StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(P2SurfaceHi).padding(12.dp)) {
        Text(label, color = P2Muted, fontFamily = TahoBody, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = P2Text, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
    }
}

@Composable
private fun Phase2SearchField(query: String, onQueryChange: (String) -> Unit, onFilters: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(P2Surface)
                .border(1.dp, P2Hairline, RoundedCornerShape(22.dp)).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕", color = P2Muted, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isBlank()) Text("Search requests…", color = P2Muted, fontFamily = TahoBody, fontSize = 12.sp)
                BasicTextField(
                    value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = P2Text, fontFamily = TahoBody, fontSize = 12.sp), cursorBrush = SolidColor(P2Blue),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Phase2IconButton("▽", "Filters", onFilters)
    }
}

@Composable
private fun Phase2FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) P2Blue else P2Surface)
            .border(1.dp, if (selected) P2Blue else P2Hairline, RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = P2Text, fontFamily = TahoBody, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Phase2PacketRow(
    request: M4CaptureRequestUiState,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val host = runCatching { URI(request.url).host ?: request.url }.getOrDefault(request.url)
    val path = runCatching { URI(request.url).rawPath.ifBlank { "/" } }.getOrDefault("/")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) P2Blue.copy(alpha = .12f) else P2Surface)
            .border(1.dp, if (selected) P2Blue.copy(alpha = .5f) else P2Hairline, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (selected) P2Blue else Color.Transparent)
                    .border(1.dp, if (selected) P2Blue else P2Muted, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { if (selected) Text("✓", color = Color.White, fontSize = 12.sp) }
            Spacer(Modifier.width(8.dp))
        }
        Phase2MethodBadge(request.method)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(host + path, color = P2Text, fontFamily = TahoBody, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(request.relevanceCategory, color = P2Muted, fontFamily = TahoBody, fontSize = 9.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            request.status?.let {
                Text(it.toString(), color = when {
                    it in 200..299 -> P2Green
                    it in 300..399 -> P2Orange
                    else -> P2Red
                }, fontFamily = TahoMono, fontSize = 10.sp)
            }
            Text(request.requestBodyCapturedBytes?.let(::phase2Bytes) ?: "—", color = P2Muted, fontFamily = TahoMono, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun Phase2CompactPacketRow(request: M4CaptureRequestUiState) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Phase2MethodBadge(request.method)
        Spacer(Modifier.width(8.dp))
        Text(
            request.url.removePrefix("https://").removePrefix("http://"), color = TahoText, fontFamily = TahoBody, fontSize = 11.sp,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        request.status?.let { Text(it.toString(), color = if (it < 400) P2Green else P2Orange, fontFamily = TahoMono, fontSize = 9.sp) }
    }
}

@Composable
private fun Phase2MethodBadge(method: String) {
    val upper = method.uppercase()
    val badge = when (upper) { "GET" -> P2Blue; "POST" -> P2Orange; else -> Color(0xFF52606D) }
    Box(Modifier.height(28.dp).width(46.dp).clip(RoundedCornerShape(7.dp)).background(badge), contentAlignment = Alignment.Center) {
        Text(upper.take(6), color = Color.White, fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Phase2KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = P2Muted, fontFamily = TahoBody, fontSize = 11.sp, modifier = Modifier.width(112.dp))
        Text(value, color = P2Text, fontFamily = TahoBody, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Phase2CodeBlock(text: String) {
    Text(
        text, color = Color(0xFF9BE7C4), fontFamily = TahoMono, fontSize = 10.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF07131A))
            .border(1.dp, P2Hairline, RoundedCornerShape(12.dp)).padding(12.dp),
    )
}

@Composable
private fun Phase2InfoNote(text: String) {
    Text(text, color = P2Muted, fontFamily = TahoBody, fontSize = 10.5.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(P2Surface).padding(10.dp))
}

@Composable
private fun Phase2SectionTitle(text: String) {
    Text(text, color = P2Text, fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Phase2PrimaryButton(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) P2Blue else P2SurfaceHi)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else P2Muted, fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun Phase2SecondaryButton(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(11.dp)).background(P2SurfaceHi)
            .border(1.dp, P2Hairline, RoundedCornerShape(11.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) P2Text else P2Muted, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable
private fun Phase2DangerButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(11.dp)).background(P2Red.copy(alpha = .92f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

private fun phase2Bytes(value: Long): String = when {
    value >= 1024L * 1024L -> String.format("%.1f MB", value / (1024.0 * 1024.0))
    value >= 1024L -> String.format("%.1f KB", value / 1024.0)
    else -> "$value B"
}

private fun phase2StatusLabel(filter: Phase2StatusFilter): String = when (filter) {
    Phase2StatusFilter.ALL -> "All"
    Phase2StatusFilter.SUCCESS -> "2xx"
    Phase2StatusFilter.REDIRECT -> "3xx"
    Phase2StatusFilter.CLIENT_ERROR -> "4xx"
    Phase2StatusFilter.SERVER_ERROR -> "5xx"
    Phase2StatusFilter.FAILED -> "Failed"
}

private fun sameSiteHost(a: String, b: String): Boolean {
    val aa = a.lowercase().removePrefix("www.")
    val bb = b.lowercase().removePrefix("www.")
    return aa == bb || aa.endsWith(".$bb") || bb.endsWith(".$aa")
}

private fun phase2ExportText(requests: List<M4CaptureRequestUiState>, format: Phase2ExportFormat): String =
    when (format) {
        Phase2ExportFormat.CSV -> buildString {
            append("method,url,status,duration_ms,category\n")
            requests.forEach { r ->
                append(csv(r.method)).append(',').append(csv(r.url)).append(',')
                append(r.status ?: "").append(',').append(r.durationMs ?: "").append(',')
                append(csv(r.relevanceCategory)).append('\n')
            }
        }
        Phase2ExportFormat.HAR -> buildString {
            append("{\n  \"log\": {\n    \"version\": \"1.2\",\n    \"creator\": {\"name\": \"Taho Browser\", \"version\": \"1\"},\n    \"entries\": [\n")
            requests.forEachIndexed { index, r ->
                if (index > 0) append(",\n")
                append("      {\"request\":{\"method\":").append(json(r.method))
                append(",\"url\":").append(json(r.url)).append("},")
                append("\"response\":{\"status\":").append(r.status ?: 0).append("},")
                append("\"time\":").append(r.durationMs ?: 0).append("}")
            }
            append("\n    ]\n  }\n}")
        }
        Phase2ExportFormat.JSON -> buildString {
            append("[\n")
            requests.forEachIndexed { index, r ->
                if (index > 0) append(",\n")
                append("  {\"id\":").append(json(r.id)).append(",\"method\":").append(json(r.method))
                append(",\"url\":").append(json(r.url)).append(",\"status\":").append(r.status ?: "null")
                append(",\"durationMs\":").append(r.durationMs ?: "null").append(",\"category\":").append(json(r.relevanceCategory))
                append(",\"headers\":[")
                r.headers.forEachIndexed { hIndex, h ->
                    if (hIndex > 0) append(',')
                    append("{\"name\":").append(json(h.name)).append(",\"value\":").append(json(h.displayValue)).append('}')
                }
                append("]}")
            }
            append("\n]")
        }
        Phase2ExportFormat.PCAP -> "PCAP export unavailable: the current GeckoView capture path does not expose raw network packets."
    }

private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
private fun json(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
