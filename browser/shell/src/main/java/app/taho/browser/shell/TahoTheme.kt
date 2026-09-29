package app.taho.browser.shell

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.taho.browser.shell.R

/*
 * Taho visual tokens. TAHO_DESIGN_SYSTEM.md is the visual authority.
 * UI code should consume these tokens rather than creating local colors,
 * radii, typography families or elevations.
 */

// ---------- solid color system ----------
internal val TahoBg = Color(0xFF0B0B0C)
internal val TahoSheet = Color(0xFF161619)
internal val TahoRaised = Color(0xFF1E1E22)
internal val TahoLine = Color(0xFF2D2D33)
internal val TahoText = Color(0xFFF2EFE8)
internal val TahoMuted = Color(0xFFA3A097)
internal val TahoFaint = Color(0xFF8A867E)
internal val TahoGold = Color(0xFFE2B44A)
internal val TahoGoldHi = Color(0xFFF0CD7E)
internal val TahoGoldWash = Color(0xFF2A2415)
internal val TahoOk = Color(0xFF5FBF8A)
internal val TahoWarn = Color(0xFFE0A64A)
internal val TahoError = Color(0xFFE06A5A)
internal val TahoInfo = Color(0xFF4FBFA3)
internal val TahoDeleteWash = Color(0xFF2E1A17)
internal val TahoPrimaryInk = Color(0xFF1A1405)
internal val TahoToastInk = Color(0xFF111111)

// Compatibility aliases. They intentionally collapse the old translucent
// surface generations into the three solid surface levels from the design system.
internal val TahoHairline = TahoLine
internal val TahoHairlineStrong = TahoLine
internal val TahoSurfaceRow = TahoRaised
internal val TahoSurfaceRowHover = TahoRaised
internal val TahoSurfaceControl = TahoRaised
internal val TahoNeutral = TahoMuted
internal val TahoJsonNum = TahoMuted

// ---------- typography ----------
// General Sans is retained as the bundled sans resource until the repository
// receives the Plus Jakarta Sans binary assets. All UI text still goes through
// one sans family; JetBrains Mono is private to DataText.
internal val TahoSans = FontFamily(
    Font(R.font.general_sans_regular, FontWeight.Normal),
    Font(R.font.general_sans_medium, FontWeight.Medium),
    Font(R.font.general_sans_semibold, FontWeight.SemiBold),
)

internal val TahoDisplay = TahoSans
internal val TahoBody = TahoSans

private val TahoDataFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

@Composable
internal fun DataText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = TahoText,
    fontSize: TextUnit = 12.5.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = 18.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = TahoDataFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

@Composable
internal fun DataText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TahoText,
    fontSize: TextUnit = 12.5.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = 18.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = TahoDataFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

// ---------- radii: 8 / 16 / 24 / pill only ----------
internal val TahoBadgeShape = RoundedCornerShape(8.dp)
internal val TahoCardShape = RoundedCornerShape(16.dp)
internal val TahoBlockShape = RoundedCornerShape(16.dp)
internal val TahoNoteShape = RoundedCornerShape(16.dp)
internal val TahoSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
internal val TahoPillShape = RoundedCornerShape(999.dp)

// ---------- motion ----------
internal val TahoEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
internal val TahoSpringEasing = CubicBezierEasing(0.34f, 1.4f, 0.44f, 1f)
internal const val TahoDurationScreen = 600
internal const val TahoDurationSheet = 700
internal const val TahoDurationVeil = 550

@Composable
internal fun TahoReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** One-shot capture-count pulse; never loops and never runs on first composition. */
@Composable
internal fun Modifier.tahoPulse(trigger: Any?): Modifier {
    val reduced = TahoReducedMotion()
    val scale = remember { Animatable(1f) }
    var seenFirst by remember { mutableStateOf(false) }
    LaunchedEffect(trigger, reduced) {
        if (!seenFirst) {
            seenFirst = true
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        if (reduced) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        scale.snapTo(1f)
        scale.animateTo(1.07f, tween(280, easing = TahoSpringEasing))
        scale.animateTo(1f, tween(520, easing = TahoEasing))
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
internal fun Modifier.tahoPressScale(
    interaction: MutableInteractionSource,
    target: Float = .96f,
): Modifier {
    val reduced = TahoReducedMotion()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) target else 1f,
        animationSpec = spring(
            dampingRatio = .55f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "tahoPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** The transfer screen is the only surface allowed to loop while visible. */
@Composable
internal fun TahoRingPulse(color: Color, modifier: Modifier = Modifier) {
    val reduced = TahoReducedMotion()
    val transition = rememberInfiniteTransition(label = "tahoRing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearOutSlowInEasing),
        ),
        label = "ringProgress",
    )
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        drawCircle(color = color, radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        if (!reduced) {
            drawCircle(
                color = color.copy(alpha = (1f - progress) * .65f),
                radius = radius * (1f + progress * .7f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** Solid status dot: no glow or decoration. */
@Composable
internal fun TahoStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .drawBehind {
                drawCircle(color = color, radius = size.minDimension / 2f)
            },
    )
}

// ---------- Material theme ----------
private val TahoColorScheme = darkColorScheme(
    background = TahoBg,
    surface = TahoSheet,
    surfaceVariant = TahoRaised,
    primary = TahoGold,
    onPrimary = TahoPrimaryInk,
    primaryContainer = TahoGoldWash,
    onPrimaryContainer = TahoGoldHi,
    secondary = TahoMuted,
    onSecondary = TahoBg,
    tertiary = TahoInfo,
    error = TahoError,
    onError = TahoBg,
    onBackground = TahoText,
    onSurface = TahoText,
    onSurfaceVariant = TahoMuted,
    outline = TahoLine,
    outlineVariant = TahoLine,
    scrim = Color.Black.copy(alpha = .60f),
)

private val TahoTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = TahoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = TahoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = TahoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = TahoSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = TahoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = TahoSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
internal fun TahoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TahoColorScheme,
        typography = TahoTypography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = TahoBadgeShape,
            small = TahoCardShape,
            medium = TahoCardShape,
            large = TahoSheetShape,
        ),
        content = content,
    )
}
