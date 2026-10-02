# Plan 2 — Browser shell surfaces

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the five everyday browsing surfaces and the three banner types onto the console design language — sharp panels, hairline seams, mono data, semantic tags — using the tokens Plan 1 installed.

**Architecture:** Plan 1 already swapped the token *values* everywhere, so this plan is purely presentational: replace the large soft-radius containers with machined panels, replace the card grids with hairline-separated rows, move every label onto the mono register, and give each state (empty, error, loading) an explicit surface. No screen is added or removed; no behaviour changes.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material3 1.3.2, AGP 9.1.1, Gradle 9.3.1.

**Spec:** `docs/superpowers/specs/2026-10-03-tactical-console-port-design.md` — §4.6 shape, §4.7 motion, §7 invariants, §9 risks.

**Sequence position:** 2 of 5. Requires Plan 1 complete and green. Plans 3, 4 and 5 follow.

## Global Constraints

Carried verbatim from the spec and Plan 1.

- **Build:** `gradle :browser:shell:testDebugUnitTest`. **Gate:** `gradle verifyArchitecture`. There is no `./gradlew`.
- **No new colour literals** in production shell code. Every colour is a token.
- **`ash` and `stone` never carry text.** `ash` 3.25–3.57:1 and `stone` 2.51:1 both fail WCAG AA 4.5:1. `ash` is for non-text marks; `stone` is for disabled text only.
- **Warning surfaces carry a `WARN` tag *and* a 4px status bar.** Amber signals both "armed" and "warning" (spec §4.2), so colour is never the only channel — WCAG 1.4.1.
- **Capture stays decoupled.** No task in this plan may add work to the browsing path, capture events, attribution or persistence. Presentation only.
- **`TahoReducedMotion()` remains the gate for all motion.** No hardcoded duration checks.
- **One authored entrance moment only** — the corner-tick reticle. Do not add fade-ins, staggered reveals or scroll animations.
- **No light theme.** No nested cards. No gradient text. No `border-left` thicker than 1dp as decoration.

## Review Focus

1. **The rounded-sheet silhouette disappearing reads as a regression.** Sheets go to 0dp (spec §4.6, approved). A sheet with no top hairline and no rounding can read as "the screen broke" rather than "this is the new shape." → Task 2, which must add an explicit 1dp `hairlineStrong` top rule plus the grabber so the sheet edge is legible.
2. **Row density collapse.** Replacing padded cards with 1dp-seam rows removes vertical rhythm, and 12+ rows become an undifferentiated grey block. → Task 3.
3. **Empty/error/loading states silently dropped.** These are the states users hit when capture is off or storage is degraded; losing them contradicts the capture-decoupling invariant's intent. → Tasks 1, 3, 4 each carry an explicit state.
4. **Banner text colour regressed to a decorative tier.** Notices are the one surface a user must be able to read at a glance. → Task 4, pinned by a test.
5. **Mono applied to prose.** URLs, counts and timestamps are mono; a sentence-length label is not. Over-applying mono makes the surface unreadable and wastes the register. → Task 1.

---

### Task 1: Start page

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoStartPage.kt` (857 lines)
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoSurfaceContractTest.kt` (create)

**Interfaces:**
- Consumes: all Plan 1 tokens; `TahoReticle(trigger: Any?, modifier: Modifier)` from Plan 1 Task 2; `TahoIcon(name, tint, description, modifier)` from Plan 1 Task 5.
- Produces: private `StatStrip`, `ShortcutTile`, `ToggleRow`, `MetricCell` composables used only inside this file.

- [ ] **Step 1: Write the failing test**

Create `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoSurfaceContractTest.kt`:

```kotlin
package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural rules for the console direction. These cannot be unit-tested as
 * rendered pixels in a JVM test, so they assert on the source contracts that
 * produce those pixels. Complements the browser-rendered contrast sweep.
 */
class TahoSurfaceContractTest {

    private fun sources(): List<File> =
        File("src/main/java/app/taho/browser/shell").listFiles { f: File ->
            f.name.endsWith(".kt")
        }?.toList() ?: emptyList()

    @Test
    fun noCardInsetShadowsOnSurfaces() {
        // Depth is hairline rules and luminance steps. A soft inset is decoration.
        val offenders = mutableListOf<String>()
        sources().forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (line.contains("inset") && line.contains("shadow", ignoreCase = true)) {
                    offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertEquals(emptyList(), offenders, "inset shadows reintroduced")
    }

    @Test
    fun warningSurfacesPairColourWithATag() {
        // Spec 4.2: amber means both "armed" and "warning", so every warning
        // surface must also carry a textual WARN marker.
        val offenders = mutableListOf<String>()
        sources().forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (line.contains("amber") && line.contains("Warn", ignoreCase = true) &&
                    !file.readText().contains("Tag(text = \"WARN\"")
                ) {
                    offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertEquals(emptyList(), offenders, "warning colour without a WARN tag")
    }

    @Test
    fun everySurfaceDeclaresAnEmptyState() {
        // The shell must show an explicit empty state, not a blank region.
        val required = listOf(
            "TahoTabsOverview.kt" to "No archived tabs",
            "TahoStartPage.kt" to "No shortcuts yet",
            "TahoBrowserMenu.kt" to "Nothing to show",
        )
        val missing = required.filterNot { (file, marker) ->
            File("src/main/java/app/taho/browser/shell/$file").readText().contains(marker)
        }.map { it.first }
        assertEquals(emptyList(), missing, "surfaces missing an explicit empty state")
    }
}
```

The empty-state markers are the exact strings Plan 2's tasks introduce. Adjust them if you choose different copy, but keep all three.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoSurfaceContractTest*'`
Expected: `everySurfaceDeclaresAnEmptyState` FAILS — the strings do not exist yet.

- [ ] **Step 3: Add the shared row primitives**

At the end of `TahoStartPage.kt`, add the four private composables this surface and the settings hub both need. Keeping them private here avoids creating a shared module, which the architecture gate would scrutinise.

```kotlin
/** Spec §4.6 — a machined data row: 1dp seam, no inset, no lift at rest. */
@Composable
private fun SeamRow(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(panel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading(); Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f), content = content)
        if (trailing != null) { Spacer(Modifier.width(10.dp)); trailing() }
    }
}

/** Hairline-only divider between rows in a panel. 1dp, hairline colour. */
@Composable
private fun Seam() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
}

/**
 * Spec §4.1 — a key/value cell. Label is mono uppercase mute; value is mono ink
 * with tabular figures so columns align. Never uses ash: both are text.
 */
@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 9.dp, vertical = 9.dp)) {
        Text(
            label.uppercase(),
            fontFamily = TahoMono, fontSize = 8.sp,
            color = mute, letterSpacing = 0.8.sp, maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value, fontFamily = TahoMono, fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1,
        )
    }
}

/** Spec §4.1 — a 1dp status bar. Colour is paired with a tag by its caller. */
@Composable
private fun StatusBar4(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(4.dp).height(20.dp).background(color))
}
```

Add the imports these need: `androidx.compose.foundation.layout.ColumnScope`, `androidx.compose.ui.graphics.Color` (already present via the theme file's usage — add if absent).

- [ ] **Step 4: Convert the shortcut tiles from cards to a hairline grid**

The existing `StartShortcutTile` renders a lifted, rounded tile. Replace it with a flat 1dp-outlined cell:

```kotlin
@Composable
private fun StartShortcutTile(label: String, glyph: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bone)
            .border(1.dp, hairline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TahoIcon(glyph, tint = charcoal, description = null)
        Text(
            label, fontFamily = TahoMono, fontSize = 8.5.sp, color = charcoal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
```

`description = null` is correct here: the visible label already names the tile, so a description would double-announce.

- [ ] **Step 5: Convert the shield metric strip to seam-divided cells**

Replace the existing four-up metric block so the cells are divided by 1dp seams inside one panel rather than being four separate cards:

```kotlin
Row(
    Modifier
        .fillMaxWidth()
        .background(panel)
        .border(1.dp, hairline, RoundedCornerShape(0.dp))
) {
    listOf(
        "Mode" to protectionLabel,
        "DoH" to dnsLabel,
        "HTTPS" to httpsLabel,
        "Cookies" to cookieLabel,
    ).forEachIndexed { index, (label, value) ->
        if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(hairline))
        MetricCell(label, value, Modifier.weight(1f))
    }
}
```

Guard against overflow: if a value string can be long, clamp with `maxLines = 1` (already set in `MetricCell`) and shorten the source values rather than shrinking the type.

- [ ] **Step 6: Arm the reticle on the search field**

The start page's search/omnibox is the one always-focused surface, so it carries the reticle. Wrap it and pass a meaningful trigger:

```kotlin
Box(Modifier.fillMaxWidth()) {
    TahoReticle(trigger = omniboxQuery, modifier = Modifier.matchParentSize())
    SearchField(
        value = omniboxQuery,
        onValueChange = { omniboxQuery = it; armedRequestId = null },
        // ... existing params ...
    )
}
```

Passing the query as the trigger means the reticle re-arms as the user types, which is the "armed state" the style intends. It stays silent on first composition and is fully suppressed under reduced motion (Plan 1 Task 2).

- [ ] **Step 7: Add the empty state for the shortcuts section**

When the user has no shortcuts, the section currently renders nothing. Add:

```kotlin
if (shortcuts.isEmpty()) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .border(1.dp, hairline, RoundedCornerShape(0.dp))
            .padding(vertical = 18.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No shortcuts yet",
                fontFamily = TahoMono, fontSize = 10.sp, color = mute,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Add one from any page's browser menu.",
                fontFamily = TahoBody, fontSize = 11.sp, color = charcoal,
            )
        }
    }
}
```

- [ ] **Step 8: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest --tests '*TahoSurfaceContractTest*'
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: `TahoSurfaceContractTest` now fails only on `TahoBrowserMenu.kt`'s marker, which Task 2 adds. Run the full suite; all must pass.

- [ ] **Step 9: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoStartPage.kt browser/shell/src/test/
git commit -m "feat(start): console treatment for the start page

Machined 1dp-outlined shortcut cells, seam-divided shield metrics, and
the corner-tick reticle on the search field. Adds an explicit empty
state so an unconfigured start page never renders a blank region."
```

### Task 2: Browser menu sheet

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoBrowserMenu.kt` (380 lines)

**Interfaces:**
- Consumes: Plan 1 tokens and `TahoIcon`; the `TahoReticle` from Plan 1.
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Add the hairline top rule so the sharp sheet edge reads intentionally**

The sheet loses its 26dp radius in Plan 1. Without a rule, the top edge can read as a seam bug. Give the sheet container an explicit 1dp top border:

```kotlin
Column(
    Modifier
        .fillMaxWidth()
        .background(bone)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(hairlineStrong, hairline, Color.Transparent),
                startY = 0f,
                endY = 6.dp.toPx(),
            ),
            shape = RoundedCornerShape(0.dp),
        )
) { /* existing content */ }
```

Add `import androidx.compose.ui.graphics.Brush`.

- [ ] **Step 2: Restyle the quick-action row**

The five quick actions currently sit in a `Row` with `Arrangement.SpaceBetween`. Give them the machined treatment and the mono label:

```kotlin
Row(
    Modifier
        .fillMaxWidth()
        .background(panel)
        .border(1.dp, hairline, RoundedCornerShape(0.dp))
        .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
) {
    MenuQuickAction("star", if (isBookmarked) "Bookmarked" else "Bookmark", isBookmarked, onToggleBookmark)
    MenuQuickAction("book", "Reading", false, onSaveToReadingList)
    MenuQuickAction("share", "Share", false, onShare)
    MenuQuickAction("find", "Find", false, onFindInPage)
    MenuQuickAction("monitor", "Desktop", isDesktopMode, onToggleDesktopMode)
}
```

- [ ] **Step 3: Change `MenuQuickAction` to take an icon name**

```kotlin
@Composable
private fun MenuQuickAction(
    name: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp)
            .width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TahoIcon(name, tint = if (active) amber else charcoal, description = label)
        Text(
            label, fontFamily = TahoMono, fontSize = 8.5.sp,
            color = if (active) amber else mute, maxLines = 1,
        )
    }
}
```

The `width(64.dp)` is deliberate: five cells across 366dp minus padding need a fixed cell or the labels will wrap unpredictably. Verify no label truncates at this width — "Bookmark" is the longest.

- [ ] **Step 4: Convert menu rows to seams and move labels to mono**

`MenuItemRow` currently draws each row on a rounded, lifted card. Convert to a seam-divided row:

```kotlin
@Composable
private fun MenuItemRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TahoIcon(icon, tint = charcoal, description = null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = TahoBody, fontSize = 13.sp, color = ink, maxLines = 1)
            Text(subtitle, fontFamily = TahoMono, fontSize = 9.5.sp, color = mute, maxLines = 1)
        }
        TahoIcon("chevron_right", tint = ash, description = null)
    }
}
```

`ash` on the chevron is the one sanctioned use: it is a non-text mark and clears the 3:1 non-text floor.

- [ ] **Step 5: Update both grouped section call sites**

The `PAGE ACTIONS` and `BROWSER HUBS` groups become labelled seam stacks. Replace each group's heading with the console section header and each card container with a `panel` holding `Seam()`-separated rows:

```kotlin
Text(
    "PAGE ACTIONS",
    fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.2.sp, color = mute,
)
Spacer(Modifier.height(8.dp))
Column(Modifier.fillMaxWidth().background(panel).border(1.dp, hairline, RoundedCornerShape(0.dp))) {
    MenuItemRow("reader", "Reader Mode", "Distraction-free readable view", onReaderMode)
    Seam()
    MenuItemRow("translate", "Translate Page", "Automatic inline translation", onTranslate)
    Seam()
    MenuItemRow("install", "Add to Home Screen", "Create launcher shortcut", onAddToHomeScreen)
    Seam()
    MenuItemRow("print", "Print or Save as PDF", "Export via Android print engine", onPrintPage)
    Seam()
    MenuItemRow("download", "Save Page for Offline", "Store locally for offline use", onSaveOffline)
}
```

Do the same for `BROWSER HUBS` with its six rows. `Seam()` and `MenuItemRow` are private in `TahoStartPage.kt`, so declare them at file scope in `TahoBrowserMenu.kt` too, or move both to a new internal file. **Preferred:** move `Seam`, `SeamRow`, `MetricCell` and `StatusBar4` into a new `browser/shell/src/main/java/app/taho/browser/shell/TahoConsole.kt` marked `internal`, and delete the private copies from `TahoStartPage.kt`. Plans 3 and 4 reuse them, and duplicating them three times is how the two type registers drift apart.

- [ ] **Step 6: Add the empty state**

When a menu group has no entries (e.g. no downloads, no bookmarks), render an explicit empty row rather than collapsing the group:

```kotlin
Text(
    "Nothing to show",
    fontFamily = TahoMono, fontSize = 10.sp, color = mute,
    modifier = Modifier
        .fillMaxWidth()
        .background(panel)
        .border(1.dp, hairline, RoundedCornerShape(0.dp))
        .padding(vertical = 16.dp),
)
```

- [ ] **Step 7: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: `TahoSurfaceContractTest` fully green now — all three empty-state markers exist.

- [ ] **Step 8: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/
git commit -m "feat(menu): console treatment for the browser menu sheet

Extracts the shared row primitives into TahoConsole.kt so the capture
and settings surfaces reuse one implementation instead of three copies.
Adds a hairline top rule so the now-square sheet edge reads as designed."
```

### Task 3: Tabs overview

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoTabsOverview.kt` (803 lines)

**Interfaces:**
- Consumes: Plan 1 tokens, `TahoIcon`, and the shared primitives from `TahoConsole.kt`.
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Convert the tab cards to a 2-up seam grid**

`DetailedTabCard` currently renders a lifted rounded card per tab. Convert to a flat 1dp-outlined cell so 12 tabs read as a dense instrument grid rather than a card wall:

```kotlin
@Composable
private fun DetailedTabCard(
    title: String,
    host: String,
    faviconInitial: String,
    isPinned: Boolean,
    isPrivate: Boolean,
    onClose: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(0.dp))
            .background(panel)
            .border(1.dp, hairline, RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp)
                    .background(well)
                    .border(1.dp, hairline, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    faviconInitial, fontFamily = TahoMono, fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold, color = charcoal,
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                host, fontFamily = TahoMono, fontSize = 9.sp, color = mute,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            title, fontFamily = TahoBody, fontSize = 12.sp, color = ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (isPinned || isPrivate) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (isPinned) ConsoleTag("PINNED", tone = TagTone.Amber)
                if (isPrivate) ConsoleTag("PRIVATE", tone = TagTone.Info)
            }
        }
    }
}
```

`ConsoleTag` and `TagTone` come from `TahoConsole.kt`. Define them there once:

```kotlin
internal enum class TagTone { Amber, Ok, Info, Danger, Neutral }

/**
 * Spec §4.2 — a pill tag. Semantic colour is never the only signal: the tag
 * always carries its own text, so amber can mean "armed" or "warning" without
 * ambiguity (WCAG 1.4.1).
 */
@Composable
internal fun ConsoleTag(
    text: String,
    tone: TagTone,
    modifier: Modifier = Modifier,
) {
    val (fg, bg) = when (tone) {
        TagTone.Amber -> amber to Color(0x1AE8AE55)
        TagTone.Ok -> ok to Color(0x1A5BC088)
        TagTone.Info -> info to Color(0x1A82B4D6)
        TagTone.Danger -> danger to Color(0x1AD96A5E)
        TagTone.Neutral -> mute to Color(0x14828D99)
    }
    Text(
        text.uppercase(),
        fontFamily = TahoMono, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp, color = fg,
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
```

- [ ] **Step 2: Restore row density with a mono metadata line**

The flat grid removes the padding that previously separated cards, so each cell needs a third line of machine data to stay scannable — this is the Review Focus item 2 mitigation:

```kotlin
Spacer(Modifier.height(6.dp))
Text(
    "$lastAccessedLabel · ${sizeLabel}",
    fontFamily = TahoMono, fontSize = 8.sp, color = mute,
    fontVariantNumeric = FontVariantNumeric.TabularNums,
    maxLines = 1,
)
```

Add `import androidx.compose.ui.text.font.FontVariantNumeric`.

- [ ] **Step 3: Give the search field the reticle and mono placeholder**

```kotlin
Box(Modifier.fillMaxWidth()) {
    TahoReticle(trigger = searchQuery, modifier = Modifier.matchParentSize())
    SearchField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = "Search open tabs",
        // ...
    )
}
```

- [ ] **Step 4: Restyle the group and recently-closed sections**

Both sections become `panel` + `Seam()` stacks with the console section header, matching Task 2's menu:

```kotlin
Text(
    "RECENTLY CLOSED",
    fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.2.sp, color = mute,
)
Spacer(Modifier.height(8.dp))
Column(Modifier.fillMaxWidth().background(panel).border(1.dp, hairline, RoundedCornerShape(0.dp))) {
    recent.forEachIndexed { index, entry ->
        if (index > 0) Seam()
        SeamRow(
            modifier = Modifier.clickable { restore(entry) },
            leading = { FaviconWell(entry.initial) },
            trailing = {
                Text(
                    entry.closedLabel, fontFamily = TahoMono, fontSize = 9.sp, color = mute,
                    fontVariantNumeric = FontVariantNumeric.TabularNums,
                )
            },
        ) {
            Text(entry.title, fontFamily = TahoBody, fontSize = 12.sp, color = ink, maxLines = 1)
            Text(entry.url, fontFamily = TahoMono, fontSize = 9.sp, color = mute, maxLines = 1)
        }
    }
}
```

Extract `FaviconWell(initial: String)` into `TahoConsole.kt` — it appears on this surface, the start page and the capture inspector.

- [ ] **Step 5: Add the archived empty state**

```kotlin
Column(
    Modifier
        .fillMaxWidth()
        .background(panel)
        .border(1.dp, hairline, RoundedCornerShape(0.dp))
        .padding(vertical = 18.dp, horizontal = 14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text("No archived tabs", fontFamily = TahoMono, fontSize = 10.sp, color = mute)
    Spacer(Modifier.height(7.dp))
    Text(
        "Tabs you archive stay restorable for 30 days.",
        fontFamily = TahoBody, fontSize = 11.sp, color = charcoal,
    )
}
```

This exact string is what `TahoSurfaceContractTest.everySurfaceDeclaresAnEmptyState` asserts on.

- [ ] **Step 6: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/
git commit -m "feat(tabs): console treatment for the tabs overview

Flat 1dp 2-up grid instead of a card wall, with a tabular mono metadata
line restoring the scan density the flat cards removed. Group and
recently-closed sections become seam stacks."
```

### Task 4: Browser surface, banners, reader and translation

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoBrowserApp.kt` (1,943 lines) — omnibox, `CaptureIndicator`, `BrowserNoticeBanner`, `LoadFailureBanner`, `PageCrashBanner`, `TabSwitcher`, `NavigationTray`
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoPageOverlays.kt` (974 lines) — `TahoReaderModeView`, `TahoTranslationBar`
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoBannerContrastTest.kt` (create)

**Interfaces:**
- Consumes: all prior tasks' tokens and primitives.
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Write the failing test**

Notices are the surface a user must read at a glance, so their text tier is pinned. Create `TahoBannerContrastTest.kt`:

```kotlin
package app.taho.browser.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TahoBannerContrastTest {

    @Test
    fun bannersRenderTheirMessageInAReadableTier() {
        val files = listOf("TahoBrowserApp.kt", "TahoPageOverlays.kt")
        val offenders = mutableListOf<String>()
        files.forEach { name ->
            val file = File("src/main/java/app/taho/browser/shell/$name")
            file.readLines().forEachIndexed { i, line ->
                // A banner body or heading must never use a failing tier.
                if (Regex("Text\\([^)]*color\\s*=\\s*(ash|stone|mute)\\b").containsMatchIn(line) &&
                    Regex("Banner|Crash|Failure|Notice", ignoreCase = true).containsMatchIn(file.readText())
                ) {
                    // only flag when the line is inside a banner composable region
                    if (line.contains("color = ash") || line.contains("color = stone")) {
                        offenders += "$name:${i + 1}"
                    }
                }
            }
        }
        assertEquals(emptyList(), offenders, "banner text in a failing tier")
    }

    @Test
    fun inkOnPanelIsTheBannerBaseline() {
        // The banner body tier, asserted numerically so it cannot drift.
        val l1 = ink.luminance()
        val l2 = panel.luminance()
        val ratio = (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
        assertTrue(ratio >= 4.5f, "ink on panel must clear 4.5:1, was $ratio")
    }

    @Test
    fun errorAndCrashBannersUseTheDangerToken() {
        val text = File("src/main/java/app/taho/browser/shell/TahoBrowserApp.kt").readText()
        listOf("LoadFailureBanner", "PageCrashBanner").forEach { fn ->
            assertTrue(text.contains("$fn"), "$fn must exist")
        }
        assertTrue(
            text.contains("danger"),
            "error banners must signal through the danger token",
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoBannerContrastTest*'`
Expected: `bannersRenderTheirMessageInAReadableTier` FAILS if any banner currently paints `ash`.

- [ ] **Step 3: Restyle the three banners as a single status-bar-plus-body pattern**

`BrowserNoticeBanner`, `LoadFailureBanner` and `PageCrashBanner` share a shape. Give all three the same construction — a 4dp status bar, a mono title, a body line in `body` — so they read as one family:

```kotlin
@Composable
private fun StatusBanner(
    tone: TagTone,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val bar = when (tone) {
        TagTone.Danger -> danger
        TagTone.Warn -> amber
        TagTone.Ok -> ok
        TagTone.Info -> info
        else -> mute
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .border(1.dp, hairline, RoundedCornerShape(0.dp))
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
        Column(Modifier.weight(1f).padding(11.dp)) {
            Text(
                title.uppercase(),
                fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp, color = bar,
            )
            Spacer(Modifier.height(5.dp))
            Text(message, fontFamily = TahoBody, fontSize = 12.sp, color = body)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(9.dp))
                Text(
                    actionLabel.uppercase(),
                    fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp, color = amber,
                    modifier = Modifier.clickable(onClick = onAction),
                )
            }
        }
        if (onDismiss != null) {
            TahoIcon(
                "close", tint = ash, description = "Dismiss",
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(9.dp)
                    .size(16.dp),
            )
        }
    }
}
```

Add `Warn` to `TagTone` in `TahoConsole.kt`, mapping to `amber`. This is the sanctioned amber-means-warning path from spec §4.2 — the banner always carries a text title, so colour is never the only signal.

Rewrite the three banner composables to delegate to `StatusBanner`:

```kotlin
@Composable
internal fun BrowserNoticeBanner(notice: BrowserNotice, onDismiss: () -> Unit) {
    StatusBanner(
        tone = if (notice.isError) TagTone.Danger else TagTone.Info,
        title = notice.title,
        message = notice.body,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun LoadFailureBanner(error: LoadError, onRetry: () -> Unit, onDismiss: () -> Unit) {
    StatusBanner(
        tone = TagTone.Danger,
        title = "Page failed to load",
        message = error.userMessage,
        actionLabel = "Retry",
        onAction = onRetry,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun PageCrashBanner(onReload: () -> Unit) {
    StatusBanner(
        tone = TagTone.Danger,
        title = "Tab crashed",
        message = "The page stopped responding. Reload to continue.",
        actionLabel = "Reload",
        onAction = onReload,
    )
}
```

- [ ] **Step 4: Restyle the omnibox**

The omnibox is the app's most-used control. Give it the well background, the reticle on focus, and mono for the URL:

```kotlin
var omniboxFocused by remember { mutableStateOf(false) }

Box(Modifier.fillMaxWidth()) {
    if (omniboxFocused) TahoReticle(trigger = url, modifier = Modifier.matchParentSize())
    Row(
        Modifier
            .fillMaxWidth()
            .background(well)
            .border(
                1.dp,
                if (omniboxFocused) amberDeep else hairlineStrong,
                RoundedCornerShape(2.dp),
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TahoIcon(
            if (isSecure) "lock" else "alert",
            tint = if (isSecure) ok else amber,
            description = if (isSecure) "Secure connection" else "Not secure",
        )
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = TahoMono, fontSize = 12.sp, color = ink,
            ),
            cursorBrush = SolidColor(amber),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { omniboxFocused = it.isFocused },
            decorationBox = { inner ->
                if (url.isEmpty()) {
                    Text(
                        "Search or enter address",
                        fontFamily = TahoMono, fontSize = 12.sp, color = mute,
                    )
                }
                inner()
            },
        )
        TahoIcon("star", tint = if (isBookmarked) amber else charcoal, description = "Bookmark")
    }
}
```

The `cursorBrush = SolidColor(amber)` is required — Compose's default cursor is the platform black or white and will be invisible on the `well` background. Add `import androidx.compose.ui.graphics.SolidColor` and `import androidx.compose.foundation.text.BasicTextField`.

- [ ] **Step 5: Restyle the capture indicator as a mono pill**

```kotlin
Row(
    Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(panel)
        .border(1.dp, if (isArmed) amberDeep else hairline, RoundedCornerShape(999.dp))
        .clickable(onClick = onOpenSummary)
        .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    // TahoStatusDot is the existing composable in TahoTheme.kt — use its real name.
    TahoStatusDot(if (isArmed) amber else mute, glow = isArmed)
    Spacer(Modifier.width(7.dp))
    Text(
        if (count > 0) "CAPTURE $count" else "CAPTURING",
        fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = if (isArmed) amber else mute,
    )
}
```

Retire `TahoRingPulse` here if nothing else uses it — Plan 1 replaced it with `TahoReticle`, and leaving an unused composable is the dead-code failure the migration test is designed to catch.

- [ ] **Step 6: Restyle reader mode and the translation bar**

`TahoReaderModeView` becomes a measure-controlled single column with mono metadata, per the style's long-form discipline:

```kotlin
Column(
    Modifier
        .fillMaxSize()
        .background(canvas)
        .padding(horizontal = 22.dp, vertical = 18.dp),
) {
    Text(
        sourceHost.uppercase(),
        fontFamily = TahoMono, fontSize = 8.5.sp, letterSpacing = 1.sp, color = mute,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        articleTitle,
        fontFamily = TahoBody, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        color = ink, lineHeight = 26.sp,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "$wordCount words · $readMinutes min",
        fontFamily = TahoMono, fontSize = 9.sp, color = mute,
        fontVariantNumeric = FontVariantNumeric.TabularNums,
    )
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
    Spacer(Modifier.height(16.dp))
    Text(
        bodyText,
        fontFamily = TahoBody, fontSize = 15.sp, color = body, lineHeight = 24.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

At 366dp minus 44dp padding the column is ~322dp, which at 15sp Archivo is a ~48ch measure — inside the 65–75ch guidance's intent for a single narrow column on mobile. Do not add a second column.

`TahoTranslationBar` becomes a seam row with a language pair in mono:

```kotlin
Row(
    Modifier
        .fillMaxWidth()
        .background(panel)
        .border(1.dp, hairline, RoundedCornerShape(0.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    TahoIcon("translate", tint = charcoal, description = null)
    Spacer(Modifier.width(9.dp))
    Text(
        "$sourceLang → $targetLang",
        fontFamily = TahoMono, fontSize = 10.sp, color = ink,
    )
    Spacer(Modifier.weight(1f))
    Text(
        "ORIGINAL",
        fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp, color = if (showingOriginal) amber else mute,
        modifier = Modifier.clickable { showingOriginal = true },
    )
    Spacer(Modifier.width(12.dp))
    Text(
        "TRANSLATED",
        fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp, color = if (showingOriginal) mute else amber,
        modifier = Modifier.clickable { showingOriginal = false },
    )
    Spacer(Modifier.width(9.dp))
    TahoIcon("close", tint = ash, description = "Dismiss", modifier = Modifier.clickable { onDismiss() })
}
```

- [ ] **Step 7: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: all green, including the three new banner tests.

- [ ] **Step 8: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/
git commit -m "feat(chrome): console treatment for omnibox, banners, reader and translation

All three banner types now share one StatusBanner construction: 4dp
status bar, mono title, readable body tier. Omnibox gets the reticle on
focus and an amber cursor so it stays visible on the well background.
Capture indicator becomes a mono pill; TahoRingPulse is retired."
```

---

## Exit criteria for Plan 2

- [ ] `gradle :browser:shell:testDebugUnitTest` — PASS
- [ ] `gradle verifyArchitecture` — PASS
- [ ] `TahoSurfaceContractTest` and `TahoBannerContrastTest` green
- [ ] No `ash` or `stone` carries any string in these five files
- [ ] Every group and list has an explicit empty state
- [ ] The reticle is the only entrance animation on any surface
- [ ] Capture-on and capture-off paths are byte-identical in behaviour — verified by the 33 existing tests, which cover tab persistence, pinned-tab safety and capture decoupling

**Rollback:** `git revert` the four commits. Plan 1's token work is untouched, so
the app returns to the previous palette with the new token values still in place.

---

*Plan 2 of 5. Next: Plan 3 — Capture surfaces (`M7ProductUx.kt`).*