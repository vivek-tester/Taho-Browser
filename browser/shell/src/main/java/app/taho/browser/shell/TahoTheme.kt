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
 * Taho design tokens — the NeedMCP `tactical-ops-console` direction (dark field).
 *
 * Colours, type, shape and motion are defined here and nowhere else. Plan 5
 * makes this file the visual source of truth for the shell; the human-readable
 * mirror is Doc/TAHO_BROWSER_UI_UX_SPEC.md §2.
 *
 * Approved spec: docs/superpowers/specs/2026-10-03-tactical-console-port-design.md.
 * TAHO_BROWSER_UI_PROTOTYPE.html is superseded and retained for history only.
 */

// ---------- colour (spec §2.1 — tactical-ops-console, dark field) ----------
// Depth is luminance-stepped; no hue is cast on any panel. The only chromatic
// accent on an interactive control is amber, reserved for the armed state and
// the primary action — tertiary/info, ok and danger are state encodings only.
internal val canvas = Color(0xFF080A0D)
internal val bone = Color(0xFF0E1216)
internal val panel = Color(0xFF12161A)
internal val raised = Color(0xFF1A1F25)
internal val well = Color(0xFF040607)

internal val ink = Color(0xFFEDF0F3)
internal val body = Color(0xFFBCC5CE)
internal val charcoal = Color(0xFF95A0AC)
internal val mute = Color(0xFF828D99)

/** Non-text marks only — chevrons, rest-state icon strokes, rules. Fails 4.5:1. See spec §4.4. */
internal val ash = Color(0xFF666F7A)

/** Disabled text only. WCAG 1.4.3 exempts disabled controls; nothing else may use it. See spec §4.4. */
internal val stone = Color(0xFF4F5861)

// The Single Amber Rule. Also carries warning state — every warning surface
// must additionally render a WARN tag and a 4px status bar so colour is never
// the only signal (WCAG 1.4.1). See spec §4.2.
internal val amber = Color(0xFFE8AE55)
internal val amberHover = Color(0xFFEFC06C)
internal val amberDeep = Color(0xFFD89A3C)

/**
 * Retained legacy name for call sites that read as warnings. Aliases [amber]
 * because the style gives amber to both the primary action and warning state;
 * every warning surface must also carry a tag and a 4dp bar (WCAG 1.4.1).
 * See spec §4.2.
 */
internal val TahoWarn = amber

internal val ok = Color(0xFF5BC088)
internal val info = Color(0xFF82B4D6)
internal val danger = Color(0xFFD96A5E)

internal val hairline = Color(0xFF1E242B)
internal val hairlineStrong = Color(0xFF2C343C)

/** Dimming veil behind modals and sheets — the one non-token value in the scheme. */
internal val scrim = Color.Black.copy(alpha = .72f)

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
    background = canvas,
    surface = panel,
    primary = amber,
    onPrimary = canvas,
    primaryContainer = amberDeep,
    onPrimaryContainer = amber,
    secondary = amberHover,
    onSecondary = canvas,
    tertiary = info,
    error = danger,
    onError = canvas,
    onBackground = body,
    onSurface = body,
    onSurfaceVariant = mute,
    outline = hairlineStrong,
    outlineVariant = hairline,
    scrim = scrim,
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
