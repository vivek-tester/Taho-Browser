package app.taho.browser.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

data class BrowserUpdateStatusUi(
    val latestVersion: String?,
    val releaseUrl: String?,
    val updateAvailable: Boolean,
    val error: String? = null,
)

enum class SettingsSubPage {
    MAIN,
    APPEARANCE,
    SEARCH_ENGINE,
    PRIVACY_SECURITY,
    CLEAR_DATA,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    PASSWORDS,
    AUTOFILL,
    PROFILES_SYNC,
    EXTENSIONS,
    ACCESSIBILITY,
    LANGUAGES,
    DEFAULT_BROWSER,
    PERFORMANCE_MEDIA,
    STORAGE_USAGE,
    DIAGNOSTICS,
    BACKUP_EXPORT,
    COLLECTIONS,
    ONBOARDING,
    WHATS_NEW,
    ABOUT,
}

@Composable
fun TahoSettingsHubSheet(
    initialSubPage: SettingsSubPage = SettingsSubPage.MAIN,
    onNavigateUrl: (String) -> Unit = {},
    onClearEngineData: (Boolean, Boolean, (Boolean) -> Unit) -> Unit =
        { _, _, callback -> callback(true) },
    onAuthenticateSensitive: (String, (Boolean) -> Unit) -> Unit =
        { _, callback -> callback(false) },
    onCheckPasswordBreach: (String, (Int?) -> Unit) -> Unit =
        { _, callback -> callback(null) },
    onDownloadPauseResume: (String) -> Unit = {},
    onDownloadCancel: (String) -> Unit = {},
    onDownloadRetry: (DownloadItemUi) -> Unit = {},
    onDownloadOpen: (DownloadItemUi) -> Unit = {},
    onDownloadDelete: (DownloadItemUi) -> Unit = {},
    onOpenOfflinePage: (OfflinePageUi) -> Unit = {},
    onDeleteOfflinePage: (OfflinePageUi) -> Unit = {},
    onRefreshExtensions: () -> Unit = {},
    onInstallExtension: (String) -> Unit = {},
    onSetExtensionEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onSetExtensionPrivate: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateExtension: (String) -> Unit = {},
    onUninstallExtension: (String) -> Unit = {},
    onSwitchProfile: (String) -> Unit = {},
    onCreateLocalProfile: (String) -> Unit = {},
    onSearchExtensionMarketplace: (
        String,
        (List<ExtensionMarketplaceItemUi>, String?) -> Unit,
    ) -> Unit = { _, callback -> callback(emptyList(), "Marketplace search is unavailable.") },
    onExportFullBackup: (Uri, String, (Boolean, String) -> Unit) -> Unit =
        { _, _, callback -> callback(false, "Full backup export is unavailable.") },
    onRestoreFullBackup: (Uri, String, (Boolean, String) -> Unit) -> Unit =
        { _, _, callback -> callback(false, "Full backup restore is unavailable.") },
    onCheckForUpdates: ((BrowserUpdateStatusUi) -> Unit) -> Unit =
        { callback ->
            callback(
                BrowserUpdateStatusUi(
                    latestVersion = null,
                    releaseUrl = null,
                    updateAvailable = false,
                    error = "Update checking is unavailable.",
                ),
            )
        },
    onDismiss: () -> Unit,
) {
    var currentSubPage by rememberSaveable { mutableStateOf(initialSubPage) }
    val settings = TahoBrowserStateStore.settings
    val context = LocalContext.current

    BackHandler(enabled = currentSubPage != SettingsSubPage.MAIN) {
        currentSubPage = SettingsSubPage.MAIN
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentSubPage != SettingsSubPage.MAIN) {
                    TahoIconButton(
                        name = TahoIconName.BACK,
                        contentDescription = "Back to settings",
                        onClick = { currentSubPage = SettingsSubPage.MAIN },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = when (currentSubPage) {
                        SettingsSubPage.MAIN -> "Settings"
                        SettingsSubPage.APPEARANCE -> "Appearance"
                        SettingsSubPage.SEARCH_ENGINE -> "Search engines"
                        SettingsSubPage.PRIVACY_SECURITY -> "Privacy and protection"
                        SettingsSubPage.CLEAR_DATA -> "Clear browsing data"
                        SettingsSubPage.BOOKMARKS -> "Bookmarks and reading"
                        SettingsSubPage.HISTORY -> "Browsing history"
                        SettingsSubPage.DOWNLOADS -> "Downloads"
                        SettingsSubPage.PASSWORDS -> "Password vault"
                        SettingsSubPage.AUTOFILL -> "Autofill and payments"
                        SettingsSubPage.PROFILES_SYNC -> "Profiles and sync"
                        SettingsSubPage.EXTENSIONS -> "Extensions"
                        SettingsSubPage.ACCESSIBILITY -> "Accessibility"
                        SettingsSubPage.LANGUAGES -> "Languages and translation"
                        SettingsSubPage.DEFAULT_BROWSER -> "Default browser"
                        SettingsSubPage.PERFORMANCE_MEDIA -> "Performance and media"
                        SettingsSubPage.STORAGE_USAGE -> "Storage usage"
                        SettingsSubPage.DIAGNOSTICS -> "Diagnostics and reset"
                        SettingsSubPage.BACKUP_EXPORT -> "Backup and restore"
                        SettingsSubPage.COLLECTIONS -> "Collections"
                        SettingsSubPage.ONBOARDING -> "Browser setup"
                        SettingsSubPage.WHATS_NEW -> "What's new"
                        SettingsSubPage.ABOUT -> "About Taho Browser"
                    },
                    color = TahoText,
                    fontFamily = TahoSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TahoIconButton(
                name = TahoIconName.CLOSE,
                contentDescription = "Close settings",
                onClick = onDismiss,
            )
        }

        Spacer(Modifier.height(14.dp))

        // Content Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            when (currentSubPage) {
                SettingsSubPage.MAIN -> SettingsMainIndex(onNavigateSub = { currentSubPage = it })
                SettingsSubPage.APPEARANCE -> SettingsAppearancePage()
                SettingsSubPage.SEARCH_ENGINE -> SettingsSearchEnginePage()
                SettingsSubPage.PRIVACY_SECURITY -> SettingsPrivacySecurityPage()
                SettingsSubPage.CLEAR_DATA -> SettingsClearDataPage(
                    onClearEngineData = onClearEngineData,
                    onDone = { currentSubPage = SettingsSubPage.MAIN },
                )
                SettingsSubPage.BOOKMARKS -> SettingsBookmarksPage(
                    onNavigate = { onDismiss(); onNavigateUrl(it) },
                    onOpenOfflinePage = onOpenOfflinePage,
                    onDeleteOfflinePage = onDeleteOfflinePage,
                )
                SettingsSubPage.HISTORY -> SettingsHistoryPage(onNavigate = { onDismiss(); onNavigateUrl(it) })
                SettingsSubPage.DOWNLOADS -> SettingsDownloadsPage(
                    onPauseResume = onDownloadPauseResume,
                    onCancel = onDownloadCancel,
                    onRetry = onDownloadRetry,
                    onOpen = onDownloadOpen,
                    onDelete = onDownloadDelete,
                )
                SettingsSubPage.PASSWORDS -> SettingsPasswordsPage(
                    onAuthenticateSensitive = onAuthenticateSensitive,
                    onCheckPasswordBreach = onCheckPasswordBreach,
                )
                SettingsSubPage.AUTOFILL -> SettingsAutofillPage()
                SettingsSubPage.PROFILES_SYNC -> SettingsProfilesSyncPage(
                    onSwitchProfile = onSwitchProfile,
                    onCreateLocalProfile = onCreateLocalProfile,
                )
                SettingsSubPage.EXTENSIONS -> SettingsExtensionsPage(
                    onRefresh = onRefreshExtensions,
                    onInstall = onInstallExtension,
                    onSetEnabled = onSetExtensionEnabled,
                    onSetPrivate = onSetExtensionPrivate,
                    onUpdate = onUpdateExtension,
                    onUninstall = onUninstallExtension,
                    onSearchMarketplace = onSearchExtensionMarketplace,
                )
                SettingsSubPage.ACCESSIBILITY -> SettingsAccessibilityPage()
                SettingsSubPage.LANGUAGES -> SettingsLanguagesPage()
                SettingsSubPage.DEFAULT_BROWSER -> SettingsDefaultBrowserPage()
                SettingsSubPage.PERFORMANCE_MEDIA -> SettingsPerformanceMediaPage()
                SettingsSubPage.STORAGE_USAGE -> SettingsStorageUsagePage()
                SettingsSubPage.DIAGNOSTICS -> SettingsDiagnosticsPage()
                SettingsSubPage.BACKUP_EXPORT -> SettingsBackupExportPage(
                    onAuthenticateSensitive = onAuthenticateSensitive,
                    onExportFullBackup = onExportFullBackup,
                    onRestoreFullBackup = onRestoreFullBackup,
                )
                SettingsSubPage.COLLECTIONS -> SettingsCollectionsPage()
                SettingsSubPage.ONBOARDING -> SettingsOnboardingPage(onFinish = { currentSubPage = SettingsSubPage.MAIN })
                SettingsSubPage.WHATS_NEW -> SettingsWhatsNewPage()
                SettingsSubPage.ABOUT -> SettingsAboutPage(onCheckForUpdates = onCheckForUpdates)
            }
        }
    }
}

// -------------------------------------------------------------
// MAIN INDEX
// -------------------------------------------------------------
private data class SettingsIndexItem(
    val icon: TahoIconName,
    val title: String,
    val page: SettingsSubPage,
)

@Composable
private fun SettingsMainIndex(onNavigateSub: (SettingsSubPage) -> Unit) {
    var searchFilter by rememberSaveable { mutableStateOf("") }

    val items = listOf(
        SettingsIndexItem(TahoIconName.PALETTE, "Appearance", SettingsSubPage.APPEARANCE),
        SettingsIndexItem(TahoIconName.SEARCH, "Search engines", SettingsSubPage.SEARCH_ENGINE),
        SettingsIndexItem(TahoIconName.SECURITY, "Privacy and protection", SettingsSubPage.PRIVACY_SECURITY),
        SettingsIndexItem(TahoIconName.CLEAR_DATA, "Clear browsing data", SettingsSubPage.CLEAR_DATA),
        SettingsIndexItem(TahoIconName.BOOKMARK, "Bookmarks and reading", SettingsSubPage.BOOKMARKS),
        SettingsIndexItem(TahoIconName.HISTORY, "Browsing history", SettingsSubPage.HISTORY),
        SettingsIndexItem(TahoIconName.DOWNLOAD, "Downloads", SettingsSubPage.DOWNLOADS),
        SettingsIndexItem(TahoIconName.PASSWORD, "Password vault", SettingsSubPage.PASSWORDS),
        SettingsIndexItem(TahoIconName.CREDIT_CARD, "Autofill and payments", SettingsSubPage.AUTOFILL),
        SettingsIndexItem(TahoIconName.PERSON, "Profiles and sync", SettingsSubPage.PROFILES_SYNC),
        SettingsIndexItem(TahoIconName.EXTENSION, "Extensions", SettingsSubPage.EXTENSIONS),
        SettingsIndexItem(TahoIconName.ACCESSIBILITY, "Accessibility", SettingsSubPage.ACCESSIBILITY),
        SettingsIndexItem(TahoIconName.LANGUAGE, "Languages and translation", SettingsSubPage.LANGUAGES),
        SettingsIndexItem(TahoIconName.DESKTOP, "Default browser", SettingsSubPage.DEFAULT_BROWSER),
        SettingsIndexItem(TahoIconName.TUNE, "Performance and media", SettingsSubPage.PERFORMANCE_MEDIA),
        SettingsIndexItem(TahoIconName.STORAGE, "Storage usage", SettingsSubPage.STORAGE_USAGE),
        SettingsIndexItem(TahoIconName.BACKUP, "Backup and restore", SettingsSubPage.BACKUP_EXPORT),
        SettingsIndexItem(TahoIconName.FOLDER, "Collections", SettingsSubPage.COLLECTIONS),
        SettingsIndexItem(TahoIconName.DIAGNOSTICS, "Diagnostics and reset", SettingsSubPage.DIAGNOSTICS),
        SettingsIndexItem(TahoIconName.ROCKET, "Browser setup", SettingsSubPage.ONBOARDING),
        SettingsIndexItem(TahoIconName.UPDATE, "What's new", SettingsSubPage.WHATS_NEW),
        SettingsIndexItem(TahoIconName.INFO, "About Taho Browser", SettingsSubPage.ABOUT),
    )

    val filtered = items.filter {
        searchFilter.isBlank() || it.title.contains(searchFilter, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(TahoPillShape)
                .background(TahoSheet)
                .border(1.dp, TahoLine, TahoPillShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TahoIcon(
                name = TahoIconName.SEARCH,
                contentDescription = null,
                tint = TahoMuted,
                size = 20.dp,
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchFilter.isEmpty()) {
                    Text(
                        text = "Search settings",
                        color = TahoFaint,
                        fontFamily = TahoSans,
                        fontSize = 14.sp,
                    )
                }
                BasicTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TahoText,
                        fontFamily = TahoSans,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(TahoGold),
                )
            }
            if (searchFilter.isNotEmpty()) {
                TahoIconButton(
                    name = TahoIconName.CLOSE,
                    contentDescription = "Clear settings search",
                    onClick = { searchFilter = "" },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(filtered) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onNavigateSub(item.page) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TahoIcon(
                        name = item.icon,
                        contentDescription = null,
                        tint = TahoMuted,
                        size = 20.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        color = TahoText,
                        fontFamily = TahoSans,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TahoIcon(
                        name = TahoIconName.CHEVRON_RIGHT,
                        contentDescription = null,
                        tint = TahoMuted,
                        size = 20.dp,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TahoLine),
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 1. APPEARANCE & THEME
// -------------------------------------------------------------
@Composable
private fun SettingsAppearancePage() {
    val settings = TahoBrowserStateStore.settings
    Column(
        modifier = Modifier
            .heightIn(min = TahoTouchTarget)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Theme mode")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TahoThemeMode.DARK to "Dark (OLED)", TahoThemeMode.LIGHT to "Light", TahoThemeMode.SYSTEM to "System").forEach { (m, label) ->
                val sel = settings.themeMode == m
                TahoChoiceChip(
                    label = label,
                    selected = sel,
                    modifier = Modifier.weight(1f),
                    description = "$label theme" + if (sel) ", selected" else "",
                    onClick = { TahoBrowserStateStore.updateSettings { it.copy(themeMode = m) } },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Browser accent color")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                0xFFE2B44A to "Taho Gold",
                0xFF4FBFA3 to "Cyan Cyber",
                0xFF5FBF8A to "Emerald",
                0xFFE06A5A to "Coral",
                0xFFC9B2F0 to "Lavender",
            ).forEach { (hex, name) ->
                val sel = settings.accentColorHex == hex
                Box(
                    modifier = Modifier
                        .size(TahoCompactTouchTarget)
                        .clip(CircleShape)
                        .background(Color(hex))
                        .border(2.dp, if (sel) TahoText else Color.Transparent, CircleShape)
                        .clickable { TahoBrowserStateStore.updateSettings { it.copy(accentColorHex = hex) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel) Text("✓", color = TahoPrimaryInk, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Toolbar & address bar position")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TahoToolbarPosition.BOTTOM to "Bottom (One-Handed)", TahoToolbarPosition.TOP to "Top (Classic)").forEach { (pos, label) ->
                val sel = settings.toolbarPosition == pos
                TahoChoiceChip(
                    label = label,
                    selected = sel,
                    modifier = Modifier.weight(1f),
                    description = "$label toolbar position" + if (sel) ", selected" else "",
                    onClick = { TahoBrowserStateStore.updateSettings { it.copy(toolbarPosition = pos) } },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Homepage customization")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TahoHomePageMode.START_PAGE to "Default Start Page", TahoHomePageMode.CUSTOM_URL to "Custom URL").forEach { (mode, label) ->
                val sel = settings.homePageMode == mode
                TahoChoiceChip(
                    label = label,
                    selected = sel,
                    modifier = Modifier.weight(1f),
                    description = "$label homepage mode" + if (sel) ", selected" else "",
                    onClick = { TahoBrowserStateStore.updateSettings { it.copy(homePageMode = mode) } },
                )
            }
        }

        if (settings.homePageMode == TahoHomePageMode.CUSTOM_URL) {
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = settings.customHomePageUrl,
                onValueChange = { url -> TahoBrowserStateStore.updateSettings { it.copy(customHomePageUrl = url) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceControl)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
                textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp),
                cursorBrush = SolidColor(TahoGold),
            )
        }
    }
}

// -------------------------------------------------------------
// 2. SEARCH ENGINES
// -------------------------------------------------------------
@Composable
private fun SettingsSearchEnginePage() {
    val engines = TahoBrowserStateStore.searchEngines
    val settings = TahoBrowserStateStore.settings
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newEngineName by rememberSaveable { mutableStateOf("") }
    var newEngineQueryUrl by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .heightIn(min = TahoTouchTarget)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionTitle("Default search engine")
            TahoInlineAction(
                label = "Add engine",
                icon = TahoIconName.ADD,
                onClick = { showAddDialog = true },
            )
        }

        engines.forEach { engine ->
            val sel = settings.defaultSearchEngineId == engine.id
            Row(
                modifier = Modifier
                    .heightIn(min = TahoTouchTarget)
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(if (sel) TahoSurfaceRowHover else TahoSurfaceRow)
                    .border(1.dp, if (sel) TahoGold else TahoHairline, TahoBlockShape)
                    .clickable { TahoBrowserStateStore.updateSettings { it.copy(defaultSearchEngineId = engine.id) } }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(engine.iconGlyph, color = TahoGoldHi, fontFamily = TahoSans, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(engine.name, color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp)
                        Text(engine.queryUrl, color = TahoFaint, fontFamily = TahoSans, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sel) {
                        Text("✓ Active", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (engines.size > 1 && !sel) {
                        Spacer(Modifier.width(10.dp))
                        TahoIconButton(
                            name = TahoIconName.DELETE,
                            contentDescription = "Remove ${engine.name} search engine",
                            destructive = true,
                            onClick = { TahoBrowserStateStore.removeSearchEngine(engine.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (showAddDialog) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold, TahoCardShape)
                    .padding(14.dp)
            ) {
                Text("Add Custom Search Engine", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = newEngineName,
                    onValueChange = { newEngineName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceControl)
                        .padding(10.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                    decorationBox = { innerTextField ->
                        if (newEngineName.isEmpty()) Text("Engine Name (e.g. SearXNG)", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                        innerTextField()
                    }
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = newEngineQueryUrl,
                    onValueChange = { newEngineQueryUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceControl)
                        .padding(10.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                    decorationBox = { innerTextField ->
                        if (newEngineQueryUrl.isEmpty()) Text("URL with %s (e.g. https://searx.org/search?q=%s)", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                        innerTextField()
                    }
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    M7SecondaryButton("Cancel", Modifier.weight(1f)) {
                        showAddDialog = false
                    }
                    M7PrimaryButton("Add Engine", showArrow = false, modifier = Modifier.weight(1f), enabled = newEngineName.isNotBlank() && newEngineQueryUrl.isNotBlank()) {
                        TahoBrowserStateStore.addSearchEngine(newEngineName.trim(), newEngineQueryUrl.trim())
                        newEngineName = ""
                        newEngineQueryUrl = ""
                        showAddDialog = false
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Search suggestions & autocomplete")
        SettingsToggleRow("Search Suggestions", "Provide real-time query suggestions", settings.searchSuggestionsEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(searchSuggestionsEnabled = !it.searchSuggestionsEnabled) }
        }
        SettingsToggleRow("Address Bar Suggestions", "Show history & bookmark matches while typing", settings.addressBarSuggestionsEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(addressBarSuggestionsEnabled = !it.addressBarSuggestionsEnabled) }
        }
        SettingsToggleRow("Autocomplete", "Automatically fill completed URLs", settings.autocompleteEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(autocompleteEnabled = !it.autocompleteEnabled) }
        }
    }
}

// -------------------------------------------------------------
// 3. PRIVACY & SECURITY
// -------------------------------------------------------------
@Composable
private fun SettingsPrivacySecurityPage() {
    val settings = TahoBrowserStateStore.settings
    Column(
        modifier = Modifier
            .heightIn(min = TahoTouchTarget)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Tracking protection level")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TahoTrackingProtectionLevel.STANDARD to "Standard",
                TahoTrackingProtectionLevel.STRICT to "Strict (Recommended)",
                TahoTrackingProtectionLevel.CUSTOM to "Custom",
            ).forEach { (level, label) ->
                val sel = settings.trackingProtectionLevel == level
                TahoChoiceChip(
                    label = label,
                    selected = sel,
                    modifier = Modifier.weight(1f),
                    description = "$label tracking protection" + if (sel) ", selected" else "",
                    onClick = { TahoBrowserStateStore.updateSettings { it.copy(trackingProtectionLevel = level) } },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Protection guards")
        SettingsToggleRow("Cross-Site Tracker Blocking", "Block known ad and telemetry trackers", settings.blockTrackers) {
            TahoBrowserStateStore.updateSettings { it.copy(blockTrackers = !it.blockTrackers) }
        }
        SettingsToggleRow("Third-Party Cookie Isolation", "Prevent multi-site identity graphing", settings.blockThirdPartyCookies) {
            TahoBrowserStateStore.updateSettings { it.copy(blockThirdPartyCookies = !it.blockThirdPartyCookies) }
        }
        SettingsToggleRow("Fingerprinting Protection", "Mask canvas, audio & hardware indicators", settings.fingerprintingProtection) {
            TahoBrowserStateStore.updateSettings { it.copy(fingerprintingProtection = !it.fingerprintingProtection) }
        }
        SettingsToggleRow("Cryptomining Defense", "Terminate in-browser mining scripts", settings.cryptominingProtection) {
            TahoBrowserStateStore.updateSettings { it.copy(cryptominingProtection = !it.cryptominingProtection) }
        }
        SettingsToggleRow("HTTPS-Only Mode", "Enforce encrypted transport across all hosts", settings.httpsOnlyMode) {
            TahoBrowserStateStore.updateSettings { it.copy(httpsOnlyMode = !it.httpsOnlyMode) }
        }
        SettingsToggleRow("Safe Browsing & Phishing Shield", "Block malware & dangerous downloads", settings.safeBrowsingEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(safeBrowsingEnabled = !it.safeBrowsingEnabled) }
        }
        SettingsToggleRow("Pop-Up & Redirect Blocker", "Suppress intrusive modals & redirect loops", settings.popupBlockerEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(popupBlockerEnabled = !it.popupBlockerEnabled) }
        }
        SettingsToggleRow("Do Not Track (DNT) Header", "Transmit RFC DNT=1 signal", settings.doNotTrack) {
            TahoBrowserStateStore.updateSettings { it.copy(doNotTrack = !it.doNotTrack) }
        }
        SettingsToggleRow("Global Privacy Control (GPC)", "Transmit Sec-GPC: 1 opt-out", settings.globalPrivacyControl) {
            TahoBrowserStateStore.updateSettings { it.copy(globalPrivacyControl = !it.globalPrivacyControl) }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Secure DNS (dns-over-https)")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(TahoSecureDns.CLOUDFLARE to "Cloudflare", TahoSecureDns.QUAD9 to "Quad9", TahoSecureDns.GOOGLE to "Google", TahoSecureDns.OFF to "System").forEach { (dns, label) ->
                val sel = settings.secureDns == dns
                TahoChoiceChip(
                    label = label,
                    selected = sel,
                    modifier = Modifier.weight(1f),
                    description = "$label secure DNS" + if (sel) ", selected" else "",
                    onClick = { TahoBrowserStateStore.updateSettings { it.copy(secureDns = dns) } },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Private browsing guards")
        SettingsToggleRow("Private Tab PIN / Biometric Lock", "Require authentication when switching to private session", settings.privateTabLock) {
            TahoBrowserStateStore.updateSettings { it.copy(privateTabLock = !it.privateTabLock) }
        }
        SettingsToggleRow("Biometric Lock for Private Tabs", "Unlock private sessions using fingerprint or device biometrics", settings.biometricLockForPrivateTabs) {
            TahoBrowserStateStore.updateSettings { it.copy(biometricLockForPrivateTabs = !it.biometricLockForPrivateTabs) }
        }
        SettingsToggleRow("Clear Private Tabs on Exit", "Automatically purge all private tabs and session cookies on close", settings.clearPrivateTabsOnExit) {
            TahoBrowserStateStore.updateSettings { it.copy(clearPrivateTabsOnExit = !it.clearPrivateTabsOnExit) }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Tracking protection exceptions (${settings.perSiteTrackingExceptions.size})")
        if (settings.perSiteTrackingExceptions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
            ) {
                Text("No tracking exceptions active. Zero-trust protection is enforced across all web origins.", color = TahoFaint, fontFamily = TahoSans, fontSize = 10.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                settings.perSiteTrackingExceptions.forEach { domain ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TahoBlockShape)
                            .background(TahoSurfaceRow)
                            .border(1.dp, TahoHairline, TahoBlockShape)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(domain, color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
                            Text("Protection bypassed for this origin", color = TahoWarn, fontFamily = TahoSans, fontSize = 8.5.sp)
                        }
                        Text(
                            text = "Remove",
                            color = TahoError,
                            fontFamily = TahoSans,
                            fontSize = 9.5.sp,
                            modifier = Modifier
                                .heightIn(min = TahoTouchTarget)
                                .clickable { TahoBrowserStateStore.toggleTrackingException(domain) }
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. CLEAR BROWSING DATA
// -------------------------------------------------------------
@Composable
private fun SettingsClearDataPage(
    onClearEngineData: (Boolean, Boolean, (Boolean) -> Unit) -> Unit,
    onDone: () -> Unit,
) {
    var clearCache by rememberSaveable { mutableStateOf(true) }
    var clearCookies by rememberSaveable { mutableStateOf(true) }
    var clearHistory by rememberSaveable { mutableStateOf(true) }
    var clearFormData by rememberSaveable { mutableStateOf(false) }
    var clearPasswords by rememberSaveable { mutableStateOf(false) }
    var clearDownloads by rememberSaveable { mutableStateOf(false) }
    var selectedTimeRange by rememberSaveable { mutableStateOf("ALL_TIME") }
    var clearedNotice by rememberSaveable { mutableStateOf(false) }
    var clearFailed by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .heightIn(min = TahoTouchTarget)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Time range")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("HOUR" to "1 Hour", "24_HOURS" to "24 Hours", "7_DAYS" to "7 Days", "ALL_TIME" to "All Time").forEach { (key, label) ->
                val sel = selectedTimeRange == key
                TahoChoiceChip(
                    label = label,
                    selected = sel,
                    modifier = Modifier.weight(1f),
                    description = "$label data-clearing range" + if (sel) ", selected" else "",
                    onClick = { selectedTimeRange = key },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Data types to clear")

        SettingsCheckboxRow("Browsing History", "${TahoBrowserStateStore.history.size} items", clearHistory) { clearHistory = !clearHistory }
        SettingsCheckboxRow("Cached Images & Files", "Gecko-managed", clearCache) { clearCache = !clearCache }
        SettingsCheckboxRow("Cookies & Site Data", "${TahoBrowserStateStore.siteData.size} origins", clearCookies) { clearCookies = !clearCookies }
        SettingsCheckboxRow("Saved Form Autofill Data", "Names & addresses", clearFormData) { clearFormData = !clearFormData }
        SettingsCheckboxRow("Saved Passwords", "${TahoBrowserStateStore.savedPasswords.size} credentials", clearPasswords) { clearPasswords = !clearPasswords }
        SettingsCheckboxRow("Download History", "${TahoBrowserStateStore.downloads.size} logs", clearDownloads) { clearDownloads = !clearDownloads }

        Spacer(Modifier.height(20.dp))

        if (clearedNotice) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoRaised)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓ Browsing data cleared successfully", color = TahoOk, fontFamily = TahoSans, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (clearFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoDeleteWash)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Browser engine data could not be fully cleared.",
                    color = TahoError,
                    fontFamily = TahoSans,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        M7PrimaryButton(
            label = "Clear Browsing Data Now",
            showArrow = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                TahoBrowserStateStore.clearBrowsingData(
                    clearCache = clearCache,
                    clearCookies = clearCookies,
                    clearHistory = clearHistory,
                    clearFormData = clearFormData,
                    clearPasswords = clearPasswords,
                    clearDownloads = clearDownloads,
                    timeRange = selectedTimeRange,
                )
                TahoBrowserStateStore.persistNow()
                clearedNotice = false
                clearFailed = false
                onClearEngineData(clearCache, clearCookies) { success ->
                    clearedNotice = success
                    clearFailed = !success
                }
            },
        )
    }
}

// -------------------------------------------------------------
// 5. BOOKMARKS & READING LIST
// -------------------------------------------------------------
@Composable
private fun SettingsBookmarksPage(
    onNavigate: (String) -> Unit,
    onOpenOfflinePage: (OfflinePageUi) -> Unit,
    onDeleteOfflinePage: (OfflinePageUi) -> Unit,
) {
    var bookmarkSearch by rememberSaveable { mutableStateOf("") }
    var activeTab by rememberSaveable { mutableStateOf("BOOKMARKS") } // "BOOKMARKS", "READING_LIST", "OFFLINE_PAGES"
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddBookmarkDialog by rememberSaveable { mutableStateOf(false) }
    var showAddFolderDialog by rememberSaveable { mutableStateOf(false) }
    var newBookmarkTitle by rememberSaveable { mutableStateOf("") }
    var newBookmarkUrl by rememberSaveable { mutableStateOf("") }
    var newBookmarkFolder by rememberSaveable { mutableStateOf("") }
    var newFolderName by rememberSaveable { mutableStateOf("") }

    val allBookmarks = TahoBrowserStateStore.bookmarks
    val folders = TahoBrowserStateStore.bookmarkFolders

    val filteredBookmarks = allBookmarks.filter { item ->
        val matchesSearch = bookmarkSearch.isBlank() ||
            item.title.contains(bookmarkSearch, ignoreCase = true) ||
            item.url.contains(bookmarkSearch, ignoreCase = true)
        val matchesFolder = selectedFolder == null || item.folderId == selectedFolder
        matchesSearch && matchesFolder
    }

    val readingList = TahoBrowserStateStore.readingList.filter {
        bookmarkSearch.isBlank() || it.title.contains(bookmarkSearch, ignoreCase = true) || it.url.contains(bookmarkSearch, ignoreCase = true)
    }

    val offlinePages = TahoBrowserStateStore.offlinePages.filter {
        bookmarkSearch.isBlank() || it.title.contains(bookmarkSearch, ignoreCase = true) || it.url.contains(bookmarkSearch, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Search Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoPillShape)
                .background(TahoSurfaceControl)
                .border(1.dp, TahoHairline, TahoPillShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = TahoFaint, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (bookmarkSearch.isEmpty()) {
                        Text("Search saved pages…", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                    }
                    BasicTextField(
                        value = bookmarkSearch,
                        onValueChange = { bookmarkSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                        cursorBrush = SolidColor(TahoGold),
                    )
                }
                if (bookmarkSearch.isNotEmpty()) {
                    Text("×", color = TahoMuted, fontSize = 14.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { bookmarkSearch = "" })
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Three Navigation Tabs
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Triple("BOOKMARKS", "Bookmarks (${allBookmarks.size})", activeTab == "BOOKMARKS"),
                Triple("READING_LIST", "Reading (${readingList.size})", activeTab == "READING_LIST"),
                Triple("OFFLINE_PAGES", "Offline (${offlinePages.size})", activeTab == "OFFLINE_PAGES"),
            ).forEach { (id, label, isSel) ->
                Box(
                    modifier = Modifier
                        .heightIn(min = TahoTouchTarget)
                        .weight(1f)
                        .clip(TahoPillShape)
                        .background(if (isSel) TahoGold else TahoSurfaceControl)
                        .clickable { activeTab = id }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (isSel) TahoPrimaryInk else TahoText, fontFamily = TahoSans, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (activeTab == "BOOKMARKS") {
            // Folder and Add Bookmark Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val allSel = selectedFolder == null
                    TahoChoiceChip(
                        label = "All",
                        selected = allSel,
                        onClick = { selectedFolder = null },
                    )
                    folders.forEach { f ->
                        val fSel = selectedFolder == f.id
                        TahoChoiceChip(
                            label = f.name,
                            selected = fSel,
                            onClick = { selectedFolder = f.id },
                        )
                    }
                    TahoInlineAction(
                        label = "Folder",
                        icon = TahoIconName.ADD,
                        color = TahoMuted,
                        onClick = { showAddFolderDialog = true },
                    )
                }

                Spacer(Modifier.width(6.dp))
                TahoInlineAction(
                    label = "Add bookmark",
                    icon = TahoIconName.ADD,
                    onClick = { showAddBookmarkDialog = true },
                )
            }

            if (showAddFolderDialog) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoGold, TahoBlockShape)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newFolderName.isEmpty()) Text("New Folder Name…", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                            inner()
                        }
                    )
                    TahoInlineAction(
                        label = "Save",
                        icon = TahoIconName.SAVE,
                        enabled = newFolderName.isNotBlank(),
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                TahoBrowserStateStore.addBookmarkFolder(newFolderName.trim())
                                newFolderName = ""
                                showAddFolderDialog = false
                            }
                        },
                    )
                    TahoIconButton(
                        name = TahoIconName.CLOSE,
                        contentDescription = "Cancel new folder",
                        onClick = { showAddFolderDialog = false },
                    )
                }
            }

            if (showAddBookmarkDialog) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoCardShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoGold, TahoCardShape)
                        .padding(12.dp),
                ) {
                    Text("Add Bookmark", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = newBookmarkTitle,
                        onValueChange = { newBookmarkTitle = it },
                        modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newBookmarkTitle.isEmpty()) Text("Page Title", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = newBookmarkUrl,
                        onValueChange = { newBookmarkUrl = it },
                        modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newBookmarkUrl.isEmpty()) Text("https://example.com", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = newBookmarkFolder,
                        onValueChange = { newBookmarkFolder = it },
                        modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newBookmarkFolder.isEmpty()) Text("Folder (Optional)", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        M7SecondaryButton("Cancel", Modifier.weight(1f)) { showAddBookmarkDialog = false }
                        M7PrimaryButton("Save", showArrow = false, modifier = Modifier.weight(1f), enabled = newBookmarkTitle.isNotBlank() && newBookmarkUrl.isNotBlank()) {
                            TahoBrowserStateStore.addBookmark(
                                title = newBookmarkTitle.trim(),
                                url = newBookmarkUrl.trim(),
                                folderId = TahoBrowserStateStore.bookmarkFolders
                                    .firstOrNull { it.name.equals(newBookmarkFolder.trim(), ignoreCase = true) }
                                    ?.id,
                            )
                            newBookmarkTitle = ""
                            newBookmarkUrl = ""
                            newBookmarkFolder = ""
                            showAddBookmarkDialog = false
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filteredBookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceRow).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No bookmarks found", color = TahoFaint, fontFamily = TahoSans, fontSize = 10.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredBookmarks) { item ->
                        Row(
                            modifier = Modifier
                                .heightIn(min = TahoTouchTarget)
                                .fillMaxWidth()
                                .clip(TahoBlockShape)
                                .background(TahoSurfaceRow)
                                .border(1.dp, TahoHairline, TahoBlockShape)
                                .clickable { onNavigate(item.url) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.title, color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    item.folderId
                                        ?.let { folderId -> TahoBrowserStateStore.bookmarkFolders.firstOrNull { it.id == folderId } }
                                        ?.let { f ->
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .heightIn(min = TahoTouchTarget)
                                                .clip(TahoBadgeShape)
                                                .background(TahoSurfaceControl)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(f.name, color = TahoGoldHi, fontFamily = TahoSans, fontSize = 7.5.sp)
                                        }
                                    }
                                }
                                Text(item.url, color = TahoFaint, fontFamily = TahoSans, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TahoIconButton(
                                name = TahoIconName.DELETE,
                                contentDescription = "Delete bookmark",
                                destructive = true,
                                onClick = { TahoBrowserStateStore.removeBookmark(item.id) },
                            )
                        }
                    }
                }
            }
        } else if (activeTab == "READING_LIST") {
            if (readingList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceRow).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No reading list items", color = TahoFaint, fontFamily = TahoSans, fontSize = 10.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(readingList) { item ->
                        Row(
                            modifier = Modifier
                                .heightIn(min = TahoTouchTarget)
                                .fillMaxWidth()
                                .clip(TahoBlockShape)
                                .background(TahoSurfaceRow)
                                .border(1.dp, TahoHairline, TahoBlockShape)
                                .clickable { onNavigate(item.url) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = if (item.isRead) TahoMuted else TahoText, fontFamily = TahoSans, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (item.isRead) "✓ Read" else "Unread · Cached offline", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TahoInlineAction(
                                    label = if (item.isRead) "Unmark" else "Mark read",
                                    color = TahoMuted,
                                    onClick = { TahoBrowserStateStore.toggleReadingListRead(item.id) },
                                )
                                TahoIconButton(
                                    name = TahoIconName.DELETE,
                                    contentDescription = "Delete reading list item",
                                    destructive = true,
                                    onClick = { TahoBrowserStateStore.removeReadingListItem(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // OFFLINE PAGES
            if (offlinePages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceRow).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No offline saved pages", color = TahoFaint, fontFamily = TahoSans, fontSize = 10.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(offlinePages) { page ->
                        Row(
                            modifier = Modifier
                                .heightIn(min = TahoTouchTarget)
                                .fillMaxWidth()
                                .clip(TahoBlockShape)
                                .background(TahoSurfaceRow)
                                .border(1.dp, TahoHairline, TahoBlockShape)
                                .clickable { onOpenOfflinePage(page) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(page.title, color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${page.url} · ${page.sizeBytes / 1024} KB PDF snapshot",
                                    color = TahoFaint,
                                    fontFamily = TahoSans,
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TahoIconButton(
                                name = TahoIconName.DELETE,
                                contentDescription = "Delete offline page",
                                destructive = true,
                                onClick = { onDeleteOfflinePage(page) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. BROWSING HISTORY
// -------------------------------------------------------------
@Composable
private fun SettingsHistoryPage(onNavigate: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val history = TahoBrowserStateStore.history.filter {
        search.isBlank() || it.title.contains(search, ignoreCase = true) || it.url.contains(search, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoPillShape)
                .background(TahoSurfaceControl)
                .border(1.dp, TahoHairline, TahoPillShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = TahoFaint, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (search.isEmpty()) {
                        Text(
                            "Search browsing history…",
                            color = TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 11.sp,
                        )
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                        cursorBrush = SolidColor(TahoGold),
                    )
                }
                if (search.isNotEmpty()) {
                    Text(
                        "×",
                        color = TahoMuted,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .heightIn(min = TahoTouchTarget)
                            .clickable { search = "" }
                            .padding(4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${history.size} visits recorded", color = TahoFaint, fontFamily = TahoSans, fontSize = 9.5.sp)
            TahoInlineAction(
                label = "Clear all",
                icon = TahoIconName.DELETE,
                color = TahoError,
                onClick = { TahoBrowserStateStore.clearAllHistory() },
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(history) { entry ->
                Row(
                    modifier = Modifier
                        .heightIn(min = TahoTouchTarget)
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoHairline, TahoBlockShape)
                        .clickable { onNavigate(entry.url) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, color = TahoText, fontFamily = TahoSans, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.url, color = TahoFaint, fontFamily = TahoSans, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { TahoBrowserStateStore.removeHistoryEntry(entry.id) }.padding(6.dp))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 7. DOWNLOAD MANAGER
// -------------------------------------------------------------
@Composable
private fun SettingsDownloadsPage(
    onPauseResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (DownloadItemUi) -> Unit,
    onOpen: (DownloadItemUi) -> Unit,
    onDelete: (DownloadItemUi) -> Unit,
) {
    val downloads = TahoBrowserStateStore.downloads

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(downloads) { dl ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dl.fileName, color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(TahoBadgeShape)
                            .background(when (dl.status) {
                                TahoDownloadStatus.COMPLETED -> TahoOk.copy(alpha = 0.2f)
                                TahoDownloadStatus.DOWNLOADING -> TahoGoldWash
                                TahoDownloadStatus.PAUSED -> TahoWarn.copy(alpha = 0.2f)
                                else -> TahoDeleteWash
                            })
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(dl.status.name, color = when (dl.status) {
                            TahoDownloadStatus.COMPLETED -> TahoOk
                            TahoDownloadStatus.DOWNLOADING -> TahoGoldHi
                            TahoDownloadStatus.PAUSED -> TahoWarn
                            else -> TahoError
                        }, fontFamily = TahoSans, fontSize = 8.5.sp)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(TahoPillShape)
                        .background(TahoText.copy(alpha = 0.08f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(dl.progress)
                            .height(3.dp)
                            .clip(TahoPillShape)
                            .background(TahoGold),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (dl.totalBytes > 0) {
                            "${dl.bytesDownloaded / 1024} KB / ${dl.totalBytes / 1024} KB"
                        } else {
                            "${dl.bytesDownloaded / 1024} KB downloaded"
                        },
                        color = TahoFaint,
                        fontFamily = TahoSans,
                        fontSize = 9.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dl.status == TahoDownloadStatus.DOWNLOADING || dl.status == TahoDownloadStatus.PAUSED) {
                            Text(if (dl.status == TahoDownloadStatus.DOWNLOADING) "Pause" else "Resume", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { onPauseResume(dl.id) })
                            Text("Cancel", color = TahoError, fontFamily = TahoSans, fontSize = 9.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { onCancel(dl.id) })
                        } else if (dl.status == TahoDownloadStatus.FAILED) {
                            Text("Retry", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { onRetry(dl) })
                        }
                        if (dl.status == TahoDownloadStatus.COMPLETED && dl.localPath != null) {
                            Text("Open", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { onOpen(dl) })
                        }
                        Text("Delete", color = TahoFaint, fontFamily = TahoSans, fontSize = 9.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { onDelete(dl) })
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 8. PASSWORD MANAGER
// -------------------------------------------------------------
@Composable
private fun SettingsPasswordsPage(
    onAuthenticateSensitive: (String, (Boolean) -> Unit) -> Unit,
    onCheckPasswordBreach: (String, (Int?) -> Unit) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var revealedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingCredentialId by rememberSaveable { mutableStateOf<String?>(null) }
    var generatedPassword by rememberSaveable { mutableStateOf("") }
    var breachStatus by rememberSaveable { mutableStateOf<String?>(null) }

    var newDomain by rememberSaveable { mutableStateOf("") }
    var newUsername by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    var editUsername by rememberSaveable { mutableStateOf("") }
    var editPassword by rememberSaveable { mutableStateOf("") }

    val allPasswords = TahoBrowserStateStore.savedPasswords
    val passwords = allPasswords.filter {
        search.isBlank() || it.domain.contains(search, ignoreCase = true) || it.username.contains(search, ignoreCase = true)
    }

    val reusedGroups = allPasswords.groupBy { it.password }.filter { it.value.size > 1 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        breachStatus?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(10.dp),
            ) {
                Text(message, color = TahoMuted, fontFamily = TahoSans, fontSize = 9.5.sp)
            }
            Spacer(Modifier.height(10.dp))
        }

        // Search & Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoPillShape)
                .background(TahoSurfaceControl)
                .border(1.dp, TahoHairline, TahoPillShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = TahoFaint, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (search.isEmpty()) {
                        Text("Search logins & credentials…", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                        cursorBrush = SolidColor(TahoGold),
                    )
                }
                if (search.isNotEmpty()) {
                    Text("×", color = TahoMuted, fontSize = 14.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { search = "" })
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${passwords.size} credentials stored", color = TahoFaint, fontFamily = TahoSans, fontSize = 9.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("+ Generate", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.5.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                    generatedPassword = TahoBrowserStateStore.generateStrongPassword(18)
                })
                Text("+ Add Login", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                    showAddDialog = true
                })
            }
        }

        if (generatedPassword.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .heightIn(min = TahoTouchTarget)
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoGoldWash)
                    .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoBlockShape)
                    .padding(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("GENERATED SECURE PASSWORD", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 8.5.sp)
                        Text(generatedPassword, color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Done", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { generatedPassword = "" })
                }
            }
        }

        if (reusedGroups.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoWarn.copy(alpha = 0.12f))
                    .border(1.dp, TahoWarn.copy(alpha = 0.35f), TahoBlockShape)
                    .padding(10.dp),
            ) {
                Text(
                    "⚠️ Security Audit: ${reusedGroups.values.sumOf { it.size }} accounts share identical passwords. Reusing passwords increases compromise risk across breaches.",
                    color = TahoWarn,
                    fontFamily = TahoSans,
                    fontSize = 9.sp,
                )
            }
        }

        if (showAddDialog) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold, TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Add New Login", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = newDomain,
                    onValueChange = { newDomain = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                    decorationBox = { inner ->
                        if (newDomain.isEmpty()) Text("Domain / Website (e.g. github.com)", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                        inner()
                    }
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                    decorationBox = { inner ->
                        if (newUsername.isEmpty()) Text("Username / Email", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                        inner()
                    }
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier.weight(1f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newPassword.isEmpty()) Text("Password", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Gen", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                        newPassword = TahoBrowserStateStore.generateStrongPassword(18)
                    }.padding(4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    M7SecondaryButton("Cancel", Modifier.weight(1f)) { showAddDialog = false }
                    M7PrimaryButton("Save Login", showArrow = false, modifier = Modifier.weight(1f), enabled = newDomain.isNotBlank() && newUsername.isNotBlank() && newPassword.isNotBlank()) {
                        TahoBrowserStateStore.savePassword(newDomain.trim(), newUsername.trim(), newPassword.trim())
                        newDomain = ""
                        newUsername = ""
                        newPassword = ""
                        showAddDialog = false
                    }
                }
            }
        }

        if (editingCredentialId != null) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold, TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Edit Credential", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = editUsername,
                    onValueChange = { editUsername = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = editPassword,
                    onValueChange = { editPassword = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    M7SecondaryButton("Cancel", Modifier.weight(1f)) { editingCredentialId = null }
                    M7PrimaryButton("Update", showArrow = false, modifier = Modifier.weight(1f), enabled = editUsername.isNotBlank() && editPassword.isNotBlank()) {
                        editingCredentialId?.let { id ->
                            TahoBrowserStateStore.updateSavedPassword(id, editUsername.trim(), editPassword.trim())
                        }
                        editingCredentialId = null
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            passwords.forEach { pw ->
                val isRevealed = revealedId == pw.id
                val isReused = (reusedGroups[pw.password]?.size ?: 0) > 1
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, if (pw.isCompromised) TahoError.copy(alpha = 0.5f) else TahoHairline, TahoBlockShape)
                        .padding(14.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(pw.domain, color = TahoText, fontFamily = TahoSans, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (pw.isCompromised) {
                                Text("⚠️ COMPROMISED", color = TahoError, fontFamily = TahoSans, fontSize = 8.sp)
                            } else if (pw.isWeak) {
                                Text("⚠️ WEAK", color = TahoWarn, fontFamily = TahoSans, fontSize = 8.sp)
                            }
                            if (isReused) {
                                Text("⚠️ REUSED", color = TahoWarn, fontFamily = TahoSans, fontSize = 8.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("User: ${pw.username}", color = TahoMuted, fontFamily = TahoSans, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRevealed) pw.password else "••••••••••••",
                            color = if (isRevealed) TahoGoldHi else TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 11.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isRevealed) "Hide" else "Reveal", color = TahoMuted, fontFamily = TahoSans, fontSize = 9.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                                if (isRevealed) {
                                    revealedId = null
                                } else if (TahoBrowserStateStore.settings.biometricLockForPasswords) {
                                    onAuthenticateSensitive("Reveal saved password") { success ->
                                        if (success) revealedId = pw.id
                                    }
                                } else {
                                    revealedId = pw.id
                                }
                            })
                            Text(
                                if (pw.isCompromised) "Breached" else "Check Breach",
                                color = if (pw.isCompromised) TahoError else TahoMuted,
                                fontFamily = TahoSans,
                                fontSize = 9.sp,
                                modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                                    breachStatus = "Checking ${pw.domain}…"
                                    onCheckPasswordBreach(pw.password) { count ->
                                        when {
                                            count == null -> {
                                                breachStatus = "Breach check failed for ${pw.domain}."
                                            }
                                            count > 0 -> {
                                                TahoBrowserStateStore.setSavedPasswordCompromised(pw.id, true)
                                                breachStatus = "Password for ${pw.domain} appears in the breach corpus."
                                            }
                                            else -> {
                                                TahoBrowserStateStore.setSavedPasswordCompromised(pw.id, false)
                                                breachStatus = "No match found for ${pw.domain}."
                                            }
                                        }
                                    }
                                },
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Edit", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                                editingCredentialId = pw.id
                                editUsername = pw.username
                                editPassword = pw.password
                            })
                            Text("Delete", color = TahoError, fontFamily = TahoSans, fontSize = 9.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                                TahoBrowserStateStore.removeSavedPassword(pw.id)
                            })
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 9. AUTOFILL & PAYMENTS
// -------------------------------------------------------------
@Composable
private fun SettingsAutofillPage() {
    val addresses = TahoBrowserStateStore.savedAddresses
    val payments = TahoBrowserStateStore.savedPayments
    val settings = TahoBrowserStateStore.settings

    var showAddAddress by rememberSaveable { mutableStateOf(false) }
    var addrLabel by rememberSaveable { mutableStateOf("") }
    var addrFullName by rememberSaveable { mutableStateOf("") }
    var addrStreet by rememberSaveable { mutableStateOf("") }
    var addrCity by rememberSaveable { mutableStateOf("") }
    var addrState by rememberSaveable { mutableStateOf("") }
    var addrZip by rememberSaveable { mutableStateOf("") }

    var showAddPayment by rememberSaveable { mutableStateOf(false) }
    var payHolder by rememberSaveable { mutableStateOf("") }
    var payNumber by rememberSaveable { mutableStateOf("") }
    var payExpiry by rememberSaveable { mutableStateOf("") }
    var payType by rememberSaveable { mutableStateOf("Visa") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Autofill settings")
        SettingsToggleRow("Autofill Addresses & Forms", "Save and fill addresses automatically on web forms", settings.addressAutofillEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(addressAutofillEnabled = !it.addressAutofillEnabled) }
        }
        SettingsToggleRow("Autofill Payment Cards", "Securely fill payment details on checkout forms", settings.paymentAutofillEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(paymentAutofillEnabled = !it.paymentAutofillEnabled) }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionTitle("SAVED ADDRESSES (${addresses.size})")
            Text("+ Add Address", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.5.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { showAddAddress = true })
        }

        if (showAddAddress) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold, TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Add Address", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = addrLabel, onValueChange = { addrLabel = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (addrLabel.isEmpty()) Text("Label (e.g. Home, Office)", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = addrFullName, onValueChange = { addrFullName = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (addrFullName.isEmpty()) Text("Full Name", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = addrStreet, onValueChange = { addrStreet = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (addrStreet.isEmpty()) Text("Street Address", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicTextField(value = addrCity, onValueChange = { addrCity = it }, modifier = Modifier.weight(1f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (addrCity.isEmpty()) Text("City", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                    BasicTextField(value = addrState, onValueChange = { addrState = it }, modifier = Modifier.weight(0.5f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (addrState.isEmpty()) Text("State", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                    BasicTextField(value = addrZip, onValueChange = { addrZip = it }, modifier = Modifier.weight(0.6f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (addrZip.isEmpty()) Text("Zip", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    M7SecondaryButton("Cancel", Modifier.weight(1f)) { showAddAddress = false }
                    M7PrimaryButton("Save Address", showArrow = false, modifier = Modifier.weight(1f), enabled = addrFullName.isNotBlank() && addrStreet.isNotBlank()) {
                        TahoBrowserStateStore.addSavedAddress(
                            label = if (addrLabel.isBlank()) "Address" else addrLabel.trim(),
                            fullName = addrFullName.trim(),
                            street = addrStreet.trim(),
                            city = addrCity.trim(),
                            state = addrState.trim(),
                            zipCode = addrZip.trim(),
                        )
                        addrLabel = ""; addrFullName = ""; addrStreet = ""; addrCity = ""; addrState = ""; addrZip = ""
                        showAddAddress = false
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        addresses.forEach { addr ->
            Row(
                modifier = Modifier
                    .heightIn(min = TahoTouchTarget)
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(addr.label, color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(addr.fullName, color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
                    Text("${addr.street}, ${addr.city}, ${addr.state} ${addr.zipCode}", color = TahoMuted, fontFamily = TahoSans, fontSize = 9.sp)
                }
                Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                    TahoBrowserStateStore.removeSavedAddress(addr.id)
                }.padding(6.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionTitle("SAVED PAYMENT METHODS (${payments.size})")
            Text("+ Add Card", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.5.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { showAddPayment = true })
        }

        if (showAddPayment) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold, TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Add Payment Card", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = payHolder, onValueChange = { payHolder = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (payHolder.isEmpty()) Text("Cardholder Name", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = payNumber, onValueChange = { payNumber = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (payNumber.isEmpty()) Text("Card Number", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicTextField(value = payExpiry, onValueChange = { payExpiry = it }, modifier = Modifier.weight(1f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (payExpiry.isEmpty()) Text("MM/YY", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                    BasicTextField(value = payType, onValueChange = { payType = it }, modifier = Modifier.weight(1f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp), decorationBox = { if (payType.isEmpty()) Text("Card Type (Visa/MC)", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() })
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    M7SecondaryButton("Cancel", Modifier.weight(1f)) { showAddPayment = false }
                    M7PrimaryButton("Save Card", showArrow = false, modifier = Modifier.weight(1f), enabled = payHolder.isNotBlank() && payNumber.isNotBlank()) {
                        TahoBrowserStateStore.addSavedPayment(
                            cardHolder = payHolder.trim(),
                            cardNumber = payNumber.trim(),
                            cardExpiry = payExpiry.trim(),
                            cardType = payType.trim(),
                        )
                        payHolder = ""; payNumber = ""; payExpiry = ""
                        showAddPayment = false
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        payments.forEach { pay ->
            Row(
                modifier = Modifier
                    .heightIn(min = TahoTouchTarget)
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pay.cardHolder, color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
                    Text("${pay.cardType} · ${TahoBrowserStateStore.maskedPaymentNumber(pay)} (Exp: ${pay.cardExpiry})", color = TahoMuted, fontFamily = TahoSans, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💳", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable {
                        TahoBrowserStateStore.removeSavedPayment(pay.id)
                    }.padding(6.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// -------------------------------------------------------------
// 10. PROFILES & SYNC
// -------------------------------------------------------------
@Composable
private fun SettingsProfilesSyncPage(
    onSwitchProfile: (String) -> Unit,
    onCreateLocalProfile: (String) -> Unit,
) {
    val profiles = TahoBrowserStateStore.profiles
    val devices = TahoBrowserStateStore.syncedDevices
    var newProfileName by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .heightIn(min = TahoTouchTarget)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Local browser profiles")
        profiles.forEach { profile ->
            Row(
                modifier = Modifier
                    .heightIn(min = TahoTouchTarget)
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(if (profile.isActive) TahoSurfaceRowHover else TahoSurfaceRow)
                    .border(1.dp, if (profile.isActive) TahoGold.copy(alpha = 0.5f) else TahoHairline, TahoBlockShape)
                    .clickable { onSwitchProfile(profile.id) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.avatarGlyph, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(profile.name, color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp)
                        Text(
                            if (profile.isGuest) "Guest profile" else "Local profile metadata",
                            color = TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 8.5.sp,
                        )
                    }
                }
                if (profile.isActive) {
                    Text("✓ Active", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BasicTextField(
                value = newProfileName,
                onValueChange = { newProfileName = it.take(40) },
                modifier = Modifier
                    .weight(1f)
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceControl)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                singleLine = true,
                textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 10.sp),
                cursorBrush = SolidColor(TahoGold),
                decorationBox = { inner ->
                    Box {
                        if (newProfileName.isBlank()) {
                            Text(
                                "New local profile name",
                                color = TahoFaint,
                                fontFamily = TahoSans,
                                fontSize = 9.5.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            M7SecondaryButton("Create", Modifier.width(76.dp)) {
                if (newProfileName.isNotBlank()) {
                    onCreateLocalProfile(newProfileName)
                    newProfileName = ""
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Normal profiles isolate Taho history/passwords and Gecko site storage. Guest is ephemeral and is never restored after app restart.",
            color = TahoFaint,
            fontFamily = TahoSans,
            fontSize = 8.5.sp,
        )

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Cross-device sync")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoCardShape)
                .background(TahoSurfaceRow)
                .border(1.dp, TahoHairline, TahoCardShape)
                .padding(14.dp),
        ) {
            Column {
                Text(
                    "Sync service not connected",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bookmarks, history, passwords, open tabs, settings and device-to-device tab sending remain local until a real authenticated sync backend is connected.",
                    color = TahoMuted,
                    fontFamily = TahoSans,
                    fontSize = 9.5.sp,
                )
            }
        }

        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SettingsSectionTitle("Synced devices")
            devices.forEach { dev ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoHairline, TahoBlockShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(dev.name, color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
                        Text("Type: ${dev.deviceType}", color = TahoFaint, fontFamily = TahoSans, fontSize = 9.sp)
                    }
                    Text("Read only", color = TahoFaint, fontFamily = TahoSans, fontSize = 9.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// 11. EXTENSIONS & WEB APPS
// -------------------------------------------------------------
@Composable
private fun SettingsExtensionsPage(
    onRefresh: () -> Unit,
    onInstall: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onSetPrivate: (String, Boolean) -> Unit,
    onUpdate: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onSearchMarketplace: (
        String,
        (List<ExtensionMarketplaceItemUi>, String?) -> Unit,
    ) -> Unit,
) {
    val exts = TahoBrowserStateStore.extensions
    val pwas = TahoBrowserStateStore.installedPwas
    var xpiUrl by rememberSaveable { mutableStateOf("") }
    var marketplaceOpen by rememberSaveable { mutableStateOf(false) }
    var marketplaceQuery by rememberSaveable { mutableStateOf("") }
    var marketplaceLoading by rememberSaveable { mutableStateOf(false) }
    var marketplaceError by rememberSaveable { mutableStateOf<String?>(null) }
    var marketplaceResults by remember { mutableStateOf(emptyList<ExtensionMarketplaceItemUi>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Mozilla webextensions")
        Text(
            "Installed extensions below come directly from GeckoView. New packages are validated and must be Mozilla-signed before Gecko installs them.",
            color = TahoMuted,
            fontFamily = TahoSans,
            fontSize = 9.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            M7SecondaryButton(
                if (marketplaceOpen) "Hide Marketplace" else "Browse Mozilla Add-ons",
                Modifier.weight(1f),
                onClick = { marketplaceOpen = !marketplaceOpen },
            )
            M7SecondaryButton(
                "Refresh",
                Modifier.weight(0.55f),
                onClick = onRefresh,
            )
        }

        if (marketplaceOpen) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoCardShape)
                    .padding(12.dp),
            ) {
                Text(
                    "Mozilla Add-ons Marketplace",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Results come from addons.mozilla.org. Gecko still verifies the signed XPI and shows its permission request before installation.",
                    color = TahoFaint,
                    fontFamily = TahoSans,
                    fontSize = 8.5.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    BasicTextField(
                        value = marketplaceQuery,
                        onValueChange = { marketplaceQuery = it.take(100) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(TahoBlockShape)
                            .background(TahoSurfaceControl)
                            .border(1.dp, TahoHairline, TahoBlockShape)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TahoText,
                            fontFamily = TahoSans,
                            fontSize = 10.sp,
                        ),
                        cursorBrush = SolidColor(TahoGold),
                        decorationBox = { inner ->
                            Box {
                                if (marketplaceQuery.isBlank()) {
                                    Text(
                                        "Search public Android extensions…",
                                        color = TahoFaint,
                                        fontFamily = TahoSans,
                                        fontSize = 9.5.sp,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    M7SecondaryButton(
                        if (marketplaceLoading) "…" else "Search",
                        Modifier.width(78.dp),
                    ) {
                        if (!marketplaceLoading) {
                            marketplaceLoading = true
                            marketplaceError = null
                            onSearchMarketplace(marketplaceQuery) { items, error ->
                                marketplaceLoading = false
                                marketplaceResults = items
                                marketplaceError = error
                            }
                        }
                    }
                }

                marketplaceError?.let { error ->
                    Spacer(Modifier.height(7.dp))
                    Text(
                        error,
                        color = TahoError,
                        fontFamily = TahoSans,
                        fontSize = 8.5.sp,
                    )
                }

                if (!marketplaceLoading && marketplaceResults.isEmpty() && marketplaceError == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Search to load public extensions.",
                        color = TahoFaint,
                        fontFamily = TahoSans,
                        fontSize = 8.5.sp,
                    )
                }

                marketplaceResults.forEach { item ->
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TahoBlockShape)
                            .background(TahoSurfaceControl)
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    color = TahoText,
                                    fontFamily = TahoSans,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "v" + item.version + " · " + item.author,
                                    color = TahoFaint,
                                    fontFamily = TahoSans,
                                    fontSize = 8.sp,
                                )
                            }
                            M7SecondaryButton("Install", Modifier.width(72.dp)) {
                                onInstall(item.installUrl)
                            }
                        }
                        if (item.summary.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.summary,
                                color = TahoMuted,
                                fontFamily = TahoSans,
                                fontSize = 8.5.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val metadata = buildList {
                            item.rating?.let { add(String.format("%.1f★", it)) }
                            item.users?.let { add(it.toString() + " users") }
                        }.joinToString(" · ")
                        if (metadata.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                metadata,
                                color = TahoFaint,
                                fontFamily = TahoSans,
                                fontSize = 8.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = xpiUrl,
            onValueChange = { xpiUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoBlockShape)
                .background(TahoSurfaceControl)
                .border(1.dp, TahoHairline, TahoBlockShape)
                .padding(10.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = TahoText,
                fontFamily = TahoSans,
                fontSize = 10.sp,
            ),
            cursorBrush = SolidColor(TahoGold),
            decorationBox = { inner ->
                Box {
                    if (xpiUrl.isBlank()) {
                        Text(
                            "https://…/addon.xpi",
                            color = TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 10.sp,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(7.dp))
        M7PrimaryButton(
            label = "Install Signed XPI",
            showArrow = false,
            modifier = Modifier.fillMaxWidth(),
            enabled = xpiUrl.trim().startsWith("https://"),
        ) {
            onInstall(xpiUrl.trim())
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("INSTALLED EXTENSIONS (${exts.size})")
        if (exts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(14.dp),
            ) {
                Text(
                    "No user-visible Gecko extensions are installed.",
                    color = TahoFaint,
                    fontFamily = TahoSans,
                    fontSize = 10.sp,
                )
            }
        }

        exts.forEach { ext ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            ext.name,
                            color = TahoText,
                            fontFamily = TahoSans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "v${ext.version} by ${ext.author}",
                            color = TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 8.5.sp,
                        )
                    }
                    TahoChoiceChip(
                        label = if (ext.isEnabled) "Active" else "Disabled",
                        selected = ext.isEnabled,
                        semanticColor = TahoOk,
                        description = "${ext.name} extension " + if (ext.isEnabled) "enabled" else "disabled",
                        onClick = { onSetEnabled(ext.id, !ext.isEnabled) },
                    )
                }

                if (ext.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ext.description,
                        color = TahoMuted,
                        fontFamily = TahoSans,
                        fontSize = 9.5.sp,
                    )
                }

                if (ext.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Permissions: " + ext.permissions.take(6).joinToString(", "),
                        color = TahoFaint,
                        fontFamily = TahoSans,
                        fontSize = 8.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TahoInlineAction(
                        label = if (ext.allowedInPrivate) "Private allowed" else "Private blocked",
                        icon = TahoIconName.SECURITY,
                        color = TahoMuted,
                        onClick = { onSetPrivate(ext.id, !ext.allowedInPrivate) },
                    )
                    TahoInlineAction(
                        label = "Check update",
                        icon = TahoIconName.UPDATE,
                        color = TahoMuted,
                        onClick = { onUpdate(ext.id) },
                    )
                    TahoInlineAction(
                        label = "Uninstall",
                        icon = TahoIconName.DELETE,
                        color = TahoError,
                        onClick = { onUninstall(ext.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("INSTALLED WEB APPS (PWAS) (${pwas.size})")
        if (pwas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(14.dp),
            ) {
                Text(
                    "No Taho web apps installed. Install is offered only when Gecko validates a Web App Manifest for the current page.",
                    color = TahoFaint,
                    fontFamily = TahoSans,
                    fontSize = 10.sp,
                )
            }
        } else {
            pwas.forEach { pwa ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoHairline, TahoBlockShape)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            pwa.name,
                            color = TahoText,
                            fontFamily = TahoSans,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            pwa.url,
                            color = TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "Installed",
                        color = TahoOk,
                        fontFamily = TahoSans,
                        fontSize = 9.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// 12. ACCESSIBILITY
// -------------------------------------------------------------
@Composable
private fun SettingsAccessibilityPage() {
    val settings = TahoBrowserStateStore.settings
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Motion & contrast")
        SettingsToggleRow("Reduced Motion", "Honor system reduced animation preference", settings.reducedMotion) {
            TahoBrowserStateStore.updateSettings { it.copy(reducedMotion = !it.reducedMotion) }
        }
        SettingsToggleRow("High Contrast Display", "Enhance hairline borders and typography contrast", settings.highContrast) {
            TahoBrowserStateStore.updateSettings { it.copy(highContrast = !it.highContrast) }
        }
        SettingsToggleRow("Force Zoom on All Sites", "Override site directives that disable zoom gestures", settings.forceZoomEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(forceZoomEnabled = !it.forceZoomEnabled) }
        }
        SettingsToggleRow("Screen Reader Optimization", "Expose semantic attributes on all chrome controls", settings.screenReaderOptimized) {
            TahoBrowserStateStore.updateSettings { it.copy(screenReaderOptimized = !it.screenReaderOptimized) }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("TEXT SCALING: ${settings.fontScalingPercent}%")
        Slider(
            value = settings.fontScalingPercent.toFloat(),
            onValueChange = { pct -> TahoBrowserStateStore.updateSettings { it.copy(fontScalingPercent = pct.toInt()) } },
            valueRange = 75f..200f,
            steps = 5,
            colors = SliderDefaults.colors(thumbColor = TahoGold, activeTrackColor = TahoGold),
        )
    }
}

// -------------------------------------------------------------
// 13. LANGUAGES
// -------------------------------------------------------------
@Composable
private fun SettingsLanguagesPage() {
    val settings = TahoBrowserStateStore.settings
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Browser interface language")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoBlockShape)
                .background(TahoSurfaceControl)
                .padding(12.dp),
        ) {
            Text(settings.browserLanguage, color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Preferred website languages")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoBlockShape)
                .background(TahoSurfaceControl)
                .padding(12.dp),
        ) {
            Text(settings.preferredWebLanguage, color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Page translation")
        SettingsToggleRow("Offer Automatic Translation", "Translate foreign language pages automatically", settings.autoTranslateEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(autoTranslateEnabled = !it.autoTranslateEnabled) }
        }
    }
}

// -------------------------------------------------------------
// 14. DEFAULT BROWSER
// -------------------------------------------------------------
@Composable
private fun SettingsDefaultBrowserPage() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Default application dispatch")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoCardShape)
                .background(TahoSurfaceRow)
                .border(1.dp, TahoHairline, TahoCardShape)
                .padding(16.dp),
        ) {
            Column {
                Text("Make Taho your default browser", color = TahoText, fontFamily = TahoDisplay, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose Taho as Android's web browser and control which verified links may open directly in it.",
                    color = TahoMuted,
                    fontFamily = TahoSans,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(14.dp))
                M7PrimaryButton("Set as Default Browser", modifier = Modifier.fillMaxWidth()) {
                    runCatching {
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                            if (roleManager?.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) == true) {
                                context.startActivity(
                                    roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER),
                                )
                            } else {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                            }
                        } else {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                M7SecondaryButton("Open Supported Links Settings", Modifier.fillMaxWidth()) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                Uri.parse("package:" + context.packageName),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                M7SecondaryButton("Open App Notification Settings", Modifier.fillMaxWidth()) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 15. PERFORMANCE & MEDIA
// -------------------------------------------------------------
@Composable
private fun SettingsPerformanceMediaPage() {
    val settings = TahoBrowserStateStore.settings
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Startup & session restoration")
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                TahoStartupBehavior.PREVIOUS_TABS to "Continue Previous Tabs",
                TahoStartupBehavior.START_PAGE to "Open Start Page",
                TahoStartupBehavior.CUSTOM_PAGE to "Open Custom Page",
            ).forEach { (mode, label) ->
                val selected = settings.startupBehavior == mode
                Row(
                    modifier = Modifier
                        .heightIn(min = TahoTouchTarget)
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(if (selected) TahoGold.copy(alpha = .14f) else TahoSurfaceRow)
                        .border(
                            1.dp,
                            if (selected) TahoGold.copy(alpha = .45f) else TahoHairline,
                            TahoBlockShape,
                        )
                        .clickable {
                            TahoBrowserStateStore.updateSettings { it.copy(startupBehavior = mode) }
                        }
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        color = if (selected) TahoGoldHi else TahoText,
                        fontFamily = TahoSans,
                        fontSize = 10.sp,
                    )
                    if (selected) {
                        Text("✓", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp)
                    }
                }
            }
        }

        if (settings.startupBehavior == TahoStartupBehavior.CUSTOM_PAGE) {
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = settings.customStartupUrl,
                onValueChange = { value ->
                    TahoBrowserStateStore.updateSettings { it.copy(customStartupUrl = value) }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = TahoText,
                    fontFamily = TahoSans,
                    fontSize = 10.5.sp,
                ),
                cursorBrush = SolidColor(TahoGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceControl)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (settings.customStartupUrl.isBlank()) {
                            Text(
                                "https://example.com",
                                color = TahoFaint,
                                fontFamily = TahoSans,
                                fontSize = 10.5.sp,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Automatic inactive tab closing")
        Row(
            modifier = Modifier
                .heightIn(min = TahoTouchTarget)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf(
                TahoAutoCloseTabs.NEVER to "Never",
                TahoAutoCloseTabs.AFTER_1_DAY to "1 Day",
                TahoAutoCloseTabs.AFTER_1_WEEK to "1 Week",
                TahoAutoCloseTabs.AFTER_1_MONTH to "1 Month",
            ).forEach { (policy, label) ->
                val selected = settings.autoCloseTabs == policy
                Box(
                    modifier = Modifier
                        .heightIn(min = TahoTouchTarget)
                        .clip(TahoPillShape)
                        .background(if (selected) TahoGold else TahoSurfaceControl)
                        .border(1.dp, if (selected) TahoGoldHi else TahoHairline, TahoPillShape)
                        .clickable {
                            TahoBrowserStateStore.updateSettings { it.copy(autoCloseTabs = policy) }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) TahoBg else TahoText,
                        fontFamily = TahoSans,
                        fontSize = 9.5.sp,
                    )
                }
            }
        }
        Text(
            text = "Pinned tabs are excluded from automatic cleanup.",
            color = TahoFaint,
            fontFamily = TahoSans,
            fontSize = 8.5.sp,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Memory & system efficiency")
        SettingsToggleRow("Memory Saver Mode", "Discard background tabs when memory is low", settings.memorySaverEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(memorySaverEnabled = !it.memorySaverEnabled) }
        }
        SettingsToggleRow("Link Preloading / Prefetch", "Accelerate navigation while respecting data plans", settings.preloadingEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(preloadingEnabled = !it.preloadingEnabled) }
        }
        SettingsToggleRow("Hardware Acceleration", "Render pages via Vulkan/OpenGL GPU pipelines", settings.hardwareAccelerationEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(hardwareAccelerationEnabled = !it.hardwareAccelerationEnabled) }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Media playback")
        SettingsToggleRow("Background Audio", "Continue audio playback when browser is in background", settings.backgroundAudioEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(backgroundAudioEnabled = !it.backgroundAudioEnabled) }
        }
        SettingsToggleRow("Picture-in-Picture (PiP)", "Float videos when switching apps", settings.pictureInPictureEnabled) {
            TahoBrowserStateStore.updateSettings { it.copy(pictureInPictureEnabled = !it.pictureInPictureEnabled) }
        }
    }
}

// -------------------------------------------------------------
// 16. STORAGE USAGE
// -------------------------------------------------------------
@Composable
private fun SettingsStorageUsagePage() {
    val context = LocalContext.current
    val downloads = TahoBrowserStateStore.downloads
    val offlinePages = TahoBrowserStateStore.offlinePages

    fun directoryBytes(root: java.io.File): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown()
            .filter { it.isFile }
            .sumOf { file -> runCatching { file.length() }.getOrDefault(0L) }
    }

    fun readableBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe >= 1024L * 1024L * 1024L ->
                String.format("%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
            safe >= 1024L * 1024L ->
                String.format("%.2f MB", safe / (1024.0 * 1024.0))
            safe >= 1024L ->
                String.format("%.1f KB", safe / 1024.0)
            else -> "$safe B"
        }
    }

    // These are app-owned directories, so byte counts are measured rather than
    // inferred from metadata. Accessing the observable lists keeps this page
    // recomposing when download/offline records change.
    val downloadBytes = downloads.let {
        directoryBytes(java.io.File(context.filesDir, "downloads"))
    }
    val offlineBytes = offlinePages.let {
        directoryBytes(java.io.File(context.filesDir, "offline_pages"))
    }
    val browserStateBytes =
        directoryBytes(java.io.File(context.noBackupFilesDir, "browser_state"))
    val totalTahoOwned = downloadBytes + offlineBytes + browserStateBytes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Measured taho-owned storage")
        DiagItem("Downloaded files", readableBytes(downloadBytes))
        DiagItem("Offline page snapshots", readableBytes(offlineBytes))
        DiagItem("Browser state + encrypted vaults", readableBytes(browserStateBytes))
        DiagItem("Measured subtotal", readableBytes(totalTahoOwned))

        Spacer(Modifier.height(8.dp))
        Text(
            "These numbers are read from Taho's app-private files. Capture storage is managed by its own Room/file layer and is not merged into this browser subtotal.",
            color = TahoFaint,
            fontFamily = TahoSans,
            fontSize = 9.5.sp,
        )

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("Gecko-managed website storage")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoCardShape)
                .background(TahoSurfaceRow)
                .border(1.dp, TahoHairline, TahoCardShape)
                .padding(14.dp),
        ) {
            Text(
                "Exact cache, cookie, IndexedDB, service-worker and per-origin byte totals are not exposed through the current GeckoView storage APIs used by Taho. They are therefore shown as unavailable rather than estimated.",
                color = TahoMuted,
                fontFamily = TahoSans,
                fontSize = 9.5.sp,
            )
        }
    }
}

// -------------------------------------------------------------
// 17. DIAGNOSTICS & RESET
// -------------------------------------------------------------
@Composable
private fun SettingsDiagnosticsPage() {
    val settings = TahoBrowserStateStore.settings
    val notifications = TahoBrowserStateStore.websiteNotifications
    val context = LocalContext.current
    val appInfo = remember(context.packageName) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Runtime diagnostics")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoCardShape)
                .background(TahoSurfaceRow)
                .border(1.dp, TahoHairline, TahoCardShape)
                .padding(14.dp),
        ) {
            Column {
                DiagItem("Application", appInfo?.versionName ?: "Unavailable")
                DiagItem("Android", android.os.Build.VERSION.RELEASE ?: "Unavailable")
                DiagItem("SDK", android.os.Build.VERSION.SDK_INT.toString())
                DiagItem("Device", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
                DiagItem(
                    "Browser engine details",
                    "Available from build/runtime evidence, not hard-coded here",
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Crash reporting & telemetry")
        Text(
            "No crash-report upload backend is connected in this build. The preference is stored locally only.",
            color = TahoFaint,
            fontFamily = TahoSans,
            fontSize = 9.5.sp,
        )
        SettingsToggleRow(
            "Crash Reporting Preference",
            "Remember whether future crash-reporting integration may be enabled",
            settings.crashReportingEnabled,
        ) {
            TahoBrowserStateStore.updateSettings {
                it.copy(crashReportingEnabled = !it.crashReportingEnabled)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionTitle("WEBSITE NOTIFICATIONS CENTER (${notifications.size})")
            if (notifications.isNotEmpty()) {
                TahoInlineAction(
                    label = "Clear all",
                    icon = TahoIconName.DELETE,
                    color = TahoError,
                    onClick = { TahoBrowserStateStore.clearWebsiteNotifications() },
                )
            }
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
            ) {
                Text(
                    "No website notifications are recorded by the Browser UI adapter.",
                    color = TahoFaint,
                    fontFamily = TahoSans,
                    fontSize = 10.sp,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                notifications.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TahoBlockShape)
                            .background(TahoSurfaceRow)
                            .border(1.dp, TahoHairline, TahoBlockShape)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                color = TahoText,
                                fontFamily = TahoSans,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${item.origin} · ${item.message}",
                                color = TahoFaint,
                                fontFamily = TahoSans,
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TahoIconButton(
                            name = TahoIconName.DELETE,
                            contentDescription = "Delete website notification",
                            destructive = true,
                            onClick = { TahoBrowserStateStore.removeWebsiteNotification(item.id) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("Reset controls")
        M7SecondaryButton(
            label = "Reset Site Permissions",
            modifier = Modifier.fillMaxWidth(),
        ) {
            TahoBrowserStateStore.resetSitePermissions(null)
        }
        Spacer(Modifier.height(10.dp))
        M7SecondaryButton(
            label = "Restore Default Settings",
            modifier = Modifier.fillMaxWidth(),
        ) {
            TahoBrowserStateStore.settings = BrowserSettingsState()
        }
    }
}

// -------------------------------------------------------------
// 18. BACKUP & EXPORT
// -------------------------------------------------------------
@Composable
private fun SettingsBackupExportPage(
    onAuthenticateSensitive: (String, (Boolean) -> Unit) -> Unit,
    onExportFullBackup: (Uri, String, (Boolean, String) -> Unit) -> Unit,
    onRestoreFullBackup: (Uri, String, (Boolean, String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var fullBackupPassphrase by rememberSaveable { mutableStateOf("") }

    fun writeText(uri: Uri, text: String): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(text)
            } ?: error("Unable to open destination")
        }.isSuccess

    fun readText(uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }
        }.getOrNull()

    val exportFullBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            val passphrase = fullBackupPassphrase
            onExportFullBackup(uri, passphrase) { success, message ->
                statusMessage = message
                if (success) fullBackupPassphrase = ""
            }
        }
    }

    val restoreFullBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val passphrase = fullBackupPassphrase
            onRestoreFullBackup(uri, passphrase) { success, message ->
                statusMessage = message
                if (success) fullBackupPassphrase = ""
            }
        }
    }

    val exportBookmarks = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        if (uri != null) {
            val content = TahoBrowserStateStore.exportBookmarksHtml()
            statusMessage = if (writeText(uri, content)) {
                "Exported ${TahoBrowserStateStore.bookmarks.size} bookmarks."
            } else {
                "Bookmark export failed."
            }
        }
    }

    val exportPasswords = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val content = TahoBrowserStateStore.exportPasswordsJson()
            statusMessage = if (writeText(uri, content)) {
                "Password export completed. The exported JSON contains readable credentials."
            } else {
                "Password export failed."
            }
        }
    }

    val exportSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val content = TahoBrowserStateStore.exportSettingsJson()
            statusMessage = if (writeText(uri, content)) {
                "Core browser settings exported."
            } else {
                "Settings export failed."
            }
        }
    }

    val importBookmarks = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val content = readText(uri)
            statusMessage = if (content != null) {
                val count = TahoBrowserStateStore.importBookmarksFromHtml(content)
                "Imported $count bookmark${if (count == 1) "" else "s"}."
            } else {
                "Unable to read bookmark file."
            }
        }
    }

    val importPasswords = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val content = readText(uri)
            statusMessage = if (content != null) {
                val count = TahoBrowserStateStore.importPasswordsFromJson(content)
                "Imported $count credential${if (count == 1) "" else "s"} into the encrypted local vault."
            } else {
                "Unable to read password file."
            }
        }
    }

    val importBrowserPasswordsCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val content = readText(uri)
            statusMessage = if (content != null) {
                val result = TahoBrowserStateStore.importPasswordsFromCsv(content)
                buildString {
                    append(result.format)
                    append(" CSV: imported ")
                    append(result.imported)
                    append(", skipped existing ")
                    append(result.skippedExisting)
                    append(", rejected ")
                    append(result.rejectedRows)
                    append(".")
                }
            } else {
                "Unable to read browser password CSV."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Encrypted full taho backup")
        Text(
            "Includes Taho-owned settings, bookmarks, history, passwords, addresses, payment cards, local profiles, downloads metadata/files, offline snapshots, collections, PWAs and browser UI records. Gecko-owned cookies, cache and IndexedDB are not copied.",
            color = TahoMuted,
            fontFamily = TahoSans,
            fontSize = 9.5.sp,
        )
        Spacer(Modifier.height(9.dp))
        BasicTextField(
            value = fullBackupPassphrase,
            onValueChange = { fullBackupPassphrase = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoBlockShape)
                .background(TahoSurfaceControl)
                .border(1.dp, TahoHairline, TahoBlockShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
            cursorBrush = SolidColor(TahoGold),
            decorationBox = { inner ->
                Box {
                    if (fullBackupPassphrase.isEmpty()) {
                        Text(
                            "Backup passphrase (minimum 10 characters)",
                            color = TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 10.5.sp,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            M7SecondaryButton("Create Encrypted Backup", Modifier.weight(1f)) {
                if (fullBackupPassphrase.length < 10) {
                    statusMessage = "Backup passphrase must be at least 10 characters."
                } else {
                    val launch = { exportFullBackup.launch("taho-browser-backup.taho") }
                    if (TahoBrowserStateStore.settings.biometricLockForPasswords) {
                        onAuthenticateSensitive("Export full browser backup") { success ->
                            if (success) launch()
                            else statusMessage = "Full backup export was not authorized."
                        }
                    } else {
                        launch()
                    }
                }
            }
            M7SecondaryButton("Restore Backup", Modifier.weight(1f)) {
                if (fullBackupPassphrase.length < 10) {
                    statusMessage = "Enter the backup passphrase before restoring."
                } else {
                    val launch = {
                        restoreFullBackup.launch(
                            arrayOf("application/octet-stream", "application/*"),
                        )
                    }
                    if (TahoBrowserStateStore.settings.biometricLockForPasswords) {
                        onAuthenticateSensitive("Restore full browser backup") { success ->
                            if (success) launch()
                            else statusMessage = "Full backup restore was not authorized."
                        }
                    } else {
                        launch()
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SettingsSectionTitle("Data export & portability")
        Text(
            "Choose the destination with Android's system document picker. Taho never fabricates an export success.",
            color = TahoMuted,
            fontFamily = TahoSans,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(14.dp))

        statusMessage?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairlineStrong, TahoBlockShape)
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message,
                        color = TahoText,
                        fontFamily = TahoSans,
                        fontSize = 10.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "×",
                        color = TahoMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.heightIn(min = TahoCompactTouchTarget).clickable { statusMessage = null },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        M7SecondaryButton("Export Bookmarks (HTML)", Modifier.fillMaxWidth()) {
            exportBookmarks.launch("taho-bookmarks.html")
        }
        Spacer(Modifier.height(8.dp))
        M7SecondaryButton("Export Passwords (JSON — contains secrets)", Modifier.fillMaxWidth()) {
            val launchExport = { exportPasswords.launch("taho-passwords.json") }
            if (TahoBrowserStateStore.settings.biometricLockForPasswords) {
                onAuthenticateSensitive("Export saved passwords") { success ->
                    if (success) launchExport()
                    else statusMessage = "Password export was not authorized."
                }
            } else {
                launchExport()
            }
        }
        Spacer(Modifier.height(8.dp))
        M7SecondaryButton("Export Core Settings (JSON)", Modifier.fillMaxWidth()) {
            exportSettings.launch("taho-settings.json")
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("Data import")
        M7SecondaryButton("Import Bookmarks (HTML)", Modifier.fillMaxWidth()) {
            importBookmarks.launch(arrayOf("text/html", "text/plain", "application/xhtml+xml"))
        }
        Spacer(Modifier.height(8.dp))
        M7SecondaryButton("Import Passwords (Taho JSON)", Modifier.fillMaxWidth()) {
            importPasswords.launch(arrayOf("application/json", "text/plain"))
        }
        Spacer(Modifier.height(8.dp))
        M7SecondaryButton("Import Chrome / Firefox Passwords (CSV)", Modifier.fillMaxWidth()) {
            importBrowserPasswordsCsv.launch(arrayOf("text/csv", "text/plain", "application/csv"))
        }
    }
}

// -------------------------------------------------------------
// 19. COLLECTIONS
// -------------------------------------------------------------
@Composable
private fun SettingsCollectionsPage() {
    val collections = TahoBrowserStateStore.collections
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var colName by rememberSaveable { mutableStateOf("") }
    var colDesc by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .heightIn(min = TahoTouchTarget)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionTitle("BROWSER COLLECTIONS (${collections.size})")
            TahoInlineAction(
                label = "Add collection",
                icon = TahoIconName.ADD,
                onClick = { showAddDialog = true },
            )
        }
        Text("Group related research links, papers, and capture sessions into persistent workspaces.", color = TahoMuted, fontFamily = TahoSans, fontSize = 10.sp)
        Spacer(Modifier.height(14.dp))

        if (showAddDialog) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold, TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Create Collection", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = colName,
                    onValueChange = { colName = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                    decorationBox = { if (colName.isEmpty()) Text("Collection Name", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() }
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = colDesc,
                    onValueChange = { colDesc = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 11.sp),
                    decorationBox = { if (colDesc.isEmpty()) Text("Description (Optional)", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.sp); it() }
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    M7SecondaryButton("Cancel", Modifier.weight(1f)) { showAddDialog = false }
                    M7PrimaryButton("Create", showArrow = false, modifier = Modifier.weight(1f), enabled = colName.isNotBlank()) {
                        TahoBrowserStateStore.addCollection(colName.trim(), colDesc.trim())
                        colName = ""
                        colDesc = ""
                        showAddDialog = false
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (collections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceRow).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No collections created yet.", color = TahoFaint, fontFamily = TahoSans, fontSize = 10.sp)
            }
        } else {
            collections.forEach { col ->
                Row(
                    modifier = Modifier
                        .heightIn(min = TahoTouchTarget)
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoHairline, TahoBlockShape)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(col.name, color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Text("${col.linkCount} links · ${col.description}", color = TahoFaint, fontFamily = TahoSans, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TahoIconButton(
                        name = TahoIconName.DELETE,
                        contentDescription = "Delete ${col.name} collection",
                        destructive = true,
                        onClick = { TahoBrowserStateStore.removeCollection(col.id) },
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// 20. ONBOARDING
// -------------------------------------------------------------
@Composable
private fun SettingsOnboardingPage(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(1) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        when (step) {
            1 -> {
                Text("Welcome to Taho Browser", color = TahoText, fontFamily = TahoDisplay, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Engineered for builders, security researchers, and privacy purists.", color = TahoMuted, fontFamily = TahoSans, fontSize = 11.sp)
                Spacer(Modifier.height(20.dp))
                OnboardingFeature("⬡", "AMOLED Pitch Black Chrome", "Zero eye strain, battery-efficient true OLED blacks.")
                OnboardingFeature("🛡", "Strict Zero-Trust Defense", "Trackers, fingerprinting, and cryptominers blocked by default.")
                OnboardingFeature("⚡", "Live Network Capture", "Inspect, replay, and transfer API payloads directly to Project-Taho.")
                Spacer(Modifier.height(24.dp))
                M7PrimaryButton("Continue", modifier = Modifier.fillMaxWidth()) { step = 2 }
            }
            2 -> {
                Text("Select Default Search Engine", color = TahoText, fontFamily = TahoDisplay, fontSize = 18.sp)
                Spacer(Modifier.height(14.dp))
                SettingsSearchEnginePage()
                Spacer(Modifier.height(20.dp))
                M7PrimaryButton("Continue", modifier = Modifier.fillMaxWidth()) { step = 3 }
            }
            3 -> {
                Text("You're Ready to Browse", color = TahoText, fontFamily = TahoDisplay, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("All systems are verified and calibrated.", color = TahoMuted, fontFamily = TahoSans, fontSize = 11.sp)
                Spacer(Modifier.height(24.dp))
                M7PrimaryButton("Start Browsing", modifier = Modifier.fillMaxWidth()) {
                    TahoBrowserStateStore.updateSettings { it.copy(hasCompletedOnboarding = true) }
                    onFinish()
                }
            }
        }
    }
}

@Composable
private fun OnboardingFeature(icon: String, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TahoText, fontFamily = TahoSans, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = TahoFaint, fontFamily = TahoSans, fontSize = 9.sp)
        }
    }
}

// -------------------------------------------------------------
// 21. WHAT'S NEW
// -------------------------------------------------------------
@Composable
private fun SettingsWhatsNewPage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("What's New in Taho v0.1.0", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        listOf(
            "Flagship Start Page" to "Full customizable new tab with procedural shortcuts, quick search, recent tabs & AMOLED canvas themes.",
            "Complete Browser Settings Hub" to "20+ comprehensive preference panels covering themes, privacy guards, autofill & extensions.",
            "In-Page Reader & Translation" to "Distraction-free typography reader mode and inline instant language translation.",
            "Find in Page & QR Code Share" to "Fast match counting with procedural scannable QR Code generation.",
            "Full Site Information Panel" to "TLS 1.3 certificate inspection and granular per-site permission toggles.",
            "Tab Groups & Restorations" to "Color-coded tab grouping, search, and one-tap restore for recently closed tabs.",
        ).forEach { (title, desc) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
            ) {
                Text(title, color = TahoText, fontFamily = TahoSans, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(desc, color = TahoMuted, fontFamily = TahoSans, fontSize = 9.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// -------------------------------------------------------------
// 22. ABOUT
// -------------------------------------------------------------
@Composable
private fun SettingsAboutPage(
    onCheckForUpdates: ((BrowserUpdateStatusUi) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val packageInfo = remember(context.packageName) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    var checking by rememberSaveable { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<BrowserUpdateStatusUi?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("Taho browser")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoCardShape)
                .background(TahoSurfaceRow)
                .border(1.dp, TahoHairline, TahoCardShape)
                .padding(14.dp),
        ) {
            Column {
                DiagItem("Version", packageInfo?.versionName ?: "Unavailable")
                DiagItem(
                    "Build",
                    packageInfo?.longVersionCode?.toString() ?: "Unavailable",
                )
                DiagItem("Package", context.packageName)
                DiagItem("Browser engine", "Mozilla GeckoView")
            }
        }

        Spacer(Modifier.height(14.dp))
        M7SecondaryButton(
            label = if (checking) "Checking…" else "Check for Updates",
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!checking) {
                checking = true
                updateStatus = null
                onCheckForUpdates { result ->
                    checking = false
                    updateStatus = result
                }
            }
        }

        updateStatus?.let { status ->
            Spacer(Modifier.height(10.dp))
            val message = when {
                status.error != null -> status.error
                status.updateAvailable ->
                    "Update available: " + (status.latestVersion ?: "new release")
                status.latestVersion != null ->
                    "Installed version is current relative to release " + status.latestVersion + "."
                else -> "No release information was returned."
            }
            Text(
                text = message,
                color = if (status.updateAvailable) TahoGoldHi else TahoFaint,
                fontFamily = TahoSans,
                fontSize = 9.5.sp,
            )
            if (status.updateAvailable && status.releaseUrl != null) {
                Spacer(Modifier.height(8.dp))
                M7SecondaryButton("Open Release Page", Modifier.fillMaxWidth()) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(status.releaseUrl)),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("Legal & support")
        SettingsLinkRow("Privacy Policy", "https://taho.app/privacy")
        SettingsLinkRow("Terms of Service", "https://taho.app/terms")
        SettingsLinkRow("Open Source Licenses", "https://taho.app/licenses")
        SettingsLinkRow(
            "Report a Problem / Send Feedback",
            "https://github.com/vivek-tester/Taho-Browser/issues",
        )
    }
}

// -------------------------------------------------------------
// UI HELPERS
// -------------------------------------------------------------
@Composable
private fun SettingsSectionTitle(title: String) {
    TahoSectionLabel(title)
}

@Composable
private fun SettingsToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    TahoToggleRow(
        title = title,
        description = desc,
        checked = checked,
        onToggle = onToggle,
    )
}

@Composable
private fun SettingsCheckboxRow(
    title: String,
    count: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TahoBlockShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(TahoBadgeShape)
                    .background(if (checked) TahoGold else TahoRaised)
                    .border(1.dp, if (checked) TahoGold else TahoLine, TahoBadgeShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Text(
                        "✓",
                        color = TahoPrimaryInk,
                        fontFamily = TahoSans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = TahoText,
                fontFamily = TahoSans,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            count,
            color = TahoFaint,
            fontFamily = TahoSans,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StorageBarRow(label: String, sizeStr: String, fraction: Float, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TahoText, fontFamily = TahoSans, fontSize = 10.sp)
            Text(sizeStr, color = TahoMuted, fontFamily = TahoSans, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(TahoPillShape)
                .background(TahoText.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(TahoPillShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun DiagItem(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = TahoFaint, fontFamily = TahoSans, fontSize = 9.5.sp)
        Text(value, color = TahoText, fontFamily = TahoSans, fontSize = 9.5.sp)
    }
}

@Composable
private fun SettingsLinkRow(label: String, url: String) {
    val context = LocalContext.current
    TahoLinkRow(
        label = label,
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        },
    )
    Spacer(Modifier.height(8.dp))
}

