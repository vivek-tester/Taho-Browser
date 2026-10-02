# Plan 4 — Settings hub

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the settings hub onto the console design language by restyling five shared row components, converting the main index, and proving the pattern across representative sub-pages.

**Architecture:** `TahoSettingsHub.kt` is the largest file in the shell at 3,117 lines, but it is not 3,117 lines of distinct design. It is 22 sub-pages composed almost entirely from five row components: `SettingsSectionTitle`, `SettingsLinkRow`, `SettingsToggleRow`, `SettingsCheckboxRow`, and `StorageBarRow`. Restyling those five restyles the entire hub. This plan therefore invests in the shared components first and treats sub-page conversion as data, not markup — which is also why the 20 remaining pages were scoped out of the port.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material3 1.3.2, AGP 9.1.1, Gradle 9.3.1.

**Spec:** `docs/superpowers/specs/2026-10-03-tactical-console-port-design.md` — §4.6 shape, §4.7 motion, §10 out of scope.

**Sequence position:** 4 of 5. Requires Plans 1–3 complete and green.

## Global Constraints

Carried verbatim from the spec and Plans 1–3.

- **Build:** `gradle :browser:shell:testDebugUnitTest` (33 tests today). **Gate:** `gradle verifyArchitecture`. There is no `./gradlew`.
- **No new colour literals.** Every colour is a token. `ash` and `stone` never carry text.
- **No fabricated measurements.** Storage bars, counters and diagnostics must reflect real reported values. If a value is unavailable, render it as unavailable — never as `0`, `0 B` or a fabricated total.
- **Settings must not gain a network dependency.** The hub is local-first; this plan adds no fetching.
- **Warning surfaces carry a `WARN` tag *and* a 4dp bar** (spec §4.2).
- **One authored entrance motion only** — the reticle.
- **No light theme. No nested cards.** Do not introduce a card inside a card while restyling.
- **Every toggle must remain operable by touch.** Rows get a 48dp minimum touch target even though their visual height shrinks.

## Review Focus

1. **Touch targets shrink below 48dp.** Flattening padded cards into tight seam rows makes rows look tappable but can leave them physically small. A settings toggle you cannot hit is a functional regression, not a cosmetic one. → Task 1.
2. **A toggle's on/off state becomes colour-only.** Amber is overloaded (armed *and* warning), so a switch reading only as amber-on/grey-off is ambiguous. The switch needs a shape or label difference. → Task 1.
3. **A storage or diagnostic figure silently becomes a plausible-looking number.** This file is where invented metrics are most tempting and most damaging — the repo has already removed fabricated sync and storage facts twice. → Task 3.
4. **Section titles lose their scan function.** `SettingsSectionTitle` appears 28+ times and is the only thing separating 22 dense pages. Flattening them to body weight would make the whole hub unscannable. → Task 1.
5. **The remaining 20 sub-pages silently drift.** They are out of scope for restyling but must still compile and stay consistent, because they inherit the shared components. → Task 4.

---

### Task 1: The five shared row components

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoSettingsHub.kt` — `SettingsSectionTitle`, `SettingsLinkRow`, `SettingsToggleRow`, `SettingsCheckboxRow`, `StorageBarRow`
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoSettingsRowTest.kt` (create)

**Interfaces:**
- Consumes: `ConsoleTag`, `TagTone`, `Seam`, `SeamRow`, `MetricCell`, `StatusBar4`, `TahoConsole.kt` primitives; Plan 1 tokens.
- Produces: the five restyled components, consumed by all 22 sub-pages unchanged in signature.

- [ ] **Step 1: Write the failing test**

Create `TahoSettingsRowTest.kt`:

```kotlin
package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TahoSettingsRowTest {

    private val hub = File("src/main/java/app/taho/browser/shell/TahoSettingsHub.kt")

    @Test
    fun theFiveSharedRowComponentsAllExist() {
        val text = hub.readText()
        listOf(
            "SettingsSectionTitle",
            "SettingsLinkRow",
            "SettingsToggleRow",
            "SettingsCheckboxRow",
            "StorageBarRow",
        ).forEach {
            assertTrue(text.contains(it), "$it must exist")
        }
    }

    @Test
    fun sectionTitlesUseTheMonoUppercaseRegister() {
        // 28+ section titles are the only thing separating 22 dense pages.
        val text = hub.readText()
        val body = text.substringAfter("private fun SettingsSectionTitle")
        assertTrue(
            body.take(600).contains("TahoMono") && body.take(600).contains("uppercase"),
            "section titles must be mono uppercase so pages stay scannable",
        )
    }

    @Test
    fun togglesCarryANonColourStateMarker() {
        // Amber means both armed and warning, so on/off cannot be colour-only.
        val text = hub.readText()
        val body = text.substringAfter("private fun SettingsToggleRow")
        val hasMarker = body.take(900).contains("●") ||
            body.take(900).contains("○") ||
            body.take(900).contains("on\"") ||
            body.take(900).contains("off\"") ||
            body.take(900).contains("checked")
        assertTrue(hasMarker, "toggle needs a non-colour on/off marker")
    }

    @Test
    fun noSettingValueIsFabricated() {
        // Guard against reintroducing invented metrics in the hub.
        val text = hub.readText()
        listOf("TODO", "FIXME", "placeholder value", "sample total").forEach { marker ->
            assertTrue(!text.contains(marker), "found $marker in the settings hub")
        }
    }

    @Test
    fun everySubPageStillDispatches() {
        // The 20 out-of-scope pages must keep working via the shared components.
        val text = hub.readText()
        listOf("APPEARANCE", "PRIVACY_SECURITY", "DIAGNOSTICS", "ABOUT", "STORAGE_USAGE")
            .forEach {
                assertTrue(text.contains(it), "sub-page $it must remain reachable")
            }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoSettingsRowTest*'`
Expected: FAIL on `sectionTitlesUseTheMonoUppercaseRegister` and `togglesCarryANonColourStateMarker`.

- [ ] **Step 3: Restyle `SettingsSectionTitle`**

```kotlin
@Composable
private fun SettingsSectionTitle(title: String) {
    // Mono uppercase, tracked out, hairline-ruled, with space above and a tag
    // slot on the right. This is the hub's only scan structure across 22 pages.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp, color = mute,
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(hairline))
    }
}
```

The rule after the title replaces the old generous top padding as the separator. More space above the heading than below it is preserved by the `18.dp`/`8.dp` asymmetry.

- [ ] **Step 4: Restyle `SettingsLinkRow`**

```kotlin
@Composable
private fun SettingsLinkRow(
    title: String,
    summary: String?,
    value: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = TahoBody, fontSize = 13.sp, color = ink, maxLines = 2)
            if (summary != null) {
                Spacer(Modifier.height(3.dp))
                Text(summary, fontFamily = TahoMono, fontSize = 9.5.sp, color = mute, maxLines = 2)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            // Values are machine data: mono, tabular, right-aligned so a column
            // of them lines up.
            Text(
                value,
                fontFamily = TahoMono, fontSize = 10.sp, color = body,
                fontVariantNumeric = FontVariantNumeric.TabularNums,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        TahoIcon("chevron_right", tint = ash, description = null)
    }
}
```

`heightIn(min = 48.dp)` is Review Focus item 1 — the visual height shrinks but the touch target does not.

- [ ] **Step 5: Restyle `SettingsToggleRow` with a shape-differentiated switch**

```kotlin
@Composable
private fun SettingsToggleRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(panel)
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = TahoBody, fontSize = 13.sp, color = ink, maxLines = 2)
            if (summary != null) {
                Spacer(Modifier.height(3.dp))
                Text(summary, fontFamily = TahoMono, fontSize = 9.5.sp, color = mute, maxLines = 2)
            }
        }
        Spacer(Modifier.width(12.dp))
        // Shape plus position differ between states, so the control is legible
        // without colour. Amber alone would be ambiguous (spec 4.2).
        Box(
            Modifier
                .width(34.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) amberDeep else hairlineStrong),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (checked) canvas else mute),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            if (checked) "ON" else "OFF",
            fontFamily = TahoMono, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp, color = if (checked) amber else mute,
        )
    }
}
```

Three redundant state channels — track fill, knob position, and an `ON`/`OFF` word — because amber cannot carry the distinction alone. `clickable(role = Role.Switch, ...)` also makes TalkBack announce it as a switch. Add `import androidx.compose.ui.semantics.Role`.

- [ ] **Step 6: Restyle `SettingsCheckboxRow`**

```kotlin
@Composable
private fun SettingsCheckboxRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val contentColor = if (enabled) ink else stone
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(panel)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (checked) amberDeep else Color.Transparent)
                .border(1.dp, if (checked) amberDeep else hairlineStrong, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    "✓", fontFamily = TahoMono, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, color = canvas,
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(
            title, fontFamily = TahoBody, fontSize = 13.sp,
            color = contentColor, modifier = Modifier.weight(1f), maxLines = 2,
        )
    }
}
```

- [ ] **Step 7: Restyle `StorageBarRow`**

```kotlin
@Composable
private fun StorageBarRow(
    label: String,
    usedBytes: Long?,
    totalBytes: Long?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontFamily = TahoBody, fontSize = 12.sp, color = ink, maxLines = 1)
            Spacer(Modifier.weight(1f))
            // If either figure is unknown, say so. Never render 0 B for a
            // measurement that was never taken — that is a fabricated fact.
            Text(
                when {
                    usedBytes == null || totalBytes == null -> "Unavailable"
                    totalBytes <= 0L -> formatBytes(usedBytes)
                    else -> "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}"
                },
                fontFamily = TahoMono, fontSize = 10.sp,
                color = if (usedBytes == null) mute else body,
                fontVariantNumeric = FontVariantNumeric.TabularNums,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(hairline),
        ) {
            val fraction = when {
                usedBytes == null || totalBytes == null || totalBytes <= 0L -> null
                else -> (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            }
            if (fraction != null) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(amber),
                )
            }
        }
    }
}
```

The `Unavailable` branch is Review Focus item 3. `usedBytes` and `totalBytes` become nullable precisely so "not measured" is representable and cannot be confused with a genuine zero.

- [ ] **Step 8: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest --tests '*TahoSettingsRowTest*'
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: green.

- [ ] **Step 9: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoSettingsHub.kt browser/shell/src/test/
git commit -m "feat(settings): console treatment for the five shared row components

Restyling these five restyles all 22 sub-pages, since they are almost
entirely composed of them. Rows keep a 48dp minimum touch target while
their visual height shrinks. Toggles carry three redundant state
channels because amber cannot distinguish on from warning. Storage bars
represent an unmeasured value as unavailable rather than as 0 B."
```

### Task 2: Main index

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoSettingsHub.kt` — `SettingsMainIndex` and the hub's root sheet container

**Interfaces:**
- Consumes: the five components from Task 1.
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Give the hub sheet a hairline top edge**

The hub lost its 26dp radius in Plan 1. As with the browser menu, add the rule so the sharp edge reads as designed rather than broken:

```kotlin
Column(
    Modifier
        .fillMaxSize()
        .background(bone)
        .border(1.dp, hairlineStrong, RoundedCornerShape(0.dp)),
) { /* existing content */ }
```

- [ ] **Step 2: Render the index as a two-column tap grid of section entries**

The index lists 22 destinations. As a single scrolling list of 22 rows it is unscannable. Give each entry a flat cell with a mono index label so the set reads as a directory:

```kotlin
@Composable
private fun SettingsIndexCell(
    code: String,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(panel)
            .border(1.dp, hairline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                code,
                fontFamily = TahoMono, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp, color = amberDeep,
            )
            Spacer(Modifier.weight(1f))
            TahoIcon("chevron_right", tint = ash, description = null)
        }
        Spacer(Modifier.height(7.dp))
        Text(title, fontFamily = TahoBody, fontSize = 12.sp, color = ink, maxLines = 2)
        Spacer(Modifier.height(3.dp))
        Text(summary, fontFamily = TahoMono, fontSize = 9.sp, color = mute, maxLines = 2)
    }
}
```

The `code` is a two-to-three letter category stamp — `PRI`, `SEC`, `APP`, `DAT`, `DLV`, `EXT`, `ABT` — not an ordinal. **Do not use `01 / 02 / 03`**: section numbers are refused by the craft floor unless the sequence itself carries information, and an index position does not.

- [ ] **Step 3: Map the 22 destinations to cells**

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf(
        Triple("PRI", "Privacy & security", "Tracking, DNS, guards"),
        Triple("APP", "Appearance", "Theme, accent, layout"),
        Triple("SRH", "Search engines", "Default and suggestions"),
        Triple("BKM", "Bookmarks", "Saved links and folders"),
        Triple("HST", "History", "Search and restore"),
        Triple("DLV", "Downloads", "Tracked files"),
        Triple("PWD", "Passwords & autofill", "Stored credentials"),
        Triple("EXT", "Extensions", "Add-ons and blockers"),
        Triple("ACC", "Accessibility", "Motion, contrast, scaling"),
        Triple("LAN", "Languages", "Interface and page translation"),
        Triple("PRF", "Profiles & sync", "Local profiles, devices"),
        Triple("STO", "Storage usage", "What this browser holds"),
        Triple("DIA", "Diagnostics", "Runtime facts, not guesses"),
        Triple("BAK", "Backup & export", "Import and export"),
        Triple("ABT", "About", "Version, licences"),
    ).chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (code, title, summary) ->
                SettingsIndexCell(
                    code, title, summary,
                    onClick = { onNavigate(subPageFor(code)) },
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
```

`subPageFor(code)` is a small `when` mapping the stamp back to its `SettingsSubPage`. Keep the mapping explicit rather than reflective — a wrong destination here silently sends a user to the wrong settings page.

- [ ] **Step 4: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

- [ ] **Step 5: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoSettingsHub.kt
git commit -m "feat(settings): console treatment for the settings index

A 22-destination single-column list is unscannable, so the index becomes
a two-column grid of flat cells with a category stamp. Stamps are
semantic (PRI, SEC, ABT), never ordinal, because section numbers are
refused decoration."
```

### Task 3: Representative sub-pages

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/TahoSettingsHub.kt` — `SettingsPrivacySecurityPage`, `SettingsAppearancePage`, `SettingsStorageUsagePage`, `SettingsDiagnosticsPage`, `SettingsAboutPage`

**Interfaces:**
- Consumes: Task 1's components.
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Verify what these five pages already compose**

Before editing, establish that they need no new components:

```bash
cd "/home/eternal/Taho/Taho Browser"
for fn in SettingsPrivacySecurityPage SettingsAppearancePage SettingsStorageUsagePage SettingsDiagnosticsPage SettingsAboutPage; do
  echo "=== $fn ==="
  awk "/private fun $fn/,/^}/" browser/shell/src/main/java/app/taho/browser/shell/TahoSettingsHub.kt \
    | grep -oE 'Settings[A-Za-z]+Row|SettingsSectionTitle|StorageBarRow|M7[A-Za-z]+' | sort | uniq -c
done
```

Expected: each page composes only the five shared components. If a page invents its own row layout inline, convert it to a shared component rather than restyling it separately — otherwise the two type registers drift.

- [ ] **Step 2: Convert any bespoke rows to shared components**

For each inline row found in Step 1, replace it with the nearest shared component. A representative conversion:

```kotlin
// Before — bespoke inline row
Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    Text("Tracking protection", fontSize = 14.sp)
    Spacer(Modifier.weight(1f))
    Text(currentLevel.name, fontSize = 13.sp)
}

// After
SettingsLinkRow(
    title = "Tracking protection",
    summary = "How aggressively third-party trackers are blocked",
    value = currentLevel.name,
    onClick = { onOpenLevelPicker() },
)
```

- [ ] **Step 3: Restyle the diagnostics page as a fact table**

Diagnostics is the page most at risk of gaining invented metrics. Present it strictly as reported facts with provenance:

```kotlin
@Composable
private fun SettingsDiagnosticsPage(diagnostics: RuntimeDiagnostics) {
    SettingsPageScaffold(title = "DIAGNOSTICS") {
        Column(
            Modifier
                .fillMaxWidth()
                .background(panel)
                .border(1.dp, hairline, RoundedCornerShape(0.dp)),
        ) {
            listOf(
                "Engine" to diagnostics.engineVersion,
                "Attribution" to diagnostics.attributionState,
                "Capture" to diagnostics.captureState,
                "Storage" to diagnostics.storageState,
                "Build" to diagnostics.buildStamp,
            ).forEachIndexed { index, (label, value) ->
                if (index > 0) Seam()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label, fontFamily = TahoMono, fontSize = 10.sp, color = mute,
                        modifier = Modifier.width(116.dp),
                    )
                    Text(
                        value, fontFamily = TahoMono, fontSize = 10.sp, color = ink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        M7HonestyNote(
            "Diagnostics report runtime state only. Metrics this build cannot " +
            "measure are shown as unavailable rather than estimated.",
        )
    }
}
```

Add `private fun SettingsPageScaffold(title: String, content: @Composable ColumnScope.() -> Unit)` if it does not already exist — every sub-page should get the same hairline-ruled header and back affordance. Reuse `M7HonestyNote` from Plan 3 rather than writing a second version; the honesty note is module-wide, not capture-only.

- [ ] **Step 4: Restyle the storage page with mono totals**

```kotlin
@Composable
private fun SettingsStorageUsagePage(usage: StorageUsage) {
    SettingsPageScaffold(title = "STORAGE USAGE") {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            usage.categories.forEachIndexed { index, entry ->
                if (index > 0) Seam()
                StorageBarRow(
                    label = entry.label,
                    usedBytes = entry.usedBytes,
                    totalBytes = entry.totalBytes,
                )
            }
        }
    }
}
```

Every category must be a real entry from `StorageUsage`. Do not add a synthetic "Other" row to fill the list, and do not compute a total the storage layer did not report.

- [ ] **Step 5: Restyle the about page**

```kotlin
@Composable
private fun SettingsAboutPage(about: AboutInfo) {
    SettingsPageScaffold(title = "ABOUT") {
        Column(
            Modifier
                .fillMaxWidth()
                .background(panel)
                .border(1.dp, hairline, RoundedCornerShape(0.dp)),
        ) {
            TahoBrand(Modifier.padding(18.dp))
            listOf(
                "Version" to about.versionName,
                "Build" to about.buildStamp,
                "Engine" to about.engineVersion,
                "Licence" to "Apache-2.0",
            ).forEachIndexed { index, (label, value) ->
                if (index > 0) Seam()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                ) {
                    Text(label, fontFamily = TahoMono, fontSize = 10.sp, color = mute,
                        modifier = Modifier.width(116.dp))
                    Text(value, fontFamily = TahoMono, fontSize = 10.sp, color = ink,
                        modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Fonts: Archivo and JetBrains Mono, SIL Open Font License 1.1.",
            fontFamily = TahoMono, fontSize = 9.sp, color = mute, lineHeight = 14.sp,
        )
    }
}
```

The About page is the natural home for `TahoBrand`, the only place the shipped
wordmark appears. Build it as a single `Canvas` drawing the mark with the amber
ring at `amberDeep`, plus the wordmark and `GECKOVIEW <version>` beneath:

```kotlin
@Composable
internal fun TahoBrand(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(56.dp)) {
            drawCircle(color = amberDeep, style = Stroke(width = 1.dp.toPx()))
            drawCircle(
                color = amber.copy(alpha = 0.55f),
                radius = size.minDimension * 0.62f,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(color = amber, radius = size.minDimension * 0.18f)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "TAHO",
            fontFamily = TahoBody, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp, color = ink,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "GECKOVIEW 128",
            fontFamily = TahoMono, fontSize = 8.sp, letterSpacing = 1.6.sp, color = mute,
        )
    }
}
```

- [ ] **Step 6: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: green.

- [ ] **Step 7: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoSettingsHub.kt
git commit -m "feat(settings): console treatment for five representative sub-pages

Converts bespoke inline rows to the shared components so the two type
registers cannot drift. Diagnostics presents reported runtime facts only
and reuses the capture module's honesty note. Adds TahoBrand, which
appears on the About page only."
```

### Task 4: Confirm the remaining sub-pages inherit correctly

**Files:**
- Modify: none expected
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/TahoSettingsRowTest.kt` (extend)

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: nothing.

The 20 sub-pages outside the representative set were scoped out of the port. They
must still compile and must inherit the new row components without manual edits —
that inheritance is the reason this plan was viable.

- [ ] **Step 1: Extend the test to assert inheritance**

Append to `TahoSettingsRowTest.kt`:

```kotlin
@Test
fun outOfScopeSubPagesComposeOnlySharedComponents() {
    val text = hub.readText()
    // Any sub-page that builds its own row layout will drift from the design
    // system. List every composable that draws a raw Row with a Text directly,
    // outside the five shared components.
    val offenders = mutableListOf<String>()
    Regex("private fun (Settings[A-Za-z]+Page)\\(").findAll(text).forEach { m ->
        val name = m.groupValues[1]
        val start = m.range.last
        val body = text.substring(start, minOf(start + 4000, text.length))
        // A page may call shared components; flag raw Row+Text construction.
        if (Regex("Row\\([^)]*\\)\\s*\\{[\\s\\S]{0,400}?Text\\(").containsMatchIn(body) &&
            !body.contains("SettingsLinkRow") && !body.contains("SettingsToggleRow") &&
            !body.contains("SettingsCheckboxRow") && !body.contains("StorageBarRow") &&
            !body.contains("SettingsPageScaffold")
        ) {
            offenders += name
        }
    }
    assertEquals(emptyList(), offenders, "sub-pages drawing rows outside the shared components")
}
```

- [ ] **Step 2: Run it and fix what it finds**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*TahoSettingsRowTest*'`
Expected: FAIL, listing sub-pages that construct rows directly. Convert each to a
shared component — the fix is mechanical, and doing it is what keeps the remaining
20 pages consistent without restyling them individually.

If a page genuinely cannot use a shared component, extract a new shared component
for it rather than exempting it, and note why in the commit message.

- [ ] **Step 3: Run the full suite and gate**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

- [ ] **Step 4: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/TahoSettingsHub.kt browser/shell/src/test/
git commit -m "test(settings): assert every sub-page inherits the shared row components

The 20 out-of-scope pages were not restyled individually; they inherit the
new components. This test makes that inheritance a checked property so a
future bespoke row cannot drift from the design system unnoticed."
```

---

## Exit criteria for Plan 4

- [ ] `gradle :browser:shell:testDebugUnitTest` — PASS
- [ ] `gradle verifyArchitecture` — PASS
- [ ] `TahoSettingsRowTest` green, including the inheritance assertion
- [ ] All 22 sub-pages reachable from the index and composed from shared components
- [ ] Every interactive row has a ≥48dp touch target
- [ ] Toggles convey state through shape, position and a text label — not colour alone
- [ ] No unmeasured figure renders as `0` or `0 B`; unavailable values say so
- [ ] No ordinal section numbers (`01 / 02`) anywhere in the hub

**Rollback:** `git revert` the four commits. Settings behaviour is unchanged; only
row presentation differs.

---

*Plan 4 of 5. Next: Plan 5 — Authority reconciliation (spec and prototype).*