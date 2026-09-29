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
import androidx.compose.foundation.shape.CircleShape
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

internal val TahoTouchTarget = 48.dp
internal val TahoCompactTouchTarget = 44.dp
internal val TahoSheetHorizontalPadding = 19.dp
internal val TahoChromeHorizontalPadding = 13.dp

internal enum class TahoActionStyle {
    PRIMARY,
    SECONDARY,
    MINI,
    DESTRUCTIVE,
}

@Composable
internal fun TahoActionButton(
    label: String,
    modifier: Modifier = Modifier,
    style: TahoActionStyle = TahoActionStyle.PRIMARY,
    enabled: Boolean = true,
    trailingGlyph: String? = if (style == TahoActionStyle.PRIMARY) "↗" else null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val minimumHeight =
        if (style == TahoActionStyle.MINI) TahoCompactTouchTarget else TahoTouchTarget
    val background = when (style) {
        TahoActionStyle.PRIMARY ->
            if (enabled) TahoGold else TahoSurfaceControl
        TahoActionStyle.SECONDARY,
        TahoActionStyle.MINI,
        -> TahoSurfaceControl
        TahoActionStyle.DESTRUCTIVE -> TahoError.copy(alpha = .10f)
    }
    val border = when (style) {
        TahoActionStyle.PRIMARY -> Color.Transparent
        TahoActionStyle.DESTRUCTIVE -> TahoError.copy(alpha = .42f)
        else -> TahoHairline
    }
    val foreground = when {
        !enabled -> TahoFaint
        style == TahoActionStyle.PRIMARY -> TahoBg
        style == TahoActionStyle.DESTRUCTIVE -> TahoError
        style == TahoActionStyle.MINI -> TahoMuted
        else -> TahoText
    }

    val pressModifier = when (style) {
        TahoActionStyle.MINI -> Modifier
        TahoActionStyle.PRIMARY -> Modifier.tahoPressScale(interaction, target = .96f)
        else -> Modifier.tahoPressScale(interaction, target = .97f)
    }

    Box(
        modifier = modifier
            .then(pressModifier)
            .heightIn(min = minimumHeight)
            .clip(TahoPillShape)
            .background(background)
            .then(
                if (border == Color.Transparent) Modifier
                else Modifier.border(1.dp, border, TahoPillShape),
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
            .padding(
                start = if (trailingGlyph != null) 16.dp else 14.dp,
                end = if (trailingGlyph != null) 6.dp else 14.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = if (trailingGlyph != null) Modifier.weight(1f) else Modifier,
                color = foreground,
                fontFamily =
                    if (style == TahoActionStyle.MINI) TahoMono else TahoBody,
                fontWeight =
                    if (style == TahoActionStyle.MINI) FontWeight.Normal else FontWeight.SemiBold,
                fontSize =
                    if (style == TahoActionStyle.MINI) 9.5.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailingGlyph?.let { glyph ->
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (style == TahoActionStyle.PRIMARY) {
                                Color.Black.copy(alpha = .15f)
                            } else {
                                TahoSurfaceRowHover
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = glyph,
                        color = foreground,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TahoIconButton(
    glyph: String,
    description: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val foreground = when {
        destructive -> TahoError
        emphasized -> TahoGoldHi
        else -> TahoMuted
    }
    Box(
        modifier = modifier
            .size(TahoCompactTouchTarget)
            .tahoPressScale(interaction, target = .97f)
            .clip(TahoNoteShape)
            .background(
                when {
                    destructive -> TahoError.copy(alpha = .08f)
                    emphasized -> TahoGold.copy(alpha = .10f)
                    else -> TahoSurfaceControl
                },
            )
            .border(
                1.dp,
                when {
                    destructive -> TahoError.copy(alpha = .32f)
                    emphasized -> TahoGold.copy(alpha = .35f)
                    else -> TahoHairline
                },
                TahoNoteShape,
            )
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
        Text(
            text = glyph,
            color = foreground,
            fontSize = 15.sp,
        )
    }
}

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
    Box(
        modifier = modifier
            .heightIn(min = TahoCompactTouchTarget)
            .clip(TahoPillShape)
            .background(
                if (selected) semanticColor.copy(alpha = .13f)
                else TahoSurfaceControl,
            )
            .border(
                1.dp,
                if (selected) semanticColor.copy(alpha = .45f)
                else TahoHairline,
                TahoPillShape,
            )
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> TahoFaint
                selected && semanticColor == TahoGold -> TahoGoldHi
                selected -> semanticColor
                else -> TahoMuted
            },
            fontFamily = TahoMono,
            fontSize = 9.5.sp,
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
        text = text.uppercase(),
        modifier = modifier.padding(bottom = 6.dp),
        color = TahoFaint,
        fontFamily = TahoMono,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TahoTouchTarget)
            .clip(TahoBlockShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TahoText,
                fontFamily = TahoBody,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            description?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    color = TahoMuted,
                    fontFamily = TahoBody,
                    fontSize = 10.sp,
                    maxLines = 3,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(TahoPillShape)
                .background(if (checked) TahoGold.copy(alpha = .16f) else TahoSurfaceControl)
                .border(
                    1.dp,
                    if (checked) TahoGold.copy(alpha = .45f) else TahoHairline,
                    TahoPillShape,
                )
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                color = if (checked) TahoGoldHi else TahoFaint,
                fontFamily = TahoMono,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun TahoLinkRow(
    label: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    trailingGlyph: String = "↗",
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TahoTouchTarget)
            .clip(TahoBlockShape)
            .background(TahoSurfaceRow)
            .border(1.dp, TahoHairline, TahoBlockShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TahoText,
                fontFamily = TahoBody,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            supportingText?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    color = TahoMuted,
                    fontFamily = TahoBody,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = trailingGlyph,
            color = TahoGoldHi,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun TahoGrabHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .width(38.dp)
            .height(4.dp)
            .clip(TahoPillShape)
            .background(TahoHairlineStrong),
    )
}
