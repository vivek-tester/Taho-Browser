package app.taho.browser.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

private val D3Bg = Color(0xFF06101A)
private val D3Surface = Color(0xFF0D1924)
private val D3SurfaceHi = Color(0xFF132231)
private val D3Border = Color.White.copy(alpha = .10f)
private val D3Text = Color(0xFFF5F7FA)
private val D3Muted = Color(0xFF98A6B3)
private val D3Blue = Color(0xFF1677FF)
private val D3Green = Color(0xFF42D392)
private val D3Yellow = Color(0xFFFFC857)
private val D3Red = Color(0xFFFF5C67)
private val D3Code = Color(0xFF8DE1C1)

internal enum class Phase3DevToolsMode(val label: String, val subtitle: String) {
    BOTTOM("Bottom panel", "Default"),
    SIDE("Side by side", "Landscape"),
    FLOATING("Floating window", "Draggable"),
    FULLSCREEN("Full screen", "Maximum space"),
}

internal enum class Phase3DevToolsPanel(val label: String, val command: String?) {
    ELEMENTS("Elements", "ELEMENTS"),
    CONSOLE("Console", "CONSOLE_INFO"),
    NETWORK("Network", null),
    SOURCES("Sources", "SOURCES"),
    PERFORMANCE("Performance", "PERFORMANCE"),
    MEMORY("Memory", "MEMORY"),
    APPLICATION("Application", "APPLICATION"),
    SECURITY("Security", "SECURITY"),
    LIGHTHOUSE("Lighthouse", "LIGHTHOUSE"),
    RECORDER("Recorder", "RECORDER"),
    ISSUES("Issues", "ISSUES"),
    RENDERING("Rendering", "RENDERING"),
    SENSORS("Sensors", "SENSORS"),
    COVERAGE("Coverage", "COVERAGE"),
    CHANGES("Changes", "CHANGES"),
    ANIMATIONS("Animations", "ANIMATIONS"),
}

@Composable
internal fun Phase3DevToolsHub(
    enabled: Boolean,
    connected: Boolean,
    mode: Phase3DevToolsMode,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (Phase3DevToolsMode) -> Unit,
    onOpenPanel: (Phase3DevToolsPanel) -> Unit,
    onOpenPacketCapture: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(D3Bg).padding(horizontal = 14.dp),
    ) {
        Phase3Header(
            title = "Developer tools",
            subtitle = if (connected) "Live inspector attached" else if (enabled) "Reload once to attach inspector" else "Disabled",
            onClose = onDismiss,
        )
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(D3Surface)
                .border(1.dp, if (enabled) D3Blue.copy(alpha = .5f) else D3Border, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Enable Developer tools", color = D3Text, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("Controlled by the Packet Capture master switch", color = D3Muted, fontFamily = TahoBody, fontSize = 10.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = D3Blue,
                    uncheckedThumbColor = D3Muted,
                    uncheckedTrackColor = D3SurfaceHi,
                ),
            )
        }

        Spacer(Modifier.height(16.dp))
        Phase3SectionLabel("Open in")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase3DevToolsMode.entries.take(2).forEach { option ->
                Phase3ModeCard(option, option == mode, enabled, Modifier.weight(1f)) { onModeChange(option) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase3DevToolsMode.entries.drop(2).forEach { option ->
                Phase3ModeCard(option, option == mode, enabled, Modifier.weight(1f)) { onModeChange(option) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Phase3SectionLabel("Quick access")
        listOf(
            Phase3DevToolsPanel.ELEMENTS,
            Phase3DevToolsPanel.CONSOLE,
            Phase3DevToolsPanel.NETWORK,
            Phase3DevToolsPanel.PERFORMANCE,
            Phase3DevToolsPanel.APPLICATION,
        ).forEach { panel ->
            Phase3HubRow(
                glyph = phase3PanelGlyph(panel),
                title = if (panel == Phase3DevToolsPanel.NETWORK) "Network monitor"
                    else if (panel == Phase3DevToolsPanel.ELEMENTS) "Elements inspector"
                    else panel.label,
                subtitle = phase3PanelDescription(panel),
                enabled = enabled,
            ) { onOpenPanel(panel) }
        }
        Phase3HubRow("⌁", "Packet capture", "Capture and inspect browser requests", true, onOpenPacketCapture)

        Spacer(Modifier.height(10.dp))
        Phase3SectionLabel("More tools")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Phase3DevToolsPanel.SOURCES,
                Phase3DevToolsPanel.MEMORY,
                Phase3DevToolsPanel.SECURITY,
                Phase3DevToolsPanel.LIGHTHOUSE,
                Phase3DevToolsPanel.RECORDER,
                Phase3DevToolsPanel.ISSUES,
                Phase3DevToolsPanel.SENSORS,
                Phase3DevToolsPanel.COVERAGE,
                Phase3DevToolsPanel.CHANGES,
                Phase3DevToolsPanel.ANIMATIONS,
            ).forEach { panel ->
                Column(
                    modifier = Modifier.widthIn(min = 104.dp).height(62.dp)
                        .clip(RoundedCornerShape(12.dp)).background(D3Surface)
                        .border(1.dp, D3Border, RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled) { onOpenPanel(panel) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(phase3PanelGlyph(panel), color = if (enabled) D3Blue else D3Muted, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(panel.label, color = if (enabled) D3Text else D3Muted, fontFamily = TahoBody, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Phase3ModeCard(
    mode: Phase3DevToolsMode,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.height(70.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) D3Blue.copy(alpha = .14f) else D3Surface)
            .border(1.dp, if (selected) D3Blue else D3Border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(10.dp),
    ) {
        Text(
            when (mode) {
                Phase3DevToolsMode.BOTTOM -> "▤"
                Phase3DevToolsMode.SIDE -> "▥"
                Phase3DevToolsMode.FLOATING -> "▣"
                Phase3DevToolsMode.FULLSCREEN -> "□"
            },
            color = if (enabled) D3Text else D3Muted,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(mode.label, color = if (enabled) D3Text else D3Muted, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        Text(mode.subtitle, color = D3Muted, fontFamily = TahoBody, fontSize = 8.5.sp)
    }
}

@Composable
private fun Phase3HubRow(
    glyph: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(D3SurfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = if (enabled) D3Text else D3Muted, fontSize = 16.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) D3Text else D3Muted, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(subtitle, color = D3Muted, fontFamily = TahoBody, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = D3Muted, fontSize = 18.sp)
    }
}

@Composable
internal fun Phase3DevToolsPanelSurface(
    enabled: Boolean,
    panel: Phase3DevToolsPanel,
    mode: Phase3DevToolsMode,
    captureRequests: List<M4CaptureRequestUiState>,
    state: DevToolsUiState,
    onPanelChange: (Phase3DevToolsPanel) -> Unit,
    onModeChange: (Phase3DevToolsMode) -> Unit,
    onRequest: (String, String?) -> Unit,
    onReloadPage: () -> Unit,
    onInspectRequest: (String) -> Unit,
    onBackToHub: () -> Unit,
    onClose: () -> Unit,
    onFloatingDrag: (Float, Float) -> Unit = { _, _ -> },
) {
    LaunchedEffect(panel, enabled, state.connected) {
        if (enabled && state.connected) panel.command?.let { onRequest(it, null) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(D3Bg)
            .border(1.dp, D3Border, RoundedCornerShape(if (mode == Phase3DevToolsMode.FULLSCREEN) 0.dp else 14.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).background(D3Surface)
                .then(
                    if (mode == Phase3DevToolsMode.FLOATING) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onFloatingDrag(dragAmount.x, dragAmount.y)
                            }
                        }
                    } else Modifier,
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (mode == Phase3DevToolsMode.FLOATING) "⋮⋮" else "←",
                color = D3Muted,
                fontSize = 17.sp,
                modifier = Modifier.size(36.dp)
                    .clickable(enabled = mode != Phase3DevToolsMode.FLOATING, onClick = onBackToHub)
                    .padding(8.dp),
                textAlign = TextAlign.Center,
            )
            Text("DevTools", color = D3Text, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (state.connected) "LIVE" else if (enabled) "WAIT" else "OFF",
                color = if (state.connected) D3Green else D3Muted,
                fontFamily = TahoMono,
                fontSize = 9.sp,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "↻",
                color = if (enabled && panel.command != null) D3Text else D3Muted,
                fontSize = 16.sp,
                modifier = Modifier.size(36.dp).clickable(enabled = enabled && panel.command != null) {
                    panel.command?.let { onRequest(it, null) }
                }.padding(8.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                "×",
                color = D3Text,
                fontSize = 19.sp,
                modifier = Modifier.size(36.dp).clickable(onClick = onClose).padding(7.dp),
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(D3Surface).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Phase3DevToolsPanel.entries.forEach { item ->
                val selected = item == panel
                Column(
                    modifier = Modifier.clickable(enabled = enabled) { onPanelChange(item) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(item.label, color = if (selected) D3Text else D3Muted, fontFamily = TahoBody, fontSize = 10.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(28.dp).height(2.dp).background(if (selected) D3Blue else Color.Transparent))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(D3SurfaceHi).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                phase3PanelDescription(panel),
                color = D3Muted,
                fontFamily = TahoBody,
                fontSize = 9.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                mode.label,
                color = D3Blue,
                fontFamily = TahoBody,
                fontSize = 9.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    val entries = Phase3DevToolsMode.entries
                    onModeChange(entries[(entries.indexOf(mode) + 1) % entries.size])
                }.padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                !enabled -> Phase3Centered("Developer tools are disabled", "Enable Packet Capture to use the live inspector.")
                panel != Phase3DevToolsPanel.NETWORK && !state.connected -> Phase3AttachPrompt(onReloadPage)
                else -> Phase3PanelContent(panel, state, captureRequests, onRequest, onInspectRequest)
            }
        }
    }
}

@Composable
private fun Phase3PanelContent(
    panel: Phase3DevToolsPanel,
    state: DevToolsUiState,
    requests: List<M4CaptureRequestUiState>,
    onRequest: (String, String?) -> Unit,
    onInspectRequest: (String) -> Unit,
) {
    when (panel) {
        Phase3DevToolsPanel.ELEMENTS -> Phase3ElementsPanel(state)
        Phase3DevToolsPanel.CONSOLE -> Phase3ConsolePanel(state, onRequest)
        Phase3DevToolsPanel.NETWORK -> Phase3NetworkPanel(requests, onInspectRequest)
        Phase3DevToolsPanel.SOURCES -> Phase3SourcesPanel(state)
        Phase3DevToolsPanel.PERFORMANCE -> Phase3PerformancePanel(state)
        Phase3DevToolsPanel.MEMORY -> Phase3MemoryPanel(state)
        Phase3DevToolsPanel.APPLICATION -> Phase3ApplicationPanel(state)
        Phase3DevToolsPanel.SECURITY -> Phase3SecurityPanel(state)
        Phase3DevToolsPanel.LIGHTHOUSE -> Phase3AuditPanel(state)
        Phase3DevToolsPanel.RECORDER -> Phase3RecorderPanel(state, onRequest)
        Phase3DevToolsPanel.ISSUES -> Phase3IssuesPanel(state)
        Phase3DevToolsPanel.RENDERING -> Phase3StructuredPanel("Rendering", state)
        Phase3DevToolsPanel.SENSORS -> Phase3StructuredPanel("Sensors", state)
        Phase3DevToolsPanel.COVERAGE -> Phase3CoveragePanel(state)
        Phase3DevToolsPanel.CHANGES -> Phase3ChangesPanel(state, onRequest)
        Phase3DevToolsPanel.ANIMATIONS -> Phase3StructuredPanel("Animations", state)
    }
}

@Composable
private fun Phase3ElementsPanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    val html = obj?.optString("html").orEmpty()
    var lowerTab by rememberSaveable { mutableStateOf("Styles") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(D3SurfaceHi).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("DOM", color = D3Muted, fontFamily = TahoBody, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(
                listOfNotNull(
                    obj?.optInt("nodeCount")?.takeIf { it > 0 }?.let { it.toString() + " nodes" },
                    obj?.optInt("linkCount")?.let { it.toString() + " links" },
                    obj?.optInt("imageCount")?.let { it.toString() + " images" },
                ).joinToString(" · "),
                color = D3Muted,
                fontFamily = TahoMono,
                fontSize = 8.5.sp,
            )
        }
        Text(
            when {
                state.loading -> "Reading live DOM…"
                state.error != null -> state.error
                html.isBlank() -> "No DOM snapshot returned."
                else -> phase3PrettyHtml(html)
            },
            color = if (state.error != null) D3Red else D3Code,
            fontFamily = TahoMono,
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(10.dp),
        )
        Row(Modifier.fillMaxWidth().background(D3Surface), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Styles", "Computed", "Layout", "Event listeners").forEach { tab ->
                Text(
                    tab,
                    color = if (tab == lowerTab) D3Text else D3Muted,
                    fontFamily = TahoBody,
                    fontSize = 9.sp,
                    modifier = Modifier.clickable { lowerTab = tab }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            when (lowerTab) {
                "Styles" -> "Live DOM snapshot. CSS-rule mutation is not exposed by the current GeckoView inspection bridge."
                "Computed" -> "Computed-style inspection requires selecting a DOM node; node picking is not yet exposed by the bridge."
                "Layout" -> "Viewport and layout metrics are available in Rendering."
                else -> "DOM event-listener enumeration is not exposed by the current GeckoView embedding API."
            },
            color = D3Muted,
            fontFamily = TahoBody,
            fontSize = 9.5.sp,
            modifier = Modifier.fillMaxWidth().background(D3SurfaceHi).padding(10.dp),
        )
    }
}

@Composable
private fun Phase3ConsolePanel(state: DevToolsUiState, onRequest: (String, String?) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val obj = phase3Object(state)
    val messages = obj?.optJSONArray("messages")
    val isEvalResult = state.command == "CONSOLE_EVAL"

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp)).background(D3Surface)
                .border(1.dp, D3Border, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("›", color = D3Blue, fontFamily = TahoMono, fontSize = 14.sp)
            Spacer(Modifier.width(7.dp))
            BasicTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = D3Text, fontFamily = TahoMono, fontSize = 10.5.sp),
                cursorBrush = SolidColor(D3Blue),
            )
            Text(
                "Run",
                color = if (code.isBlank()) D3Muted else D3Text,
                fontFamily = TahoBody,
                fontSize = 10.sp,
                modifier = Modifier.clickable(enabled = code.isNotBlank()) {
                    onRequest("CONSOLE_EVAL", code)
                }.padding(7.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> Phase3Note("Running…")
            state.error != null -> Phase3Note(state.error)
            isEvalResult -> {
                val error = obj?.optString("error").orEmpty()
                val result = if (error.isNotBlank()) error else obj?.opt("result")?.toString().orEmpty()
                Text(
                    result.ifBlank { "undefined" },
                    color = if (error.isNotBlank()) D3Red else D3Text,
                    fontFamily = TahoMono,
                    fontSize = 9.5.sp,
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                )
            }
            messages != null -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 4.dp)) {
                    Text("Live messages", color = D3Muted, fontFamily = TahoBody, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    Text(messages.length().toString(), color = D3Muted, fontFamily = TahoMono, fontSize = 8.5.sp)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items((0 until messages.length()).toList()) { i ->
                        val msg = messages.optJSONObject(i)
                        val level = msg?.optString("level").orEmpty()
                        val args = msg?.optJSONArray("args")
                        val body = buildString {
                            repeat(args?.length() ?: 0) { index ->
                                if (index > 0) append(" ")
                                append(args?.opt(index)?.toString().orEmpty())
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                when (level) {
                                    "error" -> "●"
                                    "warn" -> "▲"
                                    "info" -> "●"
                                    else -> "›"
                                },
                                color = when (level) {
                                    "error" -> D3Red
                                    "warn" -> D3Yellow
                                    "info" -> D3Blue
                                    else -> D3Muted
                                },
                                modifier = Modifier.width(22.dp),
                                fontSize = 9.sp,
                            )
                            Text(
                                body.ifBlank { level },
                                color = D3Text,
                                fontFamily = TahoMono,
                                fontSize = 8.8.sp,
                                lineHeight = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            else -> {
                Text(
                    "Console is ready. Reload once after enabling DevTools to capture page messages.",
                    color = D3Muted,
                    fontFamily = TahoBody,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Phase3NetworkPanel(requests: List<M4CaptureRequestUiState>, onInspect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(requests, query) {
        requests.filter { query.isBlank() || it.url.contains(query, true) || it.method.contains(query, true) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(D3SurfaceHi).padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(D3Red))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = D3Text, fontFamily = TahoBody, fontSize = 10.sp),
                cursorBrush = SolidColor(D3Blue),
                decorationBox = { field ->
                    Box {
                        if (query.isBlank()) Text("Filter", color = D3Muted, fontFamily = TahoBody, fontSize = 10.sp)
                        field()
                    }
                },
            )
            Text(visible.size.toString() + " requests", color = D3Muted, fontFamily = TahoMono, fontSize = 8.5.sp)
        }
        Row(Modifier.fillMaxWidth().background(D3Surface).padding(horizontal = 8.dp, vertical = 6.dp)) {
            Phase3TableHeader("Name", Modifier.weight(1f))
            Phase3TableHeader("Status", Modifier.width(44.dp))
            Phase3TableHeader("Type", Modifier.width(54.dp))
            Phase3TableHeader("Size", Modifier.width(50.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.id }) { r ->
                val host = runCatching { URI(r.url).host.orEmpty() }.getOrDefault("")
                Row(
                    Modifier.fillMaxWidth().clickable { onInspect(r.id) }.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.url.substringAfter(host).ifBlank { host },
                            color = D3Text,
                            fontFamily = TahoBody,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(host, color = D3Muted, fontFamily = TahoMono, fontSize = 7.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        r.status?.toString() ?: "—",
                        color = when {
                            r.status == null -> D3Muted
                            r.status in 200..299 -> D3Green
                            r.status in 300..399 -> D3Yellow
                            else -> D3Red
                        },
                        fontFamily = TahoMono,
                        fontSize = 8.5.sp,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(r.category.take(9), color = D3Muted, fontFamily = TahoMono, fontSize = 7.5.sp, modifier = Modifier.width(54.dp), maxLines = 1)
                    Text(r.requestBodyCapturedBytes?.let(::phase3Bytes) ?: "—", color = D3Muted, fontFamily = TahoMono, fontSize = 7.5.sp, modifier = Modifier.width(50.dp))
                }
            }
        }
    }
}

@Composable
private fun Phase3SourcesPanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    val scripts = obj?.optJSONArray("scripts")
    val sheets = obj?.optJSONArray("stylesheets")
    var selectedPreview by remember(state.payloadJson) {
        mutableStateOf(
            scripts?.optJSONObject(0)?.optString("inlinePreview").orEmpty().ifBlank {
                "Select an inline script to preview source. External-source fetching remains subject to page/CORS permissions."
            },
        )
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(150.dp).fillMaxSize().background(D3Surface)) {
            Text("Page", color = D3Text, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items((0 until (scripts?.length() ?: 0)).toList()) { i ->
                    val script = scripts?.optJSONObject(i)
                    val name = script?.optString("src").orEmpty().substringAfterLast('/').ifBlank { "inline-" + i + ".js" }
                    Text(
                        "▸ " + name,
                        color = D3Text,
                        fontFamily = TahoMono,
                        fontSize = 8.5.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedPreview = script?.optString("inlinePreview").orEmpty().ifBlank { script?.optString("src").orEmpty() }
                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                items((0 until (sheets?.length() ?: 0)).toList()) { i ->
                    val sheet = sheets?.optJSONObject(i)
                    Text(
                        "▸ " + sheet?.optString("href").orEmpty().substringAfterLast('/').ifBlank { "stylesheet-" + i + ".css" },
                        color = D3Muted,
                        fontFamily = TahoMono,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            selectedPreview.ifBlank { phase3Pretty(state) },
            color = D3Code,
            fontFamily = TahoMono,
            fontSize = 9.sp,
            lineHeight = 13.sp,
            modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        )
    }
}

@Composable
private fun Phase3PerformancePanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    val nav = obj?.optJSONObject("navigation")
    val paints = obj?.optJSONArray("paints")
    val resources = obj?.optJSONArray("resources")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase3Metric("Load", nav?.optDouble("loadEventEnd")?.phase3Ms() ?: "—", Modifier.weight(1f))
            Phase3Metric("DOM", nav?.optDouble("domContentLoadedEventEnd")?.phase3Ms() ?: "—", Modifier.weight(1f))
            Phase3Metric("Resources", (resources?.length() ?: 0).toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Phase3SectionLabel("Paint timings")
        if (paints == null || paints.length() == 0) {
            Phase3Note("No paint entries are exposed for this page.")
        } else {
            repeat(paints.length()) { i ->
                val p = paints.optJSONObject(i)
                Phase3KeyValue(p?.optString("name").orEmpty(), p?.optDouble("startTime")?.phase3Ms() ?: "—")
            }
        }
        Spacer(Modifier.height(10.dp))
        Phase3SectionLabel("Resource waterfall")
        repeat(minOf(resources?.length() ?: 0, 20)) { i ->
            val r = resources?.optJSONObject(i)
            val duration = r?.optDouble("duration") ?: 0.0
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    r?.optString("name").orEmpty().substringAfterLast('/').ifBlank { r?.optString("initiatorType").orEmpty() },
                    color = D3Text, fontFamily = TahoBody, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(D3SurfaceHi)) {
                    Box(
                        Modifier.fillMaxWidth((duration / 1500.0).coerceIn(.03, 1.0).toFloat()).height(5.dp).background(D3Blue),
                    )
                }
            }
        }
    }
}

@Composable
private fun Phase3MemoryPanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase3Metric("DOM nodes", obj?.optInt("domNodes")?.toString() ?: "—", Modifier.weight(1f))
            Phase3Metric("Scripts", obj?.optInt("scripts")?.toString() ?: "—", Modifier.weight(1f))
            Phase3Metric("Images", obj?.optInt("images")?.toString() ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Phase3KeyValue("Used JS heap", obj?.optLong("usedJSHeapSize")?.takeIf { it > 0 }?.let(::phase3Bytes) ?: "Unavailable")
        Phase3KeyValue("Total JS heap", obj?.optLong("totalJSHeapSize")?.takeIf { it > 0 }?.let(::phase3Bytes) ?: "Unavailable")
        Phase3KeyValue("Heap limit", obj?.optLong("jsHeapSizeLimit")?.takeIf { it > 0 }?.let(::phase3Bytes) ?: "Unavailable")
        obj?.optString("note")?.takeIf { it.isNotBlank() }?.let { Phase3Note(it) }
    }
}

@Composable
private fun Phase3ApplicationPanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    val local = obj?.optJSONObject("localStorage")
    val session = obj?.optJSONObject("sessionStorage")
    val service = obj?.optJSONObject("serviceWorker")
    val caches = obj?.optJSONArray("cacheStorage")
    val db = obj?.optJSONArray("indexedDB")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Phase3ApplicationRow("Manifest", obj?.optString("manifest").orEmpty().ifBlank { "None" })
        Phase3ApplicationRow("Service workers", if (service?.optBoolean("controlled") == true) "Controlled" else "Not controlled")
        Phase3ApplicationRow("Local storage", (local?.length() ?: 0).toString() + " keys")
        Phase3ApplicationRow("Session storage", (session?.length() ?: 0).toString() + " keys")
        Phase3ApplicationRow("Cache storage", (caches?.length() ?: 0).toString() + " caches")
        Phase3ApplicationRow("IndexedDB", (db?.length() ?: 0).toString() + " databases")
        Phase3ApplicationRow("Cookies", (obj?.optJSONArray("cookieNames")?.length() ?: 0).toString() + " names visible to page")
    }
}

@Composable
private fun Phase3SecurityPanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    val secure = obj?.optBoolean("secureContext") == true
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (secure) D3Green.copy(alpha = .14f) else D3Red.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (secure) "✓" else "!", color = if (secure) D3Green else D3Red, fontSize = 26.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (secure) "Connection is secure" else "Connection is not a secure context",
            color = D3Text, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        )
        Spacer(Modifier.height(14.dp))
        Phase3KeyValue("Protocol", obj?.optString("protocol").orEmpty())
        Phase3KeyValue("Cross-origin isolated", if (obj?.optBoolean("crossOriginIsolated") == true) "Yes" else "No")
        Phase3KeyValue("Referrer policy", obj?.optString("referrerPolicy").orEmpty().ifBlank { "Default" })
        Phase3KeyValue("Mixed resources", (obj?.optJSONArray("mixedContentResources")?.length() ?: 0).toString())
        Phase3Note("Certificate-chain details remain in Taho site info; this panel reports page-visible security state.")
    }
}

@Composable
private fun Phase3AuditPanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    val summary = obj?.optJSONObject("summary")
    val checks = obj?.optJSONArray("checks")
    val passed = summary?.optInt("passed") ?: 0
    val total = summary?.optInt("total") ?: 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase3Metric("Passed", passed.toString() + "/" + total, Modifier.weight(1f))
            Phase3Metric("Images", summary?.optInt("images")?.toString() ?: "—", Modifier.weight(1f))
            Phase3Metric("Inputs", summary?.optInt("inputs")?.toString() ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Phase3Note("Taho on-device audit. This is not Chromium Lighthouse and does not claim Lighthouse scores.")
        Spacer(Modifier.height(8.dp))
        repeat(checks?.length() ?: 0) { i ->
            val check = checks?.optJSONObject(i)
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (check?.optBoolean("pass") == true) "✓" else "!", color = if (check?.optBoolean("pass") == true) D3Green else D3Yellow, modifier = Modifier.width(24.dp))
                Text(check?.optString("name").orEmpty(), color = D3Text, fontFamily = TahoBody, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text(check?.optString("detail").orEmpty(), color = D3Muted, fontFamily = TahoBody, fontSize = 8.5.sp)
            }
        }
    }
}

@Composable
private fun Phase3RecorderPanel(state: DevToolsUiState, onRequest: (String, String?) -> Unit) {
    val obj = phase3Object(state)
    val events = obj?.optJSONArray("events")
    val active = obj?.optBoolean("active") == true
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase3MiniButton(if (active) "Recording…" else "Start") { onRequest("RECORDER", "start") }
            Phase3MiniButton("Stop") { onRequest("RECORDER", "stop") }
            Phase3MiniButton("Clear") { onRequest("RECORDER", "clear") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items((0 until (events?.length() ?: 0)).toList()) { i ->
                val event = events?.optJSONObject(i)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(event?.optString("type").orEmpty(), color = D3Blue, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.width(58.dp))
                    Text(event?.optString("target").orEmpty(), color = D3Text, fontFamily = TahoMono, fontSize = 8.5.sp, modifier = Modifier.weight(1f), maxLines = 2)
                }
            }
        }
        if (events == null || events.length() == 0) Phase3Note("Start recording, then interact with the page. Password values are always masked.")
    }
}

@Composable
private fun Phase3IssuesPanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    val groups = listOf(
        "Duplicate IDs" to obj?.optJSONArray("duplicateIds"),
        "Broken images" to obj?.optJSONArray("brokenImages"),
        "Insecure forms" to obj?.optJSONArray("insecureForms"),
        "Missing alt text" to obj?.optJSONArray("imagesMissingAlt"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        groups.forEach { pair ->
            val title = pair.first
            val arr = pair.second
            val count = arr?.length() ?: 0
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (count == 0) "✓" else "!", color = if (count == 0) D3Green else D3Yellow, modifier = Modifier.width(24.dp))
                Text(title, color = D3Text, fontFamily = TahoBody, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                Text(count.toString(), color = D3Muted, fontFamily = TahoMono, fontSize = 9.sp)
            }
            repeat(minOf(count, 5)) { i ->
                Text("  • " + arr?.optString(i).orEmpty(), color = D3Muted, fontFamily = TahoMono, fontSize = 8.sp, modifier = Modifier.padding(start = 20.dp, bottom = 3.dp))
            }
        }
        obj?.optString("note")?.takeIf { it.isNotBlank() }?.let { Phase3Note(it) }
    }
}

@Composable
private fun Phase3CoveragePanel(state: DevToolsUiState) {
    val obj = phase3Object(state)
    val css = obj?.optJSONObject("css")
    val total = css?.optInt("totalRules") ?: 0
    val used = css?.optInt("usedRules") ?: 0
    val pct = if (total > 0) used * 100 / total else 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Phase3Metric("CSS used", pct.toString() + "%", Modifier.weight(1f))
            Phase3Metric("Rules", total.toString(), Modifier.weight(1f))
            Phase3Metric("Unused", (css?.optInt("unusedRules") ?: 0).toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Phase3Note(obj?.optJSONObject("javascript")?.optString("note").orEmpty().ifBlank { "JavaScript execution coverage is unavailable." })
        Spacer(Modifier.height(8.dp))
        val samples = css?.optJSONArray("samples")
        repeat(minOf(samples?.length() ?: 0, 30)) { i ->
            val sample = samples?.optJSONObject(i)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(if (sample?.optBoolean("used") == true) "●" else "○", color = if (sample?.optBoolean("used") == true) D3Green else D3Muted, modifier = Modifier.width(20.dp))
                Text(sample?.optString("selector").orEmpty(), color = D3Text, fontFamily = TahoMono, fontSize = 8.sp, modifier = Modifier.weight(1f), maxLines = 2)
            }
        }
    }
}

@Composable
private fun Phase3ChangesPanel(state: DevToolsUiState, onRequest: (String, String?) -> Unit) {
    val obj = phase3Object(state)
    val changes = obj?.optJSONArray("changes")
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mutation history", color = D3Text, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Phase3MiniButton("Clear") { onRequest("CHANGES", "clear") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items((0 until (changes?.length() ?: 0)).toList()) { i ->
                val item = changes?.optJSONObject(i)
                Text(
                    item?.optString("type").orEmpty() + "  " + item?.optString("target").orEmpty(),
                    color = D3Text, fontFamily = TahoMono, fontSize = 8.5.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                )
            }
        }
        if (changes == null || changes.length() == 0) Phase3Note("No DOM mutations recorded since this panel was opened.")
    }
}

@Composable
private fun Phase3StructuredPanel(title: String, state: DevToolsUiState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Text(title, color = D3Text, fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                state.loading -> "Loading live page data…"
                state.error != null -> state.error
                else -> phase3Pretty(state)
            },
            color = if (state.error != null) D3Red else D3Code,
            fontFamily = TahoMono,
            fontSize = 9.sp,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun Phase3AttachPrompt(onReloadPage: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Inspector not attached", color = D3Text, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Reload the current page once. Taho attaches its inspection bridge at document start.",
            color = D3Muted, fontFamily = TahoBody, fontSize = 11.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Phase3MiniButton("Reload page", onReloadPage)
    }
}

@Composable
private fun Phase3Centered(title: String, detail: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = D3Text, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = D3Muted, fontFamily = TahoBody, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Phase3Header(title: String, subtitle: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("←", color = D3Text, fontSize = 18.sp, modifier = Modifier.size(42.dp).clickable(onClick = onClose).padding(10.dp), textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            Text(title, color = D3Text, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(subtitle, color = D3Muted, fontFamily = TahoBody, fontSize = 9.5.sp)
        }
        Text("⚙", color = D3Text, fontSize = 16.sp, modifier = Modifier.size(42.dp).padding(10.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Phase3SectionLabel(text: String) {
    Text(text, color = D3Muted, fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun Phase3TableHeader(text: String, modifier: Modifier) {
    Text(text, color = D3Muted, fontFamily = TahoBody, fontSize = 8.5.sp, modifier = modifier)
}

@Composable
private fun Phase3Metric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(D3Surface).border(1.dp, D3Border, RoundedCornerShape(10.dp)).padding(9.dp),
    ) {
        Text(label, color = D3Muted, fontFamily = TahoBody, fontSize = 8.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = D3Text, fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun Phase3KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = D3Muted, fontFamily = TahoBody, fontSize = 9.5.sp, modifier = Modifier.width(126.dp))
        Text(value, color = D3Text, fontFamily = TahoBody, fontSize = 9.5.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Phase3ApplicationRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("▸", color = D3Blue, modifier = Modifier.width(20.dp))
        Text(label, color = D3Text, fontFamily = TahoBody, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
        Text(value, color = D3Muted, fontFamily = TahoBody, fontSize = 8.5.sp)
    }
}

@Composable
private fun Phase3Note(text: String) {
    if (text.isBlank()) return
    Text(
        text,
        color = D3Muted,
        fontFamily = TahoBody,
        fontSize = 9.5.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(D3Surface).padding(9.dp),
    )
}

@Composable
private fun Phase3MiniButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(D3SurfaceHi)
            .border(1.dp, D3Border, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = D3Text, fontFamily = TahoBody, fontSize = 9.5.sp)
    }
}

private fun phase3PanelGlyph(panel: Phase3DevToolsPanel): String = when (panel) {
    Phase3DevToolsPanel.ELEMENTS -> "</>"
    Phase3DevToolsPanel.CONSOLE -> ">_"
    Phase3DevToolsPanel.NETWORK -> "⌁"
    Phase3DevToolsPanel.SOURCES -> "{}"
    Phase3DevToolsPanel.PERFORMANCE -> "◔"
    Phase3DevToolsPanel.MEMORY -> "▦"
    Phase3DevToolsPanel.APPLICATION -> "▤"
    Phase3DevToolsPanel.SECURITY -> "◇"
    Phase3DevToolsPanel.LIGHTHOUSE -> "◉"
    Phase3DevToolsPanel.RECORDER -> "●"
    Phase3DevToolsPanel.ISSUES -> "!"
    Phase3DevToolsPanel.RENDERING -> "▰"
    Phase3DevToolsPanel.SENSORS -> "⌖"
    Phase3DevToolsPanel.COVERAGE -> "◫"
    Phase3DevToolsPanel.CHANGES -> "∆"
    Phase3DevToolsPanel.ANIMATIONS -> "≈"
}

private fun phase3PanelDescription(panel: Phase3DevToolsPanel): String = when (panel) {
    Phase3DevToolsPanel.ELEMENTS -> "Inspect live DOM and page structure"
    Phase3DevToolsPanel.CONSOLE -> "Run JavaScript and inspect results"
    Phase3DevToolsPanel.NETWORK -> "Real captured requests, status, type and size"
    Phase3DevToolsPanel.SOURCES -> "Scripts and stylesheets visible to the page"
    Phase3DevToolsPanel.PERFORMANCE -> "Navigation, paint and resource timing"
    Phase3DevToolsPanel.MEMORY -> "Heap metrics when exposed plus DOM counters"
    Phase3DevToolsPanel.APPLICATION -> "Storage, service workers, caches and manifest"
    Phase3DevToolsPanel.SECURITY -> "Secure context, mixed content and page policy"
    Phase3DevToolsPanel.LIGHTHOUSE -> "Taho on-device audit; not Chromium Lighthouse"
    Phase3DevToolsPanel.RECORDER -> "Record click, input, change and submit events"
    Phase3DevToolsPanel.ISSUES -> "DOM and security issue scan"
    Phase3DevToolsPanel.RENDERING -> "Viewport, media and animation state"
    Phase3DevToolsPanel.SENSORS -> "Screen, orientation and device capability state"
    Phase3DevToolsPanel.COVERAGE -> "CSS selector-use approximation"
    Phase3DevToolsPanel.CHANGES -> "MutationObserver-backed DOM change history"
    Phase3DevToolsPanel.ANIMATIONS -> "Web Animations timing and play-state data"
}

private fun phase3Object(state: DevToolsUiState): JSONObject? =
    state.payloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }

private fun phase3Pretty(state: DevToolsUiState): String =
    state.payloadJson?.let { raw ->
        runCatching { JSONObject(raw).toString(2) }.getOrElse {
            runCatching { JSONArray(raw).toString(2) }.getOrDefault(raw)
        }
    }.orEmpty()

private fun phase3PrettyHtml(html: String): String = html.replace("><", ">\n<").take(120_000)

private fun phase3Bytes(value: Long): String = when {
    value >= 1024L * 1024L -> String.format("%.1f MB", value / (1024.0 * 1024.0))
    value >= 1024L -> String.format("%.1f KB", value / 1024.0)
    else -> value.toString() + " B"
}

private fun Double.phase3Ms(): String =
    if (!isFinite()) "—"
    else if (this >= 1000.0) String.format("%.2f s", this / 1000.0)
    else String.format("%.0f ms", this)
