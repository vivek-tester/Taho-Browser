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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

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
        // Navigation Header with Back button if in subpage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentSubPage != SettingsSubPage.MAIN) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(TahoSurfaceControl)
                            .clickable { currentSubPage = SettingsSubPage.MAIN },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("‹", color = TahoGoldHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = when (currentSubPage) {
                            SettingsSubPage.MAIN -> "Settings Hub"
                            SettingsSubPage.APPEARANCE -> "Appearance & Theme"
                            SettingsSubPage.SEARCH_ENGINE -> "Search Engines"
                            SettingsSubPage.PRIVACY_SECURITY -> "Privacy & Protection"
                            SettingsSubPage.CLEAR_DATA -> "Clear Browsing Data"
                            SettingsSubPage.BOOKMARKS -> "Bookmarks & Reading"
                            SettingsSubPage.HISTORY -> "Browsing History"
                            SettingsSubPage.DOWNLOADS -> "Download Manager"
                            SettingsSubPage.PASSWORDS -> "Password Vault"
                            SettingsSubPage.AUTOFILL -> "Autofill & Payments"
                            SettingsSubPage.PROFILES_SYNC -> "Profiles & Sync"
                            SettingsSubPage.EXTENSIONS -> "Extensions & Add-Ons"
                            SettingsSubPage.ACCESSIBILITY -> "Accessibility"
                            SettingsSubPage.LANGUAGES -> "Languages & Region"
                            SettingsSubPage.DEFAULT_BROWSER -> "Default Browser"
                            SettingsSubPage.PERFORMANCE_MEDIA -> "Performance & Media"
                            SettingsSubPage.STORAGE_USAGE -> "Storage Usage"
                            SettingsSubPage.DIAGNOSTICS -> "Diagnostics & Reset"
                            SettingsSubPage.BACKUP_EXPORT -> "Backup & Restore"
                            SettingsSubPage.COLLECTIONS -> "Collections"
                            SettingsSubPage.ONBOARDING -> "Browser Setup"
                            SettingsSubPage.WHATS_NEW -> "What's New"
                            SettingsSubPage.ABOUT -> "About Taho Browser"
                        },
                        color = TahoText,
                        fontFamily = TahoDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = "Taho Browser v0.1.0-release",
                        color = TahoFaint,
                        fontFamily = TahoMono,
                        fontSize = 9.sp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(TahoSurfaceControl)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = TahoMuted, fontSize = 16.sp)
            }
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
                SettingsSubPage.PROFILES_SYNC -> SettingsProfilesSyncPage()
                SettingsSubPage.EXTENSIONS -> SettingsExtensionsPage(
                    onRefresh = onRefreshExtensions,
                    onInstall = onInstallExtension,
                    onSetEnabled = onSetExtensionEnabled,
                    onSetPrivate = onSetExtensionPrivate,
                    onUpdate = onUpdateExtension,
                    onUninstall = onUninstallExtension,
                    onBrowseMarketplace = {
                        onDismiss()
                        onNavigateUrl("https://addons.mozilla.org/android/")
                    },
                )
                SettingsSubPage.ACCESSIBILITY -> SettingsAccessibilityPage()
                SettingsSubPage.LANGUAGES -> SettingsLanguagesPage()
                SettingsSubPage.DEFAULT_BROWSER -> SettingsDefaultBrowserPage()
                SettingsSubPage.PERFORMANCE_MEDIA -> SettingsPerformanceMediaPage()
                SettingsSubPage.STORAGE_USAGE -> SettingsStorageUsagePage()
                SettingsSubPage.DIAGNOSTICS -> SettingsDiagnosticsPage()
                SettingsSubPage.BACKUP_EXPORT -> SettingsBackupExportPage(
                    onAuthenticateSensitive = onAuthenticateSensitive,
                )
                SettingsSubPage.COLLECTIONS -> SettingsCollectionsPage()
                SettingsSubPage.ONBOARDING -> SettingsOnboardingPage(onFinish = { currentSubPage = SettingsSubPage.MAIN })
                SettingsSubPage.WHATS_NEW -> SettingsWhatsNewPage()
                SettingsSubPage.ABOUT -> SettingsAboutPage()
            }
        }
    }
}

// -------------------------------------------------------------
// MAIN INDEX
// -------------------------------------------------------------
@Composable
private fun SettingsMainIndex(onNavigateSub: (SettingsSubPage) -> Unit) {
    var searchFilter by rememberSaveable { mutableStateOf("") }

    val categories = listOf(
        Triple("🎨 Appearance & Theme", "OLED Dark mode, accent colors, toolbar layout", SettingsSubPage.APPEARANCE),
        Triple("🔍 Search Engines", "Default search, suggestions & autocomplete", SettingsSubPage.SEARCH_ENGINE),
        Triple("🛡 Privacy & Tracking Protection", "Strict tracker blocking, HTTPS-Only, Secure DNS", SettingsSubPage.PRIVACY_SECURITY),
        Triple("🗑 Clear Browsing Data", "Wipe cache, cookies, history, saved logins", SettingsSubPage.CLEAR_DATA),
        Triple("★ Bookmarks & Reading List", "Organize favorites, folders, save for later", SettingsSubPage.BOOKMARKS),
        Triple("⏱ Browsing History", "Timelines, search history, recently closed tabs", SettingsSubPage.HISTORY),
        Triple("↓ Download Manager", "Active transfers, history, pause & resume", SettingsSubPage.DOWNLOADS),
        Triple("🔑 Password Manager & Vault", "Saved logins, security audit & password generator", SettingsSubPage.PASSWORDS),
        Triple("📝 Autofill & Payment Methods", "Saved addresses, contact cards & secure credit cards", SettingsSubPage.AUTOFILL),
        Triple("👥 Profiles & Cross-Device Sync", "Multi-profile, guest mode, device syncing", SettingsSubPage.PROFILES_SYNC),
        Triple("🧩 Extensions & Add-Ons", "uBlock Origin, privacy shields & content blockers", SettingsSubPage.EXTENSIONS),
        Triple("♿ Accessibility", "Reduced motion, high contrast, font scaling", SettingsSubPage.ACCESSIBILITY),
        Triple("🌐 Languages & Translation", "Interface language, web content & auto-translate", SettingsSubPage.LANGUAGES),
        Triple("⚡ Performance & Media", "Memory saver, background audio, hardware accel", SettingsSubPage.PERFORMANCE_MEDIA),
        Triple("📱 Default Browser Settings", "Set Taho as default web handler & link dispatch", SettingsSubPage.DEFAULT_BROWSER),
        Triple("💾 Storage Usage", "Cache analysis, site data storage & cookies", SettingsSubPage.STORAGE_USAGE),
        Triple("📦 Backup & Data Export", "Import/Export bookmarks, passwords & settings", SettingsSubPage.BACKUP_EXPORT),
        Triple("📁 Collections", "Foldered saved sites & research sessions", SettingsSubPage.COLLECTIONS),
        Triple("🔧 Diagnostics & Reset", "Engine specs, GPU info & reset browser", SettingsSubPage.DIAGNOSTICS),
        Triple("🚀 Browser Onboarding", "First-run welcome & guided configuration", SettingsSubPage.ONBOARDING),
        Triple("✨ What's New", "Release notes and latest architecture upgrades", SettingsSubPage.WHATS_NEW),
        Triple("ⓘ About Taho Browser", "Version, open source licenses & privacy policy", SettingsSubPage.ABOUT),
    )

    val filtered = categories.filter {
        searchFilter.isBlank() ||
            it.first.contains(searchFilter, ignoreCase = true) ||
            it.second.contains(searchFilter, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    if (searchFilter.isEmpty()) {
                        Text("Search settings…", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                    }
                    BasicTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        cursorBrush = SolidColor(TahoGold),
                    )
                }
                if (searchFilter.isNotEmpty()) {
                    Text("×", color = TahoMuted, fontSize = 14.sp, modifier = Modifier.clickable { searchFilter = "" })
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered) { (title, subtitle, page) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoHairline, TahoBlockShape)
                        .clickable { onNavigateSub(page) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, color = TahoFaint, fontFamily = TahoMono, fontSize = 9.sp)
                    }
                    Text("›", color = TahoFaint, fontSize = 16.sp)
                }
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
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("THEME MODE")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TahoThemeMode.DARK to "Dark (OLED)", TahoThemeMode.LIGHT to "Light", TahoThemeMode.SYSTEM to "System").forEach { (m, label) ->
                val sel = settings.themeMode == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TahoPillShape)
                        .background(if (sel) TahoGold else TahoSurfaceControl)
                        .border(1.dp, if (sel) TahoGoldHi else TahoHairline, TahoPillShape)
                        .clickable { TahoBrowserStateStore.updateSettings { it.copy(themeMode = m) } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) TahoBg else TahoText, fontFamily = TahoMono, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("BROWSER ACCENT COLOR")
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(hex))
                        .border(2.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                        .clickable { TahoBrowserStateStore.updateSettings { it.copy(accentColorHex = hex) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel) Text("✓", color = Color.Black, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("TOOLBAR & ADDRESS BAR POSITION")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TahoToolbarPosition.BOTTOM to "Bottom (One-Handed)", TahoToolbarPosition.TOP to "Top (Classic)").forEach { (pos, label) ->
                val sel = settings.toolbarPosition == pos
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TahoPillShape)
                        .background(if (sel) TahoGold else TahoSurfaceControl)
                        .clickable { TahoBrowserStateStore.updateSettings { it.copy(toolbarPosition = pos) } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) TahoBg else TahoText, fontFamily = TahoMono, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("HOMEPAGE CUSTOMIZATION")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TahoHomePageMode.START_PAGE to "Default Start Page", TahoHomePageMode.CUSTOM_URL to "Custom URL").forEach { (mode, label) ->
                val sel = settings.homePageMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TahoPillShape)
                        .background(if (sel) TahoGold else TahoSurfaceControl)
                        .clickable { TahoBrowserStateStore.updateSettings { it.copy(homePageMode = mode) } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) TahoBg else TahoText, fontFamily = TahoMono, fontSize = 10.sp)
                }
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
                textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp),
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
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionTitle("DEFAULT SEARCH ENGINE")
            Text(
                text = "+ Add Engine",
                color = TahoGoldHi,
                fontFamily = TahoMono,
                fontSize = 10.sp,
                modifier = Modifier
                    .clickable { showAddDialog = true }
                    .padding(4.dp)
            )
        }

        engines.forEach { engine ->
            val sel = settings.defaultSearchEngineId == engine.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(if (sel) TahoSurfaceRowHover else TahoSurfaceRow)
                    .border(1.dp, if (sel) TahoGold.copy(alpha = 0.4f) else TahoHairline, TahoBlockShape)
                    .clickable { TahoBrowserStateStore.updateSettings { it.copy(defaultSearchEngineId = engine.id) } }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(engine.iconGlyph, color = TahoGoldHi, fontFamily = TahoMono, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(engine.name, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp)
                        Text(engine.queryUrl, color = TahoFaint, fontFamily = TahoMono, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sel) {
                        Text("✓ Active", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (engines.size > 1 && !sel) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "×",
                            color = TahoMuted,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clickable { TahoBrowserStateStore.removeSearchEngine(engine.id) }
                                .padding(4.dp)
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
                    .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoCardShape)
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
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                    decorationBox = { innerTextField ->
                        if (newEngineName.isEmpty()) Text("Engine Name (e.g. SearXNG)", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
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
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                    decorationBox = { innerTextField ->
                        if (newEngineQueryUrl.isEmpty()) Text("URL with %s (e.g. https://searx.org/search?q=%s)", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
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
        SettingsSectionTitle("SEARCH SUGGESTIONS & AUTOCOMPLETE")
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
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("TRACKING PROTECTION LEVEL")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TahoTrackingProtectionLevel.STANDARD to "Standard",
                TahoTrackingProtectionLevel.STRICT to "Strict (Recommended)",
                TahoTrackingProtectionLevel.CUSTOM to "Custom",
            ).forEach { (level, label) ->
                val sel = settings.trackingProtectionLevel == level
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TahoPillShape)
                        .background(if (sel) TahoGold else TahoSurfaceControl)
                        .clickable { TahoBrowserStateStore.updateSettings { it.copy(trackingProtectionLevel = level) } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) TahoBg else TahoText, fontFamily = TahoMono, fontSize = 9.5.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("PROTECTION GUARDS")
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
        SettingsSectionTitle("SECURE DNS (DNS-OVER-HTTPS)")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(TahoSecureDns.CLOUDFLARE to "Cloudflare", TahoSecureDns.QUAD9 to "Quad9", TahoSecureDns.GOOGLE to "Google", TahoSecureDns.OFF to "System").forEach { (dns, label) ->
                val sel = settings.secureDns == dns
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TahoPillShape)
                        .background(if (sel) TahoGold else TahoSurfaceControl)
                        .clickable { TahoBrowserStateStore.updateSettings { it.copy(secureDns = dns) } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) TahoBg else TahoText, fontFamily = TahoMono, fontSize = 9.5.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("PRIVATE BROWSING GUARDS")
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
        SettingsSectionTitle("TRACKING PROTECTION EXCEPTIONS (${settings.perSiteTrackingExceptions.size})")
        if (settings.perSiteTrackingExceptions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
            ) {
                Text("No tracking exceptions active. Zero-trust protection is enforced across all web origins.", color = TahoFaint, fontFamily = TahoMono, fontSize = 10.sp)
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
                            Text(domain, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
                            Text("Protection bypassed for this origin", color = TahoWarn, fontFamily = TahoMono, fontSize = 8.5.sp)
                        }
                        Text(
                            text = "Remove",
                            color = TahoError,
                            fontFamily = TahoMono,
                            fontSize = 9.5.sp,
                            modifier = Modifier
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
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("TIME RANGE")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("HOUR" to "1 Hour", "24_HOURS" to "24 Hours", "7_DAYS" to "7 Days", "ALL_TIME" to "All Time").forEach { (key, label) ->
                val sel = selectedTimeRange == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TahoPillShape)
                        .background(if (sel) TahoGold else TahoSurfaceControl)
                        .clickable { selectedTimeRange = key }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) TahoBg else TahoText, fontFamily = TahoMono, fontSize = 9.5.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("DATA TYPES TO CLEAR")

        SettingsCheckboxRow("Browsing History", "${TahoBrowserStateStore.history.size} items", clearHistory) { clearHistory = !clearHistory }
        SettingsCheckboxRow("Cached Images & Files", "~14.2 MB", clearCache) { clearCache = !clearCache }
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
                    .background(TahoOk.copy(alpha = 0.15f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓ Browsing data cleared successfully", color = TahoOk, fontFamily = TahoMono, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (clearFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoError.copy(alpha = 0.15f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Browser engine data could not be fully cleared.",
                    color = TahoError,
                    fontFamily = TahoMono,
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
                        Text("Search saved pages…", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                    }
                    BasicTextField(
                        value = bookmarkSearch,
                        onValueChange = { bookmarkSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        cursorBrush = SolidColor(TahoGold),
                    )
                }
                if (bookmarkSearch.isNotEmpty()) {
                    Text("×", color = TahoMuted, fontSize = 14.sp, modifier = Modifier.clickable { bookmarkSearch = "" })
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
                        .weight(1f)
                        .clip(TahoPillShape)
                        .background(if (isSel) TahoGold else TahoSurfaceControl)
                        .clickable { activeTab = id }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (isSel) TahoBg else TahoText, fontFamily = TahoMono, fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
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
                    Box(
                        modifier = Modifier
                            .clip(TahoBadgeShape)
                            .background(if (allSel) TahoGoldHi.copy(alpha = 0.2f) else TahoSurfaceControl)
                            .border(1.dp, if (allSel) TahoGoldHi else TahoHairline, TahoBadgeShape)
                            .clickable { selectedFolder = null }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text("All", color = if (allSel) TahoGoldHi else TahoMuted, fontFamily = TahoMono, fontSize = 8.5.sp)
                    }
                    folders.forEach { f ->
                        val fSel = selectedFolder == f.id
                        Box(
                            modifier = Modifier
                                .clip(TahoBadgeShape)
                                .background(if (fSel) TahoGoldHi.copy(alpha = 0.2f) else TahoSurfaceControl)
                                .border(1.dp, if (fSel) TahoGoldHi else TahoHairline, TahoBadgeShape)
                                .clickable { selectedFolder = f.id }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text("📁 undefined", color = if (fSel) TahoGoldHi else TahoMuted, fontFamily = TahoMono, fontSize = 8.5.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(TahoBadgeShape)
                            .background(TahoSurfaceControl)
                            .border(1.dp, TahoHairline, TahoBadgeShape)
                            .clickable { showAddFolderDialog = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text("+ Folder", color = TahoFaint, fontFamily = TahoMono, fontSize = 8.5.sp)
                    }
                }

                Spacer(Modifier.width(6.dp))
                Text(
                    text = "+ Add",
                    color = TahoGoldHi,
                    fontFamily = TahoMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { showAddBookmarkDialog = true }
                        .padding(4.dp),
                )
            }

            if (showAddFolderDialog) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoBlockShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoGold.copy(alpha = 0.4f), TahoBlockShape)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newFolderName.isEmpty()) Text("New Folder Name…", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                            inner()
                        }
                    )
                    Text("Save", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 10.sp, modifier = Modifier.clickable {
                        if (newFolderName.isNotBlank()) {
                            TahoBrowserStateStore.addBookmarkFolder(newFolderName.trim())
                            newFolderName = ""
                            showAddFolderDialog = false
                        }
                    }.padding(horizontal = 6.dp))
                    Text("×", color = TahoMuted, fontSize = 14.sp, modifier = Modifier.clickable { showAddFolderDialog = false }.padding(horizontal = 4.dp))
                }
            }

            if (showAddBookmarkDialog) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TahoCardShape)
                        .background(TahoSurfaceRow)
                        .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoCardShape)
                        .padding(12.dp),
                ) {
                    Text("Add Bookmark", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = newBookmarkTitle,
                        onValueChange = { newBookmarkTitle = it },
                        modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newBookmarkTitle.isEmpty()) Text("Page Title", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = newBookmarkUrl,
                        onValueChange = { newBookmarkUrl = it },
                        modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newBookmarkUrl.isEmpty()) Text("https://example.com", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = newBookmarkFolder,
                        onValueChange = { newBookmarkFolder = it },
                        modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newBookmarkFolder.isEmpty()) Text("Folder (Optional)", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
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
                    Text("No bookmarks found", color = TahoFaint, fontFamily = TahoMono, fontSize = 10.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredBookmarks) { item ->
                        Row(
                            modifier = Modifier
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
                                    Text(item.title, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    item.folderId
                                        ?.let { folderId -> TahoBrowserStateStore.bookmarkFolders.firstOrNull { it.id == folderId } }
                                        ?.let { f ->
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(TahoBadgeShape)
                                                .background(TahoSurfaceControl)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(f.name, color = TahoGoldHi, fontFamily = TahoMono, fontSize = 7.5.sp)
                                        }
                                    }
                                }
                                Text(item.url, color = TahoFaint, fontFamily = TahoMono, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.clickable { TahoBrowserStateStore.removeBookmark(item.id) }.padding(6.dp))
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
                    Text("No reading list items", color = TahoFaint, fontFamily = TahoMono, fontSize = 10.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(readingList) { item ->
                        Row(
                            modifier = Modifier
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
                                Text(item.title, color = if (item.isRead) TahoMuted else TahoText, fontFamily = TahoMono, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (item.isRead) "✓ Read" else "Unread · Cached offline", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (item.isRead) "Unmark" else "Mark Read", color = TahoMuted, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable { TahoBrowserStateStore.toggleReadingListRead(item.id) }.padding(4.dp))
                                Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.clickable { TahoBrowserStateStore.removeReadingListItem(item.id) }.padding(4.dp))
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
                    Text("No offline saved pages", color = TahoFaint, fontFamily = TahoMono, fontSize = 10.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(offlinePages) { page ->
                        Row(
                            modifier = Modifier
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
                                Text(page.title, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${page.url} · ${page.sizeBytes / 1024} KB PDF snapshot",
                                    color = TahoFaint,
                                    fontFamily = TahoMono,
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                "×",
                                color = TahoMuted,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { onDeleteOfflinePage(page) }
                                    .padding(6.dp),
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
                            fontFamily = TahoMono,
                            fontSize = 11.sp,
                        )
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        cursorBrush = SolidColor(TahoGold),
                    )
                }
                if (search.isNotEmpty()) {
                    Text(
                        "×",
                        color = TahoMuted,
                        fontSize = 14.sp,
                        modifier = Modifier
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
            Text("${history.size} visits recorded", color = TahoFaint, fontFamily = TahoMono, fontSize = 9.5.sp)
            Text("Clear All", color = TahoError, fontFamily = TahoMono, fontSize = 9.5.sp, modifier = Modifier.clickable { TahoBrowserStateStore.clearAllHistory() })
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(history) { entry ->
                Row(
                    modifier = Modifier
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
                        Text(entry.title, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.url, color = TahoFaint, fontFamily = TahoMono, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.clickable { TahoBrowserStateStore.removeHistoryEntry(entry.id) }.padding(6.dp))
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
                    Text(dl.fileName, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(TahoBadgeShape)
                            .background(when (dl.status) {
                                TahoDownloadStatus.COMPLETED -> TahoOk.copy(alpha = 0.2f)
                                TahoDownloadStatus.DOWNLOADING -> TahoGold.copy(alpha = 0.2f)
                                TahoDownloadStatus.PAUSED -> TahoWarn.copy(alpha = 0.2f)
                                else -> TahoError.copy(alpha = 0.2f)
                            })
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(dl.status.name, color = when (dl.status) {
                            TahoDownloadStatus.COMPLETED -> TahoOk
                            TahoDownloadStatus.DOWNLOADING -> TahoGoldHi
                            TahoDownloadStatus.PAUSED -> TahoWarn
                            else -> TahoError
                        }, fontFamily = TahoMono, fontSize = 8.5.sp)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(TahoPillShape)
                        .background(Color.White.copy(alpha = 0.08f)),
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
                        fontFamily = TahoMono,
                        fontSize = 9.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dl.status == TahoDownloadStatus.DOWNLOADING || dl.status == TahoDownloadStatus.PAUSED) {
                            Text(if (dl.status == TahoDownloadStatus.DOWNLOADING) "Pause" else "Resume", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable { onPauseResume(dl.id) })
                            Text("Cancel", color = TahoError, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable { onCancel(dl.id) })
                        } else if (dl.status == TahoDownloadStatus.FAILED) {
                            Text("Retry", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable { onRetry(dl) })
                        }
                        if (dl.status == TahoDownloadStatus.COMPLETED && dl.localPath != null) {
                            Text("Open", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable { onOpen(dl) })
                        }
                        Text("Delete", color = TahoFaint, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable { onDelete(dl) })
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
                Text(message, color = TahoMuted, fontFamily = TahoMono, fontSize = 9.5.sp)
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
                        Text("Search logins & credentials…", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        cursorBrush = SolidColor(TahoGold),
                    )
                }
                if (search.isNotEmpty()) {
                    Text("×", color = TahoMuted, fontSize = 14.sp, modifier = Modifier.clickable { search = "" })
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${passwords.size} credentials stored", color = TahoFaint, fontFamily = TahoMono, fontSize = 9.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("+ Generate", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.5.sp, modifier = Modifier.clickable {
                    generatedPassword = TahoBrowserStateStore.generateStrongPassword(18)
                })
                Text("+ Add Login", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                    showAddDialog = true
                })
            }
        }

        if (generatedPassword.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoGold.copy(alpha = 0.15f))
                    .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoBlockShape)
                    .padding(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("GENERATED SECURE PASSWORD", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 8.5.sp)
                        Text(generatedPassword, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Done", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 10.sp, modifier = Modifier.clickable { generatedPassword = "" })
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
                    fontFamily = TahoMono,
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
                    .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Add New Login", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = newDomain,
                    onValueChange = { newDomain = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                    decorationBox = { inner ->
                        if (newDomain.isEmpty()) Text("Domain / Website (e.g. github.com)", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                        inner()
                    }
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                    decorationBox = { inner ->
                        if (newUsername.isEmpty()) Text("Username / Email", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                        inner()
                    }
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier.weight(1f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                        textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                        decorationBox = { inner ->
                            if (newPassword.isEmpty()) Text("Password", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Gen", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 10.sp, modifier = Modifier.clickable {
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
                    .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Edit Credential", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = editUsername,
                    onValueChange = { editUsername = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = editPassword,
                    onValueChange = { editPassword = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
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
                        Text(pw.domain, color = TahoText, fontFamily = TahoMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (pw.isCompromised) {
                                Text("⚠️ COMPROMISED", color = TahoError, fontFamily = TahoMono, fontSize = 8.sp)
                            } else if (pw.isWeak) {
                                Text("⚠️ WEAK", color = TahoWarn, fontFamily = TahoMono, fontSize = 8.sp)
                            }
                            if (isReused) {
                                Text("⚠️ REUSED", color = TahoWarn, fontFamily = TahoMono, fontSize = 8.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("User: ${pw.username}", color = TahoMuted, fontFamily = TahoMono, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRevealed) pw.password else "••••••••••••",
                            color = if (isRevealed) TahoGoldHi else TahoFaint,
                            fontFamily = TahoMono,
                            fontSize = 11.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isRevealed) "Hide" else "Reveal", color = TahoMuted, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable {
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
                                fontFamily = TahoMono,
                                fontSize = 9.sp,
                                modifier = Modifier.clickable {
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
                            Text("Edit", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable {
                                editingCredentialId = pw.id
                                editUsername = pw.username
                                editPassword = pw.password
                            })
                            Text("Delete", color = TahoError, fontFamily = TahoMono, fontSize = 9.sp, modifier = Modifier.clickable {
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
        SettingsSectionTitle("AUTOFILL SETTINGS")
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
            Text("+ Add Address", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.5.sp, modifier = Modifier.clickable { showAddAddress = true })
        }

        if (showAddAddress) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Add Address", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = addrLabel, onValueChange = { addrLabel = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (addrLabel.isEmpty()) Text("Label (e.g. Home, Office)", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = addrFullName, onValueChange = { addrFullName = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (addrFullName.isEmpty()) Text("Full Name", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = addrStreet, onValueChange = { addrStreet = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (addrStreet.isEmpty()) Text("Street Address", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicTextField(value = addrCity, onValueChange = { addrCity = it }, modifier = Modifier.weight(1f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (addrCity.isEmpty()) Text("City", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
                    BasicTextField(value = addrState, onValueChange = { addrState = it }, modifier = Modifier.weight(0.5f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (addrState.isEmpty()) Text("State", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
                    BasicTextField(value = addrZip, onValueChange = { addrZip = it }, modifier = Modifier.weight(0.6f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (addrZip.isEmpty()) Text("Zip", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
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
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(addr.label, color = TahoGoldHi, fontFamily = TahoMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(addr.fullName, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
                    Text("${addr.street}, ${addr.city}, ${addr.state} ${addr.zipCode}", color = TahoMuted, fontFamily = TahoMono, fontSize = 9.sp)
                }
                Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.clickable {
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
            Text("+ Add Card", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.5.sp, modifier = Modifier.clickable { showAddPayment = true })
        }

        if (showAddPayment) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Add Payment Card", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = payHolder, onValueChange = { payHolder = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (payHolder.isEmpty()) Text("Cardholder Name", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                BasicTextField(value = payNumber, onValueChange = { payNumber = it }, modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (payNumber.isEmpty()) Text("Card Number", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicTextField(value = payExpiry, onValueChange = { payExpiry = it }, modifier = Modifier.weight(1f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (payExpiry.isEmpty()) Text("MM/YY", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
                    BasicTextField(value = payType, onValueChange = { payType = it }, modifier = Modifier.weight(1f).clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp), textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp), decorationBox = { if (payType.isEmpty()) Text("Card Type (Visa/MC)", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() })
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
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pay.cardHolder, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
                    Text("${pay.cardType} · ${TahoBrowserStateStore.maskedPaymentNumber(pay)} (Exp: ${pay.cardExpiry})", color = TahoMuted, fontFamily = TahoMono, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💳", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.clickable {
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
private fun SettingsProfilesSyncPage() {
    val profiles = TahoBrowserStateStore.profiles
    val devices = TahoBrowserStateStore.syncedDevices

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("LOCAL BROWSER PROFILES")
        profiles.forEach { profile ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(if (profile.isActive) TahoSurfaceRowHover else TahoSurfaceRow)
                    .border(1.dp, if (profile.isActive) TahoGold.copy(alpha = 0.5f) else TahoHairline, TahoBlockShape)
                    .clickable { TahoBrowserStateStore.switchProfile(profile.id) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.avatarGlyph, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(profile.name, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp)
                        Text(
                            if (profile.isGuest) "Guest profile" else "Local profile metadata",
                            color = TahoFaint,
                            fontFamily = TahoMono,
                            fontSize = 8.5.sp,
                        )
                    }
                }
                if (profile.isActive) {
                    Text("✓ Active", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("CROSS-DEVICE SYNC")
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
                    fontFamily = TahoMono,
                    fontSize = 9.5.sp,
                )
            }
        }

        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SettingsSectionTitle("SYNCED DEVICES")
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
                        Text(dev.name, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
                        Text("Type: ${dev.deviceType}", color = TahoFaint, fontFamily = TahoMono, fontSize = 9.sp)
                    }
                    Text("Read only", color = TahoFaint, fontFamily = TahoMono, fontSize = 9.sp)
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
    onBrowseMarketplace: () -> Unit,
) {
    val exts = TahoBrowserStateStore.extensions
    val pwas = TahoBrowserStateStore.installedPwas
    var xpiUrl by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("MOZILLA WEBEXTENSIONS")
        Text(
            "Installed extensions below come directly from GeckoView. New packages are validated and must be Mozilla-signed before Gecko installs them.",
            color = TahoMuted,
            fontFamily = TahoMono,
            fontSize = 9.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            M7SecondaryButton(
                "Browse Mozilla Add-ons",
                Modifier.weight(1f),
                onClick = onBrowseMarketplace,
            )
            M7SecondaryButton(
                "Refresh",
                Modifier.weight(0.55f),
                onClick = onRefresh,
            )
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
                fontFamily = TahoMono,
                fontSize = 10.sp,
            ),
            cursorBrush = SolidColor(TahoGold),
            decorationBox = { inner ->
                Box {
                    if (xpiUrl.isBlank()) {
                        Text(
                            "https://…/addon.xpi",
                            color = TahoFaint,
                            fontFamily = TahoMono,
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
                    fontFamily = TahoMono,
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
                            fontFamily = TahoMono,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "v${ext.version} by ${ext.author}",
                            color = TahoFaint,
                            fontFamily = TahoMono,
                            fontSize = 8.5.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(TahoPillShape)
                            .background(if (ext.isEnabled) TahoOk else TahoSurfaceControl)
                            .clickable { onSetEnabled(ext.id, !ext.isEnabled) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (ext.isEnabled) "ACTIVE" else "DISABLED",
                            color = if (ext.isEnabled) TahoBg else TahoFaint,
                            fontFamily = TahoMono,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (ext.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ext.description,
                        color = TahoMuted,
                        fontFamily = TahoMono,
                        fontSize = 9.5.sp,
                    )
                }

                if (ext.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Permissions: " + ext.permissions.take(6).joinToString(", "),
                        color = TahoFaint,
                        fontFamily = TahoMono,
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
                    Text(
                        if (ext.allowedInPrivate) "Private: Allowed" else "Private: Blocked",
                        color = TahoGoldHi,
                        fontFamily = TahoMono,
                        fontSize = 8.5.sp,
                        modifier = Modifier.clickable {
                            onSetPrivate(ext.id, !ext.allowedInPrivate)
                        },
                    )
                    Text(
                        "Check Update",
                        color = TahoMuted,
                        fontFamily = TahoMono,
                        fontSize = 8.5.sp,
                        modifier = Modifier.clickable { onUpdate(ext.id) },
                    )
                    Text(
                        "Uninstall",
                        color = TahoError,
                        fontFamily = TahoMono,
                        fontSize = 8.5.sp,
                        modifier = Modifier.clickable { onUninstall(ext.id) },
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
                    fontFamily = TahoMono,
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
                            fontFamily = TahoMono,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            pwa.url,
                            color = TahoFaint,
                            fontFamily = TahoMono,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "Installed",
                        color = TahoOk,
                        fontFamily = TahoMono,
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
        SettingsSectionTitle("MOTION & CONTRAST")
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
        SettingsSectionTitle("BROWSER INTERFACE LANGUAGE")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoBlockShape)
                .background(TahoSurfaceControl)
                .padding(12.dp),
        ) {
            Text(settings.browserLanguage, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("PREFERRED WEBSITE LANGUAGES")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoBlockShape)
                .background(TahoSurfaceControl)
                .padding(12.dp),
        ) {
            Text(settings.preferredWebLanguage, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("PAGE TRANSLATION")
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
        SettingsSectionTitle("DEFAULT APPLICATION DISPATCH")
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
                    fontFamily = TahoMono,
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
        SettingsSectionTitle("STARTUP & SESSION RESTORATION")
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                TahoStartupBehavior.PREVIOUS_TABS to "Continue Previous Tabs",
                TahoStartupBehavior.START_PAGE to "Open Start Page",
                TahoStartupBehavior.CUSTOM_PAGE to "Open Custom Page",
            ).forEach { (mode, label) ->
                val selected = settings.startupBehavior == mode
                Row(
                    modifier = Modifier
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
                        fontFamily = TahoMono,
                        fontSize = 10.sp,
                    )
                    if (selected) {
                        Text("✓", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 10.sp)
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
                    fontFamily = TahoMono,
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
                                fontFamily = TahoMono,
                                fontSize = 10.5.sp,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("AUTOMATIC INACTIVE TAB CLOSING")
        Row(
            modifier = Modifier
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
                        fontFamily = TahoMono,
                        fontSize = 9.5.sp,
                    )
                }
            }
        }
        Text(
            text = "Pinned tabs are excluded from automatic cleanup.",
            color = TahoFaint,
            fontFamily = TahoMono,
            fontSize = 8.5.sp,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("MEMORY & SYSTEM EFFICIENCY")
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
        SettingsSectionTitle("MEDIA PLAYBACK")
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
    val siteData = TahoBrowserStateStore.siteData
    val offlinePages = TahoBrowserStateStore.offlinePages
    val offlineBytes = offlinePages.sumOf { it.sizeBytes }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("LOCAL BROWSER STORAGE")
        DiagItem("Offline page records", offlinePages.size.toString())
        DiagItem("Recorded offline bytes", "${offlineBytes / 1024} KB")
        DiagItem("Tracked site-data origins", siteData.size.toString())
        Spacer(Modifier.height(8.dp))
        Text(
            "Exact Gecko cache, IndexedDB and cookie byte totals are not exposed by the current Browser storage adapter, so Taho does not estimate them.",
            color = TahoFaint,
            fontFamily = TahoMono,
            fontSize = 9.5.sp,
        )

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("SITE STORAGE (${siteData.size} DOMAINS)")

        if (siteData.isEmpty()) {
            Text(
                "No per-origin storage measurements are available from the current runtime adapter.",
                color = TahoFaint,
                fontFamily = TahoMono,
                fontSize = 10.sp,
            )
        }

        siteData.forEach { data ->
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
                    Text(data.origin, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
                    Text(
                        "${data.cookieCount} cookies · ${data.storageSizeBytes / 1024} KB",
                        color = TahoFaint,
                        fontFamily = TahoMono,
                        fontSize = 8.5.sp,
                    )
                }
                Text(
                    "Clear",
                    color = TahoError,
                    fontFamily = TahoMono,
                    fontSize = 9.sp,
                    modifier = Modifier.clickable {
                        TahoBrowserStateStore.clearSiteDataForOrigin(data.origin)
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
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
        SettingsSectionTitle("RUNTIME DIAGNOSTICS")
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
        SettingsSectionTitle("CRASH REPORTING & TELEMETRY")
        Text(
            "No crash-report upload backend is connected in this build. The preference is stored locally only.",
            color = TahoFaint,
            fontFamily = TahoMono,
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
                Text(
                    "Clear All",
                    color = TahoError,
                    fontFamily = TahoMono,
                    fontSize = 9.5.sp,
                    modifier = Modifier.clickable {
                        TahoBrowserStateStore.clearWebsiteNotifications()
                    },
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
                    fontFamily = TahoMono,
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
                                fontFamily = TahoMono,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${item.origin} · ${item.message}",
                                color = TahoFaint,
                                fontFamily = TahoMono,
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "×",
                            color = TahoMuted,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clickable { TahoBrowserStateStore.removeWebsiteNotification(item.id) }
                                .padding(4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("RESET CONTROLS")
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
) {
    val context = LocalContext.current
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("DATA EXPORT & PORTABILITY")
        Text(
            "Choose the destination with Android's system document picker. Taho never fabricates an export success.",
            color = TahoMuted,
            fontFamily = TahoMono,
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
                        fontFamily = TahoMono,
                        fontSize = 10.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "×",
                        color = TahoMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { statusMessage = null },
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
        SettingsSectionTitle("DATA IMPORT")
        M7SecondaryButton("Import Bookmarks (HTML)", Modifier.fillMaxWidth()) {
            importBookmarks.launch(arrayOf("text/html", "text/plain", "application/xhtml+xml"))
        }
        Spacer(Modifier.height(8.dp))
        M7SecondaryButton("Import Passwords (Taho JSON)", Modifier.fillMaxWidth()) {
            importPasswords.launch(arrayOf("application/json", "text/plain"))
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
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsSectionTitle("BROWSER COLLECTIONS (${collections.size})")
            Text("+ Add Collection", color = TahoGoldHi, fontFamily = TahoMono, fontSize = 9.5.sp, modifier = Modifier.clickable { showAddDialog = true })
        }
        Text("Group related research links, papers, and capture sessions into persistent workspaces.", color = TahoMuted, fontFamily = TahoMono, fontSize = 10.sp)
        Spacer(Modifier.height(14.dp))

        if (showAddDialog) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoGold.copy(alpha = 0.35f), TahoCardShape)
                    .padding(12.dp),
            ) {
                Text("Create Collection", color = TahoGoldHi, fontFamily = TahoDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = colName,
                    onValueChange = { colName = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                    decorationBox = { if (colName.isEmpty()) Text("Collection Name", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() }
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = colDesc,
                    onValueChange = { colDesc = it },
                    modifier = Modifier.fillMaxWidth().clip(TahoBlockShape).background(TahoSurfaceControl).padding(8.dp),
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoMono, fontSize = 11.sp),
                    decorationBox = { if (colDesc.isEmpty()) Text("Description (Optional)", color = TahoFaint, fontFamily = TahoMono, fontSize = 11.sp); it() }
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
                Text("No collections created yet.", color = TahoFaint, fontFamily = TahoMono, fontSize = 10.sp)
            }
        } else {
            collections.forEach { col ->
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
                        Text(col.name, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Text("${col.linkCount} links · ${col.description}", color = TahoFaint, fontFamily = TahoMono, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("×", color = TahoMuted, fontSize = 16.sp, modifier = Modifier.clickable {
                        TahoBrowserStateStore.removeCollection(col.id)
                    }.padding(6.dp))
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
                Text("Engineered for builders, security researchers, and privacy purists.", color = TahoMuted, fontFamily = TahoMono, fontSize = 11.sp)
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
                Text("All systems are verified and calibrated.", color = TahoMuted, fontFamily = TahoMono, fontSize = 11.sp)
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
            Text(title, color = TahoText, fontFamily = TahoMono, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = TahoFaint, fontFamily = TahoMono, fontSize = 9.sp)
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
                Text(title, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(desc, color = TahoMuted, fontFamily = TahoMono, fontSize = 9.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// -------------------------------------------------------------
// 22. ABOUT
// -------------------------------------------------------------
@Composable
private fun SettingsAboutPage() {
    val context = LocalContext.current
    val packageInfo = remember(context.packageName) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionTitle("TAHO BROWSER")
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
        Text(
            "Automatic update checking is not connected in this build. Updates must be verified through the distribution channel that installed the app.",
            color = TahoFaint,
            fontFamily = TahoMono,
            fontSize = 9.5.sp,
        )

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle("LEGAL & SUPPORT")
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
    Text(
        text = title,
        color = TahoFaint,
        fontFamily = TahoMono,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SettingsToggleRow(title: String, desc: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TahoBlockShape)
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
            Text(desc, color = TahoFaint, fontFamily = TahoMono, fontSize = 8.5.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(TahoPillShape)
                .background(if (checked) TahoGold else TahoSurfaceControl)
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Text(if (checked) "ON" else "OFF", color = if (checked) TahoBg else TahoFaint, fontFamily = TahoMono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsCheckboxRow(title: String, count: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TahoBlockShape)
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (checked) TahoGold else TahoSurfaceControl)
                    .border(1.dp, if (checked) TahoGoldHi else TahoHairline, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Text("✓", color = TahoBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
        }
        Text(count, color = TahoFaint, fontFamily = TahoMono, fontSize = 9.sp)
    }
}

@Composable
private fun StorageBarRow(label: String, sizeStr: String, fraction: Float, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TahoText, fontFamily = TahoMono, fontSize = 10.sp)
            Text(sizeStr, color = TahoMuted, fontFamily = TahoMono, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(TahoPillShape)
                .background(Color.White.copy(alpha = 0.08f)),
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
        Text(key, color = TahoFaint, fontFamily = TahoMono, fontSize = 9.5.sp)
        Text(value, color = TahoText, fontFamily = TahoMono, fontSize = 9.5.sp)
    }
}

@Composable
private fun SettingsLinkRow(label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TahoBlockShape)
            .background(TahoSurfaceRow)
            .border(1.dp, TahoHairline, TahoBlockShape)
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TahoText, fontFamily = TahoMono, fontSize = 11.sp)
        Text("↗", color = TahoGoldHi, fontSize = 13.sp)
    }
    Spacer(Modifier.height(6.dp))
}
