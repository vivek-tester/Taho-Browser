package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Capture-oriented first-level browser menu defined by TAHO_DESIGN_SYSTEM.md.
 *
 * Legacy callbacks remain in the signature because the host still owns those
 * flows, but tools intentionally removed from the first level are reachable
 * through Settings instead.
 */
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
    onOpenSettings: (String?) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onCloseMenu: () -> Unit,
    captureCount: Int = 0,
    onOpenCapture: () -> Unit = {},
    onClearCapturedCalls: () -> Unit = {},
    onOpenCaptureSettings: () -> Unit = {},
    onReload: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        BrowserMenuGroupTitle("Capture")
        BrowserMenuRow(
            icon = TahoIconName.OPEN_EXTERNAL,
            label = "Send to Taho",
            trailing = captureCount.takeIf { it > 0 }?.let { "$it new" },
        ) {
            onCloseMenu()
            onOpenCapture()
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = TahoIconName.CLEAR_DATA,
            label = "Clear captured calls",
        ) {
            onCloseMenu()
            onClearCapturedCalls()
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = TahoIconName.TUNE,
            label = "Capture settings",
        ) {
            onCloseMenu()
            onOpenCaptureSettings()
        }

        Spacer(Modifier.height(20.dp))
        BrowserMenuGroupTitle("Page")
        BrowserMenuRow(
            icon = TahoIconName.RELOAD,
            label = "Reload",
        ) {
            onCloseMenu()
            onReload()
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = if (isBookmarked) TahoIconName.BOOKMARK else TahoIconName.BOOKMARK_ADD,
            label = if (isBookmarked) "Remove bookmark" else "Add bookmark",
        ) {
            onToggleBookmark()
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = TahoIconName.SHARE,
            label = "Share",
        ) {
            onCloseMenu()
            onShare()
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = TahoIconName.FIND,
            label = "Find in page",
        ) {
            onCloseMenu()
            onFindInPage()
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = TahoIconName.DESKTOP,
            label = "Desktop site",
            trailing = if (isDesktopMode) "On" else "Off",
        ) {
            onToggleDesktopMode()
        }
        BrowserMenuDivider()
        PageZoomMenuRow(
            zoomPercent = zoomPercent,
            onZoomOut = onZoomOut,
            onZoomReset = onZoomReset,
            onZoomIn = onZoomIn,
        )

        Spacer(Modifier.height(20.dp))
        BrowserMenuGroupTitle("Browser")
        BrowserMenuRow(
            icon = TahoIconName.BOOKMARK,
            label = "Bookmarks",
            trailing = TahoBrowserStateStore.bookmarks.size.toString(),
        ) {
            onCloseMenu()
            onOpenSettings("BOOKMARKS")
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = TahoIconName.HISTORY,
            label = "History",
        ) {
            onCloseMenu()
            onOpenSettings("HISTORY")
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = TahoIconName.DOWNLOAD,
            label = "Downloads",
            trailing = TahoBrowserStateStore.downloads.size.toString(),
        ) {
            onCloseMenu()
            onOpenSettings("DOWNLOADS")
        }
        BrowserMenuDivider()
        BrowserMenuRow(
            icon = TahoIconName.SETTINGS,
            label = "Settings",
        ) {
            onCloseMenu()
            onOpenSettings(null)
        }
    }
}

@Composable
private fun BrowserMenuGroupTitle(text: String) {
    Text(
        text = text,
        color = TahoMuted,
        fontFamily = TahoSans,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun BrowserMenuRow(
    icon: TahoIconName,
    label: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .tahoPressScale(interaction, .96f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TahoIcon(
            name = icon,
            contentDescription = null,
            tint = TahoMuted,
            size = 20.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = TahoText,
            fontFamily = TahoSans,
            fontSize = 14.sp,
            maxLines = 1,
        )
        trailing?.let {
            Text(
                text = it,
                color = TahoMuted,
                fontFamily = TahoSans,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PageZoomMenuRow(
    zoomPercent: Int,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onZoomIn: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
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
            text = "Page zoom",
            modifier = Modifier.weight(1f),
            color = TahoText,
            fontFamily = TahoSans,
            fontSize = 14.sp,
        )
        ZoomIconButton(
            icon = TahoIconName.REMOVE,
            description = "Zoom out",
            onClick = onZoomOut,
        )
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClick = onZoomReset)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$zoomPercent%",
                color = TahoMuted,
                fontFamily = TahoSans,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        ZoomIconButton(
            icon = TahoIconName.ADD,
            description = "Zoom in",
            onClick = onZoomIn,
        )
    }
}

@Composable
private fun ZoomIconButton(
    icon: TahoIconName,
    description: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .tahoPressScale(interaction, .96f)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TahoIcon(
            name = icon,
            contentDescription = null,
            tint = TahoMuted,
            size = 20.dp,
        )
    }
}

@Composable
private fun BrowserMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TahoLine),
    )
}
