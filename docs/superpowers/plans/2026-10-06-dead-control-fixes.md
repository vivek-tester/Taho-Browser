# Plan 1.5 — Fix four confirmed dead-control defects

**Date:** 2026-10-06
**Status:** Approved for implementation
**Preceding:** Plan 1 (foundation) is complete through Task 4; Task 5 (icons) is queued and unimplemented.

## Intent

A user reported that the new-tab page has no way to open the browser menu. Investigation
showed the menu *is* reachable via the omnibox, but the same defect class — **a control that
exists, looks enabled, and swallows its tap without doing anything** — is real and appears in
four places. All four were confirmed by reading the code, not inferred.

Each is a dead-code path: a handler is declared or wired, and then never invoked. Fixing them
makes existing capability reachable. None changes a design decision.

Success means:

- Every one of the four is reachable from the surface that owns it.
- Dead parameters are either wired or removed — no silent no-ops remain.
- Each fix has a test that fails before it and passes after.
- The port's queued Task 5 is unaffected.

## Context the implementer needs

- `docs/superpowers/specs/2026-10-03-tactical-console-port-design.md` — the design authority
- Plan 1 tokens are live: `canvas bone panel raised well ink body charcoal mute ash stone amber
  amberHover amberDeep ok info danger hairline hairlineStrong scrim`, plus `TahoWarn = amber`.
  **`TahoFaint` and the other retired names are gone and a `verifyArchitecture` rule enforces it.**
- Shape is machined: panels `0.dp`, controls `2.dp`, pill `999.dp`.
- One display face: `TahoBody`. `TahoMono` for every metric, identifier, timestamp and count.
- `TahoReticle(trigger, modifier)` is the single authored entrance motion. Do not add motion.
- `TahoReducedMotion()` gates all motion.
- Emoji are still used as icons in these files — Plan 1 Task 5 will replace them with
  `TahoIcon` + vector drawables. **Do not migrate icons in this plan**; where you add a new
  control, use a `Text` glyph or an existing inline glyph so the shape of your change stays
  consistent with its neighbours.

## Global Constraints

- **Build:** `gradle :browser:shell:testDebugUnitTest` (41 tests today; `--rerun-tasks`, cold
  ~2.5 min). **Gate:** `gradle verifyArchitecture`. There is no `./gradlew`; use the absolute
  path `/tmp/opencode/gradle-dl/gradle-9.3.1/bin/gradle`.
- **No retired token names.** `TahoFaint`, `TahoGold`, `TahoText` and friends are gone and
  `verifyArchitecture` fails the build if reintroduced.
- **`ash` and `stone` never carry a string.** `ash` 3.25–3.57:1, `stone` 2.51:1. Small text uses
  `mute` at 5.38:1 minimum. A disabled control's text uses `stone`.
- **No new colour literals** in production shell code; a per-file ratchet enforces this.
- **Do not touch capture, attribution, persistence, transfer or IPC logic.** Fixing a dead
  control must not change what capture does — only whether a user can reach the UI that already
  reports it.
- **Every fix must be reachable by touch, with a ≥48dp target**, and must not remove an existing
  dismissal path.
- Keep the existing `/** ... */` KDoc convention; cite spec sections where they exist.

---

### Task 1: Make the omnibox leading glyph genuinely disabled on the new tab page

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoBrowserApp.kt` — `Omnibox`
  (`:1279`), `OmniboxLeadingGlyph` (`:1516`), and both `onLeadingClick` call sites (`:420`, `:576`)
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoOmniboxAffordanceTest.kt` (create)

**Interfaces:**
- Consumes: `TahoOmniboxLeadingEnabled.kt`-style predicate — define locally, see below.
- Produces: `leadingGlyphEnabled: Boolean` on `Omnibox`; `enabled: Boolean` on
  `OmniboxLeadingGlyph`.

- [ ] **Step 1: Write the failing test**

The behaviour is a decision about which surfaces offer site information, which is worth pinning
because it is currently implicit in two `if (!isStartPage)` guards.

Create `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoOmniboxAffordanceTest.kt`:

```kotlin
package app.taho.browser.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The omnibox leading glyph opens site information, which is meaningless on a
 * start page with no loaded document. It must therefore be *disabled* there,
 * not merely a no-op — a control that swallows a tap while looking enabled is
 * the defect this pins shut.
 */
class TahoOmniboxAffordanceTest {

    /**
     * Mirrors the predicate the composable uses. A pure function of the same
     * inputs, so the rule is testable on the JVM without Compose.
     */
    private fun leadingGlyphEnabled(isStartPage: Boolean, editing: Boolean): Boolean =
        !isStartPage && !editing

    @Test
    fun siteInformationIsUnavailableOnAStartPage() {
        assertFalse(leadingGlyphEnabled(isStartPage = true, editing = false))
    }

    @Test
    fun siteInformationIsAvailableOnALoadedPage() {
        assertTrue(leadingGlyphEnabled(isStartPage = false, editing = false))
    }

    @Test
    fun theGlyphIsDisabledWhileEditingTheAddress() {
        // Editing replaces the value with a draft; site info about the
        // pre-edit page would be confusing mid-keystroke.
        assertFalse(leadingGlyphEnabled(isStartPage = false, editing = true))
    }

    @Test
    fun theSourceNoLongerContainsABareGuardedNoOp() {
        // The defect's exact shape: `if (!isStartPage) { ... }` with no else,
        // attached to a clickable that consumes the tap regardless.
        val source = java.io.File(
            "src/main/java/app/taho/browser/shell/TahoBrowserApp.kt"
        ).readText()
        assertFalse(
            source.contains("if (!isStartPage) {\n                                showSiteInfo = true"),
            "the bare guarded no-op must be replaced by an explicit enabled flag",
        )
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoOmniboxAffordanceTest*'`
Expected: the fourth test FAILS — the bare guarded no-op is present at both call sites.

- [ ] **Step 3: Thread an explicit `enabled` flag**

Add the parameter to `Omnibox`:

```kotlin
private fun Omnibox(
    // ... existing parameters ...
    leadingGlyphEnabled: Boolean,
) {
```

Replace both call sites' handlers with a flag, and pass it. At `:412`-ish:

```kotlin
leadingGlyphEnabled = !isStartPage && !editing,
```

Delete the `onLeadingClick` parameter entirely — its only job was the no-op.

Inside `Omnibox`, pass the flag down where the glyph is composed (`:1337`):

```kotlin
OmniboxLeadingGlyph(
    value = value,
    editing = editing,
    isPrivate = isPrivate,
    enabled = leadingGlyphEnabled,
)
```

- [ ] **Step 4: Honour it in `OmniboxLeadingGlyph`**

```kotlin
@Composable
private fun OmniboxLeadingGlyph(
    value: String,
    editing: Boolean,
    isPrivate: Boolean,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            // enabled = false means no clickable at all, so the tap falls
            // through to whatever is behind rather than being consumed.
            .then(
                if (enabled) Modifier.clickable(onClick = {}) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
```

Then dim the glyph when disabled, so it reads as unavailable rather than merely inert. Use
`ash` — this is a non-text mark, which is its sanctioned use:

```kotlin
        val markColor = if (enabled) (if (isPrivate) amberHover else mute) else ash
```

Apply `markColor` to the glyphs in that composable (the `◐`, the lock `Canvas` stroke colours,
and the `Text` colours inside it). Read the composable and replace each hardcoded
`amberHover`/`mute`/`ok` with `markColor`, keeping the `ok` secure-connection lock stroke as-is
if you judge it reads better in `ok` — state your choice either way.

- [ ] **Step 5: Add a semantics role so the state is announced**

A disabled control should announce as disabled. Give the glyph a description that reflects
availability:

```kotlin
.semantics {
    contentDescription = if (enabled) "Site information" else "No site information on this page"
}
```

- [ ] **Step 6: Verify and commit**

```bash
gradle :browser:shell:testDebugUnitTest --tests '*TahoOmniboxAffordanceTest*'
gradle :browser:shell:testDebugUnitTest --rerun-tasks
gradle verifyArchitecture
```

Expected: all green. Report the suite count from the JUnit XML, not from memory.

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoBrowserApp.kt browser/shell/src/test/
git commit -m "fix(omnibox): disable the site-information glyph on a start page

The leading glyph consumed its tap and discarded it when there was no
loaded document, so it looked enabled and did nothing. Thread an explicit
enabled flag instead of a guarded no-op, so the control reports its own
availability and the tap falls through rather than vanishing."
```

---

### Task 2: Wire capture retention and "Delete Capture"

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt` —
  `M7CaptureSummarySheet` header (`:291-317`), and `M7SettingsSheet`'s signature (`:1189`)
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoBrowserApp.kt` — the
  `M7CaptureSummarySheet` call site
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoCaptureSettingsReachabilityTest.kt` (create)

**Interfaces:**
- Consumes: `BrowserUiState.captureCapabilityNote` and `.retentionMode`; the shell's
  `onClearCaptureData` and `onRetentionModeChanged` parameters (`:144`, `:150`).
- Produces: `M7SettingsSheet(captureCapabilityNote, retentionMode, onRetentionModeChanged, onClearCaptureData)`
  gains **one** call site — the summary sheet's header.

This is the highest-impact fix in the plan: `M7SettingsSheet` currently has **zero** call sites
repo-wide, so a user can review captured requests but can never change retention or delete
them. `MainActivity` already computes both values and passes both handlers to the shell, where
they dead-end.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Captured request data must be reviewable AND erasable. `M7SettingsSheet` is
 * the only UI for retention mode and for deleting captures; before this fix it
 * had no call sites at all, so both were unreachable.
 */
class TahoCaptureSettingsReachabilityTest {

    private val productUx = File("src/main/java/app/taho/browser/shell/M7ProductUx.kt")
    private val browserApp = File("src/main/java/app/taho/browser/shell/TahoBrowserApp.kt")

    @Test
    fun theCaptureSettingsSheetIsActuallyComposed() {
        val composed = productUx.readText().contains("M7SettingsSheet(") &&
            !productUx.readText().contains("private fun M7SettingsSheet(")
        val fromApp = browserApp.readText().contains("M7SettingsSheet(")
        assertTrue(
            composed || fromApp,
            "M7SettingsSheet must have a call site or retention and deletion stay unreachable",
        )
    }

    @Test
    fun theSummarySheetOffersAnEntryPointToThem() {
        val summary = productUx.readText()
            .substringAfter("M7CaptureSummarySheet(")
            .take(4000)
        assertTrue(
            summary.contains("onOpenCaptureSettings") || summary.contains("M7SettingsSheet("),
            "the capture summary needs a control that opens capture settings",
        )
    }

    @Test
    fun theShellForwardsBothHandlers() {
        val app = browserApp.readText()
        assertTrue(app.contains("onClearCaptureData"), "clear-capture must be forwarded")
        assertTrue(app.contains("onRetentionModeChanged"), "retention must be forwarded")
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoCaptureSettingsReachabilityTest*'`
Expected: the first two tests FAIL.

- [ ] **Step 3: Read `M7SettingsSheet` before wiring it**

Read `M7ProductUx.kt:1189-1312` in full. Confirm its exact parameter names and that it renders
its own dismiss affordance. Its dismissal must remain intact when nested inside the summary
sheet — if it renders a `ModalBottomSheet`, confirm nesting is acceptable; if it renders an
inline `Column`, confirm it fits the summary sheet's scroll container. **State which it is.**

- [ ] **Step 4: Add the entry point to the summary sheet header**

The summary sheet's header (`:291-317`) currently carries only a Close `×`. Add a control that
opens capture settings, next to it:

```kotlin
// in the header Row, before the close control
IconAction(
    glyph = "⚙",
    description = "Capture settings",
    onClick = onOpenCaptureSettings,
)
```

Reuse whatever icon-control composable that header already uses for the close `×` rather than
inventing one — read it first and match its size and semantics. If the header's existing control
has no `contentDescription`, add one while you are there.

Thread `onOpenCaptureSettings: () -> Unit` through `M7CaptureSummarySheet`'s parameters.

- [ ] **Step 5: Compose the settings sheet**

Add state in `TahoBrowserApp`, next to the other `show*` flags:

```kotlin
var showCaptureSettings by rememberSaveable { mutableStateOf(false) }
```

Add a dismiss case to the existing `BackHandler` `when` (`:284-327`) and include
`showCaptureSettings` in the `canHandleBack` predicate at `:218`. **Both are required** — a flag
missing from the back predicate is a strand.

Compose it from the summary sheet's call site:

```kotlin
if (showCaptureSettings) {
    M7SettingsSheet(
        captureCapabilityNote = state.captureCapabilityNote,
        retentionMode = state.retentionMode,
        onRetentionModeChanged = onRetentionModeChanged,
        onClearCaptureData = onClearCaptureData,
        onDismiss = { showCaptureSettings = false },
    )
}
```

Use the real parameter names you found in Step 3; add `onDismiss` only if the composable lacks
one, and if you add it, wire it to a control inside the sheet as well as to the back handler.

- [ ] **Step 6: Verify and commit**

```bash
gradle :browser:shell:testDebugUnitTest --rerun-tasks
gradle verifyArchitecture
```

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/ browser/shell/src/test/
git commit -m "fix(capture): make retention settings and capture deletion reachable

M7SettingsSheet had zero call sites, so a user could review captured
requests but could never change retention or delete them. MainActivity
already computed both values and forwarded both handlers to the shell,
where they dead-ended. Compose it from the capture summary header, and
register the new flag with the back handler's predicate and dispatch so
it cannot strand."
```

---

### Task 3: Let start-page shortcuts be unpinned and removed

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoStartPage.kt` —
  `StartShortcutTile` (`:768-819`) and its call site (`:388-396`)
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoShortcutLifecycleTest.kt` (create)

**Interfaces:**
- Consumes: `TahoBrowserStateStore.togglePinTopSite(id)` and `.removeTopSite(id)`, both already
  wired into dead lambdas at `:392-393`.
- Produces: `StartShortcutTile` uses both parameters.

A shortcut can be added but never removed or unpinned. `onTogglePin` and `onRemove` are declared
parameters that the composable never references; only `onClick` is wired.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A shortcut the user cannot remove is a permanent row they cannot get rid of.
 * `onTogglePin` and `onRemove` were declared but never referenced, so both
 * store methods were unreachable from the UI.
 */
class TahoShortcutLifecycleTest {

    private val startPage = File("src/main/java/app/taho/browser/shell/TahoStartPage.kt")
        .readText()

    private fun tileBody(): String =
        startPage.substringAfter("private fun StartShortcutTile(")

    @Test
    fun theTileInvokesItsRemoveHandler() {
        assertTrue(
            tileBody().take(2500).contains("onRemove"),
            "StartShortcutTile must invoke onRemove, not merely declare it",
        )
    }

    @Test
    fun theTileInvokesItsPinToggle() {
        assertTrue(
            tileBody().take(2500).contains("onTogglePin"),
            "StartShortcutTile must invoke onTogglePin",
        )
    }

    @Test
    fun removalIsDiscoverableRatherThanHidden() {
        // A long-press is acceptable only if it is paired with an affordance or
        // an explanation; a hidden gesture is not discoverable. Assert some
        // form of combinedClickable or an explicit control exists.
        val body = tileBody().take(2500)
        assertTrue(
            body.contains("combinedClickable") || body.contains("IconAction") ||
                body.contains("RemoveShortcut") || body.contains("onRemove"),
            "removal needs a real affordance",
        )
    }

    @Test
    fun theStoreMethodsAreStillReferenced() {
        assertTrue(
            startPage.contains("removeTopSite") && startPage.contains("togglePinTopSite"),
            "both store methods must remain wired at the call site",
        )
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoShortcutLifecycleTest*'`
Expected: the first three FAIL.

- [ ] **Step 3: Implement long-press plus a visible control**

Give the tile a long-press that unpins, and — because a hidden gesture is not discoverable — a
small visible remove control on the pinned state:

```kotlin
Box {
    Column(
        modifier = Modifier
            .width(72.dp)
            .tahoPressScale(interaction)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onTogglePin,
            )
            .semantics {
                contentDescription = buildString {
                    append(item.title)
                    if (item.isPinned) append(", pinned")
                    append(". Long press to unpin.")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ... existing content ...
    }

    // Visible dismissal, so removal is not gesture-only.
    if (item.isPinned) {
        IconAction(
            glyph = "×",
            description = "Remove ${item.title}",
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
```

Add the imports: `androidx.compose.foundation.combinedClickable`, and
`androidx.compose.foundation.ExperimentalFoundationApi` if the compiler requires the opt-in.

Match the existing tile's corner radius and surface treatment rather than inventing new values —
read the composable first.

- [ ] **Step 4: Verify and commit**

```bash
gradle :browser:shell:testDebugUnitTest --rerun-tasks
gradle verifyArchitecture
```

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoStartPage.kt browser/shell/src/test/
git commit -m "fix(start): let shortcuts be unpinned and removed

onTogglePin and onRemove were declared parameters the tile never
referenced, so togglePinTopSite and removeTopSite were unreachable from
the UI and a shortcut could be added but never taken off the page.
Unpin by long press with the state announced in the semantics label, and
add a visible remove control on pinned tiles so removal is not
gesture-only."
```

---

### Task 4: Give the tab switcher a Settings route and use its dropped callback

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoTabsOverview.kt` — header
  (`:130-157`)
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoTabsHeaderTest.kt` (create)

**Interfaces:**
- Consumes: `onOpenSettings` and `onCloseOverview`, both already parameters of
  `TahoTabsOverviewSheet` (`:64-65`) and both already passed live from `TahoBrowserApp.kt:839-843`.
- Produces: a Settings control in the header that calls the existing parameter.

No new plumbing is needed — `TahoBrowserApp` already passes a working `onOpenSettings` into a
void. `onCloseOverview` is dropped the same way, and the sheet relies on scrim-tap, back and drag
to dismiss; all three work, so this is not a strand.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `onOpenSettings` is passed live from TahoBrowserApp but never referenced by
 * the tab switcher, so Settings is unreachable from that surface despite the
 * callback existing.
 */
class TahoTabsHeaderTest {

    private val tabs = File("src/main/java/app/taho/browser/shell/TahoTabsOverview.kt")
        .readText()
        .substringAfter("fun TahoTabsOverviewSheet(")

    @Test
    fun theSettingsCallbackIsInvoked() {
        assertTrue(
            tabs.contains("onOpenSettings()"),
            "the tab switcher must invoke its onOpenSettings callback",
        )
    }

    @Test
    fun theHeaderCarriesMoreThanNewTabAndPrivate() {
        assertTrue(
            tabs.contains("onOpenSettings"),
            "the header must include a settings control",
        )
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoTabsHeaderTest*'`
Expected: FAILS.

- [ ] **Step 3: Add the control**

In the header `Row` (`:130-157`), which currently holds the tab count, `+ New` and `◐ Private`,
add a settings control. Match the existing header controls' size and treatment rather than
inventing values:

```kotlin
IconAction(
    glyph = "⚙",
    description = "Settings",
    onClick = onOpenSettings,
)
```

Place it before `+ New` so the primary action stays right-most. Use the same icon-control
composable the header's other buttons use, if one exists.

- [ ] **Step 4: Decide on `onCloseOverview`, do not silently drop it**

`onCloseOverview` is also declared and unused. The sheet already dismisses via scrim, back and
drag, so this is not a strand — but a declared-and-ignored parameter is exactly the shape of
defect that hid the other three. Either use it on a visible close control, or **delete the
parameter and its call-site argument** so the dead signature cannot mislead the next reader.
State which you chose and why. If you use it, ensure it closes the sheet and nothing else.

- [ ] **Step 5: Verify and commit**

```bash
gradle :browser:shell:testDebugUnitTest --rerun-tasks
gradle verifyArchitecture
```

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoTabsOverview.kt browser/shell/src/test/
git commit -m "fix(tabs): give the tab switcher a Settings route

onOpenSettings was passed live from TahoBrowserApp but never referenced,
so Settings was unreachable from this surface despite the callback
existing. Also resolve the dropped onCloseOverview parameter rather than
leaving a second declared-and-ignored handler in place."
```

---

## Exit criteria

- [ ] `gradle :browser:shell:testDebugUnitTest` — green, count reported from the XML
- [ ] `gradle verifyArchitecture` — PASS
- [ ] Every fix has a test that failed before it and passes after
- [ ] No remaining composable parameter is declared and never referenced in the four touched files
- [ ] No retired token name reintroduced; no new colour literal
- [ ] Capture behaviour itself unchanged — only the reachability of its existing UI

**Out of scope, deliberately:** replacing emoji with `TahoIcon` (Plan 1 Task 5); the
private-browsing lock's apparent lack of a real biometric gate, which is a security defect and
needs its own review; `SettingsClearDataPage.onDone` being unused, which is not a strand.