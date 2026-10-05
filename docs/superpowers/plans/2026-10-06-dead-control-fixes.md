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
import kotlin.test.assertEquals
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
    private fun leadingGlyphEnabled(isStartPage: Boolean): Boolean = !isStartPage

    @Test
    fun siteInformationIsUnavailableOnAStartPage() {
        assertFalse(leadingGlyphEnabled(isStartPage = true))
    }

    @Test
    fun siteInformationIsAvailableOnALoadedPage() {
        assertTrue(leadingGlyphEnabled(isStartPage = false))
    }

    @Test
    fun bothCallSitesGateOnIsStartPageAlone() {
        // Asserted on the PRODUCTION source. A test that re-derives the
        // predicate inside itself cannot fail for any production change, so it
        // guards nothing — the first three tests as originally written were
        // exactly that.
        val source = java.io.File(
            "src/main/java/app/taho/browser/shell/TahoBrowserApp.kt"
        ).readText()
        assertEquals(
            2,
            Regex("leadingGlyphEnabled\\s*=\\s*!isStartPage\\b").findAll(source).count(),
            "both Omnibox call sites must gate on isStartPage alone",
        )
        assertFalse(
            source.contains("!isStartPage && !editing"),
            "an editing clause re-creates a swallowed-tap dead zone: the omnibox " +
                "row's own clickable is clickable(enabled = !editing), so when " +
                "both are disabled nothing handles that tap",
        )
    }

    @Test
    fun theDisabledPathInstallsNoClickableAtAll() {
        val source = java.io.File(
            "src/main/java/app/taho/browser/shell/TahoBrowserApp.kt"
        ).readText()
        assertTrue(
            source.contains("if (enabled) Modifier.clickable(onClick = onClick) else Modifier"),
            "the disabled branch must add no clickable — clickable(enabled = false) " +
                "still installs a pointer node that consumes taps, which is the " +
                "defect being fixed",
        )
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoOmniboxAffordanceTest*'`
Expected: `bothCallSitesGateOnIsStartPageAlone` and `theDisabledPathInstallsNoClickableAtAll`
both FAIL — no `leadingGlyphEnabled` argument exists yet.

- [ ] **Step 3: Thread an explicit `enabled` flag**

Add the parameter to `Omnibox`:

```kotlin
private fun Omnibox(
    // ... existing parameters ...
    leadingGlyphEnabled: Boolean,
) {
```

Replace both call sites' handlers with a flag, and pass it. **Identically at both
sites** (`:412`-ish and `:576`-ish):

```kotlin
leadingGlyphEnabled = !isStartPage,
onLeadingClick = { showSiteInfo = true },
```

**Keep `onLeadingClick` as the enabled action — do not delete it.** The omnibox row's
own clickable is `clickable(enabled = !editing, onClick = onBeginEdit)`, and a disabled
Compose clickable still claims its pointer area (`ClickableKt.clickable` installs
`ClickableElement` unconditionally; only hover is gated on `enabled`). So with the
glyph's handler removed, a loaded page would have *nothing* handling that tap in the
glyph's region — the same swallowed-tap defect this task closes, reintroduced.

`isStartPage` alone is the predicate, with no `editing` clause: site information stays
reachable while editing, because the user is on a real page and may legitimately ask
what site they are on.

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
            // enabled = false means no clickable AT ALL, so the tap reaches
            // whatever is behind. `clickable(enabled = false)` is not enough:
            // it still installs a pointer-input node that consumes taps.
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            )
            // mergeDescendants because the enclosing omnibox Row is itself a
            // clickable, and AbstractClickableNode merges descendant semantics.
            // Without this the label folds into the Row and is never announced
            // on its own. Use semantics disabled() -- NOT Modifier.disabled(),
            // which would re-install a consuming clickable.
            .semantics(mergeDescendants = true) {
                if (enabled) {
                    role = Role.Button
                } else {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
```

Leave the secure-connection lock `Canvas` strokes in `ok`. They render only under
`!editing && value.startsWith("https://")`, which is a real loaded page — exactly where
`enabled` is true — so substituting `markColor` would collapse `ok` to `mute` and destroy a
live security signal to express a state that cannot occur in that branch. Add a one-line comment
so the next reader does not "finish" the substitution.

Then dim the other marks when disabled, so the glyph reads as unavailable rather than merely
inert. Use `ash` — this is a non-text mark, which is its sanctioned use:

```kotlin
        val markColor = if (enabled) (if (isPrivate) amberHover else mute) else ash
```

Apply `markColor` to the private-mode `◐` and the magnifier glyph. Read the composable and
replace each hardcoded `amberHover`/`mute` with `markColor`, except the `ok` lock strokes.

> **Known gap:** `TahoTokenMigrationTest.ashAndStoneAreNeverUsedOnTextRoles` matches the
> literal string `color = ash`, so a `Text(..., color = markColor)` escapes it. That is fine
> here — `markColor` never carries a string — but note it beside the guard so a future reader
> does not assume the guard covers this path.

- [ ] **Step 5: Make the semantics correct**

`contentDescription` gives a label; it does not give a *state*. Use `disabled()` from
`androidx.compose.ui.semantics` so TalkBack announces the dimmed state, and set `role =
Role.Button` when enabled, matching the three sibling omnibox controls.

Use `Modifier.semantics(mergeDescendants = true)`, because the enclosing omnibox Row is itself
clickable and `AbstractClickableNode.getShouldMergeDescendantSemantics()` is true — without it
the label folds into the Row and is never announced on its own.

Do **not** use `Modifier.disabled()`: it installs a consuming clickable, reinstating the defect.

- [ ] **Step 6: Delete the defaulted empty handlers**

`OmniboxLeadingGlyph`'s `onClick: () -> Unit = {}` and `Omnibox`'s `onLeadingClick: () -> Unit =
{}` let a future call site that omits either resurrect exactly the bug this task closed: a
clickable whose handler does nothing. Remove both defaults so omission becomes a compile error.
`onMenuClick` and `onHomeClick` share the shape but are pre-existing and always supplied — leave
them.

- [ ] **Step 7: Verify and commit**

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

### Task 4b: Give the start page its tab-switcher route

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoStartPage.kt` — header
  (`:228-231`)
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoStartPageRouteTest.kt` (create)

**Interfaces:**
- Consumes: `onOpenTabs`, already declared at `TahoStartPage.kt:70` and already passed a live
  `{ showTabs = true }` from `TahoBrowserApp.kt:347` — into a parameter nothing reads.
- Produces: the start page header invokes `onOpenTabs`.

Added after Tasks 3 and 4, from the Task 3 implementer's finding. `TahoStartPage` declares
`onOpenTabs` (`:70`), `onNewTab` (`:72`) and `onNewPrivateTab` (`:73`) and references **none** of
them. This is the same defect class as the plan's other four, and arguably the most consequential:
**the start page has no route to the tab switcher at all.**

`onNewTab` and `onNewPrivateTab` are the same defect but lower severity — "New tab" is reachable
via the omnibox tab-count button → tab switcher `+ New`. Wire `onOpenTabs` here; record the other
two as deferred rather than expanding scope.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `onOpenTabs` is passed live from TahoBrowserApp (it sets `showTabs = true`)
 * into a parameter the start page never reads, so the new-tab page has no
 * route to the tab switcher.
 */
class TahoStartPageRouteTest {

    private val live = File("src/main/java/app/taho/browser/shell/TahoStartPage.kt")
        .readText()
        .substringAfter("fun TahoStartPage(")
        .lineSequence()
        .map { it.trim() }
        .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
        .toList()

    @Test
    fun theStartPageInvokesItsTabSwitcherRoute() {
        assertTrue(
            Regex("\\bonOpenTabs\\b").findAll(live.joinToString("\n")).count() >= 2,
            "onOpenTabs must be declared AND invoked, not merely declared",
        )
    }
}
```

Note the shape: anchored on `fun TahoStartPage(` so the parameter's own signature does not
satisfy it, counted over **live lines only** so a commented-out control does not either, and
`>= 2` so declaration alone is insufficient. Those three details came from failures in Tasks 1–3.

- [ ] **Step 2: Run it to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoStartPageRouteTest*'`
Expected: FAILS — `saw 1 live occurrence(s); 1 means declaration only`.

- [ ] **Step 3: Add the control**

`TahoStartPage`'s header (`:228-231`) currently holds the wordmark plus `StartHeaderIcon("⚙",
Settings)` and `StartHeaderIcon("✦", Customize)`. Add a tab-count control:

```kotlin
StartHeaderIcon(
    glyph = "▦",
    description = "Open tabs (${state.tabCount})",
    onClick = onOpenTabs,
)
```

`StartHeaderIcon` takes `(glyph: String, description: String, onClick: () -> Unit)` — pass the
count in the description so the label is informative rather than bare. Read the call sites at
`:229-230` and match their shape exactly.

- [ ] **Step 4: Verify and commit**

```bash
gradle :browser:shell:testDebugUnitTest --rerun-tasks
gradle verifyArchitecture
```

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoStartPage.kt browser/shell/src/test/
git commit -m "fix(start): give the start page a route to the tab switcher

onOpenTabs was passed live from TahoBrowserApp but never referenced by
TahoStartPage, so the new-tab page had no way into the tab switcher. Same
defect class as the four already fixed; found while verifying Task 3.

onNewTab and onNewPrivateTab are declared and unused too, but 'New tab'
is reachable via the omnibox tab count, so those are deferred rather
than expanded into this fix."
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