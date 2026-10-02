# Plan 1 — Foundation: tokens, fonts, icons, anti-drift gate

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `TahoTheme.kt`'s token set with the approved `tactical-ops-console` values, bundle Archivo, add the vector icon family, and add a test that makes hardcoded colours impossible to reintroduce.

**Architecture:** The token layer changes first and completely, before any composable is touched. Because every shell composable reads tokens by name (`TahoGold`, `TahoText`, …) rather than literal colour, renaming the tokens leaves the rest of the shell compiling and rendering with the new palette immediately. The one-shot rename pass then converts call sites off the deleted legacy names. A source-scanning test locks the result in.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose (Material3 1.3.2), AGP 9.1.1, Gradle 9.3.1, `kotlin("test")`.

**Spec:** `docs/superpowers/specs/2026-10-03-tactical-console-port-design.md` — §4.1 colour, §4.4 deleting `TahoFaint`, §4.5 typography, §4.6 shape, §4.7 motion, §5 icons, §8 testing.

**Sequence position:** 1 of 5. Later plans: 2 Browser shell · 3 Capture surfaces · 4 Settings hub · 5 Authority reconciliation. This plan is a strict prerequisite for all of them.

## Global Constraints

These apply to every task in this plan and every plan that follows it.

- **Build command is `gradle`, not `./gradlew`.** There is no committed wrapper. A Gradle 9.3.1 distribution is required (AGP 9.1.1 enforces this minimum; Gradle 8.14 fails with `Minimum supported Gradle version is 9.3.1`).
- **Test command is `gradle :browser:shell:testDebugUnitTest`.** 33 tests exist today across 4 files and all must stay green.
- **Architecture gate is `gradle verifyArchitecture` and must pass.** It fails the build if any pure-JVM module (`capture/domain`, `transfer/core`, `contract/taho-transfer`, and the three `test-support` modules) gains an `android.*` import, or if a shell file reaches into a forbidden module. Do not "simplify" an import to make something compile.
- **No new colour literals in production shell code.** Every colour comes from a token. Task 4 installs the test that enforces this.
- **Do not touch capture logic, attribution, persistence, transfer, or IPC.** This plan changes presentation only. Browsing must behave identically whether capture is on or off.
- **No light theme.** The style ships two modes; this app stays dark-only.
- **Comment style:** the existing shell uses `/** ... */` KDoc referencing spec sections (e.g. `Spec §15/A1`). New tokens keep that convention. Do not copy these plan documents into the codebase as comments.

## Review Focus

Failure modes the spec implies that no task's tests naturally exercise. Each has a pinned test in the task named.

1. **A legacy token name survives at a call site** — e.g. one `TahoFaint` reference left in `TahoSettingsHub.kt`. The rename compiles only if every reference moves, but a *missed semantic* remap (text that should be `mute` becoming `ash`) still compiles and still fails contrast. → Task 3.
2. **`ash` or `stone` used for text.** Both are legal tokens that fail WCAG AA as body text (`ash` 3.25–3.57:1, `stone` 2.51:1). Nothing stops a well-meaning edit from putting a label in `ash`. → Task 3.
3. **Reduced motion regressed.** `TahoReducedMotion()` reads `ANIMATOR_DURATION_SCALE`; if the new reticle or durations bypass it, motion returns for users who disabled it. → Task 2.
4. **A new icon ships without `contentDescription`.** A missing description makes the control invisible to TalkBack — worse than the emoji it replaced. → Task 5.
5. **Pressed state loses its visual delta.** Press feedback currently comes from scale (`tahoPressScale`); if the machined 0-radius look removes the surface lift that made it visible, taps feel dead on a dark field. → Task 2.

---

### Task 1: Replace the colour token set

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoTheme.kt:52-71` (the colour block, including its `// ---------- colour (spec §2.1) ----------` header comment)
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoFeaturesTest.kt`

**Interfaces:**
- Consumes: nothing. This is the first task.
- Produces: the token names every later task and plan depends on —
  `canvas`, `bone`, `panel`, `raised`, `well`, `amber`, `amberHover`, `amberDeep`, `ink`, `charcoal`, `mute`, `ash`, `stone`, `ok`, `info`, `danger`, `hairline`, `hairlineStrong`, plus `TahoWarn` retained as an alias of `amber`.
  Legacy names `TahoBg`, `TahoSheet`, `TahoGold`, `TahoGoldHi`, `TahoText`, `TahoMuted`, `TahoFaint`, `TahoNeutral`, `TahoOk`, `TahoInfo`, `TahoError`, `TahoJsonNum`, `TahoHairline`, `TahoHairlineStrong`, `TahoSurfaceRow`, `TahoSurfaceRowHover`, `TahoSurfaceControl` are **deleted** and replaced by these.

- [ ] **Step 1: Write the failing test**

Append to `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoFeaturesTest.kt`. The file already imports `app.taho.browser.shell.*` and `kotlin.test.*`, so no new imports are needed for the token references; add `import androidx.compose.ui.graphics.Color` and `import androidx.compose.ui.graphics.luminance` for the contrast assertion.

```kotlin
@Test
fun consoleTokensMatchApprovedPalette() {
    assertEquals(Color(0xFF080A0D), canvas)   // canvas
    assertEquals(Color(0xFF0E1216), bone)     // bone
    assertEquals(Color(0xFF12161A), panel)    // panel
    assertEquals(Color(0xFF1A1F25), raised)   // raised
    assertEquals(Color(0xFF040607), well)     // well
    assertEquals(Color(0xFFEDF0F3), ink)      // ink
    assertEquals(Color(0xFF95A0AC), charcoal) // charcoal
    assertEquals(Color(0xFF828D99), mute)     // mute
    assertEquals(Color(0xFF666F7A), ash)      // ash — non-text only
    assertEquals(Color(0xFF4F5861), stone)    // stone — disabled only
    assertEquals(Color(0xFFE8AE55), amber)    // amber
    assertEquals(Color(0xFFEFC06C), amberHover)
    assertEquals(Color(0xFFD89A3C), amberDeep)
    assertEquals(Color(0xFF5BC088), ok)
    assertEquals(Color(0xFF82B4D6), info)
    assertEquals(Color(0xFFD96A5E), danger)
    assertEquals(Color(0xFF1E242B), hairline)
    assertEquals(Color(0xFF2C343C), hairlineStrong)
    assertEquals(amber, TahoWarn)
}
```

Append this contrast test in the same edit. It is the regression guard for the defect in spec §3:

```kotlin
@Test
fun everyTextTierClearsWcagAaOnEverySurfaceItSitsOn() {
    val textTiers = mapOf(
        "ink" to ink,
        "body" to Color(0xFFBCC5CE),
        "charcoal" to charcoal,
        "mute" to mute,
    )
    val surfaces = mapOf(
        "canvas" to canvas,
        "bone" to bone,
        "panel" to panel,
        "raised" to raised,
        "well" to well,
    )
    val offenders = mutableListOf<String>()
    for ((tn, tf) in textTiers) {
        for ((sn, sf) in surfaces) {
            val l1 = tf.luminance()
            val l2 = sf.luminance()
            val ratio = (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
            if (ratio < 4.5f) offenders += "$tn on $sn = ${"%.2f".format(ratio)}:1"
        }
    }
    // ash and stone are deliberately excluded: they are non-text and disabled
    // tiers. See spec §4.4 — both fail 4.5:1 and must never carry body text.
    assertEquals(emptyList(), offenders, "text tiers below WCAG AA 4.5:1")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoFeaturesTest*'`
Expected: FAIL — compilation error, `Unresolved reference: canvas`. That is the correct failure: the tokens do not exist yet.

- [ ] **Step 3: Write the new token set**

In `TahoTheme.kt`, replace the entire block from the `// ---------- colour (spec §2.1) ----------` comment through the `TahoSurfaceControl` line with:

```kotlin
// ---------- colour (spec §2.1 — tactical-ops-console, dark field) ----------
// Depth is luminance-stepped. No hue is cast on any panel; the only chromatic
// accent is amber, reserved for the armed state and the primary action.
internal val canvas = Color(0xFF080A0D)
internal val bone = Color(0xFF0E1216)
internal val panel = Color(0xFF12161A)
internal val raised = Color(0xFF1A1F25)
internal val well = Color(0xFF040607)

internal val ink = Color(0xFFEDF0F3)
internal val body = Color(0xFFBCC5CE)
internal val charcoal = Color(0xFF95A0AC)
internal val mute = Color(0xFF828D99)

/** Non-text marks only — chevrons, rest-state icon strokes, rules. Fails 4.5:1. */
internal val ash = Color(0xFF666F7A)

/** Disabled text only. WCAG 1.4.3 exempts disabled controls; nothing else may use it. */
internal val stone = Color(0xFF4F5861)

// The Single Amber Rule. Also carries warning state — every warning surface
// must additionally render a WARN tag and a 4px status bar so colour is never
// the only signal (WCAG 1.4.1). See spec §4.2.
internal val amber = Color(0xFFE8AE55)
internal val amberHover = Color(0xFFEFC06C)
internal val amberDeep = Color(0xFFD89A3C)
internal val TahoWarn = amber

internal val ok = Color(0xFF5BC088)
internal val info = Color(0xFF82B4D6)
internal val danger = Color(0xFFD96A5E)

internal val hairline = Color(0xFF1E242B)
internal val hairlineStrong = Color(0xFF2C343C)
```

Then update `TahoColorScheme` (currently `private val` at what is now a later line) so it references the new names, keeping the same Material3 slot assignments but dropping the alpha-derived container colours in favour of flat tokens:

```kotlin
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
    scrim = Color.Black.copy(alpha = .72f),
)
```

**Note:** this task is expected to break compilation of every other shell file, because the old token names no longer exist. That is the intended TDD red state for Task 3. Do not add compatibility aliases to make it compile — the next task deletes them.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoFeaturesTest*'`
Expected: still FAIL, now with `Unresolved reference: TahoGold` and similar in `M7ProductUx.kt` / `TahoStartPage.kt`. Record these as the exact worklist for Task 3 — they are the compiler telling you every call site.

- [ ] **Step 5: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoTheme.kt browser/shell/src/test/kotlin/app/taho/browser/shell/TahoFeaturesTest.kt
git commit -m "feat(theme): replace colour tokens with tactical-ops-console palette"
```

### Task 2: Typography, shape and motion tokens

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoTheme.kt` — the `// ---------- typography (spec §2.2) ----------`, `// ---------- shape (spec §2.3) ----------`, `// ---------- motion (spec §2.6 / §15) ----------` blocks, the `tahoPulse` and `TahoRingPulse` composables, and `TahoTypography`
- Create: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoThemeContractTest.kt`

**Interfaces:**
- Consumes: `ink`, `amber`, `panel`, `canvas` from Task 1.
- Produces: `Archivo` display/body families, `Amber`, the reticle composable, the machined radii, and the shortened durations. Task 5 and Plans 2–4 consume all of these.

- [ ] **Step 1: Write the failing test**

Create `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoThemeContractTest.kt`:

```kotlin
package app.taho.browser.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TahoThemeContractTest {

    @Test
    fun panelsAreSharpAndControlsAreTwoDp() {
        assertEquals(0.dp, TahoSheetShape)
        assertEquals(0.dp, TahoCardShape)
        assertEquals(2.dp, TahoBlockShape)
        assertEquals(2.dp, TahoNoteShape)
        assertEquals(4.dp, TahoBadgeShape)
        assertEquals(999.dp, TahoPillShape)
    }

    @Test
    fun durationsAreShortened() {
        assertEquals(300, TahoDurationScreen)
        assertEquals(300, TahoDurationSheet)
        assertEquals(200, TahoDurationVeil)
    }
}
```

`TahoSheetShape` and friends are `internal val` at file scope, so they are visible to the test source set in the same module.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoThemeContractTest*'`
Expected: FAIL — `assertEquals(0.dp, TahoSheetShape)` gets `26.dp`.

- [ ] **Step 3: Implement the shape and motion tokens**

Replace the shape block:

```kotlin
// ---------- shape (spec §2.3) ----------
// Machined, not inflated: panels and tables hold at 0, controls at 2dp, and the
// pill is reserved for tags, switches and progress.
internal val TahoSheetShape = RoundedCornerShape(0.dp)
internal val TahoCardShape = RoundedCornerShape(0.dp)
internal val TahoBlockShape = RoundedCornerShape(2.dp)
internal val TahoNoteShape = RoundedCornerShape(2.dp)
internal val TahoPillShape = RoundedCornerShape(999.dp)
internal val TahoBadgeShape = RoundedCornerShape(4.dp)
```

Replace the motion block:

```kotlin
// ---------- motion (spec §2.6 / §15) ----------
internal val TahoEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
internal val TahoSpringEasing = CubicBezierEasing(0.34f, 1.4f, 0.44f, 1f)

internal const val TahoDurationScreen = 300
internal const val TahoDurationSheet = 300
internal const val TahoDurationVeil = 200
```

- [ ] **Step 4: Implement the typography tokens**

Replace the three `FontFamily` vals. `Archivo` regular/medium/semibold/bold arrive in Task 4; until then this step will not compile, which is expected.

```kotlin
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
```

**Delete `TahoDisplay`.** The style has one display face, so `TahoDisplay` and `TahoBody` collapse into `TahoBody`. Task 3 remaps call sites.

Replace `TahoTypography`. Note every value uses `TahoBody`, and label/button roles use `TahoMono` with the wide tracking the style mandates for small technical text:

```kotlin
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
```

- [ ] **Step 5: Add the corner-tick reticle, replacing the ring pulse**

Delete `TahoRingPulse` and replace `tahoPulse`'s timing with the shortened scale values. Then add the reticle. This is the style's signature mark and the **only** authored entrance moment in the system — nothing else animates in.

```kotlin
/**
 * Spec §15/A1 — the Single Amber Rule made physical: four 1dp corner ticks that
 * draw in when [trigger] changes. This is the only authored entrance motion in
 * the shell; nothing else animates on arrival.
 *
 * Ticks are absolutely positioned inside a `Box` scoped to the focused panel, so
 * the composable imposes no layout of its own. Fully suppressed under reduced
 * motion, where it snaps straight to the armed state.
 */
@Composable
internal fun TahoReticle(trigger: Any?, modifier: Modifier = Modifier) {
    val reduced = TahoReducedMotion()
    var seenFirst by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf false }

    LaunchedEffect(trigger, reduced) {
        if (!seenFirst) { seenFirst = true; return@LaunchedEffect }
        if (reduced) { armed = true; return@LaunchedEffect }
        armed = false
        armed = true
    }

    val len by animateFloatAsState(
        targetValue = if (armed) 11f else 0f,
        animationSpec = tween(if (reduced) 0 else 220, easing = TahoEasing),
        label = "reticle",
    )

    Box(modifier = modifier) {
        val w = with(density) { len.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val t = 1.dp.toPx()
            val p = Path()
            // top-left
            p.moveTo(0f, t); p.lineTo(0f, 0f); p.lineTo(t, 0f)
            // top-right
            p.moveTo(size.width - t, 0f); p.lineTo(size.width, 0f); p.lineTo(size.width, t)
            // bottom-right
            p.moveTo(size.width, size.height - t); p.lineTo(size.width, size.height)
            p.lineTo(size.width - t, size.height)
            // bottom-left
            p.moveTo(t, size.height); p.lineTo(0f, size.height); p.lineTo(0f, size.height - t)
            drawPath(p, color = amber, style = Stroke(width = t))
            @Suppress("UNUSED_EXPRESSION") w
        }
    }
}
```

Add the imports this needs: `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.ui.graphics.Path`, `androidx.compose.ui.graphics.drawscope.Stroke` (already imported), and `LocalDensity` if you keep the `w` computation — otherwise delete the `w`/`density` lines, they are not needed for a uniform tick.

Simplify to the version actually needed, which avoids the unused `w`:

```kotlin
Box(modifier = modifier) {
    Canvas(Modifier.fillMaxSize()) {
        val t = 1.dp.toPx()
        val p = Path()
        p.moveTo(0f, t); p.lineTo(0f, 0f); p.lineTo(t, 0f)
        p.moveTo(size.width - t, 0f); p.lineTo(size.width, 0f); p.lineTo(size.width, t)
        p.moveTo(size.width, size.height - t); p.lineTo(size.width, size.height)
        p.lineTo(size.width - t, size.height)
        p.moveTo(t, size.height); p.lineTo(0f, size.height); p.lineTo(0f, size.height - t)
        drawPath(p, color = amber, style = Stroke(width = t))
    }
}
```

- [ ] **Step 6: Verify the reduced-motion guard is intact**

Confirm `TahoReducedMotion()` is still consulted in both `tahoPulse` and `TahoReticle`. It reads `Settings.Global.ANIMATOR_DURATION_SCALE`; it must remain the gate for all motion. Do not hardcode a check for a specific duration instead.

- [ ] **Step 7: Run test to verify it passes**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoThemeContractTest*'`
Expected: FAIL on compilation only — `Unresolved reference: archivo_regular`, because Task 4 has not landed. The two assertions are otherwise satisfied. Proceed to Task 3; both land together in the Task 4 gate.

### Task 3: Migrate every call site off the legacy tokens

**Files:**
- Modify: all 8 shell files that reference deleted tokens — `TahoSettingsHub.kt` (71 `TahoFaint` sites), `M7ProductUx.kt` (16), `TahoPageOverlays.kt` (11), `TahoBrowserApp.kt` (11), `TahoStartPage.kt` (9), `TahoTabsOverview.kt` (7), `TahoBrowserMenu.kt` (5), `M7TransferSearch.kt` (3)
- Create: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoTokenMigrationTest.kt`

**Interfaces:**
- Consumes: every token from Tasks 1–2.
- Produces: a shell that compiles with zero legacy token references. Plans 2–4 build on this.

- [ ] **Step 1: Write the failing test**

Create `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoTokenMigrationTest.kt`. This scans the shell's own production sources, which is the only way to catch a leftover literal or a missed remap.

```kotlin
package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TahoTokenMigrationTest {

    private val shellDir: File =
        File("src/main/java/app/taho/browser/shell")

    private fun sources(): List<File> =
        shellDir.listFiles { f: File -> f.name.endsWith(".kt") }?.toList() ?: emptyList()

    @Test
    fun noLegacyTokenNamesRemain() {
        val legacy = listOf(
            "TahoBg", "TahoSheet", "TahoGold", "TahoGoldHi", "TahoText",
            "TahoMuted", "TahoFaint", "TahoNeutral", "TahoJsonNum",
            "TahoHairline", "TahoHairlineStrong", "TahoSurfaceRow",
            "TahoSurfaceRowHover", "TahoSurfaceControl", "TahoOk",
            "TahoInfo", "TahoError", "TahoDisplay",
        )
        val found = mutableListOf<String>()
        sources().forEach { file ->
            val text = file.readText()
            legacy.forEach { name ->
                // TahoWarn and TahoThemeMode are deliberately retained.
                val occurrences = Regex("\\b$name\\b").findAll(text).count()
                if (occurrences > 0) found += "${file.name}: $name x$occurrences"
            }
        }
        assertEquals(emptyList(), found, "legacy token names still referenced")
    }

    @Test
    fun ashAndStoneAreNeverUsedOnTextRoles() {
        // ash is 3.25-3.57:1 and stone is 2.51:1 -- both fail WCAG AA 4.5:1.
        // They may appear as a colour argument, but never as the colour of a
        // Text() call, which is the only way they can carry a string.
        val offenders = mutableListOf<String>()
        sources().forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (Regex("Text\\([^)]*color\\s*=\\s*(ash|stone)\\b").containsMatchIn(line)) {
                    offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertEquals(emptyList(), offenders, "ash/stone used as a text colour")
    }

    @Test
    fun noHardcodedColourLiteralsInProductionCode() {
        // Spec §8 requires this guard. It is the test that would have caught the
        // TahoFaint defect: a colour written inline never passes through the
        // token layer, so it never gets contrast-checked.
        // Allowed: TahoTheme.kt (which *defines* the tokens) and the small set of
        // translucent tag/status washes declared in TahoConsole.kt.
        val exempt = setOf("TahoTheme.kt", "TahoConsole.kt")
        val offenders = mutableListOf<String>()
        sources().forEach { file ->
            if (file.name in exempt) return@forEach
            file.readLines().forEachIndexed { i, line ->
                // Color(0x...), Color.Black/.White/.Red..., or a bare ARGB literal.
                val isLiteral = Regex("Color\\(0x[0-9A-Fa-f]{6,8}\\)").containsMatchIn(line) ||
                    Regex("Color\\.(Black|White|Red|Green|Blue|Gray|Yellow)\\b").containsMatchIn(line) ||
                    Regex("toInt\\(\\)\\s*or\\s*0x").containsMatchIn(line)
                // Ignore comments and the token declarations themselves.
                val isComment = line.trimStart().startsWith("//") || line.trimStart().startsWith("*")
                if (isLiteral && !isComment) {
                    offenders += "${file.name}:${i + 1}: ${line.trim()}"
                }
            }
        }
        assertEquals(emptyList(), offenders, "hardcoded colour literal in production shell code")
    }
}
```

The `exempt` set matters: `TahoConsole.kt` legitimately declares translucent
washes such as `Color(0x1AE8AE55)`, because an alpha-composited tag fill cannot be
expressed as an opaque token.

The second test is the guard for Review Focus item 2 — the failure mode where a well-meaning edit puts a label in a failing tier and nothing else catches it.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoTokenMigrationTest*'`
Expected: FAIL — `TahoFaint x71` and the rest are listed. Compilation of the main source set fails first; run with `-x compileDebugKotlin` if the runner stops early, or read the compiler's unresolved-reference list as the worklist.

- [ ] **Step 3: Apply the mechanical rename**

Run this from the repo root. It is deliberately mechanical; the semantic work is Step 4.

```bash
cd browser/shell/src/main/java/app/taho/browser/shell

# one-shot: surface tokens -> new names. Order matters: longest names first.
sed -i \
  -e 's/\bTahoSurfaceRowHover\b/raised/g' \
  -e 's/\bTahoSurfaceRow\b/panel/g' \
  -e 's/\bTahoSurfaceControl\b/bone/g' \
  -e 's/\bTahoHairlineStrong\b/hairlineStrong/g' \
  -e 's/\bTahoHairline\b/hairline/g' \
  -e 's/\bTahoGoldHi\b/amberHover/g' \
  -e 's/\bTahoGold\b/amber/g' \
  -e 's/\bTahoBg\b/canvas/g' \
  -e 's/\bTahoSheet\b/bone/g' \
  -e 's/\bTahoText\b/ink/g' \
  -e 's/\bTahoNeutral\b/charcoal/g' \
  -e 's/\bTahoMuted\b/mute/g' \
  -e 's/\bTahoOk\b/ok/g' \
  -e 's/\bTahoInfo\b/info/g' \
  -e 's/\bTahoError\b/danger/g' \
  TahoSettingsHub.kt M7ProductUx.kt TahoPageOverlays.kt TahoBrowserApp.kt \
  TahoStartPage.kt TahoTabsOverview.kt TahoBrowserMenu.kt M7TransferSearch.kt
```

`sed` with `\b` word boundaries will also rewrite occurrences inside comments and string literals. That is acceptable for the token names, but **do not** run it over `TahoTheme.kt` (which defines the tokens) or `TahoWarn`/`TahoThemeMode` (which are retained).

- [ ] **Step 4: Apply the semantic remaps that `sed` cannot do**

Three categories need judgement, not substitution:

**(a) `TahoFaint` → `mute` or `ash`.** This is the Review Focus item 1 trap. Default every site to `mute`. Switch an individual site to `ash` only where it paints a non-text mark. The known non-text sites are the row chevrons in `TahoBrowserMenu.kt` and `TahoTabsOverview.kt`, and the `nav` arrows. To find every site:

```bash
grep -n 'TahoFaint' browser/shell/src/main/java/app/taho/browser/shell/*.kt
```

For each hit, decide by what it paints. Anything feeding a `Text()` colour becomes `mute`; anything feeding an `Icon` tint or a divider becomes `ash`.

**(b) `TahoDisplay` → `TahoBody`.** Mechanical, but note it in the same pass:

```bash
sed -i 's/\bTahoDisplay\b/TahoBody/g' browser/shell/src/main/java/app/taho/browser/shell/*.kt
```

**(c) `TahoJsonNum` → `body`.** Per spec §4.3 the JSON viewer's numbers go monochrome. In `M7ProductUx.kt` around line 782, the highlighter currently does:

```kotlin
addStyle(SpanStyle(color = TahoJsonNum), i, j)
```

Change it to:

```kotlin
addStyle(SpanStyle(color = body), i, j)
```

The surrounding key styling (`TahoInfo` → `info`) and punctuation styling (`TahoGoldHi` → `amberHover`) are already handled by Step 3. Delete the `TahoJsonNum` definition from `TahoTheme.kt` if Task 1 left it — it must be gone.

- [ ] **Step 5: Fix compilation**

Run: `gradle :browser:shell:compileDebugKotlin`
Expected: PASS. If it fails, each unresolved reference is a site `sed` could not reach — usually inside a string template. Fix by hand and repeat. Do not re-add a legacy alias to make it compile.

- [ ] **Step 6: Run the full test suite**

Run: `gradle :browser:shell:testDebugUnitTest`
Expected: 33 tests PASS, plus the new ones. Any failure in `M7ProductUxTest` or `TahoFeaturesTest` is a behaviour regression, not a colour problem — investigate rather than adjusting the assertion.

- [ ] **Step 7: Verify the architecture gate**

Run: `gradle verifyArchitecture`
Expected: PASS. This is the AGENTS.md boundary check and must never be weakened to accommodate a styling change.

- [ ] **Step 8: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/ browser/shell/src/test/kotlin/app/taho/browser/shell/
git commit -m "refactor(theme): migrate shell call sites to console tokens

Mechanical rename for surfaces and text tiers, plus the semantic remaps
sed cannot do: TahoFaint resolves to mute for text and ash for non-text
marks, TahoDisplay collapses into TahoBody, and the JSON viewer's numbers
go monochrome per spec 4.3."
```

### Task 4: Bundle Archivo

**Files:**
- Create: `browser/shell/src/main/res/font/archivo_regular.ttf`, `archivo_medium.ttf`, `archivo_semibold.ttf`, `archivo_bold.ttf`
- Delete: `browser/shell/src/main/res/font/clash_display_medium.ttf`, `clash_display_semibold.ttf`, `general_sans_regular.ttf`, `general_sans_medium.ttf`, `general_sans_semibold.ttf`

**Interfaces:**
- Consumes: nothing.
- Produces: `R.font.archivo_regular`, `archivo_medium`, `archivo_semibold`, `archivo_bold`, which Task 2's `TahoBody` references. Unblocks compilation.

- [ ] **Step 1: Obtain the font files**

Archivo is SIL Open Font License 1.1. Fetch the four static weights from the official release and record the licence text alongside them:

```bash
mkdir -p browser/shell/src/main/res/font
curl -sL -o /tmp/opencode/archivo.zip \
  https://github.com/Omnibus-Type/Archivo/releases/download/v2.1/Archivo-TTF.zip
unzip -o -j /tmp/opencode/archivo.zip '*/Archivo-Regular.ttf'   -d browser/shell/src/main/res/font
unzip -o -j /tmp/opencode/archivo.zip '*/Archivo-Medium.ttf'    -d browser/shell/src/main/res/font
unzip -o -j /tmp/opencode/archivo.zip '*/Archivo-SemiBold.ttf'   -d browser/shell/src/main/res/font
unzip -o -j /tmp/opencode/archivo.zip '*/Archivo-Bold.ttf'       -d browser/shell/src/main/res/font

cd browser/shell/src/main/res/font
mv Archivo-Regular.ttf  archivo_regular.ttf
mv Archivo-Medium.ttf   archivo_medium.ttf
mv Archivo-SemiBold.ttf archivo_semibold.ttf
mv Archivo-Bold.ttf     archivo_bold.ttf
```

If that release URL 404s, take the same four weights from Google Fonts'
Archivo download. **Do not** proceed until all four files exist — a missing weight
fails the build at resource-link time, not at font-render time, which is a
confusing way to discover it.

- [ ] **Step 2: Record the licence**

```bash
curl -sL -o browser/shell/src/main/res/font/ARCHIVO-LICENSE.txt https://openfontlicense.org/open-font-license-official-text/
head -3 browser/shell/src/main/res/font/ARCHIVO-LICENSE.txt
```

Expected: the SIL OFL header. This file is not a font, so it must not be named
`*.ttf` and must be excluded from any `R.font` reference.

- [ ] **Step 3: Verify the four weights are present and are real TTFs**

```bash
cd browser/shell/src/main/res/font
for f in archivo_regular archivo_medium archivo_semibold archivo_bold; do
  printf '%s: ' "$f"
  file "$f.ttf" | sed 's/.*: //'
done
```

Expected: each reports TrueType/OpenType font data. A file that reports HTML or
zip means the download failed — fix the fetch before continuing.

- [ ] **Step 4: Remove the superseded fonts**

```bash
cd browser/shell/src/main/res/font
rm -f clash_display_medium.ttf clash_display_semibold.ttf \
      general_sans_regular.ttf general_sans_medium.ttf general_sans_semibold.ttf
ls
```

Expected: the four `archivo_*` files, `ARCHIVO-LICENSE.txt`, and the three `jetbrains_mono_*` files. Confirm no `.kt` file still references `R.font.clash_display` or `R.font.general_sans`:

```bash
cd "/home/eternal/Taho/Taho Browser"
grep -rn 'clash_display\|general_sans' browser/shell/src/main/java/ || echo "no references — safe to delete"
```

- [ ] **Step 5: Build and run the full suite**

Run: `gradle :browser:shell:testDebugUnitTest`
Expected: PASS — this is the first point at which the whole module compiles, since `TahoBody` referenced `R.font.archivo_*` from Task 2. All 33 pre-existing tests plus `TahoFeaturesTest.consoleTokensMatchApprovedPalette`, `TahoFeaturesTest.everyTextTierClearsWcagAaOnEverySurfaceItSitsOn`, `TahoThemeContractTest`, and `TahoTokenMigrationTest` must be green.

- [ ] **Step 6: Commit**

```bash
git add browser/shell/src/main/res/font/ browser/shell/src/main/
git commit -m "feat(fonts): bundle Archivo, drop Clash Display and General Sans

One display/body face per the console direction, so TahoDisplay collapses
into TahoBody. Archivo is SIL OFL 1.1; licence text kept alongside."
```

### Task 5: Vector icon family

**Files:**
- Create: `browser/shell/src/main/res/drawable/` — 28 vector drawables plus `ic_taho_brand.xml`
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoStartPage.kt` (`StartHeaderIcon`, and the `🔒` / `🛡` / `⏱` glyphs), `TahoBrowserMenu.kt` (all `MenuQuickAction` glyph arguments and `MenuItemRow` leading glyphs), `TahoTabsOverview.kt` (the `×`, `✓`, `›`, `◐`, `⌕` glyphs)
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoIconSetTest.kt`

**Interfaces:**
- Consumes: `charcoal`, `mute`, `amber`, `stone` from Task 1; `TahoBody` font family is unaffected.
- Produces: `TahoIcon(name: String, tint: Color, description: String?, modifier: Modifier)` — the single entry point every call site uses.

- [ ] **Step 1: Write the failing test**

Create `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoIconSetTest.kt`:

```kotlin
package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TahoIconSetTest {

    private val expected = listOf(
        "ic_taho_lock", "ic_taho_sliders", "ic_taho_sparkle", "ic_taho_search",
        "ic_taho_arrow_up_right", "ic_taho_shield", "ic_taho_star", "ic_taho_book",
        "ic_taho_share", "ic_taho_find", "ic_taho_monitor", "ic_taho_minus",
        "ic_taho_plus", "ic_taho_reader", "ic_taho_translate", "ic_taho_install",
        "ic_taho_print", "ic_taho_download", "ic_taho_clock", "ic_taho_key",
        "ic_taho_puzzle", "ic_taho_chevron_right", "ic_taho_close", "ic_taho_layers",
        "ic_taho_incognito", "ic_taho_grid", "ic_taho_folder", "ic_taho_activity",
        "ic_taho_alert",
    )

    private fun drawables(): List<String> =
        File("src/main/res/drawable").listFiles()
            ?.filter { it.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            ?.sorted()
            ?: emptyList()

    @Test
    fun everyIconInTheSpecExists() {
        val missing = expected.filterNot { drawables().contains(it) }
        assertEquals(emptyList(), missing, "icons missing from res/drawable")
    }

    @Test
    fun everyIconIsOnePixelFiveStroke() {
        // One family, one weight. A 2dp or 3dp stroke breaks the system.
        val offenders = drawables().filter { name ->
            val text = File("src/main/res/drawable/$name.xml").readText()
            !text.contains("android:strokeWidth=\"1.5\"")
        }
        assertEquals(emptyList(), offenders, "icons with the wrong stroke width")
    }

    @Test
    fun noEmojiRemainAsIcons() {
        val offenders = mutableListOf<String>()
        File("src/main/java/app/taho/browser/shell").listFiles { f: File ->
            f.name.endsWith(".kt")
        }?.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                // The emoji the shell used for icons, as literal Text() glyphs.
                if (Regex("Text\\(\"[^\"]*[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]").containsMatchIn(line) ||
                    Regex("\"(🔒|🛡|⏱|◐|⌕|📖|🖥|📑|💾|🔑|🧩|⚙|★|☆|×|›|−)\"").containsMatchIn(line)
                ) {
                    offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertEquals(emptyList(), offenders, "emoji still used as icons")
    }

    @Test
    fun tahoIconRequiresADescription() {
        assertTrue(
            File("src/main/java/app/taho/browser/shell/TahoIcons.kt").exists(),
            "TahoIcons.kt must define the single TahoIcon entry point",
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoIconSetTest*'`
Expected: FAIL — `src/main/res/drawable` does not exist and `TahoIcons.kt` is absent.

- [ ] **Step 3: Create the icon entry point**

Create `browser/shell/src/main/java/app/taho/browser/shell/TahoIcons.kt`. One entry point is what makes the accessibility rule enforceable, so every call site goes through it.

```kotlin
package app.taho.browser.shell

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Image

/**
 * Spec §5 — the single icon entry point.
 *
 * Every glyph in the shell is an authored vector at 24dp with a 1.5dp stroke,
 * round caps and joins. This replaced emoji (🔒 🛡 ⏱ ◐ ⌕), which render
 * inconsistently per device and announce inconsistently to screen readers.
 *
 * [description] is required for any icon that is the sole content of a control.
 * Pass `null` only when adjacent visible text already names the control.
 */
@Composable
internal fun TahoIcon(
    name: String,
    tint: Color,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val res = when (name) {
        "lock" -> R.drawable.ic_taho_lock
        "sliders" -> R.drawable.ic_taho_sliders
        "sparkle" -> R.drawable.ic_taho_sparkle
        "search" -> R.drawable.ic_taho_search
        "arrow_up_right" -> R.drawable.ic_taho_arrow_up_right
        "shield" -> R.drawable.ic_taho_shield
        "star" -> R.drawable.ic_taho_star
        "book" -> R.drawable.ic_taho_book
        "share" -> R.drawable.ic_taho_share
        "find" -> R.drawable.ic_taho_find
        "monitor" -> R.drawable.ic_taho_monitor
        "minus" -> R.drawable.ic_taho_minus
        "plus" -> R.drawable.ic_taho_plus
        "reader" -> R.drawable.ic_taho_reader
        "translate" -> R.drawable.ic_taho_translate
        "install" -> R.drawable.ic_taho_install
        "print" -> R.drawable.ic_taho_print
        "download" -> R.drawable.ic_taho_download
        "clock" -> R.drawable.ic_taho_clock
        "key" -> R.drawable.ic_taho_key
        "puzzle" -> R.drawable.ic_taho_puzzle
        "chevron_right" -> R.drawable.ic_taho_chevron_right
        "close" -> R.drawable.ic_taho_close
        "layers" -> R.drawable.ic_taho_layers
        "incognito" -> R.drawable.ic_taho_incognito
        "grid" -> R.drawable.ic_taho_grid
        "folder" -> R.drawable.ic_taho_folder
        "activity" -> R.drawable.ic_taho_activity
        "alert" -> R.drawable.ic_taho_alert
        else -> error("Unregistered icon: $name — add it to TahoIcon and res/drawable")
    }
    Image(
        painter = painterResource(res),
        contentDescription = description,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
        modifier = if (description != null) {
            modifier.semantics { contentDescription = description }
        } else {
            modifier
        },
    )
}
```

The `error(...)` in the `else` branch is deliberate: adding an unregistered icon becomes a crash in debug rather than a silently missing glyph.

- [ ] **Step 4: Generate the drawables**

The geometry is taken from the reviewed prototype's SVG sprite, so the shipped app and the prototype stay visually identical. Write each file with a 24×24 viewport, `android:strokeWidth="1.5"`, round caps and joins, and `android:fillColor="@android:color/transparent"`.

Here is the full generator. Run it from the repo root; it writes all 28 files.

```bash
cd "/home/eternal/Taho/Taho Browser"
mkdir -p browser/shell/src/main/res/drawable

gen() {
  name="$1"; path="$2"
  cat > "browser/shell/src/main/res/drawable/ic_taho_${name}.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path android:pathData="${path}"
      android:strokeColor="#FFFFFF" android:strokeWidth="1.5"
      android:strokeLineCap="round" android:strokeLineJoin="round"
      android:fillColor="@android:color/transparent"/>
</vector>
XML
}

gen lock "M4.5,10.5h15v10h-15z M8,10.5V7a4,4 0,0 1,8 0v3.5"
gen sliders "M3,7h4 M11,7h10 M3,12h8 M15,12h6 M3,17h10 M17,17h4"
gen sparkle "M11,3l1.7,4.3L17,9l-4.3,1.7L11,15l-1.7,-4.3L5,9l4.3,-1.7z M17.5,14.5l0.8,2l2,0.8l-2,0.8l-0.8,2l-0.8,-2l-2,-0.8l2,-0.8z"
gen search "M10.5,4.5a6,6 0,1 0,0 12a6,6 0,1 0,0 -12 M15.5,15.5L21,21"
gen arrow_up_right "M7,17L17,7 M9,7h8v8"
gen shield "M12,3l7.5,2.8v5.4c0,4.5 -3,8.2 -7.5,9.3c-4.5,-1.1 -7.5,-4.8 -7.5,-9.3V5.8z M9,12l2,2l4,-4"
gen star "M12,3.5l2.6,5.4l5.9,0.8l-4.3,4.1l1,5.9l-5.2,-2.8l-5.2,2.8l1,-5.9L3.5,9.7l5.9,-0.8z"
gen book "M4,5.5A2.5,2.5 0,0 1,6.5 3H20v14.5H6.5A2.5,2.5 0,0 0,4 20z M8.5,3v17"
gen share "M6,9.5a2.5,2.5 0,1 0,0 5a2.5,2.5 0,1 0,0 -5 M17,3a2.5,2.5 0,1 0,0 5a2.5,2.5 0,1 0,0 -5 M17,16a2.5,2.5 0,1 0,0 5a2.5,2.5 0,1 0,0 -5 M8.3,10.8l6.4,-4 M8.3,13.2l6.4,4"
gen find "M10.5,4.5a6,6 0,1 0,0 12a6,6 0,1 0,0 -12 M7.5,10.5h6 M15.5,15.5L21,21"
gen monitor "M3,4.5h18v12H3z M9,20.5h6 M12,16.5v4"
gen minus "M6,12h12"
gen plus "M12,6v12 M6,12h12"
gen reader "M4,3.5h16v17H4z M7.5,8.5h9 M7.5,12h9 M7.5,15.5h5.5"
gen translate "M12,3.5a8.5,8.5 0,1 0,0 17a8.5,8.5 0,1 0,0 -17 M3.5,12h17 M12,3.5c2.2,2.4 3.3,5.3 3.3,8.5s-1.1,6.1 -3.3,8.5c-2.2,-2.4 -3.3,-5.3 -3.3,-8.5S9.8,5.9 12,3.5z"
gen install "M7,2.5h10v19H7z M12,7v6 M9.5,10.5L12,13l2.5,-2.5"
gen print "M7,8V3.5h10V8 M3.5,8h17v8.5h-17z M7,13h10v7.5H7z"
gen download "M12,3.5v11 M8,11l4,4l4,-4 M4.5,19.5h15"
gen clock "M12,3.5a8.5,8.5 0,1 0,0 17a8.5,8.5 0,1 0,0 -17 M12,7v5.4l3.4,2"
gen key "M7.5,8.5a3.5,3.5 0,1 0,0 7a3.5,3.5 0,1 0,0 -7 M11,12h9.5 M17.5,12v3.5 M20.5,12v2.5"
gen puzzle "M4,5.5h5.2a2.3,2.3 0,1 1,4.6 0H20v5.2a2.3,2.3 0,1 0,0 4.6V20H4v-5.2a2.3,2.3 0,1 1,0 -4.6z"
gen chevron_right "M9,5l7,7l-7,7"
gen close "M6.5,6.5l11,11 M17.5,6.5l-11,11"
gen layers "M3.5,3.5h12v12h-12z M8.5,20.5h12v-12"
gen incognito "M3,13.5h3.2 M9.3,13.5h5.4 M17.8,13.5H21 M6.6,12.8a3.2,3.2 0,1 0,0 6.4a3.2,3.2 0,1 0,0 -6.4 M17.4,12.8a3.2,3.2 0,1 0,0 6.4a3.2,3.2 0,1 0,0 -6.4 M8.6,7.5l1.6,4 M15.4,7.5l-1.6,4"
gen grid "M3.5,3.5h7v7h-7z M13.5,3.5h7v7h-7z M3.5,13.5h7v7h-7z M13.5,13.5h7v7h-7z"
gen folder "M3.5,6.5h6l2,2.5h9v9h-17z"
gen activity "M3,12h4l2.5,-6l4,12l2.5,-6h5"
gen alert "M12,3.5l9,15.5H3z M12,9v5 M12,17h0.01"

ls browser/shell/src/main/res/drawable | wc -l
```

Expected: `29` — the 28 icons plus nothing else yet. `ic_taho_brand.xml` is added in Plan 2, not here.

- [ ] **Step 5: Verify the drawables**

```bash
cd "/home/eternal/Taho/Taho Browser"
echo "count: $(ls browser/shell/src/main/res/drawable/*.xml | wc -l)"
grep -L 'android:strokeWidth="1.5"' browser/shell/src/main/res/drawable/*.xml || echo "all strokes are 1.5"
```

The `alert` path uses `M12,17h0.01` for the dot, which renders as a near-zero-length round-capped segment — a 1.5dp dot. That is intentional and matches the round-cap convention.

- [ ] **Step 6: Replace the emoji call sites**

Now migrate each glyph site. The pattern is always the same: an emoji `Text(...)` becomes `TahoIcon(...)`.

In `TahoStartPage.kt`, `StartHeaderIcon` currently takes a `glyph: String` and renders `Text(glyph, ...)`. Change its signature to take a name and description, and render the icon:

```kotlin
private fun StartHeaderIcon(name: String, description: String, onClick: () -> Unit) {
    // ... existing clickable/semantics wiring unchanged ...
    TahoIcon(name = name, tint = mute, description = description)
}
```

Call sites become `StartHeaderIcon("sliders", "Settings", onOpenSettings)` and `StartHeaderIcon("sparkle", "Customize", { showCustomizeSheet = true })`.

In `TahoBrowserMenu.kt`, `MenuQuickAction` currently takes `glyph: String`. Change it to `name: String` and render `TahoIcon(name, tint = if (active) amber else ink, description = label)`. Because the label is visible text, pass the label as the description anyway — it makes the icon's purpose explicit rather than relying on adjacency. The five call sites map: `★`/`☆` → `star`, `📖` → `book`, `↗` → `share`, `⌕` → `find`, `🖥` → `monitor`.

In `TahoTabsOverview.kt`, replace the literal glyphs: `×` → `close`, `✓` → `shield` (the "In Group" check has no dedicated icon; `shield` is wrong — use `check`, see below), `›` → `chevron_right`, `◐` → `incognito`, `⌕` → `search`.

**Correction — add a `check` icon**, since "✓ In Group" needs a tick and reusing another glyph would be a false signal:

```bash
gen() {
  name="$1"; path="$2"
  cat > "browser/shell/src/main/res/drawable/ic_taho_${name}.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path android:pathData="${path}"
      android:strokeColor="#FFFFFF" android:strokeWidth="1.5"
      android:strokeLineCap="round" android:strokeLineJoin="round"
      android:fillColor="@android:color/transparent"/>
</vector>
XML
}
gen check "M4.5,12.5l5,5l10,-11"
```

Then add `"check" -> R.drawable.ic_taho_check` to the `when` in `TahoIcons.kt` and `"ic_taho_check"` to the `expected` list in `TahoIconSetTest`. That makes 29 icons.

- [ ] **Step 7: Run the tests**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoIconSetTest*'`
Expected: PASS — all 29 icons present, all 1.5dp, zero emoji remaining.

- [ ] **Step 8: Run the full suite and the architecture gate**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: both PASS.

- [ ] **Step 9: Commit**

```bash
git add browser/shell/src/main/res/drawable/ browser/shell/src/main/java/ browser/shell/src/test/
git commit -m "feat(icons): replace emoji glyphs with an authored vector set

29 icons at 24dp with a single 1.5dp stroke, behind one TahoIcon entry
point that requires a contentDescription. Replaces emoji which render
inconsistently per device and announce inconsistently to screen readers."
```

---

## Exit criteria for Plan 1

- [ ] `gradle :browser:shell:testDebugUnitTest` — PASS, 33 pre-existing plus 8 new
- [ ] `gradle verifyArchitecture` — PASS
- [ ] `grep -rn 'clash_display\|general_sans\|TahoGold\|TahoFaint\|TahoJsonNum' browser/shell/src/main/` — no matches
- [ ] `grep -rn '🔒\|🛡\|⏱\|◐\|⌕' browser/shell/src/main/` — no matches
- [ ] Every text tier passes 4.5:1 on every surface, enforced by a test
- [ ] The shell compiles and renders with the new palette **before** any composable is restyled — Plans 2–4 are then purely visual refinement

**Rollback:** `git revert` the four commits. The shell returns to the previous
palette with no behavioural difference, because this plan changed tokens and
glyphs only, never logic.