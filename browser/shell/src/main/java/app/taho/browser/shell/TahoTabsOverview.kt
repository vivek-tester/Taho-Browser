package app.taho.browser.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TahoTabsOverviewSheet(
    tabs: List<BrowserTabUiState>,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onCloseOtherTabs: (String) -> Unit = {},
    onCloseAllTabs: () -> Unit = {},
    onDuplicateTab: (String) -> Unit = {},
    onRestoreClosedTab: (String) -> Unit = {},
    onCloseOverview: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedGroupFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showRecentlyClosedSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateGroupSheet by rememberSaveable { mutableStateOf(false) }
    var showArchiveSheet by rememberSaveable { mutableStateOf(false) }
    var tabToMoveGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var newGroupName by rememberSaveable { mutableStateOf("") }

    val tabGroups = TahoBrowserStateStore.tabGroups
    val recentlyClosed = TahoBrowserStateStore.recentlyClosedTabs
    val archivedTabs = TahoBrowserStateStore.archivedTabs

    BackHandler(
        enabled = showRecentlyClosedSheet || showCreateGroupSheet || showArchiveSheet || tabToMoveGroup != null
    ) {
        showRecentlyClosedSheet = false
        showCreateGroupSheet = false
        showArchiveSheet = false
        tabToMoveGroup = null
    }

    val filteredTabs = tabs
        .sortedWith(compareByDescending<BrowserTabUiState> { it.id in TahoBrowserStateStore.pinnedTabIds })
        .filter { tab ->
        val queryMatch = searchQuery.isBlank() ||
            (tab.title?.contains(searchQuery, ignoreCase = true) == true) ||
            (tab.location?.contains(searchQuery, ignoreCase = true) == true)
        val groupMatch = if (selectedGroupFilter == null) {
            true
        } else {
            tabGroups.find { it.id == selectedGroupFilter }?.tabIds?.contains(tab.id) == true
        }
        queryMatch && groupMatch
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Tabs Overview",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                )
                Text(
                    text = "${tabs.size} open ${if (tabs.size == 1) "tab" else "tabs"}",
                    color = TahoMuted,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(TahoPillShape)
                        .background(TahoGold.copy(alpha = 0.15f))
                        .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoPillShape)
                        .clickable(onClick = onNewTab)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+ New", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(TahoPillShape)
                        .background(TahoSurfaceControl)
                        .border(1.dp, TahoHairline, TahoPillShape)
                        .clickable(onClick = onNewPrivateTab)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("◐ Private", color = TahoText, fontFamily = TahoMono, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Tab Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoPillShape)
                .background(TahoSurfaceControl)
                .border(1.dp, TahoHairline, TahoPillShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = TahoFaint, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("Search open tabs…", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        cursorBrush = SolidColor(TahoGold),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Text("×", color = TahoMuted, fontSize = 14.sp, modifier = Modifier.clickable { searchQuery = "" })
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Tab Group Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                TabGroupChip(
                    name = "All (${tabs.size})",
                    color = Color.White,
                    isSelected = selectedGroupFilter == null,
                    onClick = { selectedGroupFilter = null },
                )
            }
            items(tabGroups) { grp ->
                TabGroupChip(
                    name = grp.name,
                    color = Color(grp.colorHex),
                    isSelected = selectedGroupFilter == grp.id,
                    onClick = { selectedGroupFilter = grp.id },
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(TahoPillShape)
                        .background(TahoSurfaceControl)
                        .border(1.dp, TahoHairline, TahoPillShape)
                        .clickable { showCreateGroupSheet = true }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+ Group", color = TahoMuted, fontFamily = TahoMono, fontSize = 9.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Secondary controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Recently Closed (${recentlyClosed.size})",
                    color = TahoGoldHi,
                    fontFamily = TahoMono,
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .clickable { showRecentlyClosedSheet = true }
                        .padding(vertical = 4.dp),
                )
                Text(
                    text = "Archive (${archivedTabs.size})",
                    color = TahoMuted,
                    fontFamily = TahoMono,
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .clickable { showArchiveSheet = true }
                        .padding(vertical = 4.dp),
                )
            }

            if (tabs.size > 1) {
                Text(
                    text = "Close All Tabs",
                    color = TahoError.copy(alpha = 0.85f),
                    fontFamily = TahoMono,
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .clickable { onCloseAllTabs() }
                        .padding(vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tab List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filteredTabs, key = { it.id }) { tab ->
                val grp = tabGroups.find { it.tabIds.contains(tab.id) }
                val pinned = tab.id in TahoBrowserStateStore.pinnedTabIds
                DetailedTabCard(
                    tab = tab,
                    group = grp,
                    isPinned = pinned,
                    onSelect = { onSelectTab(tab.id) },
                    onClose = { onCloseTab(tab.id) },
                    onDuplicate = { onDuplicateTab(tab.id) },
                    onCloseOthers = { onCloseOtherTabs(tab.id) },
                    onMoveToGroup = { tabToMoveGroup = tab.id },
                    onArchive = {
                        TahoBrowserStateStore.unpinTab(tab.id)
                        TahoBrowserStateStore.archiveTab(tab.title ?: tab.location ?: "Tab", tab.location ?: "about:blank")
                        onCloseTab(tab.id)
                    },
                    onTogglePin = {
                        TahoBrowserStateStore.togglePinnedTab(tab.id, tab.isPrivate)
                    },
                    canCloseOthers = tabs.size > 1,
                )
            }
        }
    }

    // Modal Sheet: Recently Closed Tabs
    if (showRecentlyClosedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRecentlyClosedSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = TahoSheet,
            contentColor = TahoText,
            shape = TahoSheetShape,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Recently Closed Tabs",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(12.dp))

                if (recentlyClosed.isEmpty()) {
                    Text("No recently closed tabs.", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(recentlyClosed) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(TahoBlockShape)
                                    .background(TahoSurfaceRow)
                                    .border(1.dp, TahoHairline, TahoBlockShape)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = TahoText,
                                        fontFamily = TahoMono,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.url,
                                        color = TahoFaint,
                                        fontFamily = TahoMono,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(TahoPillShape)
                                        .background(TahoGold.copy(alpha = 0.2f))
                                        .clickable {
                                            TahoBrowserStateStore.restoreClosedTab(item.id)
                                            onRestoreClosedTab(item.url)
                                            showRecentlyClosedSheet = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text("Restore", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet: Create Tab Group
    if (showCreateGroupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateGroupSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = TahoSheet,
            contentColor = TahoText,
            shape = TahoSheetShape,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "New Tab Group",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 12.sp),
                    cursorBrush = SolidColor(TahoGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceControl)
                        .border(1.dp, TahoHairline, TahoBlockShape)
                        .padding(12.dp),
                )
                Spacer(Modifier.height(16.dp))
                M7PrimaryButton(
                    label = "Create Group",
                    showArrow = false,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = newGroupName.isNotBlank(),
                    onClick = {
                        val newGrp = TabGroupUi(
                            id = java.util.UUID.randomUUID().toString(),
                            name = newGroupName.trim(),
                            colorHex = 0xFF4FBFA3,
                        )
                        TahoBrowserStateStore.tabGroups = TahoBrowserStateStore.tabGroups + newGrp
                        newGroupName = ""
                        showCreateGroupSheet = false
                    },
                )
            }
        }
    }

    // Modal Sheet: Tab Archive
    if (showArchiveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showArchiveSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = TahoSheet,
            contentColor = TahoText,
            shape = TahoSheetShape,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Tab Archive",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(12.dp))

                if (archivedTabs.isEmpty()) {
                    Text("No archived tabs.", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(archivedTabs) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(TahoBlockShape)
                                    .background(TahoSurfaceRow)
                                    .border(1.dp, TahoHairline, TahoBlockShape)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = TahoText,
                                        fontFamily = TahoMono,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.url,
                                        color = TahoFaint,
                                        fontFamily = TahoMono,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(TahoPillShape)
                                        .background(TahoGold.copy(alpha = 0.2f))
                                        .clickable {
                                            TahoBrowserStateStore.restoreArchivedTab(item.id)
                                            onRestoreClosedTab(item.url)
                                            showArchiveSheet = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text("Restore", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet: Move Tab Between Groups
    tabToMoveGroup?.let { targetTabId ->
        ModalBottomSheet(
            onDismissRequest = { tabToMoveGroup = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = TahoSheet,
            contentColor = TahoText,
            shape = TahoSheetShape,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Assign to Tab Group",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceControl)
                        .clickable {
                            TahoBrowserStateStore.tabGroups = tabGroups.map { grp ->
                                grp.copy(tabIds = grp.tabIds.filterNot { it == targetTabId })
                            }
                            tabToMoveGroup = null
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✕ Remove from All Groups", color = TahoMuted, fontFamily = TahoMono, fontSize = 11.sp)
                }

                Spacer(Modifier.height(8.dp))

                tabGroups.forEach { grp ->
                    val isMember = grp.tabIds.contains(targetTabId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TahoBlockShape)
                            .background(if (isMember) TahoSurfaceRowHover else TahoSurfaceRow)
                            .border(1.dp, if (isMember) Color(grp.colorHex) else TahoHairline, TahoBlockShape)
                            .clickable {
                                TahoBrowserStateStore.tabGroups = tabGroups.map { g ->
                                    if (g.id == grp.id) {
                                        g.copy(tabIds = if (isMember) g.tabIds else g.tabIds + targetTabId)
                                    } else {
                                        g.copy(tabIds = g.tabIds.filterNot { it == targetTabId })
                                    }
                                }
                                tabToMoveGroup = null
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(grp.colorHex))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(grp.name, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp)
                        }
                        if (isMember) {
                            Text("✓ In Group", color = Color(grp.colorHex), fontFamily = TahoMono, fontSize = 9.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun TabGroupChip(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(TahoPillShape)
            .background(if (isSelected) color.copy(alpha = 0.2f) else TahoSurfaceControl)
            .border(1.dp, if (isSelected) color else TahoHairline, TahoPillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                color = if (isSelected) color else TahoMuted,
                fontFamily = TahoMono,
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DetailedTabCard(
    tab: BrowserTabUiState,
    group: TabGroupUi?,
    isPinned: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    onCloseOthers: () -> Unit,
    onMoveToGroup: () -> Unit,
    onArchive: () -> Unit,
    onTogglePin: () -> Unit,
    canCloseOthers: Boolean,
) {
    val borderColor = if (tab.selected) TahoGold.copy(alpha = 0.55f) else TahoHairline
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TahoCardShape)
            .background(if (tab.selected) TahoSurfaceRowHover else TahoSurfaceRow)
            .border(1.dp, borderColor, TahoCardShape)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interaction, indication = null, onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (group != null) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(group.colorHex))
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (isPinned) {
                        Box(
                            modifier = Modifier
                                .clip(TahoBadgeShape)
                                .background(TahoInfo.copy(alpha = 0.18f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("PINNED", color = TahoInfo, fontFamily = TahoMono, fontSize = 7.5.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (tab.isPrivate) {
                        Box(
                            modifier = Modifier
                                .clip(TahoBadgeShape)
                                .background(TahoGold.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("PRIVATE", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 7.5.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = tab.title?.takeIf { it.isNotBlank() } ?: (tab.location ?: "New tab"),
                        color = TahoText,
                        fontFamily = TahoMono,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.location ?: "about:blank",
                    color = TahoFaint,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(TahoPillShape)
                    .clickable {
                        if (isPinned) onTogglePin()
                        onClose()
                    }
                    .semantics { role = Role.Button; contentDescription = "Close tab" },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = TahoMuted, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action Buttons Row: Duplicate, Move to Group, Archive, Close Others
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!tab.isPrivate) {
                    TabActionButton(
                        label = if (isPinned) "📌 Unpin" else "📌 Pin",
                        color = if (isPinned) TahoInfo else TahoMuted,
                        onClick = onTogglePin,
                    )
                }
                TabActionButton(label = "⧉ Duplicate", onClick = onDuplicate)
                TabActionButton(
                    label = if (group != null) "📁 ${group.name.take(10)}" else "📁 Group",
                    onClick = onMoveToGroup
                )
                TabActionButton(label = "📥 Archive", onClick = onArchive)
            }
            if (canCloseOthers) {
                TabActionButton(label = "Close Others", color = TahoError, onClick = onCloseOthers)
            }
        }
    }
}

@Composable
private fun TabActionButton(
    label: String,
    color: Color = TahoMuted,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(TahoPillShape)
            .background(TahoSurfaceControl)
            .border(1.dp, TahoHairline, TahoPillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontFamily = TahoMono,
            fontSize = 8.5.sp,
            maxLines = 1,
        )
    }
}

