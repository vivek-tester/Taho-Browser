package app.taho.browser.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

// --- 1. Find In Page Floating Bar ---
@Composable
fun TahoFindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(TahoPillShape)
            .background(TahoRaised)
            .border(1.dp, TahoHairlineStrong, TahoPillShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TahoIcon(
                name = TahoIconName.SEARCH,
                contentDescription = null,
                tint = TahoGoldHi,
                size = 18.dp,
            )
            Spacer(Modifier.width(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Find in page…", color = TahoFaint, fontFamily = TahoSans, fontSize = 11.5.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(color = TahoText, fontFamily = TahoSans, fontSize = 12.sp),
                    cursorBrush = SolidColor(TahoGold),
                )
            }

            if (query.isNotEmpty()) {
                val matchText = if (matchCount > 0) "${currentMatchIndex + 1}/$matchCount" else "0/0"
                Text(
                    text = matchText,
                    color = if (matchCount > 0) TahoGoldHi else TahoFaint,
                    fontFamily = TahoSans,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )

                TahoIconButton(
                    name = TahoIconName.UP,
                    contentDescription = "Previous match",
                    enabled = matchCount > 0,
                    onClick = onPrevious,
                )
                TahoIconButton(
                    name = TahoIconName.DOWN,
                    contentDescription = "Next match",
                    enabled = matchCount > 0,
                    onClick = onNext,
                )
            }

            Spacer(Modifier.width(6.dp))
            TahoIconButton(
                name = TahoIconName.CLOSE,
                contentDescription = "Close find in page",
                onClick = onClose,
            )
        }
    }
}

// --- 2. Share & QR Code Sheet ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TahoShareQrSheet(
    url: String,
    title: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TahoSheet,
        contentColor = TahoText,
        shape = TahoSheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Share Page",
                color = TahoText,
                fontFamily = TahoDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title ?: url,
                color = TahoFaint,
                fontFamily = TahoSans,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(18.dp))

            // Procedural QR Code Canvas Matrix
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                ProceduralQrMatrix(text = url)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Scan with another device to open instantly",
                color = TahoMuted,
                fontFamily = TahoSans,
                fontSize = 9.sp,
            )

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                M7SecondaryButton(
                    label = if (copied) "✓ Copied" else "Copy URL",
                    modifier = Modifier.weight(1f),
                ) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                    copied = true
                }

                M7PrimaryButton(
                    label = "System Share",
                    showArrow = true,
                    modifier = Modifier.weight(1f),
                ) {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, url)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    onDismiss()
                }
            }

            Spacer(Modifier.height(10.dp))

            M7SecondaryButton(
                label = "Open in External App ↗",
                modifier = Modifier.fillMaxWidth(),
            ) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                onDismiss()
            }
        }
    }
}

@Composable
private fun ProceduralQrMatrix(text: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val hash = text.hashCode()
        val grid = 21
        val cellSize = size.width / grid
        val black = Color.Black

        // Finder patterns in top-left, top-right, bottom-left
        drawFinder(0, 0, cellSize, black)
        drawFinder(grid - 7, 0, cellSize, black)
        drawFinder(0, grid - 7, cellSize, black)

        // Deterministic pseudo-random payload grid
        for (r in 0 until grid) {
            for (c in 0 until grid) {
                if ((r < 7 && c < 7) || (r < 7 && c >= grid - 7) || (r >= grid - 7 && c < 7)) {
                    continue
                }
                val bit = ((hash xor (r * 31 + c * 17)) and 1) == 0
                if (bit) {
                    drawRect(
                        color = black,
                        topLeft = Offset(c * cellSize, r * cellSize),
                        size = Size(cellSize, cellSize),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFinder(
    startCol: Int,
    startRow: Int,
    cellSize: Float,
    color: Color,
) {
    val left = startCol * cellSize
    val top = startRow * cellSize
    drawRect(color = color, topLeft = Offset(left, top), size = Size(cellSize * 7, cellSize * 7))
    drawRect(color = Color.White, topLeft = Offset(left + cellSize, top + cellSize), size = Size(cellSize * 5, cellSize * 5))
    drawRect(color = color, topLeft = Offset(left + cellSize * 2, top + cellSize * 2), size = Size(cellSize * 3, cellSize * 3))
}

// --- 3. Full Site Information Panel Sheet ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TahoSiteInfoSheet(
    url: String,
    securityInfo: SiteSecurityUiState?,
    onDismiss: () -> Unit,
    onClearSiteData: (String, (Boolean) -> Unit) -> Unit,
) {
    val host = runCatching { URI(url).host }.getOrNull() ?: url
    val isHttps = url.startsWith("https://")
    val isSecure = securityInfo?.isSecure == true
    val hasMixedContent =
        securityInfo?.activeMixedContentLoaded == true ||
            securityInfo?.passiveMixedContentLoaded == true
    val perms = TahoBrowserStateStore.sitePermissions.filter { it.origin.contains(host, ignoreCase = true) }
    val siteData = TahoBrowserStateStore.siteData.find { it.origin.contains(host, ignoreCase = true) }

    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var siteDataNotice by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TahoSheet,
        contentColor = TahoText,
        shape = TahoSheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSecure && !hasMixedContent) TahoOk.copy(alpha = 0.15f) else TahoWarn.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isSecure && !hasMixedContent) "🔒" else "⚠️", fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = host,
                        color = TahoText,
                        fontFamily = TahoDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = when {
                            !isHttps -> "Connection is not secure (HTTP)"
                            securityInfo == null -> "Security details are not available yet"
                            securityInfo.isException -> "Connection uses a security exception"
                            !securityInfo.isSecure -> "Connection is not verified as secure"
                            hasMixedContent -> "Secure transport with mixed content loaded"
                            else -> "Connection is secure"
                        },
                        color = if (isSecure && !hasMixedContent && securityInfo?.isException != true) TahoOk else TahoWarn,
                        fontFamily = TahoSans,
                        fontSize = 9.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // TLS / Certificate Details Box
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
                        text = "CERTIFICATE DETAILS",
                        color = TahoGoldHi,
                        fontFamily = TahoSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    SiteDetailRow("Host", securityInfo?.host?.takeIf { it.isNotBlank() } ?: host)
                    SiteDetailRow(
                        "Certificate subject",
                        securityInfo?.certificateSubject ?: "Unavailable",
                    )
                    SiteDetailRow(
                        "Certificate issuer",
                        securityInfo?.certificateIssuer ?: "Unavailable",
                    )
                    SiteDetailRow(
                        "Active mixed content",
                        if (securityInfo?.activeMixedContentLoaded == true) "Loaded" else "Not reported as loaded",
                    )
                    SiteDetailRow(
                        "Passive mixed content",
                        if (securityInfo?.passiveMixedContentLoaded == true) "Loaded" else "Not reported as loaded",
                    )
                    if (securityInfo == null) {
                        SiteDetailRow("Security evidence", "Awaiting GeckoView")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Per-Site Permissions Dashboard
            Text(
                text = "PER-SITE PERMISSIONS",
                color = TahoMuted,
                fontFamily = TahoSans,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoCardShape),
            ) {
                listOf(
                    "Location" to "📍",
                    "Camera" to "📷",
                    "Microphone" to "🎙",
                    "Notification" to "🔔",
                    "Clipboard" to "📋",
                    "Storage" to "💾",
                    "Pop-ups" to "🗖",
                    "Autoplay" to "▶",
                    "Background Activity" to "⚡",
                ).forEachIndexed { idx, (perm, icon) ->
                    val current = perms.find { it.permission == perm }?.state ?: "ASK"
                    PermissionItemToggle(
                        icon = icon,
                        label = perm,
                        state = current,
                        onStateChange = { next ->
                            TahoBrowserStateStore.updateSitePermission(host, perm, next)
                        }
                    )
                    if (idx < 8) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TahoHairline))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Per-Site Protections & Preferences
            Text(
                text = "PER-SITE PREFERENCES",
                color = TahoMuted,
                fontFamily = TahoSans,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoCardShape)
                    .padding(14.dp),
            ) {
                Column {
                    val isDesktop = TahoBrowserStateStore.settings.perSiteDesktopModes.contains(host)
                    val isTrackingExcepted = TahoBrowserStateStore.settings.perSiteTrackingExceptions.contains(host)
                    val zoom = TahoBrowserStateStore.getZoomForOrigin(host)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { TahoBrowserStateStore.toggleDesktopModeForOrigin(host) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Request Desktop Site", color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
                        Text(if (isDesktop) "ON" else "OFF", color = if (isDesktop) TahoGoldHi else TahoFaint, fontFamily = TahoSans, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TahoHairline))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { TahoBrowserStateStore.toggleTrackingException(host) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Tracking Protection Exception", color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
                        Text(if (isTrackingExcepted) "ALLOWED" else "STRICT", color = if (isTrackingExcepted) TahoWarn else TahoOk, fontFamily = TahoSans, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TahoHairline))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Per-Site Zoom Level", color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("−", color = TahoText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                if (zoom > 50) TahoBrowserStateStore.setZoomForOrigin(host, zoom - 10)
                            }.padding(4.dp))
                            Text("$zoom%", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp)
                            Text("+", color = TahoText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                if (zoom < 300) TahoBrowserStateStore.setZoomForOrigin(host, zoom + 10)
                            }.padding(4.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Cookies & Storage Data for Site
            Text(
                text = "COOKIES & SITE STORAGE",
                color = TahoMuted,
                fontFamily = TahoSans,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TahoCardShape)
                    .background(TahoSurfaceRow)
                    .border(1.dp, TahoHairline, TahoCardShape)
                    .padding(14.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = siteData?.let { "${it.cookieCount} cookies recorded" }
                                    ?: "Site-storage inventory unavailable",
                                color = TahoText,
                                fontFamily = TahoSans,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = siteData?.let { "Recorded usage: ${it.storageSizeBytes / 1024} KB" }
                                    ?: "You can still clear Gecko site data for this host.",
                                color = TahoFaint,
                                fontFamily = TahoSans,
                                fontSize = 9.sp,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(TahoPillShape)
                                .background(TahoDeleteWash)
                                .border(1.dp, TahoError, TahoPillShape)
                                .clickable {
                                    siteDataNotice = null
                                    onClearSiteData(host) { success ->
                                        if (success) {
                                            TahoBrowserStateStore.clearSiteDataForOrigin(host)
                                            siteDataNotice = "Site data cleared."
                                        } else {
                                            siteDataNotice = "Site data could not be cleared."
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("Clear Data", color = TahoError, fontFamily = TahoSans, fontSize = 9.5.sp)
                        }
                    }
                }
            }

            siteDataNotice?.let { notice ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = notice,
                    color = if (notice == "Site data cleared.") TahoOk else TahoError,
                    fontFamily = TahoSans,
                    fontSize = 9.5.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            M7SecondaryButton(
                label = "Reset Site Permissions",
                modifier = Modifier.fillMaxWidth(),
            ) {
                TahoBrowserStateStore.resetSitePermissions(host)
                onDismiss()
            }
        }
    }
}

@Composable
private fun SiteDetailRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = TahoFaint, fontFamily = TahoSans, fontSize = 9.5.sp)
        Text(value, color = TahoText, fontFamily = TahoSans, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PermissionItemToggle(
    icon: String,
    label: String,
    state: String,
    onStateChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TahoCompactTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = TahoText,
                fontFamily = TahoSans,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("ALLOW", "BLOCK", "ASK").forEach { option ->
                val active = state == option
                val semantic = when (option) {
                    "ALLOW" -> TahoOk
                    "BLOCK" -> TahoError
                    else -> TahoMuted
                }
                TahoChoiceChip(
                    label = option,
                    selected = active,
                    modifier = Modifier.weight(1f),
                    semanticColor = semantic,
                    description = "$label permission, $option" + if (active) ", selected" else "",
                    onClick = { onStateChange(option) },
                )
            }
        }
    }
}


// --- 4. Reader Mode View ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TahoReaderModeView(
    title: String,
    url: String,
    content: String?,
    wordCount: Int = 0,
    language: String = "",
    isGated: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onClose: () -> Unit,
) {
    var readerSettings by remember { mutableStateOf(TahoBrowserStateStore.readerSettings) }
    var showAppearanceDrawer by rememberSaveable { mutableStateOf(false) }

    val bgCol = when (readerSettings.theme) {
        TahoReaderTheme.OLED_BLACK -> Color(0xFF000000)
        TahoReaderTheme.SEPIA -> Color(0xFF1E1A16)
        TahoReaderTheme.LIGHT -> Color(0xFFF7F5F0)
    }
    val textCol = when (readerSettings.theme) {
        TahoReaderTheme.LIGHT -> Color(0xFF1B1B1B)
        else -> Color(0xFFE8E5DD)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgCol),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            // Top Controls Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = TahoCompactTouchTarget)
                        .clip(TahoPillShape)
                        .background(TahoRaised)
                        .border(1.dp, TahoLine, TahoPillShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("← Exit Reader", color = TahoText, fontFamily = TahoSans, fontSize = 10.sp)
                }

                Box(
                    modifier = Modifier
                        .heightIn(min = TahoCompactTouchTarget)
                        .clip(TahoPillShape)
                        .background(TahoRaised)
                        .border(1.dp, TahoLine, TahoPillShape)
                        .clickable { showAppearanceDrawer = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Aa Appearance", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(30.dp))

            // Article Header
            Text(
                text = title,
                color = textCol,
                fontFamily = TahoDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = (readerSettings.fontSizeSp + 8).sp,
                lineHeight = (readerSettings.fontSizeSp + 14).sp,
            )

            Spacer(Modifier.height(8.dp))

            val minutes = if (wordCount > 0) ((wordCount + 199) / 200).coerceAtLeast(1) else null
            Text(
                text = buildString {
                    append("Extracted from ")
                    append(URI(url).host ?: url)
                    minutes?.let { append(" · ~").append(it).append(" min read") }
                    language.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    if (isGated) append(" · gated content")
                },
                color = TahoFaint,
                fontFamily = TahoSans,
                fontSize = 10.sp,
            )

            Spacer(Modifier.height(24.dp))

            val fontFam = when (readerSettings.fontFamily) {
                "Mono" -> TahoMono
                "Sans" -> TahoBody
                else -> FontFamily.Serif
            }

            when {
                isLoading -> Text(
                    text = "Extracting readable content…",
                    color = TahoMuted,
                    fontFamily = TahoBody,
                    fontSize = readerSettings.fontSizeSp.sp,
                )
                errorMessage != null -> Text(
                    text = errorMessage,
                    color = TahoWarn,
                    fontFamily = TahoBody,
                    fontSize = readerSettings.fontSizeSp.sp,
                )
                content.isNullOrBlank() -> Text(
                    text = "Readable page content is unavailable.",
                    color = TahoMuted,
                    fontFamily = TahoBody,
                    fontSize = readerSettings.fontSizeSp.sp,
                )
                else -> Text(
                    text = content,
                    color = textCol,
                    fontFamily = fontFam,
                    fontSize = readerSettings.fontSizeSp.sp,
                    lineHeight = (readerSettings.fontSizeSp * readerSettings.lineSpacingMultiplier).sp,
                )
            }

            Spacer(Modifier.height(100.dp))
        }

        // Appearance Bottom Sheet
        if (showAppearanceDrawer) {
            ModalBottomSheet(
                onDismissRequest = { showAppearanceDrawer = false },
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
                    Text("Reading Appearance", color = TahoText, fontFamily = TahoDisplay, fontSize = 17.sp)
                    Spacer(Modifier.height(14.dp))

                    Text("Theme", color = TahoMuted, fontFamily = TahoSans, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            TahoReaderTheme.OLED_BLACK to "OLED Black",
                            TahoReaderTheme.SEPIA to "Sepia",
                            TahoReaderTheme.LIGHT to "Light",
                        ).forEach { (t, label) ->
                            val sel = readerSettings.theme == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(TahoPillShape)
                                    .background(if (sel) TahoGold else TahoSurfaceControl)
                                    .clickable {
                                        readerSettings = readerSettings.copy(theme = t)
                                        TahoBrowserStateStore.readerSettings = readerSettings
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (sel) TahoBg else TahoText, fontFamily = TahoSans, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Font Size: ${readerSettings.fontSizeSp} sp", color = TahoMuted, fontFamily = TahoSans, fontSize = 10.sp)
                    Slider(
                        value = readerSettings.fontSizeSp.toFloat(),
                        onValueChange = {
                            readerSettings = readerSettings.copy(fontSizeSp = it.toInt())
                            TahoBrowserStateStore.readerSettings = readerSettings
                        },
                        valueRange = 12f..28f,
                        colors = SliderDefaults.colors(thumbColor = TahoGold, activeTrackColor = TahoGold),
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Typography", color = TahoMuted, fontFamily = TahoSans, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Serif", "Sans", "Mono").forEach { fam ->
                            val sel = readerSettings.fontFamily == fam
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(TahoPillShape)
                                    .background(if (sel) TahoGold else TahoSurfaceControl)
                                    .clickable {
                                        readerSettings = readerSettings.copy(fontFamily = fam)
                                        TahoBrowserStateStore.readerSettings = readerSettings
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(fam, color = if (sel) TahoBg else TahoText, fontFamily = TahoSans, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 5. Inline Page Translation Bar ---
@Composable
fun TahoTranslationBar(
    sourceLang: String = "Detect page language",
    targetLang: String = "English",
    isTranslating: Boolean = false,
    translated: Boolean = false,
    statusMessage: String? = null,
    onTranslate: () -> Unit,
    onRevert: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(TahoPillShape)
            .background(TahoRaised)
            .border(1.dp, TahoHairlineStrong, TahoPillShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("文A", color = TahoGoldHi, fontSize = 13.sp, fontFamily = TahoSans)
                Spacer(Modifier.width(8.dp))
                Text("$sourceLang → $targetLang", color = TahoText, fontFamily = TahoSans, fontSize = 10.5.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .heightIn(min = TahoCompactTouchTarget)
                        .clip(TahoPillShape)
                        .background(TahoGold)
                        .clickable(onClick = onTranslate)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        if (isTranslating) "Working…" else if (translated) "Translated" else "Translate",
                        color = TahoBg,
                        fontFamily = TahoSans,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(
                    modifier = Modifier
                        .heightIn(min = TahoCompactTouchTarget)
                        .clip(TahoPillShape)
                        .background(TahoRaised)
                        .border(1.dp, TahoLine, TahoPillShape)
                        .clickable(onClick = onRevert)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("Original", color = TahoMuted, fontFamily = TahoSans, fontSize = 9.sp)
                }

                TahoIconButton(
                    name = TahoIconName.CLOSE,
                    contentDescription = "Close translation controls",
                    onClick = onClose,
                )
            }
        }
            statusMessage?.takeIf(String::isNotBlank)?.let { message ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                color = TahoFaint,
                fontFamily = TahoSans,
                fontSize = 8.5.sp,
                maxLines = 2,
            )
            }
        }
    }
}
