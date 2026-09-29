package app.taho.browser.shell

import android.net.Uri
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowInsetsControllerCompat
import app.taho.browser.capture.domain.CaptureState

data class BrowserTabUiState(
    val id: String,
    val title: String?,
    val location: String?,
    val isPrivate: Boolean,
    val isLoading: Boolean,
    val loadFailed: Boolean,
    val crashed: Boolean,
    val selected: Boolean,
    val relevantCaptureCount: Int = 0,
)

data class SitePermissionUiState(
    val id: String,
    val origin: String,
    val title: String,
    val detail: String,
    val isPrivate: Boolean,
)

enum class BrowserAutofillPromptKindUi {
    LOGIN_SAVE,
    LOGIN_SELECT,
    ADDRESS_SAVE,
    ADDRESS_SELECT,
    CREDIT_CARD_SAVE,
    CREDIT_CARD_SELECT,
}

data class BrowserAutofillPromptOptionUi(
    val index: Int,
    val title: String,
    val subtitle: String?,
)

data class BrowserAutofillPromptUiState(
    val id: String,
    val origin: String?,
    val kind: BrowserAutofillPromptKindUi,
    val options: List<BrowserAutofillPromptOptionUi>,
)

data class WebAppManifestUi(
    val name: String,
    val shortName: String?,
    val startUrl: String,
    val scope: String?,
    val display: String?,
    val themeColor: String?,
    val backgroundColor: String?,
)

data class SiteSecurityUiState(
    val isSecure: Boolean,
    val isException: Boolean,
    val host: String,
    val certificateSubject: String?,
    val certificateIssuer: String?,
    val activeMixedContentLoaded: Boolean,
    val passiveMixedContentLoaded: Boolean,
)

data class BrowserUiState(
    val captureState: CaptureState = CaptureState.OFF,
    val relevantCount: Int = 0,
    val omniboxText: String = "Search or enter address",
    val tabCount: Int = 1,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val crashed: Boolean = false,
    val isPrivate: Boolean = false,
    val isPictureInPicture: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val securityInfo: SiteSecurityUiState? = null,
    val webAppManifest: WebAppManifestUi? = null,
    val sitePermission: SitePermissionUiState? = null,
    val autofillPrompt: BrowserAutofillPromptUiState? = null,
    val notice: String? = null,
    val captureCapabilityNote: String? = null,
    val tabs: List<BrowserTabUiState> = emptyList(),
    val captureRequests: List<M4CaptureRequestUiState> = emptyList(),
    val transferPhase: M7TransferPhaseUi = M7TransferPhaseUi.NOT_STARTED,
    val isTahoInstalled: Boolean = true,
    val retentionMode: String = "Session only",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TahoBrowserApp(
    state: BrowserUiState = BrowserUiState(),
    onCaptureClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onReload: () -> Unit = {},
    onNewTab: () -> Unit = {},
    onNewPrivateTab: () -> Unit = {},
    onSelectTab: (String) -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onSitePermissionDecision: (String, Boolean) -> Unit = { _, _ -> },
    onAutofillPromptDecision: (String, Int?) -> Unit = { _, _ -> },
    onDismissNotice: () -> Unit = {},
    onClearCaptureData: () -> Unit = {},
    onCopyCurl: (String) -> Unit = {},
    onShare: (String) -> Unit = {},
    onSendToTaho: (String, M4SecretPolicyUi) -> Unit = { _, _ -> },
    onDeleteRequest: (String) -> Unit = {},
    onInstallTaho: () -> Unit = {},
    onRetentionModeChanged: (String) -> Unit = {},
    onReplayRequest: (String) -> Unit = {},
    onFindInPage: (String, Boolean, (Int, Int) -> Unit) -> Unit = { _, _, callback -> callback(0, 0) },
    onClearFindInPage: () -> Unit = {},
    onSetDesktopMode: (Boolean) -> Unit = {},
    onClearBrowserStorage: (Boolean, Boolean, (Boolean) -> Unit) -> Unit =
        { _, _, callback -> callback(true) },
    onClearSiteDataForHost: (String, (Boolean) -> Unit) -> Unit =
        { _, callback -> callback(false) },
    onAuthenticateSensitive: (String, (Boolean) -> Unit) -> Unit =
        { _, callback -> callback(false) },
    onCheckPasswordBreach: (String, (Int?) -> Unit) -> Unit =
        { _, callback -> callback(null) },
    onDownloadPauseResume: (String) -> Unit = {},
    onDownloadCancel: (String) -> Unit = {},
    onDownloadRetry: (DownloadItemUi) -> Unit = {},
    onDownloadOpen: (DownloadItemUi) -> Unit = {},
    onDownloadDelete: (DownloadItemUi) -> Unit = {},
    onSaveOfflinePage: (String, String) -> Unit = { _, _ -> },
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
    onExtractReaderContent: ((ReaderPageContentUi?) -> Unit) -> Unit = { callback -> callback(null) },
    onPrintPage: () -> Boolean = { false },
    onAddToHomeScreen: (String, String) -> Unit = { _, _ -> },
    onInstallWebApp: (WebAppManifestUi) -> Unit = {},
    onTranslatePage: (
        targetLanguage: String,
        (Boolean, String?, String?) -> Unit,
    ) -> Unit = { _, callback -> callback(false, null, "Translation is unavailable.") },
    onRestorePageTranslation: ((Boolean, String?) -> Unit) -> Unit =
        { callback -> callback(false, "Translation is unavailable.") },
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
    browserContent: @Composable () -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var showTabs by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBrowserMenu by rememberSaveable { mutableStateOf(false) }
    var showSiteInfo by rememberSaveable { mutableStateOf(false) }
    var showShareQr by rememberSaveable { mutableStateOf(false) }
    var showReaderMode by rememberSaveable { mutableStateOf(false) }
    var readerContent by remember { mutableStateOf<ReaderPageContentUi?>(null) }
    var readerLoading by remember { mutableStateOf(false) }
    var readerError by remember { mutableStateOf<String?>(null) }
    var showTranslationBar by rememberSaveable { mutableStateOf(false) }
    var isPageTranslated by rememberSaveable { mutableStateOf(false) }
    var translationInProgress by rememberSaveable { mutableStateOf(false) }
    var translationSourceLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var translationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var findInPageActive by rememberSaveable { mutableStateOf(false) }
    var findInPageQuery by rememberSaveable { mutableStateOf("") }
    var currentFindMatchIndex by rememberSaveable { mutableStateOf(0) }
    var findMatchCount by rememberSaveable { mutableStateOf(0) }
    var isDesktopMode by rememberSaveable { mutableStateOf(false) }
    var settingsInitialSubPage by rememberSaveable { mutableStateOf<SettingsSubPage?>(null) }
    var showCaptureSummary by rememberSaveable { mutableStateOf(false) }
    var selectedCaptureId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSelectedCaptureId by rememberSaveable { mutableStateOf<String?>(null) }
    var captureFilter by rememberSaveable { mutableStateOf(M7CaptureFilterUi.RELEVANT) }
    val captureListState = rememberLazyListState()
    var showTransferConfirmation by rememberSaveable { mutableStateOf(false) }
    var selectedSecretPolicy by rememberSaveable {
        mutableStateOf(M4SecretPolicyUi.PARAMETERIZE)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var workspaceExpanded by rememberSaveable { mutableStateOf(false) }
    var originWarningTargetUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current

    val currentTab = state.tabs.firstOrNull { it.selected }
    val isStartPage = currentTab == null ||
        currentTab.location.isNullOrBlank() ||
        currentTab.location == "about:blank" ||
        currentTab.location == "taho://start" ||
        currentTab.location == "about:home"
    val isBookmarked = currentTab?.location?.let { loc ->
        TahoBrowserStateStore.bookmarks.any { it.url == loc }
    } ?: false
    val currentOrigin = currentTab?.location ?: ""
    val isOriginDesktop = TahoBrowserStateStore.isDesktopModeForOrigin(currentOrigin)
    val effectiveDesktop = isDesktopMode || isOriginDesktop
    val currentZoom = TahoBrowserStateStore.getZoomForOrigin(currentOrigin)

    val selectedCapture = state.captureRequests.firstOrNull { it.id == selectedCaptureId }
    val darkSystemChromeVisible =
        showCaptureSummary ||
            selectedCaptureId != null ||
            showTabs ||
            showSettings ||
            showBrowserMenu ||
            showSiteInfo ||
            showShareQr ||
            showReaderMode ||
            workspaceExpanded ||
            originWarningTargetUrl != null ||
            state.sitePermission != null ||
            state.autofillPrompt != null
    val rootView = LocalView.current

    val sensitiveScreenActive = selectedCapture != null || showTransferConfirmation || workspaceExpanded
    androidx.compose.runtime.DisposableEffect(sensitiveScreenActive) {
        val window = rootView.context.findActivity()?.window
        if (sensitiveScreenActive) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (sensitiveScreenActive) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    SideEffect {
        rootView.context.findActivity()?.window?.let { window ->
            WindowInsetsControllerCompat(window, rootView).apply {
                isAppearanceLightStatusBars = !darkSystemChromeVisible
                isAppearanceLightNavigationBars = !darkSystemChromeVisible
            }
        }
    }

    LaunchedEffect(state.tabs) {
        TahoBrowserStateStore.prunePinnedTabs(state.tabs.mapTo(mutableSetOf()) { it.id })
    }

    LaunchedEffect(state.captureRequests, selectedCaptureId) {
        if (selectedCaptureId != null && selectedCapture == null) {
            selectedCaptureId = null
            showTransferConfirmation = false
            workspaceExpanded = false
        }
        if (
            lastSelectedCaptureId != null &&
            state.captureRequests.none { it.id == lastSelectedCaptureId }
        ) {
            lastSelectedCaptureId = null
        }
    }

    LaunchedEffect(state.autofillPrompt?.id) {
        if (state.autofillPrompt != null) {
            showTabs = false
            showCaptureSummary = false
            showSettings = false
            showBrowserMenu = false
            showSiteInfo = false
            showShareQr = false
            showReaderMode = false
            selectedCaptureId = null
            showTransferConfirmation = false
            workspaceExpanded = false
            originWarningTargetUrl = null
            editing = false
        }
    }

    LaunchedEffect(state.sitePermission?.id) {
        if (state.sitePermission != null) {
            showTabs = false
            showCaptureSummary = false
            showSettings = false
            showBrowserMenu = false
            showSiteInfo = false
            showShareQr = false
            showReaderMode = false
            selectedCaptureId = null
            showTransferConfirmation = false
            workspaceExpanded = false
            originWarningTargetUrl = null
            editing = false
        }
    }

    BackHandler(
        enabled = showReaderMode ||
            findInPageActive ||
            showTranslationBar ||
            showBrowserMenu ||
            showSiteInfo ||
            showShareQr ||
            originWarningTargetUrl != null ||
            workspaceExpanded ||
            state.autofillPrompt != null ||
            state.sitePermission != null ||
            showTransferConfirmation ||
            selectedCaptureId != null ||
            showCaptureSummary ||
            showSettings ||
            showTabs ||
            editing ||
            (state.canGoBack && !state.crashed),
    ) {
        when {
            showReaderMode -> showReaderMode = false
            findInPageActive -> {
                findInPageActive = false
                findInPageQuery = ""
            }
            showTranslationBar -> showTranslationBar = false
            showBrowserMenu -> showBrowserMenu = false
            showSiteInfo -> showSiteInfo = false
            showShareQr -> showShareQr = false
            originWarningTargetUrl != null -> originWarningTargetUrl = null
            workspaceExpanded -> workspaceExpanded = false
            state.autofillPrompt != null ->
                onAutofillPromptDecision(state.autofillPrompt.id, null)
            state.sitePermission != null ->
                onSitePermissionDecision(state.sitePermission.id, false)
            showTransferConfirmation -> showTransferConfirmation = false
            selectedCaptureId != null -> selectedCaptureId = null
            showCaptureSummary -> showCaptureSummary = false
            showSettings -> {
                showSettings = false
                settingsInitialSubPage = null
            }
            showTabs -> showTabs = false
            editing -> editing = false
            state.canGoBack && !state.crashed -> onBack()
        }
    }

    TahoTheme {
        val isTopToolbar = TahoBrowserStateStore.settings.toolbarPosition == TahoToolbarPosition.TOP
        var chromeHeightPx by remember { mutableStateOf(0) }
        val chromeHeight = with(LocalDensity.current) { chromeHeightPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TahoBg),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (state.isPictureInPicture) {
                            Modifier
                        } else if (isTopToolbar) {
                            Modifier.padding(top = chromeHeight)
                        } else {
                            Modifier
                                .statusBarsPadding()
                                .padding(bottom = chromeHeight)
                        },
                    ),
            ) {
                browserContent()

                if (isStartPage && !showReaderMode) {
                    TahoStartPage(
                        isPrivate = state.isPrivate,
                        onNavigate = onNavigate,
                        onOpenTabs = { showTabs = true },
                        onOpenSettings = {
                            settingsInitialSubPage = SettingsSubPage.MAIN
                            showSettings = true
                        },
                        onNewTab = onNewTab,
                        onNewPrivateTab = onNewPrivateTab,
                        recentTabs = state.tabs,
                        onSelectTab = onSelectTab,
                    )
                }

                if (showReaderMode) {
                    TahoReaderModeView(
                        title = currentTab?.title?.takeIf { it.isNotBlank() } ?: "Web Document",
                        url = currentTab?.location ?: "about:blank",
                        content = readerContent?.text,
                        wordCount = readerContent?.wordCount ?: 0,
                        language = readerContent?.language.orEmpty(),
                        isGated = readerContent?.isGated == true,
                        isLoading = readerLoading,
                        errorMessage = readerError,
                        onClose = {
                            showReaderMode = false
                            readerContent = null
                            readerError = null
                        },
                    )
                }
            }

            val toolbarAlignment = if (isTopToolbar) Alignment.TopCenter else Alignment.BottomCenter

            if (!state.isPictureInPicture) {
                Column(
                    modifier = Modifier
                        .align(toolbarAlignment)
                        .fillMaxWidth()
                        .onSizeChanged { chromeHeightPx = it.height }
                        .background(TahoBg)
                        .then(if (isTopToolbar) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                if (isTopToolbar) {
                    Omnibox(
                        value = state.omniboxText,
                        draft = draft,
                        editing = editing,
                        isLoading = state.isLoading,
                        isPrivate = state.isPrivate,
                        tabCount = state.tabCount,
                        onDraftChange = { draft = it },
                        onBeginEdit = {
                            draft = state.omniboxText
                                .takeUnless { it == "Search or enter address" }
                                .orEmpty()
                            editing = true
                        },
                        onSubmit = {
                            val input = draft.trim()
                            if (input.isNotEmpty()) {
                                onNavigate(input)
                            }
                            editing = false
                        },
                        onSuggestionSelected = { suggestion ->
                            onNavigate(suggestion)
                            draft = suggestion
                            editing = false
                        },
                        onTabsClick = { showTabs = true },
                        onMenuClick = { showBrowserMenu = true },
                        onHomeClick = {
                            if (TahoBrowserStateStore.settings.homePageMode == TahoHomePageMode.CUSTOM_URL) {
                                onNavigate(TahoBrowserStateStore.settings.customHomePageUrl)
                            } else {
                                onNavigate("about:blank")
                            }
                        },
                        onLeadingClick = {
                            if (!isStartPage) {
                                showSiteInfo = true
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                state.notice?.let { message ->
                    BrowserNoticeBanner(
                        message = message,
                        onDismiss = onDismissNotice,
                    )
                    Spacer(Modifier.height(9.dp))
                }

                if (state.crashed) {
                    PageCrashBanner(
                        onReload = onReload,
                        onViewCaptured = {
                            showTabs = false
                            editing = false
                            selectedCaptureId = null
                            showTransferConfirmation = false
                            showCaptureSummary = true
                        },
                    )
                    Spacer(Modifier.height(9.dp))
                } else if (state.loadFailed) {
                    LoadFailureBanner(onReload = onReload)
                    Spacer(Modifier.height(9.dp))
                }

                CaptureIndicator(
                    state = state.captureState,
                    relevantCount = state.relevantCount,
                    onClick = {
                        showTabs = false
                        editing = false
                        selectedCaptureId = null
                        showTransferConfirmation = false
                        showCaptureSummary = true
                        onCaptureClick()
                    },
                )
                Spacer(Modifier.height(8.dp))

                if (editing) {
                    NavigationTray(
                        canGoBack = state.canGoBack,
                        canGoForward = state.canGoForward,
                        onBack = onBack,
                        onForward = onForward,
                        onReload = onReload,
                        onCancel = { editing = false },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (findInPageActive) {
                    TahoFindInPageBar(
                        query = findInPageQuery,
                        onQueryChange = { query ->
                            findInPageQuery = query
                            if (query.isBlank()) {
                                currentFindMatchIndex = 0
                                findMatchCount = 0
                                onClearFindInPage()
                            } else {
                                onFindInPage(query, false) { current, total ->
                                    currentFindMatchIndex = (current - 1).coerceAtLeast(0)
                                    findMatchCount = total.coerceAtLeast(0)
                                }
                            }
                        },
                        matchCount = findMatchCount,
                        currentMatchIndex = currentFindMatchIndex,
                        onPrevious = {
                            if (findInPageQuery.isNotBlank()) {
                                onFindInPage(findInPageQuery, true) { current, total ->
                                    currentFindMatchIndex = (current - 1).coerceAtLeast(0)
                                    findMatchCount = total.coerceAtLeast(0)
                                }
                            }
                        },
                        onNext = {
                            if (findInPageQuery.isNotBlank()) {
                                onFindInPage(findInPageQuery, false) { current, total ->
                                    currentFindMatchIndex = (current - 1).coerceAtLeast(0)
                                    findMatchCount = total.coerceAtLeast(0)
                                }
                            }
                        },
                        onClose = {
                            onClearFindInPage()
                            findInPageActive = false
                            findInPageQuery = ""
                            currentFindMatchIndex = 0
                            findMatchCount = 0
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (showTranslationBar) {
                    TahoTranslationBar(
                        sourceLang = translationSourceLanguage ?: "Detect page language",
                        targetLang = TahoBrowserStateStore.settings.translationTargetLanguage,
                        isTranslating = translationInProgress,
                        translated = isPageTranslated,
                        statusMessage = translationMessage,
                        onTranslate = {
                            if (!translationInProgress) {
                                translationInProgress = true
                                translationMessage = null
                                onTranslatePage(
                                    TahoBrowserStateStore.settings.translationTargetLanguage,
                                ) { success, sourceLanguage, error ->
                                    translationInProgress = false
                                    translationSourceLanguage = sourceLanguage
                                    isPageTranslated = success
                                    translationMessage = if (success) {
                                        "Translated by Gecko using its page translation engine."
                                    } else {
                                        error ?: "Translation failed."
                                    }
                                }
                            }
                        },
                        onRevert = {
                            if (!translationInProgress) {
                                translationInProgress = true
                                translationMessage = null
                                onRestorePageTranslation { success, error ->
                                    translationInProgress = false
                                    if (success) {
                                        isPageTranslated = false
                                        translationMessage = "Original page restored."
                                    } else {
                                        translationMessage = error ?: "Unable to restore the original page."
                                    }
                                }
                            }
                        },
                        onClose = { showTranslationBar = false },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (!isTopToolbar) {
                    Omnibox(
                        value = state.omniboxText,
                        draft = draft,
                        editing = editing,
                        isLoading = state.isLoading,
                        isPrivate = state.isPrivate,
                        tabCount = state.tabCount,
                        onDraftChange = { draft = it },
                        onBeginEdit = {
                            draft = state.omniboxText
                                .takeUnless { it == "Search or enter address" }
                                .orEmpty()
                            editing = true
                        },
                        onSubmit = {
                            val input = draft.trim()
                            if (input.isNotEmpty()) {
                                onNavigate(input)
                            }
                            editing = false
                        },
                        onSuggestionSelected = { suggestion ->
                            onNavigate(suggestion)
                            draft = suggestion
                            editing = false
                        },
                        onTabsClick = { showTabs = true },
                        onMenuClick = { showBrowserMenu = true },
                        onHomeClick = {
                            if (TahoBrowserStateStore.settings.homePageMode == TahoHomePageMode.CUSTOM_URL) {
                                onNavigate(TahoBrowserStateStore.settings.customHomePageUrl)
                            } else {
                                onNavigate("about:blank")
                            }
                        },
                        onLeadingClick = {
                            if (!isStartPage) {
                                showSiteInfo = true
                            }
                        },
                    )
                }
            }

            }

            M7TransferProgressOverlay(
                phase = state.transferPhase,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showCaptureSummary && selectedCaptureId == null && state.sitePermission == null) {
            ModalBottomSheet(
                onDismissRequest = { showCaptureSummary = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = MaterialTheme.colorScheme.scrim,
                dragHandle = { TahoGrabHandle() },
            ) {
                M7CaptureSummarySheet(
                    requests = state.captureRequests,
                    filter = captureFilter,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    listState = captureListState,
                    selectedId = lastSelectedCaptureId,
                    onFilterSelected = { captureFilter = it },
                    onSelect = { requestId ->
                        lastSelectedCaptureId = requestId
                        selectedCaptureId = requestId
                        selectedSecretPolicy = M4SecretPolicyUi.PARAMETERIZE
                    },
                    onClose = {
                        showCaptureSummary = false
                        searchQuery = ""
                    },
                )
            }
        }

        selectedCapture?.let { request ->
            if (!showTransferConfirmation && !workspaceExpanded && originWarningTargetUrl == null && state.sitePermission == null) {
                val currentTabLocation = state.tabs.firstOrNull { it.selected }?.location
                val currentTabHost = currentTabLocation?.let { loc ->
                    runCatching { java.net.URI(loc).host?.lowercase() }.getOrNull()
                }

                ModalBottomSheet(
                    onDismissRequest = { selectedCaptureId = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = TahoSheet,
                    contentColor = TahoText,
                    shape = TahoSheetShape,
                    tonalElevation = 0.dp,
                    scrimColor = MaterialTheme.colorScheme.scrim,
                    dragHandle = { TahoGrabHandle() },
                ) {
                    M7RequestInspectorSheet(
                        request = request,
                        onBack = { selectedCaptureId = null },
                        onCopyCurl = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onCopyCurl(M7SafeExport.curl(request))
                        },
                        onShare = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onShare(M7SafeExport.shareText(request))
                        },
                        onSendToTaho = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            selectedSecretPolicy = M4SecretPolicyUi.PARAMETERIZE
                            showTransferConfirmation = true
                        },
                        onReplay = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val targetHost = runCatching { java.net.URI(request.url).host?.lowercase() }.getOrNull()
                            if (currentTabHost != null && targetHost != null && currentTabHost != targetHost) {
                                originWarningTargetUrl = request.url
                            } else {
                                selectedCaptureId = null
                                onReplayRequest(request.url)
                            }
                        },
                        onDelete = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onDeleteRequest(request.id)
                            selectedCaptureId = null
                        },
                        onToggleWorkspace = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            workspaceExpanded = true
                        },
                    )
                }
            }

            if (showTransferConfirmation && state.sitePermission == null) {
                ModalBottomSheet(
                    onDismissRequest = { showTransferConfirmation = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = TahoSheet,
                    contentColor = TahoText,
                    shape = TahoSheetShape,
                    tonalElevation = 0.dp,
                    scrimColor = MaterialTheme.colorScheme.scrim,
                    dragHandle = { TahoGrabHandle() },
                ) {
                    M7SendConfirmationSheet(
                        request = request,
                        selectedPolicy = selectedSecretPolicy,
                        onPolicySelected = { policy ->
                            if (policy != M4SecretPolicyUi.EXPLICIT || request.explicitPolicyAllowed) {
                                selectedSecretPolicy = policy
                            }
                        },
                        onCancel = { showTransferConfirmation = false },
                        onConfirm = {
                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSendToTaho(request.id, selectedSecretPolicy)
                            showTransferConfirmation = false
                        },
                        isTahoInstalled = state.isTahoInstalled,
                        onInstallTaho = onInstallTaho,
                        onExportInstead = {
                            onShare(M7SafeExport.shareText(request))
                            showTransferConfirmation = false
                        },
                    )
                }
            }

            if (workspaceExpanded) {
                M7TechnicalWorkspaceView(
                    request = request,
                    onClose = { workspaceExpanded = false },
                    onCopyCurl = {
                        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onCopyCurl(M7SafeExport.curl(request))
                    },
                    onShare = {
                        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onShare(M7SafeExport.shareText(request))
                    },
                )
            }
        }

        originWarningTargetUrl?.let { targetUrl ->
            val parsedTarget = runCatching { java.net.URI(targetUrl).host }.getOrNull() ?: targetUrl
            ModalBottomSheet(
                onDismissRequest = { originWarningTargetUrl = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = MaterialTheme.colorScheme.scrim,
                dragHandle = { TahoGrabHandle() },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = "Destination Origin Changed",
                        color = TahoWarn,
                        fontFamily = TahoDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "This request is addressed to $parsedTarget, which differs from your current tab origin. Replaying it will send network traffic to this external host without live browser cookies or credentials.",
                        color = TahoText,
                        fontFamily = TahoSans,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        M7PrimaryButton(
                            label = "Replay Safely",
                            showArrow = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val url = targetUrl
                                originWarningTargetUrl = null
                                selectedCaptureId = null
                                onReplayRequest(url)
                            },
                        )
                        M7SecondaryButton("Cancel", Modifier.weight(1f)) {
                            originWarningTargetUrl = null
                        }
                    }
                }
            }
        }

        if (showTabs && state.sitePermission == null) {
            ModalBottomSheet(
                onDismissRequest = { showTabs = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = MaterialTheme.colorScheme.scrim,
                dragHandle = { TahoGrabHandle() },
            ) {
                TahoTabsOverviewSheet(
                    tabs = state.tabs,
                    onSelectTab = {
                        onSelectTab(it)
                        showTabs = false
                    },
                    onCloseTab = {
                        val tabToClose = state.tabs.find { t -> t.id == it }
                        TahoBrowserStateStore.recordClosedTab(tabToClose?.title, tabToClose?.location, tabToClose?.isPrivate ?: false)
                        onCloseTab(it)
                    },
                    onNewTab = {
                        onNewTab()
                        showTabs = false
                    },
                    onNewPrivateTab = {
                        onNewPrivateTab()
                        showTabs = false
                    },
                    onCloseOtherTabs = { keepId ->
                        state.tabs
                            .filterNot { it.id == keepId || it.id in TahoBrowserStateStore.pinnedTabIds }
                            .forEach { t -> onCloseTab(t.id) }
                    },
                    onCloseAllTabs = {
                        val closable = state.tabs.filterNot { it.id in TahoBrowserStateStore.pinnedTabIds }
                        closable.forEach { t -> onCloseTab(t.id) }
                        if (TahoBrowserStateStore.pinnedTabIds.isEmpty()) {
                            onNewTab()
                        }
                        showTabs = false
                    },
                    onDuplicateTab = { id ->
                        val t = state.tabs.find { it.id == id }
                        t?.location?.let { loc -> onNavigate(loc) }
                        showTabs = false
                    },
                    onRestoreClosedTab = { url ->
                        onNavigate(url)
                        showTabs = false
                    },
                    onCloseOverview = { showTabs = false },
                    onOpenSettings = {
                        showTabs = false
                        settingsInitialSubPage = SettingsSubPage.MAIN
                        showSettings = true
                    },
                )
            }
        }

        if (showSettings && state.sitePermission == null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showSettings = false
                    settingsInitialSubPage = null
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = MaterialTheme.colorScheme.scrim,
                dragHandle = { TahoGrabHandle() },
            ) {
                TahoSettingsHubSheet(
                    initialSubPage = settingsInitialSubPage ?: SettingsSubPage.MAIN,
                    onNavigateUrl = { url ->
                        onNavigate(url)
                        showSettings = false
                        settingsInitialSubPage = null
                    },
                    onClearEngineData = onClearBrowserStorage,
                    onAuthenticateSensitive = onAuthenticateSensitive,
                    onCheckPasswordBreach = onCheckPasswordBreach,
                    onDownloadPauseResume = onDownloadPauseResume,
                    onDownloadCancel = onDownloadCancel,
                    onDownloadRetry = onDownloadRetry,
                    onDownloadOpen = onDownloadOpen,
                    onDownloadDelete = onDownloadDelete,
                    onOpenOfflinePage = onOpenOfflinePage,
                    onDeleteOfflinePage = onDeleteOfflinePage,
                    onRefreshExtensions = onRefreshExtensions,
                    onInstallExtension = onInstallExtension,
                    onSetExtensionEnabled = onSetExtensionEnabled,
                    onSetExtensionPrivate = onSetExtensionPrivate,
                    onUpdateExtension = onUpdateExtension,
                    onUninstallExtension = onUninstallExtension,
                    onSwitchProfile = onSwitchProfile,
                    onCreateLocalProfile = onCreateLocalProfile,
                    onSearchExtensionMarketplace = onSearchExtensionMarketplace,
                    onExportFullBackup = onExportFullBackup,
                    onRestoreFullBackup = onRestoreFullBackup,
                    onCheckForUpdates = onCheckForUpdates,
                    onDismiss = {
                        showSettings = false
                        settingsInitialSubPage = null
                    },
                )
            }
        }

        if (showBrowserMenu && state.sitePermission == null) {
            ModalBottomSheet(
                onDismissRequest = { showBrowserMenu = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = MaterialTheme.colorScheme.scrim,
                dragHandle = { TahoGrabHandle() },
            ) {
                TahoBrowserMenuSheet(
                    currentLocation = currentTab?.location,
                    currentTitle = currentTab?.title,
                    isDesktopMode = effectiveDesktop,
                    isBookmarked = isBookmarked,
                    zoomPercent = currentZoom,
                    onZoomIn = {
                        TahoBrowserStateStore.setZoomForOrigin(currentOrigin, (currentZoom + 10).coerceAtMost(300))
                    },
                    onZoomOut = {
                        TahoBrowserStateStore.setZoomForOrigin(currentOrigin, (currentZoom - 10).coerceAtLeast(50))
                    },
                    onZoomReset = {
                        TahoBrowserStateStore.setZoomForOrigin(currentOrigin, 100)
                    },
                    onToggleBookmark = {
                        currentTab?.location?.let { loc ->
                            if (isBookmarked) {
                                val bm = TahoBrowserStateStore.bookmarks.find { it.url == loc }
                                if (bm != null) TahoBrowserStateStore.removeBookmark(bm.id)
                            } else {
                                TahoBrowserStateStore.addBookmark(currentTab.title ?: loc, loc)
                            }
                        }
                    },
                    onSaveToReadingList = {
                        currentTab?.location?.let { loc ->
                            TahoBrowserStateStore.addReadingListItem(currentTab.title ?: loc, loc)
                        }
                    },
                    onShare = { showShareQr = true },
                    onFindInPage = { findInPageActive = true },
                    onToggleDesktopMode = {
                        val nextDesktop = !effectiveDesktop
                        currentTab?.location?.let { loc ->
                            TahoBrowserStateStore.toggleDesktopModeForOrigin(loc)
                        }
                        isDesktopMode = nextDesktop
                        onSetDesktopMode(nextDesktop)
                    },
                    onReaderMode = {
                        showReaderMode = true
                        readerContent = null
                        readerError = null
                        readerLoading = true
                        onExtractReaderContent { extracted ->
                            readerLoading = false
                            readerContent = extracted
                            if (extracted == null) {
                                readerError = "This page does not expose readable content."
                            }
                        }
                    },
                    onTranslate = { showTranslationBar = true },
                    installableWebAppName = state.webAppManifest?.name,
                    onAddToHomeScreen = {
                        currentTab?.location?.let { loc ->
                            onAddToHomeScreen(currentTab.title ?: loc, loc)
                        }
                    },
                    onInstallWebApp = {
                        state.webAppManifest?.let(onInstallWebApp)
                    },
                    onPrintPage = {
                        showBrowserMenu = false
                        onPrintPage()
                    },
                    onSaveOffline = {
                        currentTab?.location?.let { loc ->
                            onSaveOfflinePage(currentTab.title ?: loc, loc)
                        }
                    },
                    onSiteInfo = { showSiteInfo = true },
                    onOpenSettings = { section ->
                        settingsInitialSubPage = when (section) {
                            "BOOKMARKS" -> SettingsSubPage.BOOKMARKS
                            "HISTORY" -> SettingsSubPage.HISTORY
                            "DOWNLOADS" -> SettingsSubPage.DOWNLOADS
                            "PASSWORDS" -> SettingsSubPage.PASSWORDS
                            "EXTENSIONS" -> SettingsSubPage.EXTENSIONS
                            else -> SettingsSubPage.MAIN
                        }
                        showSettings = true
                    },
                    onNewTab = {
                        onNewTab()
                        showBrowserMenu = false
                    },
                    onNewPrivateTab = {
                        onNewPrivateTab()
                        showBrowserMenu = false
                    },
                    onCloseMenu = { showBrowserMenu = false },
                    captureCount = state.relevantCount,
                    onOpenCapture = {
                        showBrowserMenu = false
                        selectedCaptureId = null
                        showTransferConfirmation = false
                        showCaptureSummary = true
                    },
                    onClearCapturedCalls = {
                        showBrowserMenu = false
                        onClearCaptureData()
                    },
                    onOpenCaptureSettings = {
                        showBrowserMenu = false
                        settingsInitialSubPage = SettingsSubPage.MAIN
                        showSettings = true
                    },
                    onReload = {
                        showBrowserMenu = false
                        onReload()
                    },
                )
            }
        }

        if (showSiteInfo && state.sitePermission == null) {
            TahoSiteInfoSheet(
                url = currentTab?.location ?: "about:blank",
                securityInfo = state.securityInfo,
                onDismiss = { showSiteInfo = false },
                onClearSiteData = onClearSiteDataForHost,
            )
        }

        if (showShareQr && state.sitePermission == null) {
            TahoShareQrSheet(
                url = currentTab?.location ?: "https://taho.app",
                title = currentTab?.title,
                onDismiss = { showShareQr = false },
            )
        }

        state.autofillPrompt?.let { prompt ->
            ModalBottomSheet(
                onDismissRequest = {
                    onAutofillPromptDecision(prompt.id, null)
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = MaterialTheme.colorScheme.scrim,
                dragHandle = { TahoGrabHandle() },
            ) {
                AutofillPromptSheet(
                    prompt = prompt,
                    onCancel = { onAutofillPromptDecision(prompt.id, null) },
                    onChoose = { index ->
                        onAutofillPromptDecision(prompt.id, index)
                    },
                )
            }
        }

        state.sitePermission?.let { prompt ->
            ModalBottomSheet(
                onDismissRequest = {
                    onSitePermissionDecision(prompt.id, false)
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = MaterialTheme.colorScheme.scrim,
                dragHandle = { TahoGrabHandle() },
            ) {
                SitePermissionSheet(
                    prompt = prompt,
                    onDeny = {
                        onSitePermissionDecision(prompt.id, false)
                    },
                    onAllow = {
                        onSitePermissionDecision(prompt.id, true)
                    },
                )
            }
        }
    }
}

@Composable
private fun BrowserNoticeBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TahoBlockShape)
            .background(TahoSheet)
            .border(1.dp, TahoHairlineStrong, TahoBlockShape)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = TahoMuted,
            fontFamily = TahoSans,
            fontSize = 10.sp,
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = TahoText, fontSize = 17.sp)
        }
    }
}

@Composable
private fun PageCrashBanner(
    onReload: () -> Unit,
    onViewCaptured: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TahoSheet)
            .border(1.dp, TahoWarn.copy(alpha = .50f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "This page stopped responding.",
                color = TahoText,
                fontFamily = TahoSans,
                fontSize = 11.sp,
            )
            Text(
                text = "The tab is still open.",
                color = TahoFaint,
                fontFamily = TahoSans,
                fontSize = 9.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Reload Page",
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .semantics { role = Role.Button; contentDescription = "Reload Page" }
                    .clickable(onClick = onReload)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                color = TahoGoldHi,
                fontFamily = TahoSans,
                fontSize = 10.sp,
            )
            Text(
                text = "View Captured Requests",
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .semantics { role = Role.Button; contentDescription = "View Captured Requests" }
                    .clickable(onClick = onViewCaptured)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                color = TahoMuted,
                fontFamily = TahoSans,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun LoadFailureBanner(onReload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TahoSheet)
            .border(1.dp, TahoError.copy(alpha = .45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Page failed to load",
            modifier = Modifier.weight(1f),
            color = TahoText,
            fontFamily = TahoSans,
            fontSize = 11.sp,
        )
        Text(
            text = "Reload",
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(999.dp))
                .semantics { role = Role.Button; contentDescription = "Reload" }
                .clickable(onClick = onReload)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            color = TahoGoldHi,
            fontFamily = TahoSans,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun NavigationTray(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChromeAction(
            label = "Back",
            icon = TahoIconName.BACK,
            modifier = Modifier.weight(1f),
            enabled = canGoBack,
            onClick = onBack,
        )
        ChromeAction(
            label = "Forward",
            icon = TahoIconName.FORWARD,
            modifier = Modifier.weight(1f),
            enabled = canGoForward,
            onClick = onForward,
        )
        ChromeAction(
            label = "Reload",
            icon = TahoIconName.RELOAD,
            modifier = Modifier.weight(1f),
            enabled = true,
            onClick = onReload,
        )
        ChromeAction(
            label = "Done",
            icon = TahoIconName.CLOSE,
            modifier = Modifier.weight(1f),
            enabled = true,
            onClick = onCancel,
        )
    }
}

@Composable
private fun ChromeAction(
    label: String,
    icon: TahoIconName,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(48.dp)
            .tahoPressScale(interaction, target = .96f)
            .clip(TahoPillShape)
            .background(TahoRaised)
            .border(1.dp, TahoLine, TahoPillShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TahoIcon(
            name = icon,
            contentDescription = null,
            tint = if (enabled) TahoMuted else TahoFaint,
            size = 20.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (enabled) TahoText else TahoFaint,
            fontFamily = TahoSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun CaptureIndicator(
    state: CaptureState,
    relevantCount: Int,
    onClick: () -> Unit,
) {
    val count = relevantCount.coerceAtLeast(0)
    val label = when (state) {
        CaptureState.OBSERVING -> "Capture active"
        CaptureState.CAPTURING ->
            "$count relevant " + if (count == 1) "request" else "requests"
        CaptureState.PAUSED -> "Capture paused"
        CaptureState.LIMITED -> "Capture limited"
        CaptureState.ERROR -> "Capture unavailable"
        CaptureState.OFF -> "Capture off"
    }
    val dotColor = when (state) {
        CaptureState.OBSERVING, CaptureState.CAPTURING -> TahoOk
        CaptureState.LIMITED -> TahoWarn
        CaptureState.ERROR -> TahoError
        CaptureState.PAUSED, CaptureState.OFF -> TahoFaint
    }

    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(48.dp)
            .tahoPulse(trigger = "$state:$count"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .tahoPressScale(interaction, target = .96f)
                .clip(TahoPillShape)
                .background(TahoSheet)
                .border(1.dp, TahoLine, TahoPillShape)
                .semantics {
                    role = Role.Button
                    contentDescription = buildString {
                        append(label)
                        if (count > 0 && state !in setOf(CaptureState.CAPTURING)) {
                            append(", ")
                            append(count)
                            append(if (count == 1) " request" else " requests")
                        }
                    }
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TahoStatusDot(color = dotColor)
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = TahoText,
                fontFamily = TahoSans,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Omnibox(
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
    onMenuClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onLeadingClick: () -> Unit = {},
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(TahoPillShape)
                .background(TahoSheet)
                .border(1.dp, TahoLine, TahoPillShape)
                .clickable(enabled = !editing, onClick = onBeginEdit)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OmniboxLeadingIcon(
                value = value,
                editing = editing,
                isPrivate = isPrivate,
                onClick = onLeadingClick,
            )
            Spacer(Modifier.width(8.dp))

            if (editing) {
                Box(modifier = Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Search or enter address",
                            color = TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 14.sp,
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
                            fontFamily = TahoSans,
                            fontSize = 14.sp,
                        ),
                        cursorBrush = SolidColor(TahoGold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                onSubmit()
                                focusManager.clearFocus()
                            },
                        ),
                    )
                }
            } else {
                DataText(
                    text = tokenizedUrl(value),
                    modifier = Modifier.weight(1f),
                    color = TahoText,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TabCountButton(
                tabCount = tabCount,
                onClick = onTabsClick,
            )
            TahoIconButton(
                name = TahoIconName.MORE,
                contentDescription = "Browser menu",
                onClick = onMenuClick,
            )
        }

        if (editing && draft.isNotBlank()) {
            val query = draft.trim()
            val settings = TahoBrowserStateStore.settings
            val localSuggestions = if (settings.addressBarSuggestionsEnabled) {
                (
                    TahoBrowserStateStore.bookmarks.map { it.title to it.url } +
                        TahoBrowserStateStore.history.map { it.title to it.url }
                    )
                    .asSequence()
                    .filter { (title, url) ->
                        title.contains(query, ignoreCase = true) ||
                            url.contains(query, ignoreCase = true)
                    }
                    .distinctBy { it.second }
                    .take(4)
                    .toList()
            } else {
                emptyList()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                localSuggestions.forEachIndexed { index, (title, url) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { onSuggestionSelected(url) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TahoIcon(
                            name = TahoIconName.OPEN_EXTERNAL,
                            contentDescription = null,
                            tint = TahoMuted,
                            size = 20.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = TahoText,
                                fontFamily = TahoSans,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            DataText(
                                text = url,
                                color = TahoFaint,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index < localSuggestions.lastIndex || settings.searchSuggestionsEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(TahoLine),
                        )
                    }
                }

                if (settings.searchSuggestionsEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { onSuggestionSelected(query) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TahoIcon(
                            name = TahoIconName.SEARCH,
                            contentDescription = null,
                            tint = TahoMuted,
                            size = 20.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Search for “$query”",
                            color = TahoText,
                            fontFamily = TahoSans,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OmniboxLeadingIcon(
    value: String,
    editing: Boolean,
    isPrivate: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TahoIcon(
            name = when {
                isPrivate -> TahoIconName.SECURITY
                !editing && value.startsWith("https://") -> TahoIconName.LOCK
                else -> TahoIconName.SEARCH
            },
            contentDescription = when {
                isPrivate -> "Private browsing"
                !editing && value.startsWith("https://") -> "Secure connection"
                else -> "Search"
            },
            tint = TahoMuted,
            size = 18.dp,
        )
    }
}

@Composable
private fun TabCountButton(
    tabCount: Int,
    onClick: () -> Unit,
) {
    val count = tabCount.coerceAtLeast(1)
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = count.toString() + if (count == 1) " tab" else " tabs"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(TahoBadgeShape)
                .border(1.5.dp, TahoMuted, TahoBadgeShape),
            contentAlignment = Alignment.Center,
        ) {
            DataText(
                text = count.toString(),
                color = TahoText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
            )
        }
    }
}

private fun tokenizedUrl(raw: String) = buildAnnotatedString {
    val value = raw.takeUnless { it == "Search or enter address" }.orEmpty()
    if (value.isBlank()) {
        withStyle(SpanStyle(color = TahoFaint)) {
            append("Search or enter address")
        }
        return@buildAnnotatedString
    }

    val parsed = runCatching { java.net.URI(value) }.getOrNull()
    val host = parsed?.host
    if (host.isNullOrBlank()) {
        withStyle(SpanStyle(color = TahoText)) { append(value) }
        return@buildAnnotatedString
    }

    val scheme = parsed.scheme?.let { "$it://" }.orEmpty()
    val port = parsed.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
    val path = buildString {
        append(parsed.rawPath.orEmpty())
        parsed.rawQuery?.let { append('?').append(it) }
        parsed.rawFragment?.let { append('#').append(it) }
    }

    withStyle(SpanStyle(color = TahoFaint)) { append(scheme) }
    withStyle(SpanStyle(color = TahoText)) { append(host).append(port) }
    withStyle(SpanStyle(color = TahoFaint)) { append(path) }
}

@Composable
private fun AutofillPromptSheet(
    prompt: BrowserAutofillPromptUiState,
    onCancel: () -> Unit,
    onChoose: (Int) -> Unit,
) {
    val savePrompt = prompt.kind == BrowserAutofillPromptKindUi.LOGIN_SAVE ||
        prompt.kind == BrowserAutofillPromptKindUi.ADDRESS_SAVE ||
        prompt.kind == BrowserAutofillPromptKindUi.CREDIT_CARD_SAVE
    val title = when (prompt.kind) {
        BrowserAutofillPromptKindUi.LOGIN_SAVE -> "Save login?"
        BrowserAutofillPromptKindUi.LOGIN_SELECT -> "Choose a saved login"
        BrowserAutofillPromptKindUi.ADDRESS_SAVE -> "Save address?"
        BrowserAutofillPromptKindUi.ADDRESS_SELECT -> "Choose a saved address"
        BrowserAutofillPromptKindUi.CREDIT_CARD_SAVE -> "Save payment card?"
        BrowserAutofillPromptKindUi.CREDIT_CARD_SELECT -> "Choose a payment card"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
    ) {
        Text(
            text = title,
            color = TahoText,
            fontFamily = TahoDisplay,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
        )
        prompt.origin?.takeIf(String::isNotBlank)?.let { origin ->
            Spacer(Modifier.height(5.dp))
            Text(
                text = origin,
                color = TahoGoldHi,
                fontFamily = TahoSans,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))

        prompt.options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoBlockShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoBlockShape)
                    .clickable { onChoose(option.index) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.title,
                        color = TahoText,
                        fontFamily = TahoSans,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    option.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = TahoFaint,
                            fontFamily = TahoSans,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = if (savePrompt) "Save" else "Use",
                    color = TahoGoldHi,
                    fontFamily = TahoSans,
                    fontSize = 9.5.sp,
                )
            }
            Spacer(Modifier.height(7.dp))
        }

        M7SecondaryButton(
            label = if (savePrompt) "Not Now" else "Cancel",
            modifier = Modifier.fillMaxWidth(),
            onClick = onCancel,
        )
    }
}

@Composable
private fun SitePermissionSheet(
    prompt: SitePermissionUiState,
    onDeny: () -> Unit,
    onAllow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
    ) {
        Text(
            text = "Site permission",
            color = TahoText,
            fontFamily = TahoDisplay,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = prompt.origin,
            color = TahoGoldHi,
            fontFamily = TahoSans,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = prompt.title,
            color = TahoText,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = prompt.detail,
            color = TahoMuted,
            fontSize = 12.sp,
        )

        if (prompt.isPrivate) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Private tab · this prompt is not saved by Taho Browser.",
                color = TahoFaint,
                fontFamily = TahoSans,
                fontSize = 9.sp,
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PermissionAction(
                text = "Deny",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onDeny,
            )
            PermissionAction(
                text = "Allow",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = onAllow,
            )
        }
    }
}

@Composable
private fun PermissionAction(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TahoActionButton(
        label = text,
        modifier = modifier,
        style = if (primary) TahoActionStyle.PRIMARY else TahoActionStyle.SECONDARY,
        trailingGlyph = null,
        onClick = onClick,
    )
}

@Composable
private fun TabSwitcher(
    tabs: List<BrowserTabUiState>,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tabs",
                    color = TahoText,
                    fontFamily = TahoDisplay,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Text(
                    text = tabs.size.toString() + if (tabs.size == 1) " open tab" else " open tabs",
                    color = TahoMuted,
                    fontFamily = TahoSans,
                    fontSize = 10.sp,
                )
            }
            MiniAction("New tab", onNewTab)
            Spacer(Modifier.width(8.dp))
            MiniAction("Private", onNewPrivateTab)
        }

        Spacer(Modifier.height(8.dp))
        MiniAction("Settings", onSettings)

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabRow(
                    tab = tab,
                    onSelect = { onSelectTab(tab.id) },
                    onClose = { onCloseTab(tab.id) },
                )
            }
        }
    }
}

@Composable
private fun MiniAction(
    text: String,
    onClick: () -> Unit,
) {
    TahoActionButton(
        label = text,
        style = TahoActionStyle.MINI,
        trailingGlyph = null,
        onClick = onClick,
    )
}

@Composable
private fun TabRow(
    tab: BrowserTabUiState,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val borderColor = if (tab.selected) {
        TahoGold.copy(alpha = .55f)
    } else {
        TahoLine
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TahoRaised)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tab.isPrivate) {
                    Text(
                        text = "PRIVATE",
                        color = TahoGoldHi,
                        fontFamily = TahoSans,
                        fontSize = 8.sp,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = tab.title?.takeIf { it.isNotBlank() } ?: tabTitle(tab.location),
                    color = TahoText,
                    fontFamily = TahoSans,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(
                        when {
                            tab.crashed -> "Page stopped responding"
                            tab.loadFailed -> "Failed to load"
                            tab.isLoading -> "Loading…"
                            tab.location.isNullOrBlank() || tab.location == "about:blank" -> "New tab"
                            else -> compactLocation(tab.location)
                        },
                    )
                    if (tab.relevantCaptureCount > 0) {
                        append(" · ")
                        append(tab.relevantCaptureCount)
                        append(" captured")
                    }
                },
                color = when {
                    tab.crashed -> TahoWarn
                    tab.loadFailed -> TahoError
                    else -> TahoFaint
                },
                fontFamily = TahoSans,
                fontSize = 9.sp,
                maxLines = 1,
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
            Text("×", color = TahoMuted, fontSize = 18.sp)
        }
    }
}

@Composable
internal fun SheetGrabHandle() {
    TahoGrabHandle()
}

