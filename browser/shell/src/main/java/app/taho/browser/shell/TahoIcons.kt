package app.taho.browser.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class TahoIconName {
    LOCK,
    MORE,
    HOME,
    SEARCH,
    BACK,
    FORWARD,
    RELOAD,
    CLOSE,
    BOOKMARK,
    BOOKMARK_ADD,
    SHARE,
    FIND,
    DESKTOP,
    SETTINGS,
    CLEAR_DATA,
    TUNE,
    DOWNLOAD,
    HISTORY,
    PASSWORD,
    EXTENSION,
    TRANSLATE,
    PRINT,
    SAVE,
    READER,
    OPEN_EXTERNAL,
    INFO,
    CAMERA,
    MICROPHONE,
    NOTIFICATIONS,
    STORAGE,
    CHEVRON_RIGHT,
    UP,
    DOWN,
    ADD,
    REMOVE,
    FOLDER,
    CREDIT_CARD,
    PERSON,
    SECURITY,
    LANGUAGE,
    BACKUP,
    DIAGNOSTICS,
    PALETTE,
    ACCESSIBILITY,
    ROCKET,
    UPDATE,
    QR,
    COPY,
    SHOW,
    HIDE,
    PIN,
    ARCHIVE,
    TAB,
    DEVICES,
    DESCRIPTION,
    WARNING,
    CHECK,
    FULLSCREEN,
    DELETE,
    STAR,
    STAR_BORDER,
    LINK,
}

private fun TahoIconName.vector(): ImageVector = when (this) {
    TahoIconName.LOCK -> Icons.Outlined.Lock
    TahoIconName.MORE -> Icons.Outlined.MoreVert
    TahoIconName.HOME -> Icons.Outlined.Home
    TahoIconName.SEARCH -> Icons.Outlined.Search
    TahoIconName.BACK -> Icons.Outlined.ArrowBack
    TahoIconName.FORWARD -> Icons.Outlined.ArrowForward
    TahoIconName.RELOAD -> Icons.Outlined.Refresh
    TahoIconName.CLOSE -> Icons.Outlined.Close
    TahoIconName.BOOKMARK -> Icons.Outlined.Bookmark
    TahoIconName.BOOKMARK_ADD -> Icons.Outlined.BookmarkBorder
    TahoIconName.SHARE -> Icons.Outlined.Share
    TahoIconName.FIND -> Icons.Outlined.FindInPage
    TahoIconName.DESKTOP -> Icons.Outlined.DesktopWindows
    TahoIconName.SETTINGS -> Icons.Outlined.Settings
    TahoIconName.CLEAR_DATA -> Icons.Outlined.DeleteSweep
    TahoIconName.TUNE -> Icons.Outlined.Tune
    TahoIconName.DOWNLOAD -> Icons.Outlined.Download
    TahoIconName.HISTORY -> Icons.Outlined.History
    TahoIconName.PASSWORD -> Icons.Outlined.Key
    TahoIconName.EXTENSION -> Icons.Outlined.Extension
    TahoIconName.TRANSLATE -> Icons.Outlined.Translate
    TahoIconName.PRINT -> Icons.Outlined.Print
    TahoIconName.SAVE -> Icons.Outlined.SaveAlt
    TahoIconName.READER -> Icons.Outlined.MenuBook
    TahoIconName.OPEN_EXTERNAL -> Icons.Outlined.OpenInNew
    TahoIconName.INFO -> Icons.Outlined.Info
    TahoIconName.CAMERA -> Icons.Outlined.CameraAlt
    TahoIconName.MICROPHONE -> Icons.Outlined.Mic
    TahoIconName.NOTIFICATIONS -> Icons.Outlined.NotificationsNone
    TahoIconName.STORAGE -> Icons.Outlined.Storage
    TahoIconName.CHEVRON_RIGHT -> Icons.Outlined.ChevronRight
    TahoIconName.UP -> Icons.Outlined.KeyboardArrowUp
    TahoIconName.DOWN -> Icons.Outlined.KeyboardArrowDown
    TahoIconName.ADD -> Icons.Outlined.Add
    TahoIconName.REMOVE -> Icons.Outlined.Remove
    TahoIconName.FOLDER -> Icons.Outlined.Folder
    TahoIconName.CREDIT_CARD -> Icons.Outlined.CreditCard
    TahoIconName.PERSON -> Icons.Outlined.Person
    TahoIconName.SECURITY -> Icons.Outlined.Security
    TahoIconName.LANGUAGE -> Icons.Outlined.Language
    TahoIconName.BACKUP -> Icons.Outlined.Backup
    TahoIconName.DIAGNOSTICS -> Icons.Outlined.Build
    TahoIconName.PALETTE -> Icons.Outlined.Palette
    TahoIconName.ACCESSIBILITY -> Icons.Outlined.AccessibilityNew
    TahoIconName.ROCKET -> Icons.Outlined.RocketLaunch
    TahoIconName.UPDATE -> Icons.Outlined.Update
    TahoIconName.QR -> Icons.Outlined.QrCode
    TahoIconName.COPY -> Icons.Outlined.ContentCopy
    TahoIconName.SHOW -> Icons.Outlined.Visibility
    TahoIconName.HIDE -> Icons.Outlined.VisibilityOff
    TahoIconName.PIN -> Icons.Outlined.PushPin
    TahoIconName.ARCHIVE -> Icons.Outlined.Archive
    TahoIconName.TAB -> Icons.Outlined.Tab
    TahoIconName.DEVICES -> Icons.Outlined.Devices
    TahoIconName.DESCRIPTION -> Icons.Outlined.Description
    TahoIconName.WARNING -> Icons.Outlined.WarningAmber
    TahoIconName.CHECK -> Icons.Outlined.CheckCircle
    TahoIconName.FULLSCREEN -> Icons.Outlined.Fullscreen
    TahoIconName.DELETE -> Icons.Outlined.Delete
    TahoIconName.STAR -> Icons.Outlined.Star
    TahoIconName.STAR_BORDER -> Icons.Outlined.StarBorder
    TahoIconName.LINK -> Icons.Outlined.Link
}

@Composable
internal fun TahoIcon(
    name: TahoIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = TahoMuted,
    size: Dp = 20.dp,
) {
    Icon(
        imageVector = name.vector(),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
internal fun TahoIconButton(
    name: TahoIconName,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .tahoPressScale(interaction, target = .96f)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TahoIcon(
            name = name,
            contentDescription = null,
            tint = when {
                destructive -> TahoError
                active -> TahoText
                else -> TahoMuted
            },
        )
    }
}
