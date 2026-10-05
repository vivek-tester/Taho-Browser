package app.taho.browser.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TahoStartPage(
    isPrivate: Boolean,
    onNavigate: (String) -> Unit,
    onOpenTabs: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    recentTabs: List<BrowserTabUiState> = emptyList(),
    onSelectTab: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCustomizeSheet by rememberSaveable { mutableStateOf(false) }
    var showAddShortcutDialog by rememberSaveable { mutableStateOf(false) }
    var newShortcutTitle by rememberSaveable { mutableStateOf("") }
    var newShortcutUrl by rememberSaveable { mutableStateOf("") }

    val settings = TahoBrowserStateStore.settings
    var privateUnlocked by rememberSaveable { mutableStateOf(!settings.biometricLockForPrivateTabs) }

    BackHandler(enabled = showCustomizeSheet || showAddShortcutDialog) {
        showCustomizeSheet = false
        showAddShortcutDialog = false
    }

    val topSites = TahoBrowserStateStore.topSites
    val history = TahoBrowserStateStore.history
    val searchEngine = TahoBrowserStateStore.searchEngines.firstOrNull { it.id == settings.defaultSearchEngineId }
        ?: TahoBrowserStateStore.searchEngines.first()

    val bgModifier = when (settings.startPageBackground) {
        "CARBON_GRID" -> Modifier.background(Color(0xFF0A0A0C))
        "DEEP_NAVY" -> Modifier.background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF040812), Color(0xFF08101E), Color(0xFF020408))
            )
        )
        "GOLD_RADIAL" -> Modifier.background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF1E170A), Color(0xFF080705), Color(0xFF000000)),
                radius = 1200f
            )
        )
        else -> Modifier.background(canvas)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(bgModifier)
    ) {
        if (settings.startPageBackground == "CARBON_GRID") {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 32.dp.toPx()
                val lineCol = Color.White.copy(alpha = 0.025f)
                var x = 0f
                while (x < size.width) {
                    drawLine(lineCol, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(lineCol, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }
        }

        if (isPrivate && settings.biometricLockForPrivateTabs && !privateUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(amber.copy(alpha = 0.15f))
                        .border(1.dp, amber, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🔒", fontSize = 28.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Private Browsing Locked",
                    color = ink,
                    fontFamily = TahoBody,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Biometric authentication is required to access ephemeral private sessions.",
                    color = mute,
                    fontFamily = TahoMono,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                M7PrimaryButton(
                    label = "Unlock Session",
                    showArrow = true,
                    onClick = { privateUnlocked = true },
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))

            // Brand Header / Crest
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(amber.copy(alpha = 0.15f))
                            .border(1.dp, amber.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isPrivate) "◐" else "⬡",
                            color = amberHover,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isPrivate) "TAHO PRIVATE" else "TAHO BROWSER",
                            color = ink,
                            fontFamily = TahoBody,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = if (isPrivate) "Encrypted Ephemeral Engine" else "Zero-Trust Engineering Chrome",
                            color = if (isPrivate) amberHover else mute,
                            fontFamily = TahoMono,
                            fontSize = 9.sp,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StartHeaderIcon("⚙", "Settings", onOpenSettings)
                    StartHeaderIcon("✦", "Customize", { showCustomizeSheet = true })
                }
            }

            Spacer(Modifier.height(26.dp))

            // Private Mode Shield Banner (if in private tab)
            if (isPrivate) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoCardShape)
                        .background(Color(0xFF141009))
                        .border(1.dp, amber.copy(alpha = 0.35f), TahoCardShape)
                        .padding(16.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🛡", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Strict Private Isolation Active",
                                color = amberHover,
                                fontFamily = TahoBody,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "History, cookies, and cache are memory-only and immediately wiped when this tab closes. Biometric protection is enabled for credential vaults.",
                            color = mute,
                            fontFamily = TahoMono,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Central Branded Search Bar
            if (settings.showQuickSearchOnStartPage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoPillShape)
                        .background(Color(0xFF121216))
                        .border(1.dp, hairlineStrong, TahoPillShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = searchEngine.iconGlyph,
                            color = amberHover,
                            fontSize = 14.sp,
                            fontFamily = TahoMono,
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search with ${searchEngine.name} or enter URL",
                                    color = mute,
                                    fontFamily = TahoMono,
                                    fontSize = 11.5.sp,
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = ink,
                                    fontFamily = TahoMono,
                                    fontSize = 12.sp,
                                ),
                                cursorBrush = SolidColor(amber),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        val q = searchQuery.trim()
                                        if (q.isNotEmpty()) {
                                            val target = if (q.contains('.') && !q.contains(' ')) {
                                                if (q.startsWith("http://") || q.startsWith("https://")) q else "https://$q"
                                            } else {
                                                searchEngine.queryUrl.replace("%s", java.net.URLEncoder.encode(q, "UTF-8"))
                                            }
                                            onNavigate(target)
                                        }
                                    }
                                ),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "Go ↗",
                                color = amberHover,
                                fontFamily = TahoMono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable {
                                        val q = searchQuery.trim()
                                        if (q.isNotEmpty()) {
                                            val target = if (q.contains('.') && !q.contains(' ')) {
                                                if (q.startsWith("http://") || q.startsWith("https://")) q else "https://$q"
                                            } else {
                                                searchEngine.queryUrl.replace("%s", java.net.URLEncoder.encode(q, "UTF-8"))
                                            }
                                            onNavigate(target)
                                        }
                                    }
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Top Sites / Shortcuts Grid
            if (settings.showTopSitesOnStartPage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SHORTCUTS & TOP SITES",
                        color = mute,
                        fontFamily = TahoMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = "+ Add",
                        color = amberHover,
                        fontFamily = TahoMono,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable { showAddShortcutDialog = true }
                            .padding(4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    maxItemsInEachRow = 4,
                ) {
                    topSites.forEach { site ->
                        StartShortcutTile(
                            item = site,
                            onClick = { onNavigate(site.url) },
                            onTogglePin = { TahoBrowserStateStore.togglePinTopSite(site.id) },
                            onRemove = { TahoBrowserStateStore.removeTopSite(site.id) },
                        )
                    }
                }

                Spacer(Modifier.height(26.dp))
            }

            // Continue Browsing / Recent Tabs Section
            if (settings.showRecentTabsOnStartPage && recentTabs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "CONTINUE BROWSING",
                        color = mute,
                        fontFamily = TahoMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = "${recentTabs.size} open",
                        color = mute,
                        fontFamily = TahoMono,
                        fontSize = 9.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    recentTabs.take(5).forEach { tab ->
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .clip(TahoCardShape)
                                .background(panel)
                                .border(1.dp, hairline, TahoCardShape)
                                .clickable { onSelectTab(tab.id) }
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    text = tab.title?.takeIf { it.isNotBlank() } ?: "Blank Tab",
                                    color = ink,
                                    fontFamily = TahoMono,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = tab.location ?: "about:blank",
                                    color = mute,
                                    fontFamily = TahoMono,
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
            }

            // Recently Visited History Entries
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "RECENTLY VISITED",
                        color = mute,
                        fontFamily = TahoMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = "Clear",
                        color = mute,
                        fontFamily = TahoMono,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .clickable { TahoBrowserStateStore.clearAllHistory() }
                            .padding(4.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.take(4).forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(TahoBlockShape)
                                .background(panel)
                                .border(1.dp, hairline, TahoBlockShape)
                                .clickable { onNavigate(entry.url) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("⏱", fontSize = 12.sp, color = mute)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    color = ink,
                                    fontFamily = TahoMono,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = entry.url,
                                    color = mute,
                                    fontFamily = TahoMono,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Privacy & Protection Monitor Widget
            if (settings.showPrivacyStatsOnStartPage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoCardShape)
                        .background(Color(0xFF0D0D12))
                        .border(1.dp, hairline, TahoCardShape)
                        .padding(14.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "PRIVACY & SECURITY SHIELD",
                                color = amberHover,
                                fontFamily = TahoMono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(TahoBadgeShape)
                                    .background(ok.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("PROTECTED", color = ok, fontFamily = TahoMono, fontSize = 8.5.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ShieldMetric("Mode", settings.trackingProtectionLevel.name)
                            ShieldMetric("DoH", settings.secureDns.name)
                            ShieldMetric("HTTPS-Only", if (settings.httpsOnlyMode) "Enforced" else "Off")
                            ShieldMetric("Cookies", if (settings.cookiePolicy == TahoCookiePolicy.BLOCK_THIRD_PARTY) "3rd Blocked" else "Strict")
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp)) // Leave space above bottom omnibox
        }
    }

    // Modal Sheet: Start Page Customization
    if (showCustomizeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCustomizeSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = bone,
            contentColor = ink,
            shape = TahoSheetShape,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Customize Start Page",
                    color = ink,
                    fontFamily = TahoBody,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(14.dp))

                Text("Background Style", color = mute, fontFamily = TahoMono, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("AMOLED_BLACK" to "AMOLED", "CARBON_GRID" to "Carbon", "DEEP_NAVY" to "Navy", "GOLD_RADIAL" to "Gold").forEach { (key, label) ->
                        val selected = settings.startPageBackground == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(TahoPillShape)
                                .background(if (selected) amber else bone)
                                .border(1.dp, if (selected) amberHover else hairline, TahoPillShape)
                                .clickable {
                                    TahoBrowserStateStore.updateSettings { it.copy(startPageBackground = key) }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) canvas else ink,
                                fontFamily = TahoMono,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("Widgets", color = mute, fontFamily = TahoMono, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))

                StartWidgetToggle("Search Bar", settings.showQuickSearchOnStartPage) {
                    TahoBrowserStateStore.updateSettings { it.copy(showQuickSearchOnStartPage = !it.showQuickSearchOnStartPage) }
                }
                StartWidgetToggle("Top Sites & Shortcuts", settings.showTopSitesOnStartPage) {
                    TahoBrowserStateStore.updateSettings { it.copy(showTopSitesOnStartPage = !it.showTopSitesOnStartPage) }
                }
                StartWidgetToggle("Continue Browsing Tabs", settings.showRecentTabsOnStartPage) {
                    TahoBrowserStateStore.updateSettings { it.copy(showRecentTabsOnStartPage = !it.showRecentTabsOnStartPage) }
                }
                StartWidgetToggle("Privacy Shield Monitor", settings.showPrivacyStatsOnStartPage) {
                    TahoBrowserStateStore.updateSettings { it.copy(showPrivacyStatsOnStartPage = !it.showPrivacyStatsOnStartPage) }
                }

                Spacer(Modifier.height(16.dp))
                M7PrimaryButton(label = "Done", modifier = Modifier.fillMaxWidth()) {
                    showCustomizeSheet = false
                }
            }
        }
    }

    // Modal Sheet: Add Shortcut
    if (showAddShortcutDialog) {
        ModalBottomSheet(
            onDismissRequest = { showAddShortcutDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = bone,
            contentColor = ink,
            shape = TahoSheetShape,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Add Shortcut",
                    color = ink,
                    fontFamily = TahoBody,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(14.dp))

                Text("Title", color = mute, fontFamily = TahoMono, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = newShortcutTitle,
                    onValueChange = { newShortcutTitle = it },
                    textStyle = TextStyle(color = ink, fontFamily = TahoMono, fontSize = 12.sp),
                    cursorBrush = SolidColor(amber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(bone)
                        .border(1.dp, hairline, TahoBlockShape)
                        .padding(12.dp),
                )

                Spacer(Modifier.height(12.dp))
                Text("URL", color = mute, fontFamily = TahoMono, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = newShortcutUrl,
                    onValueChange = { newShortcutUrl = it },
                    textStyle = TextStyle(color = ink, fontFamily = TahoMono, fontSize = 12.sp),
                    cursorBrush = SolidColor(amber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(bone)
                        .border(1.dp, hairline, TahoBlockShape)
                        .padding(12.dp),
                )

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    M7SecondaryButton("Cancel", Modifier.weight(1f)) {
                        showAddShortcutDialog = false
                    }
                    M7PrimaryButton(
                        label = "Save",
                        showArrow = false,
                        modifier = Modifier.weight(1f),
                        enabled = newShortcutTitle.isNotBlank() && newShortcutUrl.isNotBlank(),
                        onClick = {
                            val url = if (newShortcutUrl.startsWith("http://") || newShortcutUrl.startsWith("https://")) {
                                newShortcutUrl.trim()
                            } else {
                                "https://" + newShortcutUrl.trim()
                            }
                            TahoBrowserStateStore.addTopSite(newShortcutTitle.trim(), url, isPinned = true)
                            newShortcutTitle = ""
                            newShortcutUrl = ""
                            showAddShortcutDialog = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StartHeaderIcon(glyph: String, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(38.dp)
            .tahoPressScale(interaction)
            .clip(RoundedCornerShape(10.dp))
            .background(bone)
            .border(1.dp, hairline, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = mute, fontSize = 14.sp)
    }
}

/**
 * A shortcut must be navigable, re-pin-able and removable.
 *
 * `onTogglePin` and `onRemove` used to be declared here and never referenced, so
 * `TahoBrowserStateStore.togglePinTopSite` and `.removeTopSite` — both passed as
 * live lambdas at the call site — were unreachable, and a shortcut could be
 * added but never taken off the page.
 *
 * Two affordances, because one is not enough: a long press toggles the pin, and
 * a visible Remove control takes the shortcut off the page outright. The long
 * press carries an accessibility label and the pin state is part of the tile's
 * announced name, so a screen reader learns both the state and what the gesture
 * will do.
 */
@Composable
private fun StartShortcutTile(
    item: TopSiteItem,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = Modifier.width(72.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tahoPressScale(interaction)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClickLabel = if (item.isPinned) {
                        "Unpin ${item.title}"
                    } else {
                        "Pin ${item.title}"
                    },
                    onLongClick = onTogglePin,
                )
                // A clickable merges its descendants, and contentDescription is
                // parent-preferred on merge, so this label survives the merge
                // and the glyph/title Texts cannot clobber it.
                .semantics {
                    contentDescription = buildString {
                        append(item.title)
                        if (item.isPinned) append(", pinned")
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141418))
                    .border(1.dp, if (item.isPinned) amber.copy(alpha = 0.45f) else hairline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.iconGlyph,
                    color = if (item.isPinned) amberHover else ink,
                    fontFamily = TahoMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                if (item.isPinned) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(amber)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title,
                color = mute,
                fontFamily = TahoMono,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        // Visible dismissal, so removal is not gesture-only. It gets its own
        // 48dp band rather than overlaying the glyph: the tile is 72dp wide and
        // 52dp of that is the glyph box, so two 48dp targets cannot coexist
        // inside it, and an overlay would make the tile's own tap ambiguous.
        //
        // It is a *sibling* of the tile, not a child, on purpose — a clickable
        // merges its descendant semantics, so a nested Remove control would be
        // folded into the tile's announced name and never exposed on its own.
        //
        // Rendered for pinned and unpinned tiles alike. Gating it on isPinned
        // would re-create the very defect this fixes: the long press unpinning
        // moves a tile into a state with no Remove at all, where the only way
        // back would be another long press to re-pin it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onRemove)
                .semantics {
                    role = Role.Button
                    contentDescription = "Remove ${item.title}"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("× Remove", color = mute, fontFamily = TahoMono, fontSize = 9.sp)
        }
    }
}

@Composable
private fun StartWidgetToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TahoBlockShape)
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ink, fontFamily = TahoMono, fontSize = 11.sp)
        Box(
            modifier = Modifier
                .clip(TahoPillShape)
                .background(if (checked) amber else bone)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                color = if (checked) canvas else mute,
                fontFamily = TahoMono,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ShieldMetric(label: String, value: String) {
    Column {
        Text(label, color = mute, fontFamily = TahoMono, fontSize = 8.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = ink, fontFamily = TahoMono, fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
    }
}
