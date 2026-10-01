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
import app.taho.browser.capture.domain.CaptureState
import java.net.URLEncoder
import org.json.JSONObject

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
                        text = searchEngine.iconGlyph,
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
        modifier = Modifier.fillMaxWidth(),
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
                    .heightIn(min = 46.dp)
                    .clip(TahoPillShape)
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
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
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
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${count.coerceAtLeast(1)} tabs"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, TahoText.copy(alpha = 0.86f), RoundedCornerShape(8.dp)),
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
            .size(48.dp)
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
    captureEnabled: Boolean,
    onToggleDesktopMode: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onOpenSettings: (String?) -> Unit,
    onFindInPage: () -> Unit,
    onTranslate: () -> Unit,
    onAddToHomeScreen: () -> Unit,
    onOpenPacketCapture: () -> Unit,
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
        MockupMenuRow("◎", "Packet capture", trailing = if (captureEnabled) "On" else "Off") {
            onCloseMenu()
            onOpenPacketCapture()
        }
        MockupMenuRow("⌘", "Developer tools", trailing = "›") {
            onCloseMenu()
            onOpenDeveloperTools()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
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
internal fun TahoMockupPacketCaptureSheet(
    enabled: Boolean,
    captureState: CaptureState,
    relevantCount: Int,
    totalCount: Int,
    capabilityNote: String?,
    onEnabledChange: (Boolean) -> Unit,
    onViewCaptured: () -> Unit,
    onDismiss: () -> Unit,
) {
    val status = when {
        !enabled -> "Off"
        captureState == CaptureState.CAPTURING -> "Capturing"
        captureState == CaptureState.OBSERVING -> "Active"
        captureState == CaptureState.LIMITED -> "Limited"
        captureState == CaptureState.ERROR -> "Unavailable"
        else -> "Starting"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Packet capture",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Text(
                    "Master developer mode",
                    color = TahoText.copy(alpha = 0.48f),
                    fontFamily = TahoBody,
                    fontSize = 11.sp,
                )
            }
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

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MockupChromeElevated)
                .border(1.dp, MockupBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Enable packet capture",
                    color = TahoText,
                    fontFamily = TahoBody,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (enabled) {
                        "State is saved and restored on the next browser launch."
                    } else {
                        "Off means normal browsing; developer tools remain disabled."
                    },
                    color = TahoText.copy(alpha = 0.48f),
                    fontFamily = TahoBody,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TahoBg)
                .border(1.dp, MockupBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Status",
                    color = TahoText.copy(alpha = 0.5f),
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    status,
                    color = TahoText,
                    fontFamily = TahoBody,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$relevantCount relevant",
                    color = TahoText,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                )
                Text(
                    "$totalCount retained",
                    color = TahoText.copy(alpha = 0.45f),
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                )
            }
        }

        if (enabled && capabilityNote != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                capabilityNote,
                color = TahoText.copy(alpha = 0.58f),
                fontFamily = TahoBody,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MockupChromeElevated)
                .border(1.dp, MockupBorder, RoundedCornerShape(14.dp))
                .clickable(enabled = totalCount > 0, onClick = onViewCaptured)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                if (totalCount > 0) "View captured requests" else "No captured requests yet",
                color = if (totalCount > 0) TahoText else TahoText.copy(alpha = 0.36f),
                fontFamily = TahoBody,
                fontSize = 13.sp,
            )
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
            .heightIn(min = 52.dp)
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
    enabled: Boolean,
    captureRequests: List<M4CaptureRequestUiState>,
    devToolsState: DevToolsUiState,
    onRequest: (String, String?) -> Unit,
    onReloadPage: () -> Unit,
    onInspectRequest: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(MockupDevToolsPanel.ELEMENTS) }
    var mode by rememberSaveable { mutableStateOf(MockupDevToolsMode.BOTTOM) }

    LaunchedEffect(selected, enabled, devToolsState.connected) {
        val command = devToolsCommandFor(selected)
        if (enabled && devToolsState.connected && command != null) {
            onRequest(command, null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .navigationBarsPadding()
            .background(MockupChrome)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
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
                "↻",
                color = if (enabled && selected != MockupDevToolsPanel.NETWORK) TahoText else TahoText.copy(alpha = .28f),
                fontSize = 18.sp,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(enabled = enabled && selected != MockupDevToolsPanel.NETWORK) {
                        devToolsCommandFor(selected)?.let { onRequest(it, null) }
                    }
                    .padding(10.dp),
                textAlign = TextAlign.Center,
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
                    if (enabled) "Developer tools enabled" else "Developer tools disabled",
                    color = TahoText,
                    fontFamily = TahoBody,
                    fontSize = 14.sp,
                )
                Text(
                    when {
                        !enabled -> "Enable Packet Capture from the Taho menu to use these panels."
                        devToolsState.connected -> "Live inspector attached to the current page."
                        else -> "Packet Capture is on; reload once to attach the live page inspector."
                    },
                    color = TahoText.copy(alpha = 0.48f),
                    fontFamily = TahoBody,
                    fontSize = 11.sp,
                )
            }
            Text(
                when {
                    !enabled -> "OFF"
                    devToolsState.connected -> "LIVE"
                    else -> "WAIT"
                },
                color = TahoText.copy(alpha = if (enabled) 0.82f else 0.42f),
                fontFamily = TahoMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            MockupDevToolsMode.entries.forEach { item ->
                val active = item == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (active) MockupAccent else MockupChromeElevated)
                        .border(1.dp, if (active) MockupAccent else MockupBorder, RoundedCornerShape(18.dp))
                        .clickable(enabled = enabled) { mode = item }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        item.title,
                        color = if (active) TahoBg else TahoText,
                        fontFamily = TahoBody,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MockupDevToolsPanel.entries.forEach { panel ->
                val active = panel == selected
                Column(
                    modifier = Modifier
                        .clickable(enabled = enabled) { selected = panel }
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
            when {
                !enabled -> DevToolsCenteredMessage(
                    title = "Developer tools are disabled",
                    detail = "Enable Packet Capture from the Taho menu to inspect this tab.",
                )

                selected != MockupDevToolsPanel.NETWORK && !devToolsState.connected -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Inspector not attached to this page",
                            color = TahoText,
                            fontFamily = TahoDisplay,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Reload the current page once. Taho attaches its live inspection bridge at document start.",
                            color = TahoText.copy(alpha = .52f),
                            fontFamily = TahoBody,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MockupChromeElevated)
                                .border(1.dp, MockupBorder, RoundedCornerShape(14.dp))
                                .clickable(onClick = onReloadPage)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Reload page", color = TahoText, fontFamily = TahoBody, fontSize = 12.sp)
                        }
                    }
                }

                else -> MockupDevToolsPanelContent(
                    panel = selected,
                    captureRequests = captureRequests,
                    devToolsState = devToolsState,
                    mode = mode,
                    onRequest = onRequest,
                    onInspectRequest = onInspectRequest,
                )
            }
        }
    }
}

@Composable
private fun DevToolsCenteredMessage(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = TahoText, fontFamily = TahoDisplay, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            detail,
            color = TahoText.copy(alpha = .5f),
            fontFamily = TahoBody,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MockupDevToolsPanelContent(
    panel: MockupDevToolsPanel,
    captureRequests: List<M4CaptureRequestUiState>,
    devToolsState: DevToolsUiState,
    mode: MockupDevToolsMode,
    onRequest: (String, String?) -> Unit,
    onInspectRequest: (String) -> Unit,
) {
    when (panel) {
        MockupDevToolsPanel.NETWORK -> MockupNetworkPanel(captureRequests, onInspectRequest)
        MockupDevToolsPanel.ELEMENTS -> MockupElementsPanel(devToolsState)
        MockupDevToolsPanel.CONSOLE -> MockupConsolePanel(devToolsState, onRequest)
        else -> MockupLiveDataPanel(panel, devToolsState, mode, onRequest)
    }
}

@Composable
private fun MockupElementsPanel(state: DevToolsUiState) {
    val payload = state.payloadJson
    val parsed = remember(payload) {
        payload?.let { runCatching { JSONObject(it) }.getOrNull() }
    }
    val html = parsed?.optString("html").orEmpty()
    val meta = parsed?.let {
        it.optInt("nodeCount").toString() + " nodes · " +
            it.optInt("linkCount") + " links · " +
            it.optInt("imageCount") + " images"
    }.orEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("DOM", color = TahoText.copy(alpha = 0.55f), fontFamily = TahoMono, fontSize = 10.sp)
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(meta, color = TahoText.copy(alpha = .42f), fontFamily = TahoMono, fontSize = 9.sp)
        }
        Spacer(Modifier.height(10.dp))
        when {
            state.loading -> Text("Reading live DOM…", color = TahoText.copy(alpha = .48f), fontFamily = TahoMono, fontSize = 10.sp)
            state.error != null -> Text(state.error, color = TahoText.copy(alpha = .62f), fontFamily = TahoMono, fontSize = 10.sp)
            html.isBlank() -> Text("No DOM snapshot returned.", color = TahoText.copy(alpha = .42f), fontFamily = TahoMono, fontSize = 10.sp)
            else -> Text(
                html,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                color = TahoText,
                fontFamily = TahoMono,
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun MockupConsolePanel(
    state: DevToolsUiState,
    onRequest: (String, String?) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val pretty = remember(state.payloadJson) { prettyDevToolsJson(state.payloadJson) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "JavaScript console",
            color = TahoText,
            fontFamily = TahoDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Runs in Taho's WebExtension page-inspection world with live DOM access.",
            color = TahoText.copy(alpha = .46f),
            fontFamily = TahoBody,
            fontSize = 10.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MockupChromeElevated)
                .border(1.dp, MockupBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("›", color = MockupAccent, fontFamily = TahoMono, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 10.5.sp),
                cursorBrush = SolidColor(TahoText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = { if (code.isNotBlank()) onRequest("CONSOLE_EVAL", code) },
                ),
            )
            Text(
                "Run",
                color = if (code.isBlank()) TahoText.copy(alpha = .28f) else TahoText,
                fontFamily = TahoBody,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = code.isNotBlank()) { onRequest("CONSOLE_EVAL", code) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.loading -> Text("Running…", color = TahoText.copy(alpha = .46f), fontFamily = TahoMono, fontSize = 10.sp)
            state.error != null -> Text(state.error, color = TahoText.copy(alpha = .62f), fontFamily = TahoMono, fontSize = 10.sp)
            pretty.isNotBlank() -> Text(
                pretty,
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                color = TahoText.copy(alpha = .82f),
                fontFamily = TahoMono,
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
            )
            else -> Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MockupLiveDataPanel(
    panel: MockupDevToolsPanel,
    state: DevToolsUiState,
    mode: MockupDevToolsMode,
    onRequest: (String, String?) -> Unit,
) {
    val command = devToolsCommandFor(panel)
    val pretty = remember(state.payloadJson) { prettyDevToolsJson(state.payloadJson) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    panel.title,
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text(
                    "Live page data · " + mode.title,
                    color = TahoText.copy(alpha = .42f),
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                )
            }
            if (panel == MockupDevToolsPanel.RECORDER) {
                DevToolsMiniAction("Start") { onRequest("RECORDER", "start") }
                DevToolsMiniAction("Stop") { onRequest("RECORDER", "stop") }
                DevToolsMiniAction("Clear") { onRequest("RECORDER", "clear") }
            } else if (panel == MockupDevToolsPanel.CHANGES) {
                DevToolsMiniAction("Clear") { onRequest("CHANGES", "clear") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            desktopPanelDescription(panel),
            color = TahoText.copy(alpha = .58f),
            fontFamily = TahoBody,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(12.dp))
        when {
            state.loading -> Text("Collecting live data…", color = TahoText.copy(alpha = .46f), fontFamily = TahoMono, fontSize = 10.sp)
            state.error != null -> Text(state.error, color = TahoText.copy(alpha = .62f), fontFamily = TahoMono, fontSize = 10.sp)
            pretty.isNotBlank() -> Text(
                pretty,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                color = TahoText.copy(alpha = .84f),
                fontFamily = TahoMono,
                fontSize = 9.3.sp,
                lineHeight = 14.sp,
            )
            command != null -> Text(
                "No live data returned yet.",
                color = TahoText.copy(alpha = .42f),
                fontFamily = TahoMono,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun DevToolsMiniAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TahoText.copy(alpha = .78f),
        fontFamily = TahoMono,
        fontSize = 9.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
    )
}

@Composable
private fun MockupNetworkPanel(
    captureRequests: List<M4CaptureRequestUiState>,
    onInspectRequest: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = TahoText, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Captured traffic",
                color = TahoText.copy(alpha = 0.5f),
                fontFamily = TahoMono,
                fontSize = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                captureRequests.size.toString() + " requests",
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
            DevToolsCenteredMessage(
                title = "No captured requests",
                detail = "Browse or reload the page while Packet Capture is enabled.",
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            ) {
                captureRequests.take(80).forEach { request ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectRequest(request.id) }
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
                            color = TahoText.copy(alpha = if ((request.status ?: 0) in 200..399) 1f else .62f),
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
                        modifier = Modifier.fillMaxWidth().height(1.dp).background(TahoText.copy(alpha = 0.05f)),
                    )
                }
            }
        }
    }
}

private fun devToolsCommandFor(panel: MockupDevToolsPanel): String? =
    when (panel) {
        MockupDevToolsPanel.ELEMENTS -> "ELEMENTS"
        MockupDevToolsPanel.CONSOLE -> "CONSOLE_INFO"
        MockupDevToolsPanel.SOURCES -> "SOURCES"
        MockupDevToolsPanel.NETWORK -> null
        MockupDevToolsPanel.PERFORMANCE -> "PERFORMANCE"
        MockupDevToolsPanel.MEMORY -> "MEMORY"
        MockupDevToolsPanel.APPLICATION -> "APPLICATION"
        MockupDevToolsPanel.SECURITY -> "SECURITY"
        MockupDevToolsPanel.LIGHTHOUSE -> "LIGHTHOUSE"
        MockupDevToolsPanel.RECORDER -> "RECORDER"
        MockupDevToolsPanel.ISSUES -> "ISSUES"
        MockupDevToolsPanel.RENDERING -> "RENDERING"
        MockupDevToolsPanel.SENSORS -> "SENSORS"
        MockupDevToolsPanel.COVERAGE -> "COVERAGE"
        MockupDevToolsPanel.CHANGES -> "CHANGES"
        MockupDevToolsPanel.ANIMATIONS -> "ANIMATIONS"
    }

private fun prettyDevToolsJson(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return runCatching { JSONObject(raw).toString(2) }.getOrDefault(raw)
}

private fun desktopPanelDescription(panel: MockupDevToolsPanel): String =
    when (panel) {
        MockupDevToolsPanel.SOURCES -> "Live script and stylesheet inventory with inline-source previews."
        MockupDevToolsPanel.PERFORMANCE -> "Navigation, paint and resource timing collected from the live page."
        MockupDevToolsPanel.MEMORY -> "Live JavaScript heap metrics when Gecko exposes them, plus DOM resource counters."
        MockupDevToolsPanel.APPLICATION -> "Local/session storage, service workers, Cache Storage, IndexedDB, cookie names and manifest."
        MockupDevToolsPanel.SECURITY -> "Secure-context, protocol, referrer policy, CSP meta and mixed-resource inspection."
        MockupDevToolsPanel.LIGHTHOUSE -> "Taho on-device performance/accessibility/best-practice checks. This is not Chromium Lighthouse."
        MockupDevToolsPanel.RECORDER -> "Record live click, input, change and submit events. Password values are never recorded."
        MockupDevToolsPanel.ISSUES -> "Live DOM/security scan for duplicate IDs, broken images, insecure forms and missing alt text."
        MockupDevToolsPanel.RENDERING -> "Viewport, visual viewport, scroll state, media preferences and animation count."
        MockupDevToolsPanel.SENSORS -> "Live screen, orientation, touch and sensor API capability information."
        MockupDevToolsPanel.COVERAGE -> "Live CSS selector-use approximation plus JavaScript load inventory."
        MockupDevToolsPanel.CHANGES -> "MutationObserver-backed DOM change history from the moment this panel is opened."
        MockupDevToolsPanel.ANIMATIONS -> "Live Web Animations list with timing, target and play-state information."
        MockupDevToolsPanel.ELEMENTS -> "Live DOM snapshot from the current page."
        MockupDevToolsPanel.CONSOLE -> "Execute JavaScript in Taho's page-inspection WebExtension context."
        MockupDevToolsPanel.NETWORK -> "Real captured requests; tap a row to inspect and send it to Taho."
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
