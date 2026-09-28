package app.taho.browser.shell

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
    val sitePermission: SitePermissionUiState? = null,
    val notice: String? = null,
    val captureCapabilityNote: String? = null,
    val tabs: List<BrowserTabUiState> = emptyList(),
    val captureRequests: List<M4CaptureRequestUiState> = emptyList(),
    val transferPhase: M7TransferPhaseUi = M7TransferPhaseUi.NOT_STARTED,
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
    browserContent: @Composable () -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var showTabs by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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

    val selectedCapture = state.captureRequests.firstOrNull { it.id == selectedCaptureId }
    val darkSystemChromeVisible =
        showCaptureSummary ||
            selectedCaptureId != null ||
            showTabs ||
            showSettings ||
            state.sitePermission != null
    val rootView = LocalView.current

    SideEffect {
        rootView.context.findActivity()?.window?.let { window ->
            WindowInsetsControllerCompat(window, rootView).apply {
                isAppearanceLightStatusBars = !darkSystemChromeVisible
                isAppearanceLightNavigationBars = !darkSystemChromeVisible
            }
        }
    }

    LaunchedEffect(state.captureRequests, selectedCaptureId) {
        if (selectedCaptureId != null && selectedCapture == null) {
            selectedCaptureId = null
            showTransferConfirmation = false
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
            selectedCaptureId = null
            showTransferConfirmation = false
            editing = false
        }
    }

    BackHandler(
        enabled = state.sitePermission != null ||
            showTransferConfirmation ||
            selectedCaptureId != null ||
            showCaptureSummary ||
            showSettings ||
            showTabs ||
            editing ||
            (state.canGoBack && !state.crashed),
    ) {
        when {
            state.sitePermission != null ->
                onSitePermissionDecision(state.sitePermission.id, false)
            showTransferConfirmation -> showTransferConfirmation = false
            selectedCaptureId != null -> selectedCaptureId = null
            showCaptureSummary -> showCaptureSummary = false
            showSettings -> showSettings = false
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
            Box(modifier = Modifier.fillMaxSize()) {
                browserContent()
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                    onTabsClick = { showTabs = true },
                )
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
            if (!showTransferConfirmation && state.sitePermission == null) {
                ModalBottomSheet(
                    onDismissRequest = { selectedCaptureId = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = TahoSheet,
                    contentColor = TahoText,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    tonalElevation = 0.dp,
                    scrimColor = Color.Black.copy(alpha = .50f),
                    dragHandle = { SheetGrabHandle() },
                ) {
                    M7RequestInspectorSheet(
                        request = request,
                        onBack = { selectedCaptureId = null },
                        onCopyCurl = { onCopyCurl(M7SafeExport.curl(request)) },
                        onShare = { onShare(M7SafeExport.shareText(request)) },
                        onSendToTaho = {
                            selectedSecretPolicy = M4SecretPolicyUi.PARAMETERIZE
                            showTransferConfirmation = true
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
                            onSendToTaho(request.id, selectedSecretPolicy)
                            showTransferConfirmation = false
                        },
                    )
                }
            }
        }

        if (showTabs && state.sitePermission == null) {
            ModalBottomSheet(
                onDismissRequest = { showTabs = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
            ) {
                TabSwitcher(
                    tabs = state.tabs,
                    onNewTab = {
                        onNewTab()
                        showTabs = false
                    },
                    onNewPrivateTab = {
                        onNewPrivateTab()
                        showTabs = false
                    },
                    onSelectTab = {
                        onSelectTab(it)
                        showTabs = false
                    },
                    onCloseTab = onCloseTab,
                    onSettings = {
                        showTabs = false
                        showSettings = true
                    },
                )
            }
        }

        if (showSettings && state.sitePermission == null) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TahoSheet,
                contentColor = TahoText,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = .50f),
                dragHandle = { SheetGrabHandle() },
            ) {
                M7SettingsSheet(
                    captureCapabilityNote = state.captureCapabilityNote,
                    onClearCaptureData = onClearCaptureData,
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
    onTabsClick: () -> Unit,
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
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OmniboxLeadingGlyph(
                value = value,
                editing = editing,
                isPrivate = isPrivate,
            )
            Spacer(Modifier.width(9.dp))

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

            Spacer(Modifier.width(10.dp))
            TabCountButton(
                tabCount = tabCount,
                onClick = onTabsClick,
            )
        }
    }
}

@Composable
private fun OmniboxLeadingGlyph(
    value: String,
    editing: Boolean,
    isPrivate: Boolean,
) {
    if (isPrivate) {
        Text(
            text = "◐",
            color = TahoGoldHi,
            fontSize = 14.sp,
        )
        return
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
                color = TahoFaint,
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = TahoFaint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * .29f, size.height * .12f),
                size = Size(size.width * .42f, size.height * .54f),
                style = Stroke(width = stroke),
            )
        }
        return
    }

    Text(
        text = "⌕",
        color = TahoFaint,
        fontSize = 14.sp,
    )
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
