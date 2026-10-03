package app.taho.browser.shell

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
// accent on an interactive control is amber, reserved for the active state and
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

/** Non-text marks only — chevrons, rest-state icon strokes, rules. Fails 4.5:1. See spec §4.1. */
internal val ash = Color(0xFF666F7A)

/** Disabled text only. WCAG 1.4.3 exempts disabled controls; nothing else may use it. See spec §4.1. */
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

/** Dimming veil behind modals and sheets. */
internal val scrim = Color.Black.copy(alpha = .72f)

// ---------- typography (spec §2.2) ----------
// Two registers, not three. Archivo carries display, heading and body;
// JetBrains Mono carries every metric, identifier, timestamp and label value.
internal val TahoMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

internal val TahoBody = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
)

// ---------- shape (spec §2.3) ----------
// Machined, not inflated: panels and tables hold at 0, controls at 2dp, and the
// pill is reserved for tags, switches and progress.
internal val TahoSheetShape = RoundedCornerShape(0.dp)
internal val TahoCardShape = RoundedCornerShape(0.dp)
internal val TahoBlockShape = RoundedCornerShape(2.dp)
internal val TahoNoteShape = RoundedCornerShape(2.dp)
internal val TahoPillShape = RoundedCornerShape(999.dp)
internal val TahoBadgeShape = RoundedCornerShape(4.dp)

// ---------- motion (spec §2.6 / §15) ----------
internal val TahoEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
internal val TahoSpringEasing = CubicBezierEasing(0.34f, 1.4f, 0.44f, 1f)

internal const val TahoDurationScreen = 300
internal const val TahoDurationSheet = 300
internal const val TahoDurationVeil = 200

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
 * Spec §15/A1 — capture-pill content pulse: scale 1 → 1.07 → 1, split 120/180 so
 * the whole beat closes inside one [TahoDurationScreen] transition.
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
        scale.animateTo(1.07f, tween(120, easing = TahoSpringEasing))
        scale.animateTo(1f, tween(180, easing = TahoEasing))
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

/**
 * Spec §15/A1 — the Single Amber Rule made physical: four 11dp corner ticks
 * stroked at 1dp, drawn in when [trigger] changes. This is the only authored
 * entrance motion in the shell; nothing else animates on arrival.
 *
 * Ticks are absolutely positioned inside a `Box` scoped to the focused panel, so
 * the composable imposes no layout of its own. Fully suppressed under reduced
 * motion, where it snaps straight to fully extended.
 *
 * The arm is an [Animatable], not an `animateFloatAsState` target: the two
 * writes must be sequential suspensions on one `MutatorMutex` so the `0f` reset
 * is actually applied before the tween starts. Two plain state writes in one
 * `LaunchedEffect` body land in the same snapshot, Compose coalesces them, and
 * the tween would never relaunch — the reticle would fire once and go inert.
 */
@Composable
internal fun TahoReticle(trigger: Any?, modifier: Modifier = Modifier) {
    val reduced = TahoReducedMotion()
    var seenFirst by remember { mutableStateOf(false) }
    val lenDp = remember { Animatable(0f) }

    LaunchedEffect(trigger, reduced) {
        if (!seenFirst) { seenFirst = true; return@LaunchedEffect }
        if (reduced) { lenDp.snapTo(11f); return@LaunchedEffect }
        lenDp.snapTo(0f)
        lenDp.animateTo(11f, tween(220, easing = TahoEasing))
    }

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val t = 1.dp.toPx()
            // The arm length is what animates. It must be consumed here.
            val arm = lenDp.value.dp.toPx()
            val p = Path()
            p.moveTo(0f, arm); p.lineTo(0f, 0f); p.lineTo(arm, 0f)
            p.moveTo(size.width - arm, 0f); p.lineTo(size.width, 0f)
            p.lineTo(size.width, arm)
            p.moveTo(size.width, size.height - arm); p.lineTo(size.width, size.height)
            p.lineTo(size.width - arm, size.height)
            p.moveTo(arm, size.height); p.lineTo(0f, size.height)
            p.lineTo(0f, size.height - arm)
            // Butt cap is pinned deliberately: at arm == 0 every subpath
            // collapses to three coincident points, and Skia drops a
            // zero-length stroked segment. A round or square cap would
            // instead paint a stray 1dp dot at each corner for one frame.
            drawPath(
                p,
                color = amber,
                style = Stroke(width = t, cap = StrokeCap.Butt),
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
    displaySmall = TextStyle(fontFamily = TahoBody, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = TahoBody, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = TahoBody, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = TahoBody, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelMedium = TextStyle(
        fontFamily = TahoMono, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = TahoMono, fontWeight = FontWeight.Medium,
        fontSize = 9.sp, letterSpacing = 0.72.sp,
    ),
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
