package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

internal val TahoTouchTarget = 48.dp
internal val TahoCompactTouchTarget = 48.dp
internal val TahoSheetHorizontalPadding = 20.dp
internal val TahoChromeHorizontalPadding = 12.dp

internal enum class TahoActionStyle {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    MINI,
    DESTRUCTIVE,
}

@Composable
internal fun TahoActionButton(
    label: String,
    modifier: Modifier = Modifier,
    style: TahoActionStyle = TahoActionStyle.PRIMARY,
    enabled: Boolean = true,
    trailingGlyph: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val showNestedActionIcon =
        style == TahoActionStyle.PRIMARY &&
            (label == "Send to Taho" || label == "Open Request")
    val background = when (style) {
        TahoActionStyle.PRIMARY -> TahoGold
        TahoActionStyle.SECONDARY,
        TahoActionStyle.MINI,
        -> TahoRaised
        TahoActionStyle.TERTIARY -> Color.Transparent
        TahoActionStyle.DESTRUCTIVE -> TahoDeleteWash
    }
    val foreground = when (style) {
        TahoActionStyle.PRIMARY -> TahoPrimaryInk
        TahoActionStyle.SECONDARY -> TahoText
        TahoActionStyle.TERTIARY, TahoActionStyle.MINI -> TahoMuted
        TahoActionStyle.DESTRUCTIVE -> TahoError
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .tahoPressScale(interaction, target = .96f)
            .clip(TahoPillShape)
            .background(background)
            .then(
                when (style) {
                    TahoActionStyle.SECONDARY,
                    TahoActionStyle.MINI,
                    TahoActionStyle.DESTRUCTIVE,
                    -> Modifier.border(
                        1.dp,
                        if (style == TahoActionStyle.DESTRUCTIVE) TahoError else TahoLine,
                        TahoPillShape,
                    )
                    else -> Modifier
                },
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = if (showNestedActionIcon) 8.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else .40f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = if (showNestedActionIcon) Modifier.weight(1f) else Modifier,
                color = foreground,
                fontFamily = TahoSans,
                fontWeight = if (style == TahoActionStyle.PRIMARY) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showNestedActionIcon) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(TahoPillShape)
                        .background(TahoPrimaryInk),
                    contentAlignment = Alignment.Center,
                ) {
                    TahoIcon(
                        name = TahoIconName.OPEN_EXTERNAL,
                        contentDescription = null,
                        tint = TahoGold,
                        size = 16.dp,
                    )
                }
            }
        }
    }
}

/**
 * Legacy glyph-call adapter while screens are migrated. It renders from the
 * single outlined icon set; glyph text is never shown.
 */
@Composable
internal fun TahoChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    semanticColor: Color = TahoGold,
    description: String = label,
    onClick: () -> Unit,
) {
    val selectedFill =
        if (semanticColor == TahoGold) TahoGoldWash
        else TahoRaised
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(TahoPillShape)
            .background(if (selected) selectedFill else TahoSheet)
            .border(
                1.dp,
                if (selected) semanticColor else TahoLine,
                TahoPillShape,
            )
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.alpha(if (enabled) 1f else .40f),
            color = when {
                selected && semanticColor == TahoGold -> TahoGoldHi
                selected -> semanticColor
                else -> TahoMuted
            },
            fontFamily = TahoSans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun TahoSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(bottom = 8.dp),
        color = TahoMuted,
        fontFamily = TahoSans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun TahoToggleRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TahoText,
                    fontFamily = TahoSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                description?.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = TahoMuted,
                        fontFamily = TahoSans,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(TahoPillShape)
                    .background(if (checked) TahoGoldWash else TahoRaised)
                    .border(1.dp, if (checked) TahoGold else TahoLine, TahoPillShape)
                    .padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(TahoPillShape)
                        .background(if (checked) TahoGoldHi else TahoMuted),
                )
            }
        }
        TahoDivider()
    }
}

@Composable
internal fun TahoLinkRow(
    label: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    trailingGlyph: String = "",
    onClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = TahoText,
                    fontFamily = TahoSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supportingText?.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = TahoMuted,
                        fontFamily = TahoSans,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            TahoIcon(
                name = TahoIconName.CHEVRON_RIGHT,
                contentDescription = null,
                tint = TahoMuted,
                size = 20.dp,
            )
        }
        TahoDivider()
    }
}

@Composable
internal fun TahoDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TahoLine),
    )
}

@Composable
internal fun TahoGrabHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(top = 8.dp, bottom = 8.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(TahoPillShape)
            .background(TahoLine),
    )
}
