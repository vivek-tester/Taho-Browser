package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TahoBrowserMenuSheet(
    currentLocation: String?,
    currentTitle: String?,
    isDesktopMode: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onSaveToReadingList: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onReaderMode: () -> Unit,
    onTranslate: () -> Unit,
    installableWebAppName: String? = null,
    onAddToHomeScreen: () -> Unit,
    onInstallWebApp: () -> Unit = {},
    onPrintPage: () -> Unit,
    onSaveOffline: () -> Unit,
    onSiteInfo: () -> Unit,
    zoomPercent: Int = 100,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onZoomReset: () -> Unit = {},
    onOpenSettings: (String?) -> Unit, // section tag or null for hub
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onCloseMenu: () -> Unit,
) {
    val cleanLocation = currentLocation?.removePrefix("https://")?.removePrefix("http://") ?: "New Tab"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Page Info Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TahoBlockShape)
                .background(TahoSurfaceRow)
                .border(1.dp, TahoHairline, TahoBlockShape)
                .clickable {
                    onCloseMenu()
                    onSiteInfo()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(TahoOk.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔒", fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTitle?.takeIf { it.isNotBlank() } ?: cleanLocation,
                    color = TahoText,
                    fontFamily = TahoSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = cleanLocation,
                    color = TahoFaint,
                    fontFamily = TahoSans,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("ⓘ Details", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 9.5.sp)
        }

        Spacer(Modifier.height(14.dp))

        // Quick Horizontal Icon Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MenuQuickAction(if (isBookmarked) "★" else "☆", if (isBookmarked) "Bookmarked" else "Bookmark", isBookmarked) {
                onToggleBookmark()
            }
            MenuQuickAction("📖", "Reading", false) {
                onCloseMenu()
                onSaveToReadingList()
            }
            MenuQuickAction("↗", "Share", false) {
                onCloseMenu()
                onShare()
            }
            MenuQuickAction("⌕", "Find", false) {
                onCloseMenu()
                onFindInPage()
            }
            MenuQuickAction(if (isDesktopMode) "🖥✓" else "🖥", "Desktop", isDesktopMode) {
                onToggleDesktopMode()
            }
        }

        Spacer(Modifier.height(14.dp))

        // Page Zoom Controls
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
            Text("Page Zoom", color = TahoText, fontFamily = TahoSans, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(TahoPillShape)
                        .background(TahoSurfaceControl)
                        .clickable(onClick = onZoomOut)
                        .semantics { role = Role.Button; contentDescription = "Zoom out" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("−", color = TahoText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(TahoPillShape)
                        .background(TahoSurfaceControl)
                        .clickable(onClick = onZoomReset)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$zoomPercent%", color = TahoGoldHi, fontFamily = TahoSans, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(TahoPillShape)
                        .background(TahoSurfaceControl)
                        .clickable(onClick = onZoomIn)
                        .semantics { role = Role.Button; contentDescription = "Zoom in" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = TahoText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Page Actions Section
        Text(
            text = "PAGE ACTIONS",
            color = TahoFaint,
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
            MenuItemRow("📑 Reader Mode", "Distraction-free readable view") {
                onCloseMenu()
                onReaderMode()
            }
            MenuItemDivider()
            MenuItemRow("文A Translate Page", "Automatic inline translation") {
                onCloseMenu()
                onTranslate()
            }
            MenuItemDivider()
            if (installableWebAppName != null) {
                MenuItemRow(
                    "⊞ Install Web App",
                    "Install ${installableWebAppName.take(36)} in Taho's standalone PWA runtime",
                ) {
                    onCloseMenu()
                    onInstallWebApp()
                }
            } else {
                MenuItemRow("⊞ Add Page to Home Screen", "Create a launcher shortcut") {
                    onCloseMenu()
                    onAddToHomeScreen()
                }
            }
            MenuItemDivider()
            MenuItemRow("⎙ Print or Save as PDF", "Export page via Android print engine") {
                onCloseMenu()
                onPrintPage()
            }
            MenuItemDivider()
            MenuItemRow("💾 Save Page for Offline Reading", "Store locally for offline use") {
                onCloseMenu()
                onSaveOffline()
            }
        }

        Spacer(Modifier.height(18.dp))

        // Browser Management Section
        Text(
            text = "BROWSER HUBS",
            color = TahoFaint,
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
            MenuItemRow("★ Bookmarks & Folders", "${TahoBrowserStateStore.bookmarks.size} saved links") {
                onCloseMenu()
                onOpenSettings("BOOKMARKS")
            }
            MenuItemDivider()
            MenuItemRow("⏱ Browsing History", "Search, filter & restore closed tabs") {
                onCloseMenu()
                onOpenSettings("HISTORY")
            }
            MenuItemDivider()
            MenuItemRow("↓ Download Manager", "${TahoBrowserStateStore.downloads.size} files tracked") {
                onCloseMenu()
                onOpenSettings("DOWNLOADS")
            }
            MenuItemDivider()
            MenuItemRow("🔑 Password Manager & Autofill", "${TahoBrowserStateStore.savedPasswords.size} passwords stored") {
                onCloseMenu()
                onOpenSettings("PASSWORDS")
            }
            MenuItemDivider()
            MenuItemRow("🧩 Extensions & Content Blockers", "${TahoBrowserStateStore.extensions.size} add-ons active") {
                onCloseMenu()
                onOpenSettings("EXTENSIONS")
            }
            MenuItemDivider()
            MenuItemRow("⚙ Full Settings Hub", "Preferences, Privacy, Profiles & About") {
                onCloseMenu()
                onOpenSettings(null)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MenuQuickAction(
    glyph: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(62.dp)
            .tahoPressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (active) TahoGold.copy(alpha = 0.2f) else TahoSurfaceControl)
                .border(1.dp, if (active) TahoGoldHi else TahoHairline, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = if (active) TahoGoldHi else TahoText, fontSize = 16.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = if (active) TahoGoldHi else TahoMuted,
            fontFamily = TahoSans,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun MenuItemRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TahoText,
                fontFamily = TahoSans,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TahoFaint,
                fontFamily = TahoSans,
                fontSize = 9.sp,
            )
        }
        Text("›", color = TahoFaint, fontSize = 16.sp)
    }
}

@Composable
private fun MenuItemDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TahoHairline)
    )
}
