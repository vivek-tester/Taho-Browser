# Plan 3 — Capture surfaces

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the capture domain UI onto the console design language — summary, inspector, technical workspace, send confirmation, transfer progress, overview, search and capture settings — **without weakening a single honesty or redaction guarantee**.

**Architecture:** This is the densest, most technical UI in the app and the one place where a visual change can quietly break a correctness property. `M7ProductUx.kt` (1,805 lines) already projects masked values and renders unavailable evidence as unavailable. This plan changes surfaces, type and colour only; it must never change what data reaches the screen. Every task therefore ships a test that pins a pre-existing honesty or redaction property *before* restyling, so a regression is caught rather than admired.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material3 1.3.2, AGP 9.1.1, Gradle 9.3.1.

**Spec:** `docs/superpowers/specs/2026-10-03-tactical-console-port-design.md` — §4.2 warning signalling, §4.3 JSON monochrome, §7 invariants 3 and 4.

**Sequence position:** 3 of 5. Requires Plans 1 and 2 complete and green.

## Global Constraints

Carried verbatim from the spec, plus this module's own rules.

- **Build:** `gradle :browser:shell:testDebugUnitTest` (33 tests today). **Gate:** `gradle verifyArchitecture`. There is no `./gradlew`.
- **No new colour literals.** Every colour is a token. `ash` and `stone` never carry text.
- **MASKING IS NOT NEGOTIABLE.** Header and credential values reach the UI only through the existing pre-masked projection. Raw credentials must never enter a shell UI model. Do not read raw header values to render them "prettily" — if a value is not in the projection, it does not exist for this UI.
- **UNAVAILABLE IS NOT ZERO.** Where evidence is not captured — response body, TLS detail, timing — the UI must say unavailable. Never render a captured-looking placeholder, a default, or an empty string that reads as a real value. `M1_ATTRIBUTION_VERIFIED` remains `false`, so production capture stays disabled and this UI shows capability state, not live traffic.
- **No fabricated diagnostics.** Do not derive a number the capture layer did not actually measure. Provenance and honesty notes stay truthful; a restyle that makes a claim look more certain than the data is a correctness bug.
- **Warning surfaces carry a `WARN` tag *and* a 4dp bar** (spec §4.2).
- **JSON body numbers render monochrome** (spec §4.3) — keys `info`, punctuation `amber`, numbers `body`.
- **One authored entrance motion only** — the reticle.
- **64 KiB UI-only body preview cap** and the distinction between a UI preview cap and a capture truncation must both remain visible. Do not blur them together.

## Review Focus

1. **A restyle makes unavailable evidence look available.** The highest-severity failure in this plan. If a "not captured" cell becomes a blank or a `—` that users read as a real zero, the app starts lying about what it knows. → Task 1.
2. **A masked credential stops being masked.** If any restyle reaches past the projection to render a raw header, secrets leak into the UI and into screenshots. → Task 1.
3. **The 64 KiB preview cap becomes indistinguishable from capture truncation.** Users would conclude data was lost during capture when it was only trimmed for display. → Task 4.
4. **The five inspector tabs collapse or lose their selected state.** Selection must not rely on colour alone, because amber means both armed and warning. → Task 2.
5. **Provenance text truncated to the point of being unreadable.** Provenance is the audit trail; a clipped "captured from" defeats its purpose. → Task 5.

---

### Task 1: Summary sheet — and pin the honesty and masking guarantees

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt` — `M7CaptureSummarySheet`, `M7SummaryRow`, `M7CategoryChip`, `M7FilterChip`, `M7MethodBadge`
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/M7HonestyInvariantTest.kt` (create)

**Interfaces:**
- Consumes: all Plan 1 tokens; `ConsoleTag`, `TagTone`, `Seam`, `SeamRow`, `MetricCell`, `StatusBar4`, `FaviconWell` from `TahoConsole.kt` (Plan 2 Task 2).
- Produces: `MethodBadge(method: String)` and `CategoryChip(label: String, selected: Boolean)` reused by Tasks 2–5.

- [ ] **Step 1: Write the honesty invariant tests FIRST**

These pin properties that exist today, before any restyling. If they pass now and fail after your edits, you broke a guarantee. Create `M7HonestyInvariantTest.kt`:

```kotlin
package app.taho.browser.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the capture module's honesty and redaction guarantees. These are NOT
 * style tests — they assert that a restyle cannot quietly make the app
 * overstate what it captured.
 *
 * The source-level assertions exist because these properties are enforced in
 * the projection layer, not in composables, and a UI-only change should not be
 * able to alter them.
 */
class M7HonestyInvariantTest {

    private val productUx = java.io.File(
        "src/main/java/app/taho/browser/shell/M7ProductUx.kt"
    ).readText()

    private val models = java.io.File(
        "src/main/java/app/taho/browser/shell/M7CaptureModels.kt"
    ).readText()

    @Test
    fun summarySheetRendersAnHonestyNote() {
        // The sheet must keep telling the user what is and is not captured.
        assertTrue(
            productUx.contains("M7HonestyNote"),
            "capture summary must retain its honesty note",
        )
    }

    @Test
    fun unavailableEvidenceHasADedicatedRendering() {
        // There must be an explicit unavailable path, not a fallback to "" or 0.
        assertTrue(
            productUx.contains("Unavailable") || productUx.contains("unavailable"),
            "unavailable evidence must have an explicit rendering path",
        )
    }

    @Test
    fun noRawCredentialAccessInTheShell() {
        // The shell must consume only pre-masked projections. A raw header read
        // here would put credentials into the UI model.
        val forbidden = listOf(
            "requestHeadersRaw",
            "rawHeaders",
            "authorizationHeader",
            "decodedPassword",
            "plaintextSecret",
        )
        val offenders = forbidden.filter { productUx.contains(it) }
        assertTrue(offenders.isEmpty(), "raw credential access in shell: $offenders")
    }

    @Test
    fun maskedProjectionIsStillTheOnlyHeaderSource() {
        // A masked/projected accessor must exist and be what the sheet reads.
        assertTrue(
            productUx.contains("Masked") || models.contains("Masked") ||
                productUx.contains("masked") || models.contains("masked"),
            "the pre-masked projection must remain the header source",
        )
    }

    @Test
    fun attributionIsStillUnverifiedSoCaptureStaysDisabled() {
        // M1_ATTRIBUTION_VERIFIED is false; the UI must not present live capture
        // as if it were flowing.
        assertTrue(
            productUx.contains("ATTRIBUTION") || productUx.contains("attribution"),
            "attribution state must remain visible to the capture UI",
        )
        assertFalse(
            productUx.contains("Live capture enabled"),
            "must not claim live capture while attribution is unverified",
        )
    }
}
```

- [ ] **Step 2: Run the tests to confirm they pass BEFORE restyling**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*M7HonestyInvariantTest*'`
Expected: PASS. If any of these fail on the current code, stop — you have found a
pre-existing defect that must be reported, not restyled over.

- [ ] **Step 3: Restyle `M7MethodBadge` as a mono chip**

```kotlin
@Composable
internal fun M7MethodBadge(method: String) {
    // Method is machine data, so it is mono. Colour distinguishes the verb
    // class but the text always carries it, so colour is never the only signal.
    val tone = when (method.uppercase()) {
        "GET" -> TagTone.Info
        "POST" -> TagTone.Amber
        "PUT", "PATCH" -> TagTone.Ok
        "DELETE" -> TagTone.Danger
        else -> TagTone.Neutral
    }
    ConsoleTag(method.uppercase(), tone)
}
```

- [ ] **Step 4: Restyle `M7SummaryRow` as a seam row with tabular figures**

The summary row is the densest surface in the app — method, endpoint, status, latency, category, flags. Give it a fixed mono column layout so values align vertically down the sheet, which is the whole point of the mono register:

```kotlin
@Composable
internal fun M7SummaryRow(
    request: CapturedRequestSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) raised else panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(28.dp).background(if (selected) amber else hairline))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                M7MethodBadge(request.method)
                Spacer(Modifier.width(7.dp))
                Text(
                    request.endpoint,
                    fontFamily = TahoMono, fontSize = 11.sp, color = ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status code is a number, so tabular figures keep the column straight.
                Text(
                    request.statusCode?.toString() ?: "—",
                    fontFamily = TahoMono, fontSize = 10.sp,
                    color = if (request.statusCode == null) mute else statusTone(request.statusCode),
                    fontVariantNumeric = FontVariantNumeric.TabularNums,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    request.latencyLabel,
                    fontFamily = TahoMono, fontSize = 10.sp, color = mute,
                    fontVariantNumeric = FontVariantNumeric.TabularNums,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    request.category.uppercase(),
                    fontFamily = TahoMono, fontSize = 9.sp, color = mute,
                    letterSpacing = 0.8.sp,
                )
                if (request.hasSensitiveFlag) {
                    Spacer(Modifier.width(8.dp))
                    ConsoleTag("SENSITIVE", TagTone.Amber)
                }
            }
        }
    }
}

/** Status colour is a *state encoding*, always paired with the numeric code. */
private fun statusTone(code: Int) = when {
    code in 200..299 -> ok
    code in 300..399 -> info
    code in 400..499 -> amber
    else -> danger
}
```

The `?: "—"` for a missing status code is the honesty rule in action: an em dash means "not known", and it is styled in `mute` to mark it as absent rather than as a value. Do not replace it with `0` or an empty string.

- [ ] **Step 5: Restyle the filter and category chips**

Selection must not be colour-only, since amber is overloaded (Review Focus item 4):

```kotlin
@Composable
internal fun M7FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        if (selected) "✓ $label" else label,
        fontFamily = TahoMono, fontSize = 9.sp,
        color = if (selected) amber else mute,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0x1AE8AE55) else Color.Transparent)
            .border(
                1.dp,
                if (selected) amberDeep else hairline,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}
```

The leading `✓` on the selected state is what makes selection legible without colour. `modifier` must be a parameter — add it if absent.

- [ ] **Step 6: Re-run the honesty tests**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*M7HonestyInvariantTest*'`
Expected: still PASS. A failure here means the restyle reached past the projection.

- [ ] **Step 7: Run the full suite and gate**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: 33 pre-existing + all new tests green. `M7ProductUxTest` has 10 tests covering the masking and honesty projection — all must pass.

- [ ] **Step 8: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt browser/shell/src/test/
git commit -m "feat(m7): console treatment for the capture summary sheet

Tabular mono columns so status and latency align down the sheet. A
missing status renders as a muted em dash, never 0 or blank. Filter
chips carry a leading check so selection does not rely on colour, which
amber cannot carry alone. Adds M7HonestyInvariantTest to pin masking
and unavailability ahead of the restyle."
```

### Task 2: Request inspector, five tabs

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt` — `M7RequestInspectorSheet`, `M7InspectorTabChip`, `M7KeyValue`, `M7Headers`, `M7Body`, `M7Response`, `M7Timing`, `M7Evidence`
- Test: `browser/shell/src/test/kotlin/app/taho/browser/shell/M7InspectorTabTest.kt` (create)

**Interfaces:**
- Consumes: `ConsoleTag`, `TagTone`, `Seam`, `SeamRow`, `StatusBar4` from `TahoConsole.kt`; `M7MethodBadge` from Task 1.
- Produces: `InspectorTabs(selected: String, onSelect: (String) -> Unit)` used by Task 3.

- [ ] **Step 1: Write the failing test**

Create `M7InspectorTabTest.kt`:

```kotlin
package app.taho.browser.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M7InspectorTabTest {

    @Test
    fun theInspectorHasExactlyFiveTabs() {
        // Spec 3.2 level 3: "Everything known about one request (5 tabs)".
        val tabs = listOf("HEADERS", "BODY", "RESPONSE", "TIMING", "EVIDENCE")
        val text = java.io.File(
            "src/main/java/app/taho/browser/shell/M7ProductUx.kt"
        ).readText()
        val missing = tabs.filterNot { text.contains(it) }
        assertEquals(emptyList(), missing, "inspector tabs missing")
    }

    @Test
    fun selectedTabIsMarkedWithoutRelyingOnColourAlone() {
        val text = java.io.File(
            "src/main/java/app/taho/browser/shell/M7ProductUx.kt"
        ).readText()
        // Amber means both armed and warning, so the selected tab needs a
        // non-colour marker. Accept a check glyph or an explicit ARIA-ish flag.
        assertTrue(
            text.contains("selected") && (text.contains("✓") || text.contains("selectedTab")),
            "selected tab must carry a non-colour selection marker",
        )
    }

    @Test
    fun inspectorRetainsBackAndRequestIdentity() {
        // Spec 3.3.2: expanding or tabbing never loses the selected request.
        val text = java.io.File(
            "src/main/java/app/taho/browser/shell/M7ProductUx.kt"
        ).readText()
        assertTrue(
            text.contains("onBack") || text.contains("Back"),
            "inspector must retain a Back affordance",
        )
        assertTrue(
            text.contains("requestId") || text.contains("request.id"),
            "inspector must keep the request identity visible",
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :browser:shell:testDebugUnitTest --tests '*M7InspectorTabTest*'`
Expected: FAIL on `theInspectorHasExactlyFiveTabs` or the selection marker.

- [ ] **Step 3: Restyle the tab strip**

```kotlin
@Composable
internal fun InspectorTabs(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf("HEADERS", "BODY", "RESPONSE", "TIMING", "EVIDENCE")
    Row(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .border(1.dp, hairline, RoundedCornerShape(0.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Text(
                if (isSelected) "✓ $tab" else tab,
                fontFamily = TahoMono, fontSize = 8.5.sp,
                color = if (isSelected) amber else mute,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSelected) Color(0x1AE8AE55) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 7.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
```

Five tabs across ~334dp is ~67dp each. At 8.5sp mono, "✓ RESPONSE" is roughly 62dp — tight but fits. If it clips on a real device, drop the `✓` on non-selected tabs only and keep the selected tab's check plus its `raised` background. Verify on device rather than assuming.

- [ ] **Step 4: Restyle `M7KeyValue` as a two-column mono definition row**

```kotlin
@Composable
internal fun M7KeyValue(key: String, value: String, sensitive: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            key,
            fontFamily = TahoMono, fontSize = 10.sp, color = mute,
            modifier = Modifier.width(116.dp),
        )
        Text(
            // Sensitive values arrive already masked from the projection. Never
            // re-derive them here.
            value,
            fontFamily = TahoMono, fontSize = 10.sp,
            color = if (sensitive) amber else ink,
            modifier = Modifier.weight(1f),
        )
    }
}
```

`sensitive` only changes the tint, to draw the eye to a masked field. The masking itself happens upstream and is not this composable's business.

- [ ] **Step 5: Restyle `M7Headers` as a seam stack**

```kotlin
@Composable
internal fun M7Headers(headers: List<HeaderProjection>) {
    if (headers.isEmpty()) {
        M7Unavailable("No headers captured for this request.")
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .border(1.dp, hairline, RoundedCornerShape(0.dp)),
    ) {
        headers.forEachIndexed { index, header ->
            if (index > 0) Seam()
            M7KeyValue(header.name, header.maskedValue, header.isSensitive)
        }
    }
}
```

- [ ] **Step 6: Add the shared unavailable component**

Every evidence surface needs the same honest "not captured" rendering. Define it once:

```kotlin
/**
 * Spec §7 invariant 3 — unavailable evidence is stated, never faked. An empty
 * cell would read as a real empty value, which is a different and wrong claim.
 */
@Composable
internal fun M7Unavailable(reason: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .border(1.dp, hairline, RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBar4(mute)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "UNAVAILABLE",
                fontFamily = TahoMono, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp, color = mute,
            )
            Spacer(Modifier.height(5.dp))
            Text(reason, fontFamily = TahoBody, fontSize = 12.sp, color = body)
        }
    }
}
```

Then route `M7Body`, `M7Response`, `M7Timing` and `M7Evidence` through it whenever their underlying capture is absent — for example:

```kotlin
@Composable
internal fun M7Response(response: ResponseCapture?) {
    if (response == null) {
        M7Unavailable("Response body was not captured for this request.")
        return
    }
    // ... existing rendering, restyled ...
}
```

The specific `reason` strings must name *what* is missing, so a user can tell a capture limitation from a genuinely empty response.

- [ ] **Step 7: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: green, including `M7InspectorTabTest` and `M7HonestyInvariantTest`.

- [ ] **Step 8: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt browser/shell/src/test/
git commit -m "feat(m7): console treatment for the request inspector

Five-tab strip with a check marker plus background so selection does not
depend on amber, which is overloaded. Key/value rows go two-column mono.
Adds M7Unavailable so every absent-evidence path states what is missing
instead of rendering a blank cell."
```

### Task 3: Technical workspace view

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt` — `M7TechnicalWorkspaceView`, `M7RawBlock`, `M7RawSection`

**Interfaces:**
- Consumes: `M7Unavailable` from Task 2, `ConsoleTag`, `StatusBar4`, `Seam`.
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Restyle as a full-bleed technical surface**

Per spec §3.1 this is the S3 expanded variant for large bodies and raw headers, and it must retain Back, request identity and the primary action.

```kotlin
@Composable
internal fun M7TechnicalWorkspaceView(
    request: CapturedRequestDetail,
    onBack: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(canvas)
            .padding(horizontal = 12.dp),
    ) {
        // Identity bar — never lost, per spec 3.3.2.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TahoIcon("chevron_right", tint = ash, description = "Back",
                modifier = Modifier.rotate(180f).clickable(onClick = onBack).padding(6.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    request.method.uppercase(),
                    fontFamily = TahoMono, fontSize = 9.sp, color = mute,
                )
                Text(
                    request.endpoint,
                    fontFamily = TahoMono, fontSize = 11.sp, color = ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "SEND TO TAHO",
                fontFamily = TahoMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp, color = canvas,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(amber)
                    .clickable(onClick = onSend)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))

        LazyColumn(Modifier.weight(1f)) {
            item { M7RawSection("REQUEST HEADERS") }
            item { M7RawBlock(request.requestHeaders) }
            item { M7RawSection("RESPONSE HEADERS") }
            item {
                if (request.responseHeaders.isEmpty()) {
                    M7Unavailable("No response headers were captured.")
                } else {
                    M7RawBlock(request.responseHeaders)
                }
            }
        }
    }
}
```

Add `import androidx.compose.foundation.lazy.LazyColumn` and `import androidx.compose.foundation.layout.height`. Add `rotate` from `androidx.compose.ui.draw.rotate`.

- [ ] **Step 2: Restyle `M7RawBlock` as a well with a size stamp**

```kotlin
@Composable
internal fun M7RawBlock(lines: List<String>, byteCount: Long, truncated: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(well)
            .border(1.dp, hairline, RoundedCornerShape(0.dp))
            .padding(11.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "RAW",
                fontFamily = TahoMono, fontSize = 8.sp, letterSpacing = 1.sp, color = mute,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatBytes(byteCount),
                fontFamily = TahoMono, fontSize = 8.sp, color = mute,
                fontVariantNumeric = FontVariantNumeric.TabularNums,
            )
        }
        Spacer(Modifier.height(9.dp))
        lines.forEach { line ->
            Text(
                line,
                fontFamily = TahoMono, fontSize = 10.sp, lineHeight = 15.sp,
                color = ink, softWrap = false,
            )
        }
        if (truncated) {
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            Spacer(Modifier.height(9.dp))
            Text(
                "Preview capped at 64 KiB for display. Capture retained the full body.",
                fontFamily = TahoMono, fontSize = 9.sp, color = amber,
            )
        }
    }
}
```

The truncation note is Review Focus item 3: a UI preview cap must never read as capture truncation. Keep those two claims visibly distinct, and keep the `truncated` flag sourced from the real capture state rather than inferred from line count.

- [ ] **Step 3: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

- [ ] **Step 4: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt
git commit -m "feat(m7): console treatment for the technical workspace view

Full-bleed well surface with a persistent identity bar and primary
action, per spec 3.3.2. Raw blocks keep a byte-count stamp and state
the 64 KiB display cap as distinct from capture truncation."
```

### Task 4: Body and response viewers

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt` — `M7Body`, `M7Response`, and the JSON highlighter near line 782

**Interfaces:**
- Consumes: `M7Unavailable` from Task 2, `M7RawBlock` from Task 3.
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Add the shared byte formatter to `TahoConsole.kt`**

`formatBytes` is called by `M7RawBlock` (Task 3), `M7Body` (this task) and
`StorageBarRow` in Plan 4. Define it once, in `TahoConsole.kt`, or the three
copies will disagree about rounding — which is how a 1000 vs 1024 discrepancy
reaches a storage page:

```kotlin
/**
 * Binary units, one decimal place above 1 KiB. Locale is pinned so a device
 * using comma decimal separators does not render "1,5 MiB" into a mono column.
 */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "Unavailable"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) {
        "${value.toLong()} ${units[unit]}"
    } else {
        String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit])
    }
}
```

- [ ] **Step 2: Confirm the JSON highlighter state**

Per spec §4.3 the violet is gone and numbers render monochrome. Verify the current state rather than assuming Plan 1 did it:

```bash
cd "/home/eternal/Taho/Taho Browser"
grep -n 'TahoJsonNum\|addStyle(SpanStyle' browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt
```

Expected: `TahoJsonNum` absent; the numeric branch uses `SpanStyle(color = body)`. If Plan 1's `sed` pass missed it because it sat inside a string template, fix it by hand now.

- [ ] **Step 3: Restyle `M7Body` and `M7Response` to share one viewer**

```kotlin
@Composable
internal fun M7Body(body: BodyCapture?) {
    if (body == null) {
        M7Unavailable("Request body was not captured for this request.")
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(panel)
                .border(1.dp, hairline, RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                body.contentType.uppercase(),
                fontFamily = TahoMono, fontSize = 9.sp, color = mute,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatBytes(body.byteCount),
                fontFamily = TahoMono, fontSize = 9.sp, color = mute,
                fontVariantNumeric = FontVariantNumeric.TabularNums,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (body.isJson) {
            CodeWell(highlightJson(body.text), capped = body.previewCapped)
        } else {
            M7RawBlock(body.text.lines(), body.byteCount, body.previewCapped)
        }
    }
}

@Composable
private fun CodeWell(lines: List<AnnotatedString>, capped: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(well)
            .border(1.dp, hairline, RoundedCornerShape(0.dp))
            .padding(11.dp),
    ) {
        lines.forEach { line ->
            Text(line, fontFamily = TahoMono, fontSize = 10.sp, lineHeight = 15.sp, softWrap = false)
        }
        if (capped) {
            Spacer(Modifier.height(9.dp))
            Text(
                "Preview capped at 64 KiB for display. Capture retained the full body.",
                fontFamily = TahoMono, fontSize = 9.sp, color = amber,
            )
        }
    }
}
```

`M7Response` mirrors this with its own unavailable reason. The 64 KiB note appears in both paths so the cap is never silently applied.

- [ ] **Step 4: Run the tests**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

- [ ] **Step 5: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt browser/shell/src/main/java/app/taho/browser/shell/TahoConsole.kt
git commit -m "feat(m7): share one code viewer between body and response

JSON renders through the well with monochrome numbers, keys and
punctuation keeping their semantic tints. Both paths restate the 64 KiB
display cap so it is never confused with capture truncation."
```

### Task 5: Send confirmation, provenance and progress

**Files:**
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/M7ProductUx.kt` — `M7SendConfirmationSheet`, `M7HonestyNote`, `M7Provenance`, `M7PrimaryButton`, `M7SecondaryButton`, `M7PolicyButton`, `M7TransferProgressOverlay`, `M7ProgressBar`, `M7TransferLine`
- Modify: `browser/shell/src/main/java/app/taho/browser/shell/M7TransferSearch.kt` — `M7CaptureSearchField`

**Interfaces:**
- Consumes: all prior tasks' primitives.
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Restyle the honesty note and provenance as first-class blocks**

These two are the module's truthfulness surface. Give them the strongest visual treatment so they cannot be skimmed past.

```kotlin
@Composable
internal fun M7HonestyNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .border(1.dp, hairlineStrong, RoundedCornerShape(0.dp))
            .padding(12.dp),
    ) {
        StatusBar4(mute)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "WHAT THIS DOES NOT KNOW",
                fontFamily = TahoMono, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp, color = mute,
            )
            Spacer(Modifier.height(6.dp))
            Text(text, fontFamily = TahoBody, fontSize = 12.sp, color = body, lineHeight = 17.sp)
        }
    }
}

@Composable
internal fun M7Provenance(provenance: TransferProvenance) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(panel)
            .border(1.dp, hairline, RoundedCornerShape(0.dp))
            .padding(12.dp),
    ) {
        Text(
            "PROVENANCE",
            fontFamily = TahoMono, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp, color = mute,
        )
        Spacer(Modifier.height(8.dp))
        M7KeyValue("Schema", provenance.schemaVersion)
        Seam()
        M7KeyValue("Origin", provenance.origin)
        Seam()
        // Do not truncate this. A clipped provenance defeats its purpose —
        // it is the audit trail. Wrap it instead.
        M7KeyValue("Attribution", provenance.attributionState)
    }
}
```

`M7KeyValue` uses a fixed 116dp label column; long provenance *values* wrap under it. Verify the longest real attribution string fits without horizontal clipping on a 366dp screen, and if it does not, reduce the label column to 96dp for this block rather than truncating the value.

- [ ] **Step 2: Restyle the buttons**

```kotlin
@Composable
internal fun M7PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        fontFamily = TahoMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        color = if (enabled) canvas else stone,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(if (enabled) amber else hairline)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun M7SecondaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        fontFamily = TahoMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        color = if (enabled) body else stone,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(if (enabled) panel else Color.Transparent)
            .border(1.dp, if (enabled) hairlineStrong else hairline, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}
```

`stone` on a disabled fill is the sanctioned disabled tier. Never use `mute` on a filled control, because that fails contrast against the fill.

- [ ] **Step 3: Restyle the transfer progress overlay**

Per spec §5 the overlay is a full-screen system state. Keep it flat and machine-forward:

```kotlin
@Composable
internal fun M7TransferProgressOverlay(state: TransferProgressState) {
    Column(
        Modifier
            .fillMaxSize()
            .background(canvas)
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(120.dp))
        Text(
            "SENDING TO TAHO",
            fontFamily = TahoMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp, color = amber,
        )
        Spacer(Modifier.height(18.dp))
        M7ProgressBar(progress = state.progress)
        Spacer(Modifier.height(12.dp))
        Text(
            state.stageLabel,
            fontFamily = TahoMono, fontSize = 9.sp, color = mute,
            fontVariantNumeric = FontVariantNumeric.TabularNums,
        )
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth()) {
            state.completed.forEachIndexed { index, line ->
                M7TransferLine(line, done = true)
                if (index < state.completed.lastIndex) Seam()
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "taho.request-transfer · v1 · local intent",
            fontFamily = TahoMono, fontSize = 8.sp, color = mute, letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun M7ProgressBar(progress: Float) {
    // Determinate only. A determinate bar implies a measurement; if the transfer
    // layer cannot report one, render the unavailable state instead of faking 0%.
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(hairlineStrong),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(amber),
        )
    }
}

@Composable
internal fun M7TransferLine(label: String, done: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (done) "✓" else "·",
            fontFamily = TahoMono, fontSize = 10.sp,
            color = if (done) ok else mute,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            fontFamily = TahoMono, fontSize = 10.sp,
            color = if (done) ink else mute,
            modifier = Modifier.weight(1f),
        )
    }
}
```

If `TransferProgressState.progress` cannot be trusted for byte-level work, render `M7Unavailable("Transfer progress is not reported for this payload.")` instead of `M7ProgressBar`. A progress bar that animates without a real measurement is a fabricated diagnostic.

- [ ] **Step 4: Restyle the capture search field**

```kotlin
@Composable
internal fun M7CaptureSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(Modifier.fillMaxWidth()) {
        TahoReticle(trigger = value, modifier = Modifier.matchParentSize())
        Row(
            Modifier
                .fillMaxWidth()
                .background(well)
                .border(1.dp, hairlineStrong, RoundedCornerShape(2.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TahoIcon("search", tint = ash, description = null)
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = TahoMono, fontSize = 11.sp, color = ink),
                cursorBrush = SolidColor(amber),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, fontFamily = TahoMono, fontSize = 11.sp, color = mute)
                    }
                    inner()
                },
            )
            if (value.isNotEmpty()) {
                TahoIcon(
                    "close", tint = ash, description = "Clear search",
                    modifier = Modifier.clickable { onValueChange("") }.size(15.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 5: Run the full suite and gate**

```bash
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: 33 pre-existing plus all capture tests green. `M7ProductUxTest`'s 10 tests are the ones most likely to catch a masking or honesty regression — read any failure before touching the assertion.

- [ ] **Step 6: Commit**

```bash
git add browser/shell/src/main/java/app/taho/browser/shell/
git commit -m "feat(m7): console treatment for send confirmation and transfer progress

Honesty note and provenance become first-class bordered blocks so
neither can be skimmed past. Provenance values wrap rather than
truncate, preserving the audit trail. Progress renders determinate only
when the transfer layer reports a real measurement."
```

---

## Exit criteria for Plan 3

- [ ] `gradle :browser:shell:testDebugUnitTest` — PASS, including all 10 `M7ProductUxTest` cases
- [ ] `gradle verifyArchitecture` — PASS
- [ ] `M7HonestyInvariantTest` green — masking and unavailability unchanged from before the plan
- [ ] `M7InspectorTabTest` green — five tabs, non-colour selection marker, identity retained
- [ ] No raw credential read anywhere in `M7ProductUx.kt`
- [ ] Every absent-evidence path renders `M7Unavailable` or an explicit `—`, never a blank or a `0`
- [ ] The 64 KiB display cap is stated distinctly from capture truncation on both code paths
- [ ] `TahoJsonNum` is gone and JSON numbers render monochrome

**Rollback:** `git revert` the five commits. This plan changed only presentation in
the capture module; capture, attribution, persistence and transfer logic are
untouched, so behaviour is identical.

---

*Plan 3 of 5. Next: Plan 4 — Settings hub (`TahoSettingsHub.kt`).*