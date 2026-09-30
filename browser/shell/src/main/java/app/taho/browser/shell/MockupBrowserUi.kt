package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder

private val MockupChrome: Color get() = TahoBg
private val MockupChromeElevated: Color get() = TahoSheet
private val MockupControl: Color get() = TahoSurfaceControl
private val MockupBorder: Color get() = TahoHairlineStrong
private val MockupAccent: Color get() = TahoText

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TahoMockupStartPage(
    isPrivate: Boolean,
    onNavigate: (String) -> Unit,
    recentTabs: List<BrowserTabUiState>,
    onSelectTab: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val settings = TahoBrowserStateStore.settings
    val searchEngine = TahoBrowserStateStore.searchEngines
        .firstOrNull { it.id == settings.defaultSearchEngineId }
        ?: TahoBrowserStateStore.searchEngines.first()
    val shortcuts = TahoBrowserStateStore.topSites.take(8)
    val suggestions = remember(query, TahoBrowserStateStore.history, TahoBrowserStateStore.bookmarks) {
        val q = query.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            (
                TahoBrowserStateStore.bookmarks.map { it.title to it.url } +
                    TahoBrowserStateStore.history.map { it.title to it.url }
                )
                .asSequence()
                .filter { (title, url) ->
                    title.contains(q, ignoreCase = true) || url.contains(q, ignoreCase = true)
                }
                .distinctBy { it.second }
                .take(5)
                .toList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TahoBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Text(
                text = if (isPrivate) "Taho Private" else "Taho",
                color = TahoText,
                fontFamily = TahoDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isPrivate) 38.sp else 48.sp,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isPrivate) "Browse privately" else "Browse Freely",
                color = TahoText.copy(alpha = 0.78f),
                fontFamily = TahoBody,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(30.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(TahoSheet)
                    .border(1.dp, TahoText.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "G",
                        color = TahoText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search or enter address",
                                color = TahoMuted,
                                fontFamily = TahoBody,
                                fontSize = 14.sp,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TahoText,
                                fontFamily = TahoBody,
                                fontSize = 14.sp,
                            ),
                            cursorBrush = SolidColor(MockupAccent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    resolveMockupNavigation(query, searchEngine)?.let(onNavigate)
                                },
                            ),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "●",
                        color = TahoMuted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .size(32.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Voice search"
                            },
                    )
                }

                if (query.isNotBlank()) {
                    suggestions.forEach { (title, url) ->
                        MockupSearchSuggestion(
                            title = title,
                            url = url,
                            onClick = {
                                query = url
                                onNavigate(url)
                            },
                        )
                    }
                    MockupSearchSuggestion(
                        title = "Search with ${searchEngine.name}",
                        url = query,
                        onClick = {
                            resolveMockupNavigation(query, searchEngine)?.let(onNavigate)
                        },
                    )
                }
            }

            Spacer(Modifier.height(34.dp))

            if (shortcuts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    shortcuts.forEach { item ->
                        Column(
                            modifier = Modifier
                                .width(72.dp)
                                .clickable { onNavigate(item.url) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(TahoSurfaceControl)
                                    .border(1.dp, TahoText.copy(alpha = 0.08f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = item.title.take(1).uppercase(),
                                    color = TahoText,
                                    fontFamily = TahoDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                text = item.title,
                                color = TahoText.copy(alpha = 0.9f),
                                fontFamily = TahoBody,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (recentTabs.any { it.location != null && it.location != "about:blank" }) {
                Spacer(Modifier.height(34.dp))
                Text(
                    text = "Recent tabs",
                    color = TahoText.copy(alpha = 0.72f),
                    fontFamily = TahoBody,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                recentTabs
                    .filter { it.location != null && it.location != "about:blank" }
                    .take(3)
                    .forEach { tab ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(TahoSurfaceRow)
                                .clickable { onSelectTab(tab.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tab.title?.takeIf { it.isNotBlank() } ?: "Tab",
                                color = TahoText,
                                fontFamily = TahoBody,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("›", color = TahoText.copy(alpha = 0.6f), fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
            }
        }
    }
}

@Composable
private fun MockupSearchSuggestion(
    title: String,
    url: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = TahoMuted, fontSize = 15.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TahoText,
                fontFamily = TahoBody,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = url,
                color = TahoMuted,
                fontFamily = TahoMono,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("↗", color = TahoMuted, fontSize = 13.sp)
    }
}

@Composable
internal fun TahoMockupToolbar(
    value: String,
    draft: String,
    editing: Boolean,
    isLoading: Boolean,
    isPrivate: Boolean,
    tabCount: Int,
    onDraftChange: (String) -> Unit,
    onBeginEdit: () -> Unit,
    onSubmit: () -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onHomeClick: () -> Unit,
    onLeadingClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MockupChrome)
            .border(1.dp, MockupBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(2.dp)
                    .background(MockupAccent),
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MockupSquareButton("⌂", "Home", onHomeClick)
            Spacer(Modifier.width(4.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MockupControl)
                    .clickable(enabled = !editing, onClick = onBeginEdit)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isPrivate) "◐" else if (value.startsWith("https://")) "▣" else "⌕",
                    color = if (isPrivate) TahoText else TahoText.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onLeadingClick),
                )
                Spacer(Modifier.width(9.dp))
                if (editing) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (draft.isEmpty()) {
                            Text(
                                "Search or enter address",
                                color = TahoText.copy(alpha = 0.46f),
                                fontFamily = TahoBody,
                                fontSize = 13.sp,
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TahoText,
                                fontFamily = TahoBody,
                                fontSize = 13.sp,
                            ),
                            cursorBrush = SolidColor(MockupAccent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                        )
                    }
                } else {
                    Text(
                        text = compactMockupUrl(value),
                        color = TahoText,
                        fontFamily = TahoBody,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.width(4.dp))
            MockupTabButton(tabCount, onTabsClick)
            Spacer(Modifier.width(4.dp))
            TahoMockupMenuButton(onMenuClick)
        }

        if (editing && draft.isNotBlank()) {
            val q = draft.trim()
            val localSuggestions = (
                TahoBrowserStateStore.bookmarks.map { it.title to it.url } +
                    TahoBrowserStateStore.history.map { it.title to it.url }
                )
                .asSequence()
                .filter { (title, url) ->
                    title.contains(q, ignoreCase = true) || url.contains(q, ignoreCase = true)
                }
                .distinctBy { it.second }
                .take(4)
                .toList()

            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MockupChromeElevated),
            ) {
                localSuggestions.forEach { (title, url) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionSelected(url) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⌕", color = TahoText.copy(alpha = 0.5f))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                color = TahoText,
                                fontFamily = TahoBody,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                compactMockupUrl(url),
                                color = TahoText.copy(alpha = 0.5f),
                                fontFamily = TahoMono,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionSelected(q) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("↗", color = MockupAccent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Search for “$q”",
                        color = TahoText,
                        fontFamily = TahoBody,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MockupSquareButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = TahoText.copy(alpha = 0.9f),
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MockupTabButton(
    count: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${count.coerceAtLeast(1)} tabs"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.5.dp, TahoText.copy(alpha = 0.86f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.coerceAtLeast(1).toString(),
                color = TahoText,
                fontFamily = TahoMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TahoMockupMenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Taho menu"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(TahoText.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "T",
                color = TahoText,
                fontFamily = TahoDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
internal fun TahoMockupBrowserMenuSheet(
    isDesktopMode: Boolean,
    onToggleDesktopMode: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onOpenSettings: (String?) -> Unit,
    onFindInPage: () -> Unit,
    onTranslate: () -> Unit,
    onAddToHomeScreen: () -> Unit,
    onOpenDeveloperTools: () -> Unit,
    onCloseMenu: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        MockupMenuRow("+", "New tab") {
            onCloseMenu()
            onNewTab()
        }
        MockupMenuRow("◐", "New incognito tab") {
            onCloseMenu()
            onNewPrivateTab()
        }
        MockupMenuDivider()
        MockupMenuRow("↶", "History") {
            onCloseMenu()
            onOpenSettings("HISTORY")
        }
        MockupMenuRow("↓", "Downloads") {
            onCloseMenu()
            onOpenSettings("DOWNLOADS")
        }
        MockupMenuRow("▯", "Bookmarks") {
            onCloseMenu()
            onOpenSettings("BOOKMARKS")
        }
        MockupMenuRow("▤", "Recent tabs") {
            onCloseMenu()
            onOpenSettings("HISTORY")
        }
        MockupMenuDivider()
        MockupMenuRow("✚", "Extensions", trailing = "›") {
            onCloseMenu()
            onOpenSettings("EXTENSIONS")
        }
        MockupMenuRow("◇", "Privacy tools", trailing = "›") {
            onCloseMenu()
            onOpenSettings("PRIVACY")
        }
        MockupMenuRow("⌘", "Developer tools", trailing = "›") {
            onCloseMenu()
            onOpenDeveloperTools()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("▣", color = TahoText.copy(alpha = 0.72f), fontSize = 16.sp)
            Spacer(Modifier.width(14.dp))
            Text(
                "Desktop site",
                color = TahoText,
                fontFamily = TahoBody,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isDesktopMode,
                onCheckedChange = { onToggleDesktopMode() },
            )
        }
        MockupMenuDivider()
        MockupMenuRow("⌕", "Find in page") {
            onCloseMenu()
            onFindInPage()
        }
        MockupMenuRow("文", "Translate page") {
            onCloseMenu()
            onTranslate()
        }
        MockupMenuRow("⊞", "Add to Home screen") {
            onCloseMenu()
            onAddToHomeScreen()
        }
        MockupMenuDivider()
        MockupMenuRow("⚙", "Settings") {
            onCloseMenu()
            onOpenSettings(null)
        }
        MockupMenuRow("?", "Help & feedback") {
            onCloseMenu()
            onOpenSettings("ABOUT")
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MockupMenuRow(
    glyph: String,
    label: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            glyph,
            color = TahoText.copy(alpha = 0.76f),
            fontSize = 16.sp,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = TahoText,
            fontFamily = TahoBody,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, color = TahoText.copy(alpha = 0.45f), fontSize = 18.sp)
        }
    }
}

@Composable
private fun MockupMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(TahoText.copy(alpha = 0.08f)),
    )
}

private enum class MockupDevToolsPanel(val title: String) {
    ELEMENTS("Elements"),
    CONSOLE("Console"),
    SOURCES("Sources"),
    NETWORK("Network"),
    PERFORMANCE("Performance"),
    MEMORY("Memory"),
    APPLICATION("Application"),
    SECURITY("Security"),
    LIGHTHOUSE("Lighthouse"),
    RECORDER("Recorder"),
    ISSUES("Issues"),
    RENDERING("Rendering"),
    SENSORS("Sensors"),
    COVERAGE("Coverage"),
    CHANGES("Changes"),
    ANIMATIONS("Animations"),
}

private enum class MockupDevToolsMode(val title: String) {
    BOTTOM("Bottom panel"),
    SIDE("Side by side"),
    FLOATING("Floating"),
    FULLSCREEN("Full screen"),
}

@Composable
internal fun TahoMockupDeveloperToolsSheet(
    captureRequests: List<M4CaptureRequestUiState>,
    onDismiss: () -> Unit,
) {
    var enabled by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf(MockupDevToolsPanel.ELEMENTS) }
    var mode by rememberSaveable { mutableStateOf(MockupDevToolsMode.BOTTOM) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .navigationBarsPadding()
            .background(MockupChrome)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Developer tools",
                color = TahoText,
                fontFamily = TahoDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "×",
                color = TahoText,
                fontSize = 22.sp,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onDismiss)
                    .padding(10.dp),
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MockupChromeElevated)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Enable Developer tools",
                    color = TahoText,
                    fontFamily = TahoBody,
                    fontSize = 14.sp,
                )
                Text(
                    "Desktop-grade panels adapted for touch",
                    color = TahoText.copy(alpha = 0.48f),
                    fontFamily = TahoBody,
                    fontSize = 11.sp,
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            MockupDevToolsMode.entries.forEach { item ->
                val active = item == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (active) MockupAccent else MockupChromeElevated)
                        .border(
                            1.dp,
                            if (active) MockupAccent else MockupBorder,
                            RoundedCornerShape(18.dp),
                        )
                        .clickable { mode = item }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        item.title,
                        color = TahoText,
                        fontFamily = TahoBody,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MockupDevToolsPanel.entries.forEach { panel ->
                val active = panel == selected
                Column(
                    modifier = Modifier
                        .clickable { selected = panel }
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        panel.title,
                        color = if (active) TahoText else TahoText.copy(alpha = 0.5f),
                        fontFamily = TahoBody,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (active) MockupAccent else Color.Transparent),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(TahoBg)
                .border(1.dp, MockupBorder, RoundedCornerShape(14.dp)),
        ) {
            if (!enabled) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Developer tools are disabled",
                        color = TahoText,
                        fontFamily = TahoDisplay,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Enable the switch above to inspect this tab.",
                        color = TahoText.copy(alpha = 0.5f),
                        fontFamily = TahoBody,
                        fontSize = 12.sp,
                    )
                }
            } else {
                MockupDevToolsPanelContent(
                    panel = selected,
                    captureRequests = captureRequests,
                    mode = mode,
                )
            }
        }
    }
}

@Composable
private fun MockupDevToolsPanelContent(
    panel: MockupDevToolsPanel,
    captureRequests: List<M4CaptureRequestUiState>,
    mode: MockupDevToolsMode,
) {
    when (panel) {
        MockupDevToolsPanel.NETWORK -> MockupNetworkPanel(captureRequests)
        MockupDevToolsPanel.ELEMENTS -> MockupElementsPanel()
        MockupDevToolsPanel.CONSOLE -> MockupConsolePanel()
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Text(
                    panel.title,
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Layout: ${mode.title}",
                    color = MockupAccent,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    desktopPanelDescription(panel),
                    color = TahoText.copy(alpha = 0.72f),
                    fontFamily = TahoBody,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "UI shell is wired. Engine transport for this panel is the next implementation layer; no placeholder inspection data is being fabricated.",
                    color = TahoText.copy(alpha = 0.42f),
                    fontFamily = TahoBody,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun MockupElementsPanel() {
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(12.dp),
        ) {
            Text(
                "DOM",
                color = TahoText.copy(alpha = 0.55f),
                fontFamily = TahoMono,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "<html>\n  <head>…</head>\n  <body>\n    Inspector transport not connected\n  </body>\n</html>",
                color = TahoText,
                fontFamily = TahoMono,
                fontSize = 10.sp,
                lineHeight = 16.sp,
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(TahoText.copy(alpha = 0.08f)),
        )
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .padding(12.dp),
        ) {
            Text(
                "Styles  Computed  Layout",
                color = TahoText.copy(alpha = 0.7f),
                fontFamily = TahoMono,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Select an element to inspect CSS.",
                color = TahoText.copy(alpha = 0.4f),
                fontFamily = TahoBody,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MockupConsolePanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MockupChromeElevated)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕ Filter", color = TahoText.copy(alpha = 0.45f), fontFamily = TahoMono, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("Default levels ▾", color = TahoText.copy(alpha = 0.6f), fontFamily = TahoMono, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Console transport not connected yet.",
            color = TahoText.copy(alpha = 0.52f),
            fontFamily = TahoMono,
            fontSize = 10.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("›", color = MockupAccent, fontFamily = TahoMono, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text("Run JavaScript in this page", color = TahoText.copy(alpha = 0.34f), fontFamily = TahoMono, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MockupNetworkPanel(
    captureRequests: List<M4CaptureRequestUiState>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("●", color = TahoText, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Text("Filter", color = TahoText.copy(alpha = 0.5f), fontFamily = TahoMono, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "${captureRequests.size} requests",
                color = TahoText.copy(alpha = 0.45f),
                fontFamily = TahoMono,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MockupChromeElevated)
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Text("Name", color = TahoText.copy(alpha = 0.6f), fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.weight(1.7f))
            Text("Status", color = TahoText.copy(alpha = 0.6f), fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.weight(0.7f))
            Text("Type", color = TahoText.copy(alpha = 0.6f), fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.weight(0.7f))
        }
        if (captureRequests.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No captured requests for this tab",
                    color = TahoText.copy(alpha = 0.45f),
                    fontFamily = TahoBody,
                    fontSize = 12.sp,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                captureRequests.take(40).forEach { request ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1.7f)) {
                            Text(
                                text = request.url.substringAfterLast('/').ifBlank { request.url },
                                color = TahoText,
                                fontFamily = TahoMono,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = request.method,
                                color = MockupAccent,
                                fontFamily = TahoMono,
                                fontSize = 8.sp,
                            )
                        }
                        Text(
                            request.status?.toString() ?: "—",
                            color = if ((request.status ?: 0) in 200..399) TahoText else TahoText.copy(alpha = 0.6f),
                            fontFamily = TahoMono,
                            fontSize = 9.sp,
                            modifier = Modifier.weight(0.7f),
                        )
                        Text(
                            request.category,
                            color = TahoText.copy(alpha = 0.5f),
                            fontFamily = TahoMono,
                            fontSize = 8.5.sp,
                            modifier = Modifier.weight(0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TahoText.copy(alpha = 0.05f)),
                    )
                }
            }
        }
    }
}

private fun desktopPanelDescription(panel: MockupDevToolsPanel): String =
    when (panel) {
        MockupDevToolsPanel.SOURCES -> "Debugger, source tree, breakpoints, scope, watch expressions and snippets."
        MockupDevToolsPanel.PERFORMANCE -> "CPU, rendering, network and interaction timeline recording."
        MockupDevToolsPanel.MEMORY -> "Heap snapshots, allocation sampling and constructor comparison."
        MockupDevToolsPanel.APPLICATION -> "Manifest, service workers, storage, cookies, IndexedDB and cache storage."
        MockupDevToolsPanel.SECURITY -> "TLS/security overview, certificate details, mixed-content state and permissions."
        MockupDevToolsPanel.LIGHTHOUSE -> "Performance, accessibility, best-practices and SEO audits."
        MockupDevToolsPanel.RECORDER -> "Record, replay and export user flows."
        MockupDevToolsPanel.ISSUES -> "Aggregated browser, security and compatibility issues."
        MockupDevToolsPanel.RENDERING -> "Paint flashing, layout shifts, FPS meter and rendering diagnostics."
        MockupDevToolsPanel.SENSORS -> "Geolocation, orientation, touch and device-state emulation."
        MockupDevToolsPanel.COVERAGE -> "JavaScript and CSS coverage."
        MockupDevToolsPanel.CHANGES -> "Track local CSS and source modifications."
        MockupDevToolsPanel.ANIMATIONS -> "Inspect and scrub CSS/Web Animations timelines."
        MockupDevToolsPanel.ELEMENTS -> "DOM and CSS inspector."
        MockupDevToolsPanel.CONSOLE -> "JavaScript console."
        MockupDevToolsPanel.NETWORK -> "Network request inspector."
    }

private fun compactMockupUrl(value: String): String =
    value
        .removePrefix("https://")
        .removePrefix("http://")
        .takeIf { it.isNotBlank() && it != "Search or enter address" }
        ?: "Search or enter address"

private fun resolveMockupNavigation(
    input: String,
    searchEngine: SearchEngineItem,
): String? {
    val q = input.trim()
    if (q.isBlank()) return null
    if (q.startsWith("http://") || q.startsWith("https://")) return q
    if (q.contains('.') && !q.contains(' ')) return "https://$q"
    return searchEngine.queryUrl.replace("%s", URLEncoder.encode(q, "UTF-8"))
}
