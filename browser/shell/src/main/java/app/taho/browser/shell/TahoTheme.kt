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
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.taho.browser.shell.R

/*
 * Taho design tokens — extracted 1:1 from Doc/TAHO_BROWSER_UI_UX_SPEC.md §2/§15
 * (source of truth: TAHO_BROWSER_UI_PROTOTYPE.html).
 */

// ---------- color (spec §2.1) ----------
internal val TahoBg = Color(0xFF050505)
internal val TahoSheet = Color(0xFF0D0D10)
internal val TahoGold = Color(0xFFE2B44A)
internal val TahoGoldHi = Color(0xFFF0CD7E)
internal val TahoText = Color(0xFFF3F0E9)
internal val TahoMuted = Color(0xFF98948A)
internal val TahoFaint = Color(0xFF6B675F)
internal val TahoOk = Color(0xFF5FBF8A)
internal val TahoWarn = Color(0xFFE0A64A)
internal val TahoInfo = Color(0xFF4FBFA3)
internal val TahoError = Color(0xFFE06A5A)
internal val TahoNeutral = Color(0xFFC9C5BB)
internal val TahoJsonNum = Color(0xFFC9B2F0)

internal val TahoHairline = Color.White.copy(alpha = .08f)
internal val TahoHairlineStrong = Color.White.copy(alpha = .14f)
internal val TahoSurfaceRow = Color.White.copy(alpha = .028f)
internal val TahoSurfaceRowHover = Color.White.copy(alpha = .055f)
internal val TahoSurfaceControl = Color.White.copy(alpha = .035f)

// ---------- typography (spec §2.2) ----------
internal val TahoMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

internal val TahoDisplay = FontFamily(
    Font(R.font.clash_display_medium, FontWeight.Medium),
    Font(R.font.clash_display_semibold, FontWeight.SemiBold),
)

internal val TahoBody = FontFamily(
    Font(R.font.general_sans_regular, FontWeight.Normal),
    Font(R.font.general_sans_medium, FontWeight.Medium),
    Font(R.font.general_sans_semibold, FontWeight.SemiBold),
)

// ---------- shape (spec §2.3) ----------
internal val TahoSheetShape = RoundedCornerShape(26.dp)
internal val TahoCardShape = RoundedCornerShape(16.dp)
internal val TahoBlockShape = RoundedCornerShape(14.dp)
internal val TahoNoteShape = RoundedCornerShape(12.dp)
internal val TahoPillShape = RoundedCornerShape(999.dp)
internal val TahoBadgeShape = RoundedCornerShape(6.dp)

// ---------- motion (spec §2.6 / §15) ----------
internal val TahoEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
internal val TahoSpringEasing = CubicBezierEasing(0.34f, 1.4f, 0.44f, 1f)

internal const val TahoDurationScreen = 600
internal const val TahoDurationSheet = 750
internal const val TahoDurationVeil = 550

/** True when the user has disabled system animations (animator scale 0). */
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

/**
 * Spec §15/A1 — capture-pill content pulse: scale 1 → 1.07 @35% → 1 over 800ms.
 * Plays only when [trigger] *changes* after initial composition — never on first
 * entry, matching the prototype (the pill is quiet until an event lands).
 * Disabled under reduced motion.
 */
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

/**
 * Spec §15/A4 + D1 — press physics: press scales to [target] (.96 primary,
 * .97 controls) with a springy release. Wire [interaction] into clickable().
 */
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

/** Spec §15/F1 — handoff ring pulse: expanding, fading outline over 1.6s. */
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
        drawCircle(
            color = color,
            radius = radius,
            style = Stroke(width = 1.dp.toPx()),
        )
        if (!reduced) {
            drawCircle(
                color = color.copy(alpha = (1f - progress) * .65f),
                radius = radius * (1f + progress * .7f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** Spec §4.2 — status dot with the 10px radial glow from the prototype. */
@Composable
internal fun TahoStatusDot(color: Color, modifier: Modifier = Modifier, glow: Boolean = true) {
    Box(
        modifier = modifier
            .size(6.dp)
            .drawBehind {
                if (glow) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = .55f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 2.6f,
                        ),
                        radius = size.width * 2.6f,
                    )
                }
                drawCircle(color = color, radius = size.minDimension / 2f)
            },
    )
}

// ---------- theme ----------
private val TahoColorScheme = darkColorScheme(
    background = TahoBg,
    surface = TahoSheet,
    primary = TahoGold,
    onPrimary = TahoBg,
    primaryContainer = TahoGold.copy(alpha = .16f),
    onPrimaryContainer = TahoGoldHi,
    secondary = TahoGoldHi,
    onSecondary = TahoBg,
    tertiary = TahoInfo,
    error = TahoError,
    onError = TahoBg,
    onBackground = TahoText,
    onSurface = TahoText,
    onSurfaceVariant = TahoMuted,
    outline = TahoHairlineStrong,
    outlineVariant = TahoHairline,
    scrim = Color.Black.copy(alpha = .5f),
)

private val TahoTypography = Typography(
    displaySmall = TextStyle(fontFamily = TahoDisplay, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = TahoDisplay, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = TahoBody, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = TahoBody, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = TahoBody, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelMedium = TextStyle(fontFamily = TahoMono, fontWeight = FontWeight.Normal, fontSize = 10.sp),
    labelSmall = TextStyle(fontFamily = TahoMono, fontWeight = FontWeight.Normal, fontSize = 9.sp),
)

@Composable
internal fun TahoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TahoColorScheme,
        typography = TahoTypography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = TahoBadgeShape,
            small = TahoNoteShape,
            medium = TahoCardShape,
            large = TahoSheetShape,
        ),
        content = content,
    )
}
