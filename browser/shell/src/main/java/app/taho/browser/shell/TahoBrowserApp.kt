package app.taho.browser.shell

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.taho.browser.capture.domain.CaptureState
import kotlin.math.roundToInt

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

data class SiteSecurityUiState(
    val isSecure: Boolean,
    val isException: Boolean,
    val host: String,
    val certificateSubject: String?,
    val certificateIssuer: String?,
    val activeMixedContentLoaded: Boolean,
    val passiveMixedContentLoaded: Boolean,
)

data class DevToolsUiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val command: String? = null,
    val payloadJson: String? = null,
    val error: String? = null,
)

data class BrowserUiState(
    val captureState: CaptureState = CaptureState.OFF,
    val captureEnabled: Boolean = false,
    val relevantCount: Int = 0,
    val omniboxText: String = "Search or enter address",
    val tabCount: Int = 1,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val crashed: Boolean = false,
    val isPrivate: Boolean = false,
    val isFullScreen: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val securityInfo: SiteSecurityUiState? = null,
    val sitePermission: SitePermissionUiState? = null,
    val notice: String? = null,
    val captureCapabilityNote: String? = null,
    val tabs: List<BrowserTabUiState> = emptyList(),
    val captureRequests: List<M4CaptureRequestUiState> = emptyList(),
    val transferPhase: M7TransferPhaseUi = M7TransferPhaseUi.NOT_STARTED,
    val isTahoInstalled: Boolean = true,
    val retentionMode: String = "Session only",
    val devTools: DevToolsUiState = DevToolsUiState(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TahoBrowserApp(
    state: BrowserUiState = BrowserUiState(),
    onCaptureEnabledChange: (Boolean) -> Unit = {},
    onDevToolsRequest: (String, String?) -> Unit = { _, _ -> },
    onDevToolsReloadPage: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onReload: () -> Unit = {},
    onExitFullScreen: () -> Unit = {},
    onNewTab: () -> Unit = {},
    onNewPrivateTab: () -> Unit = {},
    onSelectTab: (String) -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onSitePermissionDecision: (String, Boolean) -> Unit = { _, _ -> },
    onDismissNotice: () -> Unit = {},
    onClearCaptureData: () -> Unit = {},
    onCopyCurl: (String) -> Unit = {},
    onShare: (String) -> Unit = {},
    onExportCaptureFile: (String, String, String) -> Unit = { _, _, _ -> },
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
    onExtractReaderContent: ((ReaderPageContentUi?) -> Unit) -> Unit = { callback -> callback(null) },
    onPrintPage: () -> Boolean = { false },
    onAddToHomeScreen: (String, String) -> Unit = { _, _ -> },
    browserContent: @Composable () -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var showTabs by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBrowserMenu by rememberSaveable { mutableStateOf(false) }
    var showAddShortcut by rememberSaveable { mutableStateOf(false) }
    var newShortcutTitle by rememberSaveable { mutableStateOf("") }
    var newShortcutUrl by rememberSaveable { mutableStateOf("") }
    var showPacketCapture by rememberSaveable { mutableStateOf(false) }
    var showPrivacyToolsMenu by rememberSaveable { mutableStateOf(false) }
    var showCaptureQuickPanel by rememberSaveable { mutableStateOf(false) }
    var showCaptureFilters by rememberSaveable { mutableStateOf(false) }
    var showCaptureExport by rememberSaveable { mutableStateOf(false) }
    var showCaptureSettings by rememberSaveable { mutableStateOf(false) }
    var exportCaptureIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var exportReturnToCaptureSettings by rememberSaveable { mutableStateOf(false) }
    var phase2MethodFilter by rememberSaveable { mutableStateOf(Phase2MethodFilter.ALL) }
    var phase2StatusFilter by rememberSaveable { mutableStateOf(Phase2StatusFilter.ALL) }
    var phase2DomainFilter by rememberSaveable { mutableStateOf("") }
    var phase2ThirdPartyOnly by rememberSaveable { mutableStateOf(false) }
    var phase2FailedOnly by rememberSaveable { mutableStateOf(false) }
    var phase2WebSocketOnly by rememberSaveable { mutableStateOf(false) }
    var showDeveloperTools by rememberSaveable { mutableStateOf(false) }
    var showDevToolsPanel by rememberSaveable { mutableStateOf(false) }
    var phase3DevToolsMode by remember { mutableStateOf(Phase3DevToolsMode.BOTTOM) }
    var phase3DevToolsPanel by remember { mutableStateOf(Phase3DevToolsPanel.ELEMENTS) }
    var devToolsFloatingX by remember { mutableStateOf(0f) }
    var devToolsFloatingY by remember { mutableStateOf(0f) }
    var devToolsBottomHeightDp by remember { mutableStateOf(420f) }
    var devToolsSideWidthDp by remember { mutableStateOf(360f) }
    var showSiteInfo by rememberSaveable { mutableStateOf(false) }
    var showShareQr by rememberSaveable { mutableStateOf(false) }
    var showReaderMode by rememberSaveable { mutableStateOf(false) }
    var readerContent by remember { mutableStateOf<ReaderPageContentUi?>(null) }
    var readerLoading by remember { mutableStateOf(false) }
    var readerError by remember { mutableStateOf<String?>(null) }
    var showTranslationBar by rememberSaveable { mutableStateOf(false) }
    var isPageTranslated by rememberSaveable { mutableStateOf(false) }
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
    val isTopToolbar = TahoBrowserStateStore.settings.toolbarPosition == TahoToolbarPosition.TOP
    val density = LocalDensity.current
    val topSystemInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomSystemInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val toolbarReserve = 72.dp

    val selectedCapture = state.captureRequests.firstOrNull { it.id == selectedCaptureId }
    val darkSystemChromeVisible =
        showCaptureSummary ||
            showCaptureQuickPanel ||
            showCaptureFilters ||
            showCaptureExport ||
            showCaptureSettings ||
            showPrivacyToolsMenu ||
            selectedCaptureId != null ||
            showTabs ||
            showSettings ||
            showBrowserMenu ||
            showAddShortcut ||
            showPacketCapture ||
            showDeveloperTools ||
            showDevToolsPanel ||
            showSiteInfo ||
            showShareQr ||
            showReaderMode ||
            workspaceExpanded ||
            originWarningTargetUrl != null ||
            state.sitePermission != null
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
                if (state.isFullScreen) {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    show(WindowInsetsCompat.Type.systemBars())
                    val lightTheme = TahoBrowserStateStore.settings.themeMode == TahoThemeMode.LIGHT
                    isAppearanceLightStatusBars = lightTheme && !darkSystemChromeVisible
                    isAppearanceLightNavigationBars = lightTheme && !darkSystemChromeVisible
                }
            }
        }
    }

    LaunchedEffect(state.isFullScreen) {
        if (state.isFullScreen) {
            editing = false
            showTabs = false
            showSettings = false
            showBrowserMenu = false
            showPacketCapture = false
            showPrivacyToolsMenu = false
            showCaptureQuickPanel = false
            showCaptureFilters = false
            showCaptureExport = false
            showCaptureSettings = false
            showDeveloperTools = false
            showDevToolsPanel = false
            showSiteInfo = false
            showShareQr = false
            showReaderMode = false
            findInPageActive = false
            showTranslationBar = false
            showCaptureSummary = false
            selectedCaptureId = null
            showTransferConfirmation = false
            workspaceExpanded = false
            originWarningTargetUrl = null
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

    LaunchedEffect(state.sitePermission?.id) {
        if (state.sitePermission != null) {
            showTabs = false
            showCaptureSummary = false
            showSettings = false
            showBrowserMenu = false
            showPacketCapture = false
            showPrivacyToolsMenu = false
            showCaptureQuickPanel = false
            showCaptureFilters = false
            showCaptureExport = false
            showCaptureSettings = false
            showDeveloperTools = false
            showDevToolsPanel = false
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
        enabled = state.isFullScreen ||
            showReaderMode ||
            findInPageActive ||
            showTranslationBar ||
            showBrowserMenu ||
            showPrivacyToolsMenu ||
            showPacketCapture ||
            showCaptureQuickPanel ||
            showCaptureFilters ||
            showCaptureExport ||
            showCaptureSettings ||
            showDeveloperTools ||
            showDevToolsPanel ||
            showSiteInfo ||
            showShareQr ||
            originWarningTargetUrl != null ||
            workspaceExpanded ||
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
            state.isFullScreen -> onExitFullScreen()
            showReaderMode -> showReaderMode = false
            findInPageActive -> {
                findInPageActive = false
                findInPageQuery = ""
            }
            showTranslationBar -> showTranslationBar = false
            showDevToolsPanel -> {
                showDevToolsPanel = false
                showDeveloperTools = true
            }
            showDeveloperTools -> showDeveloperTools = false
            showCaptureExport -> showCaptureExport = false
            showCaptureFilters -> showCaptureFilters = false
            showCaptureSettings -> showCaptureSettings = false
            showCaptureQuickPanel -> showCaptureQuickPanel = false
            showPacketCapture -> showPacketCapture = false
            showPrivacyToolsMenu -> showPrivacyToolsMenu = false
            showAddShortcut -> showAddShortcut = false
            showBrowserMenu -> showBrowserMenu = false
            showSiteInfo -> showSiteInfo = false
            showShareQr -> showShareQr = false
            originWarningTargetUrl != null -> originWarningTargetUrl = null
            workspaceExpanded -> workspaceExpanded = false
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TahoBg),
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (showDevToolsPanel && phase3DevToolsMode == Phase3DevToolsMode.SIDE) {
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(.42f)
                                .align(Alignment.CenterStart)
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                    .padding(
                        top = if (!state.isFullScreen && !isStartPage && isTopToolbar) {
                            toolbarReserve + topSystemInset
                        } else {
                            0.dp
                        },
                        bottom = if (!state.isFullScreen && !isStartPage && !isTopToolbar) {
                            toolbarReserve + bottomSystemInset
                        } else {
                            0.dp
                        },
                    ),
            ) {
                browserContent()

                if (isStartPage && !showReaderMode) {
                    TahoMockupStartPage(
                        isPrivate = state.isPrivate,
                        onNavigate = onNavigate,
                        recentTabs = state.tabs,
                        onSelectTab = onSelectTab,
                        onOpenBookmarks = {
                            settingsInitialSubPage = SettingsSubPage.BOOKMARKS
                            showSettings = true
                        },
                        onOpenHistory = {
                            settingsInitialSubPage = SettingsSubPage.HISTORY
                            showSettings = true
                        },
                        onOpenDownloads = {
                            settingsInitialSubPage = SettingsSubPage.DOWNLOADS
                            showSettings = true
                        },
                        onAddShortcut = {
                            newShortcutTitle = ""
                            newShortcutUrl = ""
                            showAddShortcut = true
                        },
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

            val toolbarAlignment = when {
                showDevToolsPanel && phase3DevToolsMode == Phase3DevToolsMode.SIDE && isTopToolbar -> Alignment.TopStart
                showDevToolsPanel && phase3DevToolsMode == Phase3DevToolsMode.SIDE -> Alignment.BottomStart
                isTopToolbar -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            }

            if (!state.isFullScreen && !isStartPage) Column(
                modifier = Modifier
                    .align(toolbarAlignment)
                    .fillMaxWidth(
                        if (showDevToolsPanel && phase3DevToolsMode == Phase3DevToolsMode.SIDE) .42f else 1f,
                    )
                    .then(if (isTopToolbar) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                    .padding(horizontal = TahoToolbarOuterPadding, vertical = TahoToolbarVerticalPadding)
                    .tahoElevated(TahoChromeShape, elevation = 9.dp)
                    .clip(TahoChromeShape)
                    .background(TahoSheet)
                    .border(1.dp, TahoHairlineStrong, TahoChromeShape)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!isStartPage && isTopToolbar) {
                    TahoMockupToolbar(
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
                        targetLang = TahoBrowserStateStore.settings.translationTargetLanguage,
                        onTranslate = { isPageTranslated = true },
                        onRevert = {
                            isPageTranslated = false
                            showTranslationBar = false
                        },
                        onClose = { showTranslationBar = false },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (!isStartPage && !isTopToolbar) {
                    TahoMockupToolbar(
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

            M7TransferProgressOverlay(
                phase = state.transferPhase,
                modifier = Modifier.fillMaxSize(),
            )

            if (
                state.captureEnabled &&
                TahoBrowserStateStore.captureFloatingCharacterEnabled &&
                !state.isFullScreen &&
                state.sitePermission == null &&
                !showBrowserMenu &&
                !showPrivacyToolsMenu &&
                !showPacketCapture &&
                !showCaptureQuickPanel &&
                !showCaptureFilters &&
                !showCaptureExport &&
                !showCaptureSettings &&
                !showDeveloperTools &&
                !showDevToolsPanel &&
                !showSettings &&
                !showCaptureSummary &&
                selectedCaptureId == null
            ) {
                TahoFloatingCaptureCompanion(
                    relevantCount = state.relevantCount,
                    onClick = { showCaptureQuickPanel = true },
                )
            }
        }

        if (showCaptureSummary && selectedCaptureId == null && state.sitePermission == null) {
            val currentHost = currentTab?.location?.let { location ->
                runCatching { java.net.URI(location).host?.lowercase() }.getOrNull()
            }
            Phase2CapturedPacketsScreen(
                requests = state.captureRequests,
                currentHost = currentHost,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                methodFilter = phase2MethodFilter,
                statusFilter = phase2StatusFilter,
                domainFilter = phase2DomainFilter,
                thirdPartyOnly = phase2ThirdPartyOnly,
                failedOnly = phase2FailedOnly,
                webSocketOnly = phase2WebSocketOnly,
                onMethodFilterChange = { phase2MethodFilter = it },
                onInspect = { requestId ->
                    lastSelectedCaptureId = requestId
                    selectedCaptureId = requestId
                    selectedSecretPolicy = M4SecretPolicyUi.PARAMETERIZE
                },
                onOpenFilters = { showCaptureFilters = true },
                onOpenExport = { requests ->
                    exportCaptureIds = requests.map { it.id }
                    exportReturnToCaptureSettings = false
                    showCaptureExport = true
                },
                onDeleteSelected = { ids -> ids.forEach(onDeleteRequest) },
                onSendSingleToTaho = { requestId ->
                    lastSelectedCaptureId = requestId
                    selectedCaptureId = requestId
                    selectedSecretPolicy = M4SecretPolicyUi.PARAMETERIZE
                    showTransferConfirmation = true
                },
                onBack = {
                    showCaptureSummary = false
                    searchQuery = ""
                },
            )
        }

        selectedCapture?.let { request ->
            if (!showTransferConfirmation && !workspaceExpanded && originWarningTargetUrl == null && state.sitePermission == null) {
                val currentTabLocation = state.tabs.firstOrNull { it.selected }?.location
                val currentTabHost = currentTabLocation?.let { loc ->
                    runCatching { java.net.URI(loc).host?.lowercase() }.getOrNull()
                }

                Phase2PacketDetailsScreen(
                    request = request,
                    onBack = { selectedCaptureId = null },
                    onCopy = {
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
                )
            }

            if (showTransferConfirmation && state.sitePermission == null) {
                ModalBottomSheet(
                    onDismissRequest = { showTransferConfirmation = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = TahoSheet,
                    contentColor = TahoText,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    tonalElevation = 0.dp,
                    scrimColor = Color.Black.copy(alpha = .50f),
                    dragHandle = { SheetGrabHandle() },
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
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
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
                        fontFamily = TahoMono,
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
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
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
                        t?.location?.let { loc ->
                            onNewTab()
                            onNavigate(loc)
                        }
                        showTabs = false
                    },
                    onRestoreClosedTab = { url ->
                        onNewTab()
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

        if (showAddShortcut && state.sitePermission == null) {
            ModalBottomSheet(
                onDismissRequest = { showAddShortcut = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Add shortcut to Taho",
                        color = TahoText,
                        fontFamily = TahoDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    BasicTextField(
                        value = newShortcutTitle,
                        onValueChange = { newShortcutTitle = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TahoText,
                            fontFamily = TahoBody,
                            fontSize = 14.sp,
                        ),
                        cursorBrush = SolidColor(TahoText),
                        decorationBox = { field ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(TahoPillShape)
                                    .background(TahoSurfaceControl)
                                    .border(1.dp, TahoHairlineStrong, TahoPillShape)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                if (newShortcutTitle.isBlank()) {
                                    Text("Name", color = TahoFaint, fontFamily = TahoBody, fontSize = 14.sp)
                                }
                                field()
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    BasicTextField(
                        value = newShortcutUrl,
                        onValueChange = { newShortcutUrl = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TahoText,
                            fontFamily = TahoBody,
                            fontSize = 14.sp,
                        ),
                        cursorBrush = SolidColor(TahoText),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        decorationBox = { field ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(TahoPillShape)
                                    .background(TahoSurfaceControl)
                                    .border(1.dp, TahoHairlineStrong, TahoPillShape)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                if (newShortcutUrl.isBlank()) {
                                    Text("URL", color = TahoFaint, fontFamily = TahoBody, fontSize = 14.sp)
                                }
                                field()
                            }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        M7SecondaryButton("Cancel", Modifier.weight(1f)) {
                            showAddShortcut = false
                        }
                        M7PrimaryButton(
                            label = "Add",
                            showArrow = false,
                            modifier = Modifier.weight(1f),
                            enabled = newShortcutTitle.isNotBlank() && newShortcutUrl.isNotBlank(),
                        ) {
                            TahoBrowserStateStore.addTopSite(
                                title = newShortcutTitle.trim(),
                                url = newShortcutUrl.trim(),
                                isPinned = true,
                            )
                            showAddShortcut = false
                            newShortcutTitle = ""
                            newShortcutUrl = ""
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
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
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
            ) {
                TahoSettingsHubSheet(
                    initialSubPage = settingsInitialSubPage ?: SettingsSubPage.MAIN,
                    onNavigateUrl = { url ->
                        onNavigate(url)
                        showSettings = false
                        settingsInitialSubPage = null
                    },
                    onClearEngineData = onClearBrowserStorage,
                    onDismiss = {
                        showSettings = false
                        settingsInitialSubPage = null
                    },
                )
            }
        }

        if (showBrowserMenu && state.sitePermission == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = .34f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showBrowserMenu = false },
                )

                Box(
                    modifier = Modifier
                        .align(if (isTopToolbar) Alignment.TopEnd else Alignment.BottomEnd)
                        .then(if (isTopToolbar) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                        .padding(
                            top = if (isTopToolbar) 68.dp else 12.dp,
                            end = 12.dp,
                            bottom = if (isTopToolbar) 12.dp else 68.dp,
                        )
                        .widthIn(min = 292.dp, max = 332.dp)
                        .heightIn(max = 690.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(TahoSheet)
                        .border(1.dp, TahoHairlineStrong, RoundedCornerShape(18.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {},
                ) {
                    TahoMockupBrowserMenuSheet(
                        isDesktopMode = effectiveDesktop,
                        captureEnabled = state.captureEnabled,
                        onToggleDesktopMode = {
                            val nextDesktop = !effectiveDesktop
                            currentTab?.location?.let { loc ->
                                TahoBrowserStateStore.toggleDesktopModeForOrigin(loc)
                            }
                            isDesktopMode = nextDesktop
                            onSetDesktopMode(nextDesktop)
                        },
                        onNewTab = onNewTab,
                        onNewPrivateTab = onNewPrivateTab,
                        onOpenSettings = { section ->
                            if (section == "PRIVACY") {
                                showPrivacyToolsMenu = true
                            } else {
                                settingsInitialSubPage = when (section) {
                                    "BOOKMARKS" -> SettingsSubPage.BOOKMARKS
                                    "HISTORY" -> SettingsSubPage.HISTORY
                                    "DOWNLOADS" -> SettingsSubPage.DOWNLOADS
                                    "EXTENSIONS" -> SettingsSubPage.EXTENSIONS
                                    "ABOUT" -> SettingsSubPage.ABOUT
                                    else -> SettingsSubPage.MAIN
                                }
                                showSettings = true
                            }
                        },
                        onFindInPage = { findInPageActive = true },
                        onTranslate = { showTranslationBar = true },
                        onAddToHomeScreen = {
                            currentTab?.location?.let { loc ->
                                onAddToHomeScreen(currentTab.title ?: loc, loc)
                            }
                        },
                        onOpenPacketCapture = { showPacketCapture = true },
                        onOpenDeveloperTools = { showDeveloperTools = true },
                        onOpenRecentTabs = {
                            showBrowserMenu = false
                            showTabs = true
                        },
                        onCloseMenu = { showBrowserMenu = false },
                    )
                }
            }
        }

        if (showPacketCapture && state.sitePermission == null) {
            Phase2PacketCaptureScreen(
                enabled = state.captureEnabled,
                captureState = state.captureState,
                requests = state.captureRequests,
                relevantCount = state.relevantCount,
                captureInBackground = TahoBrowserStateStore.captureInBackground,
                floatingEnabled = TahoBrowserStateStore.captureFloatingCharacterEnabled,
                onEnabledChange = onCaptureEnabledChange,
                onBackgroundChange = TahoBrowserStateStore::updateCaptureInBackground,
                onFloatingChange = TahoBrowserStateStore::updateCaptureFloatingCharacterEnabled,
                onViewCaptured = {
                    showPacketCapture = false
                    showCaptureSummary = true
                },
                onOpenSettings = {
                    showPacketCapture = false
                    showCaptureSettings = true
                },
                onBack = { showPacketCapture = false },
            )
        }

        if (showCaptureQuickPanel && state.sitePermission == null) {
            ModalBottomSheet(
                onDismissRequest = { showCaptureQuickPanel = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = .45f),
                dragHandle = { SheetGrabHandle() },
            ) {
                Phase2CaptureQuickPanel(
                    requests = state.captureRequests,
                    onOpenFull = {
                        showCaptureQuickPanel = false
                        showCaptureSummary = true
                    },
                    onStop = {
                        onCaptureEnabledChange(false)
                        showCaptureQuickPanel = false
                    },
                    onDismiss = { showCaptureQuickPanel = false },
                )
            }
        }

        if (showCaptureFilters && state.sitePermission == null) {
            Phase2CaptureFiltersScreen(
                methodFilter = phase2MethodFilter,
                statusFilter = phase2StatusFilter,
                domainFilter = phase2DomainFilter,
                thirdPartyOnly = phase2ThirdPartyOnly,
                failedOnly = phase2FailedOnly,
                webSocketOnly = phase2WebSocketOnly,
                onMethodChange = { phase2MethodFilter = it },
                onStatusChange = { phase2StatusFilter = it },
                onDomainChange = { phase2DomainFilter = it },
                onThirdPartyChange = { phase2ThirdPartyOnly = it },
                onFailedChange = { phase2FailedOnly = it },
                onWebSocketChange = { phase2WebSocketOnly = it },
                onReset = {
                    phase2MethodFilter = Phase2MethodFilter.ALL
                    phase2StatusFilter = Phase2StatusFilter.ALL
                    phase2DomainFilter = ""
                    phase2ThirdPartyOnly = false
                    phase2FailedOnly = false
                    phase2WebSocketOnly = false
                },
                onApply = { showCaptureFilters = false },
                onBack = { showCaptureFilters = false },
            )
        }

        if (showCaptureExport && state.sitePermission == null) {
            Phase2ExportScreen(
                requests = state.captureRequests.filter { it.id in exportCaptureIds },
                onExport = { fileName, mimeType, exportText ->
                    onExportCaptureFile(fileName, mimeType, exportText)
                    showCaptureExport = false
                    if (exportReturnToCaptureSettings) {
                        showCaptureSettings = true
                    }
                    exportReturnToCaptureSettings = false
                },
                onBack = {
                    showCaptureExport = false
                    if (exportReturnToCaptureSettings) {
                        showCaptureSettings = true
                    }
                    exportReturnToCaptureSettings = false
                },
            )
        }

        if (showCaptureSettings && state.sitePermission == null) {
            Phase2CaptureSettingsScreen(
                enabled = state.captureEnabled,
                captureInBackground = TahoBrowserStateStore.captureInBackground,
                floatingEnabled = TahoBrowserStateStore.captureFloatingCharacterEnabled,
                animationStyle = TahoBrowserStateStore.captureAnimationStyle,
                defaultPosition = TahoBrowserStateStore.captureDefaultPosition,
                capabilityNote = state.captureCapabilityNote,
                onEnabledChange = onCaptureEnabledChange,
                onBackgroundChange = TahoBrowserStateStore::updateCaptureInBackground,
                onFloatingChange = TahoBrowserStateStore::updateCaptureFloatingCharacterEnabled,
                onAnimationStyleChange = TahoBrowserStateStore::updateCaptureAnimationStyle,
                onDefaultPositionChange = TahoBrowserStateStore::updateCaptureDefaultPosition,
                onExport = {
                    exportCaptureIds = state.captureRequests.map { it.id }
                    exportReturnToCaptureSettings = true
                    showCaptureSettings = false
                    showCaptureExport = true
                },
                onClear = onClearCaptureData,
                onBack = { showCaptureSettings = false },
            )
        }

        if (showPrivacyToolsMenu && state.sitePermission == null) {
            Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .34f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { showPrivacyToolsMenu = false },
            )

            Box(
                modifier = Modifier
                    .align(if (isTopToolbar) Alignment.TopEnd else Alignment.BottomEnd)
                    .then(if (isTopToolbar) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                    .padding(
                        top = if (isTopToolbar) 68.dp else 12.dp,
                        end = 12.dp,
                        bottom = if (isTopToolbar) 12.dp else 68.dp,
                    )
                    .widthIn(min = 292.dp, max = 332.dp)
                    .tahoElevated(TahoPopupShape, elevation = 18.dp)
                    .clip(TahoPopupShape)
                    .background(TahoSheet)
                    .border(1.dp, TahoHairlineStrong, TahoPopupShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
                Phase2PrivacyToolsMenu(
                    captureEnabled = state.captureEnabled,
                    httpsOnlyEnabled = TahoBrowserStateStore.settings.httpsOnlyMode,
                    trackingProtectionEnabled = TahoBrowserStateStore.settings.blockTrackers,
                    onPacketCapture = {
                        showPrivacyToolsMenu = false
                        showPacketCapture = true
                    },
                    onClearBrowsingData = {
                        showPrivacyToolsMenu = false
                        settingsInitialSubPage = SettingsSubPage.CLEAR_DATA
                        showSettings = true
                    },
                    onSitePermissions = {
                        showPrivacyToolsMenu = false
                        settingsInitialSubPage = SettingsSubPage.PRIVACY_SECURITY
                        showSettings = true
                    },
                    onTrackerProtection = {
                        showPrivacyToolsMenu = false
                        settingsInitialSubPage = SettingsSubPage.PRIVACY_SECURITY
                        showSettings = true
                    },
                    onHttpsOnlyChange = { enabled ->
                        TahoBrowserStateStore.updateSettings { it.copy(httpsOnlyMode = enabled) }
                    },
                    onBack = {
                        showPrivacyToolsMenu = false
                        showBrowserMenu = true
                    },
                )
            }
            }
        }

        if (showDeveloperTools && state.sitePermission == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF06101A)),
            ) {
                Phase3DevToolsHub(
                    enabled = state.captureEnabled,
                    connected = state.devTools.connected,
                    mode = phase3DevToolsMode,
                    onEnabledChange = onCaptureEnabledChange,
                    onModeChange = { phase3DevToolsMode = it },
                    onOpenPanel = { panel ->
                        phase3DevToolsPanel = panel
                        showDeveloperTools = false
                        showDevToolsPanel = true
                    },
                    onOpenPacketCapture = {
                        showDeveloperTools = false
                        showPacketCapture = true
                    },
                    onDismiss = { showDeveloperTools = false },
                )
            }
        }

        if (showDevToolsPanel && state.sitePermission == null) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val bottomMin = maxHeight.value * .32f
            val bottomMax = maxHeight.value * .78f
            val sideMin = maxWidth.value * .42f
            val sideMax = maxWidth.value * .68f
            val resolvedBottomHeight = devToolsBottomHeightDp.coerceIn(bottomMin, bottomMax)
            val resolvedSideWidth = devToolsSideWidthDp.coerceIn(sideMin, sideMax)
            val maxFloatingX = with(density) { (maxWidth * .08f).toPx() }
            val maxFloatingY = with(density) { (maxHeight * .16f).toPx() }

            val devToolsModifier = when (phase3DevToolsMode) {
                Phase3DevToolsMode.BOTTOM -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(resolvedBottomHeight.dp)
                Phase3DevToolsMode.SIDE -> Modifier
                    .align(Alignment.CenterEnd)
                    .width(resolvedSideWidth.dp)
                    .fillMaxHeight()
                Phase3DevToolsMode.FLOATING -> Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(.84f)
                    .fillMaxHeight(.68f)
                    .offset {
                        IntOffset(
                            devToolsFloatingX.coerceIn(-maxFloatingX, maxFloatingX).roundToInt(),
                            devToolsFloatingY.coerceIn(-maxFloatingY, maxFloatingY).roundToInt(),
                        )
                    }
                Phase3DevToolsMode.FULLSCREEN -> Modifier.fillMaxSize()
            }

            Box(
                modifier = devToolsModifier
                    .then(
                        if (phase3DevToolsMode == Phase3DevToolsMode.FULLSCREEN) {
                            Modifier
                        } else {
                            Modifier
                                .padding(6.dp)
                                .tahoElevated(RoundedCornerShape(14.dp), elevation = 14.dp)
                                .clip(RoundedCornerShape(14.dp))
                        },
                    )
                    .background(Color(0xFF06101A)),
            ) {
                Phase3DevToolsPanelSurface(
                    enabled = state.captureEnabled,
                    panel = phase3DevToolsPanel,
                    mode = phase3DevToolsMode,
                    captureRequests = state.captureRequests,
                    state = state.devTools,
                    onPanelChange = { phase3DevToolsPanel = it },
                    onModeChange = { mode ->
                        phase3DevToolsMode = mode
                        if (mode != Phase3DevToolsMode.FLOATING) {
                            devToolsFloatingX = 0f
                            devToolsFloatingY = 0f
                        }
                    },
                    onRequest = onDevToolsRequest,
                    onReloadPage = onDevToolsReloadPage,
                    onInspectRequest = { requestId ->
                        showDevToolsPanel = false
                        lastSelectedCaptureId = requestId
                        selectedCaptureId = requestId
                        selectedSecretPolicy = M4SecretPolicyUi.PARAMETERIZE
                    },
                    onBackToHub = {
                        showDevToolsPanel = false
                        showDeveloperTools = true
                    },
                    onClose = { showDevToolsPanel = false },
                    onFloatingDrag = { dx, dy ->
                        devToolsFloatingX = (devToolsFloatingX + dx).coerceIn(-maxFloatingX, maxFloatingX)
                        devToolsFloatingY = (devToolsFloatingY + dy).coerceIn(-maxFloatingY, maxFloatingY)
                    },
                    onResize = { dx, dy ->
                        when (phase3DevToolsMode) {
                            Phase3DevToolsMode.BOTTOM -> {
                                devToolsBottomHeightDp =
                                    (devToolsBottomHeightDp - (dy / density.density))
                                        .coerceIn(bottomMin, bottomMax)
                            }
                            Phase3DevToolsMode.SIDE -> {
                                devToolsSideWidthDp =
                                    (devToolsSideWidthDp - (dx / density.density))
                                        .coerceIn(sideMin, sideMax)
                            }
                            else -> Unit
                        }
                    },
                )
            }
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

        state.sitePermission?.let { prompt ->
            ModalBottomSheet(
                onDismissRequest = {
                    onSitePermissionDecision(prompt.id, false)
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
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
            fontFamily = TahoMono,
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
                fontFamily = TahoMono,
                fontSize = 11.sp,
            )
            Text(
                text = "The tab is still open.",
                color = TahoFaint,
                fontFamily = TahoMono,
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
                fontFamily = TahoMono,
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
                fontFamily = TahoMono,
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
            fontFamily = TahoMono,
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
            fontFamily = TahoMono,
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
        ChromeAction("Back", "‹", Modifier.weight(1f), canGoBack, onBack)
        ChromeAction("Forward", "›", Modifier.weight(1f), canGoForward, onForward)
        ChromeAction("Reload", "↻", Modifier.weight(1f), true, onReload)
        ChromeAction("Done", "×", Modifier.weight(1f), true, onCancel)
    }
}

@Composable
private fun ChromeAction(
    label: String,
    glyph: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xD9161619))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            glyph,
            color = if (enabled) TahoGoldHi else TahoFaint,
            fontSize = 15.sp,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = if (enabled) TahoMuted else TahoFaint,
            fontFamily = TahoMono,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TahoFloatingCaptureCompanion(
    relevantCount: Int,
    onClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val preferredPosition = TahoBrowserStateStore.captureDefaultPosition
        val animationStyle = TahoBrowserStateStore.captureAnimationStyle
        val visualSize = 40.dp
        val touchSize = 48.dp
        val margin = 10.dp
        val maxX = with(density) { (maxWidth - touchSize - margin).toPx() }.coerceAtLeast(0f)
        val maxY = with(density) { (maxHeight - touchSize - margin).toPx() }.coerceAtLeast(0f)
        val minX = with(density) { margin.toPx() }
        val minY = with(density) { margin.toPx() }

        var x by rememberSaveable { mutableStateOf(Float.NaN) }
        var y by rememberSaveable { mutableStateOf(Float.NaN) }

        val safeX = if (x.isNaN()) {
            if (preferredPosition == "Left side") minX.coerceAtMost(maxX) else maxX
        } else {
            x.coerceIn(minX.coerceAtMost(maxX), maxX)
        }
        val safeY = if (y.isNaN()) maxY * 0.56f else y.coerceIn(minY.coerceAtMost(maxY), maxY)
        val countLabel = when {
            relevantCount > 99 -> "99+"
            relevantCount > 0 -> relevantCount.toString()
            else -> ""
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(safeX.roundToInt(), safeY.roundToInt()) }
                .size(touchSize)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val startX = if (x.isNaN()) safeX else x
                        val startY = if (y.isNaN()) safeY else y
                        x = (startX + dragAmount.x).coerceIn(minX.coerceAtMost(maxX), maxX)
                        y = (startY + dragAmount.y).coerceIn(minY.coerceAtMost(maxY), maxY)
                    }
                }
                .then(
                    if (animationStyle == "Subtle") Modifier.tahoPulse(trigger = relevantCount)
                    else Modifier,
                )
                .semantics {
                    role = Role.Button
                    contentDescription =
                        if (relevantCount == 1) "Taho capture, 1 relevant request"
                        else "Taho capture, $relevantCount relevant requests"
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(visualSize)
                    .tahoElevated(RoundedCornerShape(14.dp), elevation = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF07111B).copy(alpha = .97f))
                    .border(1.dp, Color(0xFF21D4FD).copy(alpha = .68f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TahoCaptureMascot()
            }

            if (countLabel.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .heightIn(min = 18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF1677FF))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = countLabel,
                        color = Color.White,
                        fontFamily = TahoMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TahoCaptureMascot() {
    Canvas(modifier = Modifier.size(31.dp)) {
        val outline = 1.2.dp.toPx()
        val cyan = Color(0xFF45D9FF)
        val shell = Color(0xFF2A3744)
        val shellHi = Color(0xFF536474)
        val dark = Color(0xFF07111B)

        // Antenna and halo-like head ring from the approved floating robot mockup.
        drawLine(
            color = shellHi,
            start = Offset(size.width * .50f, size.height * .05f),
            end = Offset(size.width * .50f, size.height * .13f),
            strokeWidth = outline,
        )
        drawCircle(
            color = cyan,
            radius = size.width * .035f,
            center = Offset(size.width * .50f, size.height * .045f),
        )

        val headLeft = size.width * .18f
        val headTop = size.height * .13f
        val headWidth = size.width * .64f
        val headHeight = size.height * .39f
        drawRoundRect(
            color = shell,
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(size.width * .15f, size.width * .15f),
        )
        drawRoundRect(
            color = shellHi,
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(size.width * .15f, size.width * .15f),
            style = Stroke(width = outline),
        )
        drawRoundRect(
            color = dark,
            topLeft = Offset(size.width * .25f, size.height * .20f),
            size = Size(size.width * .50f, size.height * .23f),
            cornerRadius = CornerRadius(size.width * .10f, size.width * .10f),
        )
        drawCircle(cyan, size.width * .035f, Offset(size.width * .41f, size.height * .315f))
        drawCircle(cyan, size.width * .035f, Offset(size.width * .59f, size.height * .315f))
        drawLine(
            color = cyan.copy(alpha = .72f),
            start = Offset(size.width * .45f, size.height * .39f),
            end = Offset(size.width * .55f, size.height * .39f),
            strokeWidth = .9.dp.toPx(),
        )

        // Compact armored body and limbs.
        drawRoundRect(
            color = shell,
            topLeft = Offset(size.width * .31f, size.height * .54f),
            size = Size(size.width * .38f, size.height * .26f),
            cornerRadius = CornerRadius(size.width * .09f, size.width * .09f),
        )
        drawRoundRect(
            color = shellHi,
            topLeft = Offset(size.width * .31f, size.height * .54f),
            size = Size(size.width * .38f, size.height * .26f),
            cornerRadius = CornerRadius(size.width * .09f, size.width * .09f),
            style = Stroke(width = outline),
        )
        drawCircle(
            color = cyan.copy(alpha = .88f),
            radius = size.width * .035f,
            center = Offset(size.width * .50f, size.height * .66f),
        )
        drawLine(shellHi, Offset(size.width * .31f, size.height * .61f), Offset(size.width * .16f, size.height * .72f), outline)
        drawLine(shellHi, Offset(size.width * .69f, size.height * .61f), Offset(size.width * .84f, size.height * .72f), outline)
        drawLine(shellHi, Offset(size.width * .41f, size.height * .79f), Offset(size.width * .34f, size.height * .94f), outline)
        drawLine(shellHi, Offset(size.width * .59f, size.height * .79f), Offset(size.width * .66f, size.height * .94f), outline)
        drawCircle(shellHi, size.width * .055f, Offset(size.width * .14f, size.height * .74f))
        drawCircle(shellHi, size.width * .055f, Offset(size.width * .86f, size.height * .74f))
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
            count.toString() + " relevant request" + if (count == 1) "" else "s"
        CaptureState.PAUSED ->
            "Ⅱ Capture paused · " + count + " requests retained"
        CaptureState.LIMITED ->
            if (count == 0) "△ Capture limited" else "△ Capture limited · " + count + " requests"
        CaptureState.ERROR -> "Capture unavailable"
        CaptureState.OFF -> return
    }
    val dotColor = when (state) {
        CaptureState.OBSERVING, CaptureState.CAPTURING -> TahoOk
        CaptureState.LIMITED -> TahoWarn
        CaptureState.ERROR -> TahoError
        CaptureState.PAUSED, CaptureState.OFF -> Color.Transparent
    }
    val showDot = state != CaptureState.PAUSED && state != CaptureState.OFF

    val pillInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .tahoPressScale(pillInteraction, target = .96f)
            .tahoPulse(trigger = label)
            .clip(TahoPillShape)
            .background(Color(0xD1161619))
            .border(1.dp, TahoHairlineStrong, TahoPillShape)
            .semantics {
                role = Role.Button
                contentDescription = when (state) {
                    CaptureState.OBSERVING, CaptureState.CAPTURING ->
                        M7CaptureUx.captureAnnouncement(count)
                    CaptureState.PAUSED -> "Capture paused, " + count + " requests retained"
                    CaptureState.LIMITED -> "Capture limited, " + count + " requests retained"
                    CaptureState.ERROR -> "Capture unavailable"
                    CaptureState.OFF -> ""
                }
            }
            .clickable(
                interactionSource = pillInteraction,
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = 44.dp)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            TahoStatusDot(color = dotColor)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = TahoText,
            fontFamily = TahoMono,
            fontSize = 10.5.sp,
        )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xE618181B))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(999.dp)),
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(.26f)
                    .height(2.dp)
                    .background(TahoGold),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(enabled = !editing, onClick = onBeginEdit)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OmniboxLeadingGlyph(
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
                            fontFamily = TahoMono,
                            fontSize = 12.sp,
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
                            fontFamily = TahoMono,
                            fontSize = 12.sp,
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
                Text(
                    text = tokenizedUrl(value),
                    modifier = Modifier.weight(1f),
                    fontFamily = TahoMono,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(4.dp))
            OmniboxHomeButton(onClick = onHomeClick)
            Spacer(Modifier.width(2.dp))
            TabCountButton(
                tabCount = tabCount,
                onClick = onTabsClick,
            )
            Spacer(Modifier.width(2.dp))
            OmniboxMenuButton(onClick = onMenuClick)
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
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                localSuggestions.forEach { (title, url) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSuggestionSelected(url) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("↗", color = TahoFaint, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = TahoText,
                                fontFamily = TahoBody,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = url,
                                color = TahoFaint,
                                fontFamily = TahoMono,
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (settings.searchSuggestionsEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSuggestionSelected(query) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⌕", color = TahoGoldHi, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Search for “$query”",
                            color = TahoGoldHi,
                            fontFamily = TahoBody,
                            fontSize = 10.5.sp,
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
private fun OmniboxHomeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Home"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("⌂", color = TahoMuted, fontSize = 15.sp)
    }
}

@Composable
private fun OmniboxMenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Menu"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("⋮", color = TahoText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OmniboxLeadingGlyph(
    value: String,
    editing: Boolean,
    isPrivate: Boolean,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPrivate) {
            Text(
                text = "◐",
                color = TahoGoldHi,
                fontSize = 14.sp,
            )
            return@Box
        }

        if (!editing && value.startsWith("https://")) {
            Canvas(
                modifier = Modifier
                    .size(18.dp)
                    .semantics { contentDescription = "Secure connection" },
            ) {
                val stroke = 1.35.dp.toPx()
                val bodyWidth = size.width * .56f
                val bodyHeight = size.height * .42f
                val bodyLeft = (size.width - bodyWidth) / 2f
                val bodyTop = size.height * .46f

                drawRoundRect(
                    color = TahoOk,
                    topLeft = Offset(bodyLeft, bodyTop),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = TahoOk,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width * .29f, size.height * .12f),
                    size = Size(size.width * .42f, size.height * .54f),
                    style = Stroke(width = stroke),
                )
            }
            return@Box
        }

        Text(
            text = "⌕",
            color = TahoFaint,
            fontSize = 14.sp,
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
            .size(44.dp)
            .semantics {
                role = Role.Button
                contentDescription = count.toString() + if (count == 1) " tab" else " tabs"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = TahoText,
                fontFamily = TahoMono,
                fontSize = 10.sp,
            )
        }
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
            fontFamily = TahoMono,
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
                fontFamily = TahoMono,
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
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) TahoGold else Color.White.copy(alpha = .045f))
            .border(
                1.dp,
                if (primary) TahoGold else Color.White.copy(alpha = .12f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) TahoBg else TahoText,
            fontFamily = TahoMono,
            fontSize = 11.sp,
        )
    }
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
                    fontFamily = TahoMono,
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
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = .04f))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TahoGoldHi,
            fontFamily = TahoMono,
            fontSize = 10.sp,
        )
    }
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
        Color.White.copy(alpha = .08f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (tab.selected) .055f else .028f))
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
                        fontFamily = TahoMono,
                        fontSize = 8.sp,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = tab.title?.takeIf { it.isNotBlank() } ?: tabTitle(tab.location),
                    color = TahoText,
                    fontFamily = TahoMono,
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
                fontFamily = TahoMono,
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
private fun SheetGrabHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .width(42.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = .16f)),
    )
}

private fun tokenizedUrl(value: String) = buildAnnotatedString {
    if (value == "Search or enter address" || value.isBlank() || value == "about:blank") {
        pushStyle(SpanStyle(color = TahoMuted))
        append("Search or enter address")
        pop()
        return@buildAnnotatedString
    }

    val schemeEnd = value.indexOf("://")
    if (schemeEnd < 0) {
        pushStyle(SpanStyle(color = TahoText))
        append(value)
        pop()
        return@buildAnnotatedString
    }

    val authorityStart = schemeEnd + 3
    val authorityEnd = sequenceOf(
        value.indexOf('/', authorityStart),
        value.indexOf('?', authorityStart),
        value.indexOf('#', authorityStart),
    ).filter { it >= 0 }.minOrNull() ?: value.length

    pushStyle(SpanStyle(color = TahoFaint))
    append(value.substring(0, authorityStart))
    pop()

    pushStyle(SpanStyle(color = TahoText))
    append(value.substring(authorityStart, authorityEnd))
    pop()

    if (authorityEnd < value.length) {
        pushStyle(SpanStyle(color = TahoFaint))
        append(value.substring(authorityEnd))
        pop()
    }
}

private fun compactLocation(location: String): String =
    location
        .removePrefix("https://")
        .removePrefix("http://")

private fun tabTitle(location: String?): String {
    if (location.isNullOrBlank() || location == "about:blank") return "New tab"
    val compact = compactLocation(location)
    return compact.substringBefore('/').substringBefore('?').ifBlank { "Tab" }
}


private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
