package app.taho.browser.shell

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Task 5 guards for the authored icon family (spec §5).
 *
 * Three independent claims, each asserted against the tree rather than against
 * a value recomputed inside the test — an earlier version of this file re-derived
 * the predicate it was testing, so no production change could fail it.
 *
 * 1. `res/drawable` holds exactly the 42 manifest files, set-equal in both
 *    directions, named `ic_taho_<snake_case>.xml`.
 * 2. Every one of them is a 24dp viewport stroked at 1.5 with round caps and
 *    joins — one family, one weight — except `ic_taho_brand`, the filled crest,
 *    whose form is asserted explicitly instead of skipped.
 * 3. No emoji is left standing in an icon slot, while inline prose marks and
 *    data fields are still text. The distinction is the whole point of the
 *    task, so the test expresses it and pins the survivors by name and count.
 */
class TahoIconSetTest {

    // ------------------------------------------------------------------
    // 1. the manifest is exactly what is on disk
    // ------------------------------------------------------------------

    @Test
    fun drawableDirectoryIsExactlyTheManifest() {
        // Anchor check. An earlier guard did `substringAfter("chevron-")`, which
        // also matches the substring inside its own declaration below and so
        // silently produced the expected name from the test itself.
        assertTrue(
            MANIFEST.contains("chevron-right") && MANIFEST.contains("warn-triangle"),
            "manifest lost its hyphenated spellings: $MANIFEST",
        )
        assertEquals(
            emptyList(),
            MANIFEST.filter { it.contains('_') },
            "the manifest is written hyphenated and mapped to snake_case; " +
                "an underscore here means the mapping would be skipped",
        )

        val expected: Map<String, String> =
            MANIFEST.associateBy({ it }, { "ic_taho_${it.replace('-', '_')}.xml" })
        assertEquals(
            42,
            expected.size,
            "spec §5 states 42 icons and this test pins the number, so the " +
                "set cannot be quietly trimmed to make an assertion pass",
        )

        assertTrue(
            drawableDir.isDirectory,
            "expected the icon family at ${drawableDir.path}",
        )
        val onDisk = drawableDir.listFiles { f: File -> f.name.endsWith(".xml") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        assertTrue(
            onDisk.isNotEmpty(),
            "${drawableDir.path} holds no vector drawables at all",
        )

        val missing = expected.values.filterNot { it in onDisk }.sorted()
        val extra = onDisk.filterNot { it in expected.values }.sorted()
        assertEquals(emptyList(), missing, "manifest icons with no drawable file")
        assertEquals(emptyList(), extra, "drawables in res/drawable that are not in the manifest")
        assertEquals(42, onDisk.size, "res/drawable must hold exactly the 42 manifest files")
    }

    // ------------------------------------------------------------------
    // 2. one family, one weight
    // ------------------------------------------------------------------

    @Test
    fun everyDrawableIsOneAndAHalfDpStrokedOnATwentyFourDpViewport() {
        assertTrue(drawableDir.isDirectory, "expected the icon family at ${drawableDir.path}")
        val files = (drawableDir.listFiles { f: File -> f.name.endsWith(".xml") } ?: emptyArray())
            .sortedBy { it.name }
        assertEquals(
            MANIFEST.size,
            files.size,
            "expected the ${MANIFEST.size} manifest drawables before checking their form",
        )

        val problems = mutableListOf<String>()
        files.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
            val root = doc.documentElement
            fun attr(name: String): String? =
                root.getAttribute("android:$name").takeIf { it.isNotEmpty() }

            listOf("width", "height").forEach { axis ->
                val value = attr(axis)
                if (value != "24dp") problems += "${file.name}: android:$axis=$value, expected 24dp"
            }
            listOf("viewportWidth", "viewportHeight").forEach { axis ->
                val value = attr(axis)
                if (value != "24") problems += "${file.name}: android:$axis=$value, expected 24"
            }

            val paths = root.getElementsByTagName("path")
            if (paths.length == 0) {
                problems += "${file.name}: no <path> elements"
                return@forEach
            }

            if (file.name == BRAND_FILE) {
                // The crest is a filled mark. Its form is pinned rather than
                // waived: no stroke, and a fill on every path.
                if (attr("strokeWidth") != null) {
                    problems += "${file.name}: the crest is filled, expected no android:strokeWidth"
                }
                if (attr("fillColor") == null) {
                    problems += "${file.name}: the crest is filled, expected android:fillColor"
                }
                repeat(paths.length) { i ->
                    val p = paths.item(i) as Element
                    if (p.getAttribute("android:pathData").isEmpty()) {
                        problems += "${file.name}: path $i has no pathData"
                    }
                    if (p.getAttribute("android:fillColor").isEmpty()) {
                        problems += "${file.name}: path $i is not filled"
                    }
                }
            } else {
                val width = attr("strokeWidth")
                if (width != "1.5") {
                    problems += "${file.name}: android:strokeWidth=$width, expected 1.5"
                }
                if (attr("strokeLineCap") != "round") {
                    problems += "${file.name}: android:strokeLineCap=${attr("strokeLineCap")}, expected round"
                }
                if (attr("strokeLineJoin") != "round") {
                    problems += "${file.name}: android:strokeLineJoin=${attr("strokeLineJoin")}, expected round"
                }
                repeat(paths.length) { i ->
                    val p = paths.item(i) as Element
                    if (p.getAttribute("android:pathData").isEmpty()) {
                        problems += "${file.name}: path $i has no pathData"
                    }
                    if (p.getAttribute("android:strokeColor").isEmpty()) {
                        problems += "${file.name}: path $i declares no android:strokeColor"
                    }
                }
            }
        }
        assertEquals(emptyList(), problems, "drawable form does not match spec §5")
    }

    // ------------------------------------------------------------------
    // 3. no emoji left in an icon slot
    // ------------------------------------------------------------------

    /**
     * An icon slot is a string literal whose entire content is glyph characters.
     * A glyph embedded in a longer string (`"✓ complete"`, `"📁 Requests"`) is a
     * typographic mark inside prose and stays text, per spec §5 "Inline marks are
     * not icons".
     *
     * Scanning only live lines matters: an earlier guard counted raw occurrences
     * and was satisfied by commenting the control out.
     */
    @Test
    fun noEmojiRemainsInAnIconSlot() {
        val files = kotlinSourcesUnder(mainSrcDir)
        assertTrue(
            files.size >= 8,
            "expected the shell's production sources under ${mainSrcDir.path}, saw ${files.size}",
        )

        val slots = mutableListOf<Slot>()
        files.forEach { file ->
            val rel = file.path.removePrefix("src/main/")
            liveLines(file.readText()).forEach { (number, code) ->
                STRING_LITERAL.findAll(code).forEach { m ->
                    val body = m.groupValues[1]
                    if (isIconSlot(body)) slots += Slot(rel, number, body)
                }
            }
        }
        assertTrue(
            slots.size >= RESIDUAL_TOTAL,
            "the scan found ${slots.size} icon-slot literals, fewer than the " +
                "${RESIDUAL_TOTAL} survivors it is asked to account for — the " +
                "scan has gone stale",
        )

        val offenders = slots.filterNot { it.isExplained() }
        assertEquals(
            emptyList(),
            offenders.map { "${it.file}:${it.line} ${it.body}" },
            "emoji still standing in an icon slot; convert it to TahoIcon, or, if " +
                "it is really prose or data, say so in this test",
        )

        // Pin the survivors exactly. Without this the offenders assertion alone
        // would accept a *growing* carve-out, and the gap this test documents
        // would widen invisibly.
        val residual = slots.filter { it.isExplained() }
            .groupingBy { it.body }.eachCount()
            .filterKeys { it in EXPLAINED }
            .mapValues { (_, count) -> count }
        assertEquals(
            EXPLAINED.filterValues { it > 0 }.mapValues { it.value },
            residual,
            "the documented prose/data/manifest-gap literals changed. Convert one " +
                "and drop it here with its reason; add one and it must be explained " +
                "above before it can pass.",
        )

        // The other half of the boundary: prose and data are still text. A guard
        // that only looked for leftovers would be satisfied by converting these.
        PROSE_MUST_REMAIN_TEXT.forEach { (where, needle) ->
            assertTrue(
                files.any { it.readText().contains(needle) },
                "$where: expected `$needle` to still be text. Spec §5 keeps inline " +
                    "prose marks and data-field glyphs as characters; they are marks " +
                    "in sentences, not icons.",
            )
        }
    }

    // ---- helpers -------------------------------------------------------

    private data class Slot(val file: String, val line: Int, val body: String) {
        /** True when the test can name why this literal is legitimately still text. */
        fun isExplained(): Boolean =
            body in TYPOGRAPHIC_MARKS || body.all { c: Char -> c in TYPOGRAPHIC_MARK_CHARS } ||
                body in DATA_FIELDS || EXPLAINED.containsKey(body)
    }

    /** Dropped lines cannot satisfy a guard, so they are removed before scanning. */
    private fun liveLines(source: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var inBlock = false
        source.split("\n").forEachIndexed { index, raw ->
            val trimmed = raw.trimStart()
            if (inBlock) {
                val close = raw.indexOf("*/")
                if (close == -1) return@forEachIndexed
                inBlock = false
                // keep whatever follows the block comment on the closing line
                val tail = raw.substring(close + 2)
                out += (index + 1) to stripTrailingComment(tail)
                return@forEachIndexed
            }
            if (trimmed.startsWith("//")) return@forEachIndexed
            if (trimmed.startsWith("/*")) {
                val close = raw.indexOf("*/", 2)
                if (close == -1) {
                    inBlock = true
                    return@forEachIndexed
                }
                out += (index + 1) to stripTrailingComment(raw.substring(close + 2))
                return@forEachIndexed
            }
            out += (index + 1) to stripTrailingComment(raw)
        }
        return out
    }

    /** Cuts a `//` comment that is not inside a string literal, so `"https://"` survives. */
    private fun stripTrailingComment(line: String): String {
        var inString = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inString -> when (c) {
                    '\\' -> i++
                    '"' -> inString = false
                }
                c == '"' -> inString = true
                c == '/' && i + 1 < line.length && line[i + 1] == '/' -> return line.substring(0, i)
            }
            i++
        }
        return line
    }

    /**
     * The icon-slot predicate. A slot holds nothing but glyph characters, with no
     * leading or trailing space — a glyph followed by a space is a prefix on a
     * sentence (`"△ " + state`), which is prose.
     */
    private fun isIconSlot(body: String): Boolean {
        if (body != body.trim()) return false
        if (body.isEmpty()) return false
        if (body == PLUS_ASCII) return true
        return body.all { it.code > 0x7F && it !in PROSE_PUNCTUATION }
    }

    private fun kotlinSourcesUnder(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .sortedBy { it.path }
            .toList()
    }

    private companion object {
        val drawableDir = File("src/main/res/drawable")
        val mainSrcDir = File("src/main")

        val BRAND_FILE = "ic_taho_brand.xml"

        /** Spec §5, written exactly as the spec spells it. */
        val MANIFEST = listOf(
            "close", "chevron-left", "chevron-right", "minus", "plus", "search",
            "check", "warn-triangle", "settings",
            "person", "incognito", "brand", "shield", "shield-check", "lock",
            "bolt", "monitor",
            "monitor-check", "sparkle", "grid", "clock", "caret-up", "caret-down",
            "alert", "home", "card",
            "star", "star-outline", "book", "reader", "expand",
            "reload", "overflow", "external-link",
            "location", "camera", "microphone", "notification", "clipboard",
            "storage", "popups",
            "autoplay",
        )

        val STRING_LITERAL = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")

        /** Prose furniture. Never an icon, and never a lone literal either. */
        val PROSE_PUNCTUATION = setOf('—', '–', '·', '§', '…', '“', '”', '≠')

        val PLUS_ASCII = "+"

        /**
         * Glyphs that sit alone in a literal and are still punctuation: `•` is the
         * separator in "method · url · status" style strings and the character of a
         * masked card number. Neither is an affordance.
         */
        val TYPOGRAPHIC_MARK_CHARS = setOf('•')

        /** Repeated runs of the same mark, e.g. a masked card number. */
        val TYPOGRAPHIC_MARKS = setOf("•")

        /**
         * The `iconGlyph` / `avatarGlyph` data fields on `SearchEngineItem`,
         * `TopSiteItem` and `BrowserProfileUi` (`TahoModels.kt`). Their values are
         * caller-supplied content — `addSearchEngine` truncates to `.take(2)` — and
         * `BrowserProfileUi` is written to the encrypted state file by
         * `TahoBrowserPersistence`. Converting them is a model and serialisation
         * change, not an icon swap (spec §5, "Two glyphs are data, not chrome").
         *
         * This set excuses the *characters*, not the sites: it is why the chrome
         * `⌕` in the find bar and the search fields had to be converted by hand
         * rather than falling out as offenders. Spec §5 is explicit — "chrome uses
         * of the same characters become drawables; the data field keeps text" —
         * and a character-level excuse cannot enforce that, so the conversion is
         * not optional and neither is [EXPLAINED]'s census below.
         */
        val DATA_FIELDS = setOf("⌕", "👤")

        /**
         * `glyph -> number of icon-slot sites`, for every survivor.
         *
         * These counts are the census, not a target: each one is the number of
         * sites that genuinely cannot be an icon, so converting one means
         * lowering its number here and saying why in that glyph's own comment
         * above. Raising one is how a real icon would be quietly re-excused.
         *
         * Note what is *not* listed, because [TYPOGRAPHIC_MARK_CHARS] already
         * covers it structurally: the masked-card run `••••••••••••` is explained
         * by every-character membership, so it needs no per-glyph entry, and it
         * does not appear in [EXPLAINED] because this map is keyed by exact
         * body. Its `•` sibling at `M4CaptureModels.kt` is the same situation.
         */
        val EXPLAINED = mapOf(
            // The masking check in M4CaptureModels: `displayValue.contains("•")`
            // is a predicate over a value, not an affordance.
            "•" to 1,
            // iconGlyph on SearchEngineItem: the `isDefault` seed, the
            // resetInMemoryForTests seed, addSearchEngine's default parameter
            // and its blank-fallback. All four are data.
            "⌕" to 4,
            // avatarGlyph on BrowserProfileUi: the live seed and the
            // resetInMemoryForTests seed. Serialised to the encrypted state file.
            "👤" to 2,
        )

        val RESIDUAL_TOTAL = EXPLAINED.values.sum()

        /**
         * Positive half of the boundary. If an edit converts one of these the test
         * fails, which a leftover-only guard could never do.
         */
        val PROSE_MUST_REMAIN_TEXT = listOf(
            "M7ProductUx.kt: inline prose mark" to "\"✓ complete\"",
            "M7ProductUx.kt: inline prose mark" to "\"△ body partial\"",
            "M7ProductUx.kt: inline prose prefix" to "\"△ \"",
            "M7ProductUx.kt: inline prose prefix" to "\"ⓘ \"",
            "TahoSettingsHub.kt: inline prose mark" to "\"📁 Collections\"",
            "TahoTabsOverview.kt: inline prose mark" to "\"◐ Private\"",
            "TahoTabsOverview.kt: inline prose mark" to "\"📌 Pin\"",
            "TahoBrowserMenu.kt: inline prose mark" to "\"📑 Reader Mode\"",
            "TahoBrowserStateStore.kt: data field" to "avatarGlyph = \"👤\"",
            "TahoBrowserStateStore.kt: data field" to "iconGlyph: String = \"⌕\"",
            "TahoBrowserStateStore.kt: data field" to "iconGlyph.take(2)",
        )
    }
}
