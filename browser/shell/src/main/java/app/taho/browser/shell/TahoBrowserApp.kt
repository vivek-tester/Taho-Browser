package app.taho.browser.shell

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
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
                .background(canvas),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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

            val isTopToolbar = TahoBrowserStateStore.settings.toolbarPosition == TahoToolbarPosition.TOP
            val toolbarAlignment = if (isTopToolbar) Alignment.TopCenter else Alignment.BottomCenter

            Column(
                modifier = Modifier
                    .align(toolbarAlignment)
                    .fillMaxWidth()
                    .then(if (isTopToolbar) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                    .padding(horizontal = 13.dp, vertical = 11.dp),
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
                        leadingGlyphEnabled = !isStartPage,
                        onLeadingClick = { showSiteInfo = true },
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

                if (state.captureState != CaptureState.OFF) {
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
                        leadingGlyphEnabled = !isStartPage,
                        onLeadingClick = { showSiteInfo = true },
                    )
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
                containerColor = bone,
                contentColor = ink,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
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
                    containerColor = bone,
                    contentColor = ink,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    tonalElevation = 0.dp,
                    scrimColor = Color.Black.copy(alpha = .50f),
                    dragHandle = { SheetGrabHandle() },
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
                    containerColor = bone,
                    contentColor = ink,
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
                containerColor = bone,
                contentColor = ink,
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
                        fontFamily = TahoBody,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "This request is addressed to $parsedTarget, which differs from your current tab origin. Replaying it will send network traffic to this external host without live browser cookies or credentials.",
                        color = ink,
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
                containerColor = bone,
                contentColor = ink,
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
                containerColor = bone,
                contentColor = ink,
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
            ModalBottomSheet(
                onDismissRequest = { showBrowserMenu = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = bone,
                contentColor = ink,
                shape = TahoSheetShape,
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
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
                    onAddToHomeScreen = {
                        currentTab?.location?.let { loc ->
                            onAddToHomeScreen(currentTab.title ?: loc, loc)
                        }
                    },
                    onPrintPage = {
                        showBrowserMenu = false
                        onPrintPage()
                    },
                    onSaveOffline = {
                        currentTab?.location?.let { loc ->
                            TahoBrowserStateStore.offlinePages = TahoBrowserStateStore.offlinePages + OfflinePageUi(
                                id = java.util.UUID.randomUUID().toString(),
                                title = currentTab.title ?: loc,
                                url = loc,
                            )
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

        state.sitePermission?.let { prompt ->
            ModalBottomSheet(
                onDismissRequest = {
                    onSitePermissionDecision(prompt.id, false)
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = bone,
                contentColor = ink,
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
            .background(bone)
            .border(1.dp, hairlineStrong, TahoBlockShape)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = mute,
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
            Text("×", color = ink, fontSize = 17.sp)
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
            .background(bone)
            .border(1.dp, TahoWarn.copy(alpha = .50f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "This page stopped responding.",
                color = ink,
                fontFamily = TahoMono,
                fontSize = 11.sp,
            )
            Text(
                text = "The tab is still open.",
                color = mute,
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
                color = amberHover,
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
                color = mute,
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
            .background(bone)
            .border(1.dp, danger.copy(alpha = .45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Page failed to load",
            modifier = Modifier.weight(1f),
            color = ink,
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
            color = amberHover,
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
        // The armed/rest distinction lives on the glyph only. The label stays mute
        // in both states: this is a chrome bar on a near-black fill (mute 5.44:1,
        // stone 2.54:1 -- stone fails AA and this label is never truly disabled).
        Text(
            glyph,
            color = if (enabled) amberHover else mute,
            fontSize = 15.sp,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = mute,
            fontFamily = TahoMono,
            fontSize = 9.sp,
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
            count.toString() + " relevant request" + if (count == 1) "" else "s"
        CaptureState.PAUSED ->
            "Ⅱ Capture paused · " + count + " requests retained"
        CaptureState.LIMITED ->
            if (count == 0) "△ Capture limited" else "△ Capture limited · " + count + " requests"
        CaptureState.ERROR -> "Capture unavailable"
        CaptureState.OFF -> return
    }
    val dotColor = when (state) {
        CaptureState.OBSERVING, CaptureState.CAPTURING -> ok
        CaptureState.LIMITED -> TahoWarn
        CaptureState.ERROR -> danger
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
            .border(1.dp, hairlineStrong, TahoPillShape)
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
            color = ink,
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
    /**
     * Site information describes a loaded document, so the leading glyph has
     * nothing to open on a start page. Passing this as a flag rather than
     * guarding inside [onLeadingClick] is what makes the glyph genuinely
     * disabled: a disabled glyph installs no clickable at all, so its tap falls
     * through to the omnibox row instead of being swallowed by a handler that
     * did nothing. `isStartPage` alone is the predicate — `editing` must NOT be
     * part of it, because the row's own clickable is
     * `clickable(enabled = !editing)`, and disabling both leaves that tap
     * handled by nothing. See spec §4.1 for the mark and disabled tokens.
     */
    leadingGlyphEnabled: Boolean,
    onDraftChange: (String) -> Unit,
    onBeginEdit: () -> Unit,
    onSubmit: () -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    // Deliberately not defaulted: an omitted handler would compile into a
    // clickable that does nothing, which is the defect this flag closed.
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
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xE618181B))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(999.dp)),
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(.26f)
                    .height(2.dp)
                    .background(amber),
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
                enabled = leadingGlyphEnabled,
                onClick = onLeadingClick,
            )
            Spacer(Modifier.width(8.dp))

            if (editing) {
                Box(modifier = Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Search or enter address",
                            color = mute,
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
                            color = ink,
                            fontFamily = TahoMono,
                            fontSize = 12.sp,
                        ),
                        cursorBrush = SolidColor(amber),
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
                        Text("↗", color = mute, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = ink,
                                fontFamily = TahoBody,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = url,
                                color = mute,
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
                        Text("⌕", color = amberHover, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Search for “$query”",
                            color = amberHover,
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
        Text("⌂", color = mute, fontSize = 15.sp)
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
        Text("⋮", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OmniboxLeadingGlyph(
    value: String,
    editing: Boolean,
    isPrivate: Boolean,
    enabled: Boolean,
    // Deliberately not defaulted: an omitted handler would compile into a
    // clickable that does nothing, which is the defect `enabled` exists to fix.
    onClick: () -> Unit,
) {
    /**
     * `ash` is the sanctioned non-text mark token and the only value here that
     * says "unavailable" without being read as a colour state (spec §4.1). The
     * glyph carries no string -- the accessible name comes from the
     * contentDescription below -- so the mark may take a sub-4.5:1 token.
     *
     * Known gap: `TahoTokenMigrationTest.ashAndStoneAreNeverUsedOnTextRoles`
     * matches the literal `color = ash`, so these `color = markColor` call
     * sites escape that guard entirely. Safe only because `markColor` never
     * carries a string -- do not assume the guard covers this path.
     */
    val markColor = if (enabled) (if (isPrivate) amberHover else mute) else ash
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            // No pointer-input node at all when disabled. `clickable(enabled =
            // false)` is not enough: ClickableKt.clickable always appends a
            // ClickableElement (only hover is gated on `enabled`), and
            // detectTapGestures consumes, so the glyph would swallow the tap.
            // With nothing installed, the tap reaches whatever is beneath --
            // here the omnibox row, which begins editing only when `!editing`.
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            // mergeDescendants because the enclosing omnibox Row is itself
            // clickable, and AbstractClickableNode merges descendant semantics:
            // without it this label folds into the Row's and is never announced
            // on its own. Use semantics `disabled()` rather than
            // Modifier.disabled(), which would re-install a consuming clickable.
            .semantics(mergeDescendants = true) {
                contentDescription = if (enabled) {
                    "Site information"
                } else {
                    // `enabled` is false in exactly one case now: no loaded
                    // document, which is what this label describes.
                    "No site information on this page"
                }
                if (enabled) {
                    role = Role.Button
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isPrivate) {
            Text(
                text = "◐",
                color = markColor,
                fontSize = 14.sp,
            )
            return@Box
        }

        if (!editing && value.startsWith("https://")) {
            // The lock stays `ok` rather than tracking `markColor`: its green
            // states transport security, which is independent of whether the
            // control is available. Dimming it would erase a real signal on a
            // genuinely secure page, where `enabled` is true anyway.
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
                    color = ok,
                    topLeft = Offset(bodyLeft, bodyTop),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = ok,
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
            color = markColor,
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
                color = ink,
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
            color = ink,
            fontFamily = TahoBody,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = prompt.origin,
            color = amberHover,
            fontFamily = TahoMono,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = prompt.title,
            color = ink,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = prompt.detail,
            color = mute,
            fontSize = 12.sp,
        )

        if (prompt.isPrivate) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Private tab · this prompt is not saved by Taho Browser.",
                color = mute,
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
            .background(if (primary) amber else Color.White.copy(alpha = .045f))
            .border(
                1.dp,
                if (primary) amber else Color.White.copy(alpha = .12f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) canvas else ink,
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
                    color = ink,
                    fontFamily = TahoBody,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                )
                Text(
                    text = tabs.size.toString() + if (tabs.size == 1) " open tab" else " open tabs",
                    color = mute,
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
            color = amberHover,
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
        amber.copy(alpha = .55f)
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
                        color = amberHover,
                        fontFamily = TahoMono,
                        fontSize = 8.sp,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = tab.title?.takeIf { it.isNotBlank() } ?: tabTitle(tab.location),
                    color = ink,
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
                    tab.loadFailed -> danger
                    else -> mute
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
            Text("×", color = mute, fontSize = 18.sp)
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
        pushStyle(SpanStyle(color = mute))
        append("Search or enter address")
        pop()
        return@buildAnnotatedString
    }

    val schemeEnd = value.indexOf("://")
    if (schemeEnd < 0) {
        pushStyle(SpanStyle(color = ink))
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

    pushStyle(SpanStyle(color = mute))
    append(value.substring(0, authorityStart))
    pop()

    pushStyle(SpanStyle(color = ink))
    append(value.substring(authorityStart, authorityEnd))
    pop()

    if (authorityEnd < value.length) {
        pushStyle(SpanStyle(color = mute))
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
