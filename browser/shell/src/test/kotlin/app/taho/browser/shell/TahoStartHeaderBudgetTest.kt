package app.taho.browser.shell

import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The brand header cannot overflow at the minimum supported screen width.
 *
 * Adding the tab-switcher icon (`bb94d53`) grew the right-hand group from
 * `38 + 8 + 38 = 84dp` to `38 + 8 + 38 + 8 + 38 = 130dp`. The row's own budget is
 * `screenWidth - 40dp` (a 20dp horizontal padding either side), so on a 360dp
 * screen there is 320dp to fill, and the left group — crest, spacer, and a
 * wordmark column whose tagline is 29 glyphs of 9sp JetBrains Mono — wanted
 * ~331dp. Neither the left `Row` nor the wordmark `Column` was weighted, so the
 * fixed-size right group was laid out past the measured box and **overlapped**
 * rather than wrapping away.
 *
 * So this asserts the **arithmetic**, not the shape. A shape assertion ("the left
 * Row carries a weight") passes on a row that still overflows, and an overflow is
 * a number, not a shape: every constant below is read out of production source,
 * so adding a fourth header icon, or bumping an icon from 38dp to 44dp, or
 * lengthening the tagline, fails this test instead of shipping.
 *
 * ## The one constant that is *not* in the source
 *
 * [monoAdvanceEm] is a font metric, not a declaration: `TahoTheme.kt` loads
 * `R.font.jetbrains_mono_*` into `TahoMono`, and the per-glyph advance lives
 * inside the TTF. JetBrains Mono is monospaced at 600/1000 em, so one glyph
 * advances `0.6 * fontSize`. That is the only figure this test asserts rather
 * than extracts, and it is why the budget carries a stated slack below. If the
 * typeface ever changes, the measured total is wrong in one direction only — the
 * fix is to correct this constant, not to relax the assertion.
 *
 * Every other figure is extracted, and each extraction asserts a plausible
 * non-zero value: a regex that stopped matching would otherwise read 0dp and make
 * this test pass vacuously.
 */
class TahoStartHeaderBudgetTest {

    /** JetBrains Mono advance width, 600/1000 em. Documented above. */
    private val monoAdvanceEm = 0.6

    /** The narrowest screen this app is expected to lay out on. */
    private val minScreenWidthDp = 360.0

    /** JetBrains Mono is monospaced; [monoAdvanceEm] comes from TahoMono. */
    private val startPageFile = File("src/main/java/app/taho/browser/shell/TahoStartPage.kt")

    /**
     * Live lines only, trimmed.
     *
     * A commented-out control still leaves its identifier sitting in the file, so
     * a raw text count can be satisfied by prose. Trimming also makes depth
     * explicit: afterwards `Row(` is unambiguously a row at that nesting level
     * and `private fun ` is unambiguously top-level.
     */
    private val live: List<String> = startPageFile.readText()
        .lineSequence()
        .map { it.trim() }
        .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
        .toList()

    /** String literals blanked, so brace counting cannot trip over prose. */
    private fun withoutStrings(line: String): String =
        line.replace(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), "\"\"")

    /**
     * Index just past the block or declaration starting at [from].
     *
     * Brace matching, not a `substringBefore("\nprivate fun ")` boundary: this file
     * nests composables inside `if (…)` blocks, and a string boundary cannot tell
     * the end of a block from the end of a lambda nested inside it.
     */
    private fun endOf(from: Int): Int {
        var depth = 0
        var started = false
        for (index in from until live.size) {
            for (ch in withoutStrings(live[index])) {
                when (ch) {
                    '{' -> { depth++; started = true }
                    '}' -> depth--
                }
            }
            if (started && depth == 0) return index + 1
        }
        throw AssertionError("unbalanced braces from line ${from + 1} — the region " +
            "bounding below would run to the end of the file")
    }

    /**
     * A call's *signature* — its declaration lines up to and including the one
     * that opens the body, where that line ends in `{`.
     *
     * A modifier chain is not always one line: `Row(` on its own line followed by
     * `modifier = Modifier.weight(1f),` would defeat a check that only reads the
     * first line. Stopping at the opening brace also keeps a *child*'s modifier
     * out of the answer, so "the left Row is weighted" cannot be satisfied by
     * the wordmark Column underneath it.
     */
    private fun signatureOf(index: Int): String {
        val builder = StringBuilder()
        for (at in index until live.size) {
            builder.append(live[at]).append('\n')
            if (live[at].endsWith("{")) return builder.toString()
        }
        throw AssertionError("no opening brace after line ${index + 1}")
    }

    private fun indexOfFirst(
        from: Int = 0,
        to: Int = live.size,
        predicate: (String) -> Boolean,
    ): Int = (from until to).firstOrNull { predicate(live[it]) }
        ?: throw AssertionError(
            "no live line matched between ${from + 1} and $to — the pattern drifted, so " +
                "the budget below would silently read zeros and pass vacuously",
        )

    private fun allOf(pattern: String, haystack: String): List<Double> =
        Regex(pattern).findAll(haystack).map { it.groupValues[1].toDouble() }.toList()

    private fun oneOf(pattern: String, haystack: String, label: String): Double {
        val found = allOf(pattern, haystack)
        assertTrue(found.isNotEmpty(), "could not read the $label out of the source")
        return found.first()
    }

    /** Width of one `Text` in dp: `fontSize = 9.sp` → 9, at 0.6 em per glyph. */
    private fun textWidthDp(block: String, copy: String): Double {
        val size = oneOf("fontSize\\s*=\\s*([\\d.]+)\\.sp", block, "fontSize")
        val tracking = allOf("letterSpacing\\s*=\\s*([\\d.]+)\\.sp", block).firstOrNull() ?: 0.0
        // letterSpacing is charged per glyph *pair*, so n glyphs carry n-1 gaps.
        return copy.length * size * monoAdvanceEm + tracking * (copy.length - 1)
    }

    /**
     * The public branch of `text = if (isPrivate) "…" else "…"` — the resting
     * brand, which is what has to fit.
     */
    private fun publicCopyOrNull(line: String): String? =
        Regex("text = if \\(isPrivate\\) \"[^\"]*\" else \"([^\"]*)\"")
            .find(line)?.groupValues?.get(1)

    @Test
    fun theBrandHeaderFitsTheMinimumScreenWidth() {
        val body = indexOfFirst { it.startsWith("fun TahoStartPage(") }

        // ---- the header row, located from the icon calls that end it -------
        // `StartHeaderIcon("…"` matches the three call sites and not the
        // declaration, which is `private fun StartHeaderIcon(glyph: String`.
        val iconCall = indexOfFirst(from = body) { it.startsWith("StartHeaderIcon(\"") }
        val iconRowIndex = indexOfFirst(to = iconCall) { it.startsWith("Row(") }

        // The brand header is the *outermost* Row whose own extent contains the
        // first icon call: brace-matched, not guessed by indentation or by a
        // fixed line offset. The right group (243) is inside the left group
        // (208) inside the header (203), and only 203's braces reach line 246.
        val enclosing = (iconRowIndex downTo body)
            .filter { live[it].startsWith("Row(") }
            .filter { it <= iconCall && iconCall < endOf(it) }
        assertTrue(enclosing.isNotEmpty(), "no Row encloses the header icons — the " +
            "region bounding below would fall through to the whole file")
        val headerStart = enclosing.last()
        val headerEnd = endOf(headerStart)
        val header = live.subList(headerStart, headerEnd)
        val headerText = header.joinToString("\n")

        val iconCount = header.count { it.startsWith("StartHeaderIcon(\"") }
        assertTrue(iconCount > 0, "no header icons in the brand header — the anchor " +
            "drifted and the right-hand group would measure as empty")

        // ---- right group: count x icon size + the gaps between them --------
        val iconSize = oneOf(
            "\\.size\\(([\\d.]+)\\.dp\\)",
            live.subList(
                indexOfFirst { it.startsWith("private fun StartHeaderIcon(") },
                endOf(indexOfFirst { it.startsWith("private fun StartHeaderIcon(") }),
            ).joinToString("\n"),
            "StartHeaderIcon size",
        )
        assertTrue(iconSize > 0, "header icon size read as 0 — the extraction failed")
        val gap = oneOf("spacedBy\\(([\\d.]+)\\.dp\\)", headerText, "icon gap")
        assertTrue(gap > 0, "icon gap read as ${fmt(gap)} — the extraction failed")

        // ---- left group: crest + spacer + the wider of its two Texts -------
        val leftRowIndex = indexOfFirst(from = headerStart + 1, to = headerEnd) { it.startsWith("Row(") }
        val crestSizes = allOf("\\.size\\(([\\d.]+)\\.dp\\)", headerText)
        assertTrue(crestSizes.size == 1, "expected exactly one fixed-size box in the brand " +
            "header, found ${crestSizes.size} (${crestSizes.joinToString { fmt(it) }}) — the " +
            "crest size is read as the first, so an added box would be measured wrongly")
        val crest = crestSizes.first()
        val spacer = oneOf("\\.width\\(([\\d.]+)\\.dp\\)", headerText, "crest spacer")

        // The crest glyph is *also* an `isPrivate` text branch, so the wordmark
        // is located from the left Row's own Column and the two branches read
        // inside it — never by scanning the whole header for the first match.
        val wordmarkColumnIndex = indexOfFirst(
            from = leftRowIndex + 1,
            to = headerEnd,
        ) { it.startsWith("Column") }
        val wordmarkEnd = endOf(wordmarkColumnIndex)
        val copies = (wordmarkColumnIndex until wordmarkEnd)
            .mapNotNull { index -> publicCopyOrNull(live[index])?.let { index to it } }
        assertTrue(
            copies.size == 2,
            "expected the wordmark and its tagline inside the wordmark column, read " +
                "${copies.size} (${copies.joinToString { it.second }}) — those two Texts are " +
                "what the budget below measures, so a drift here makes it meaningless",
        )
        val (wordmarkIndex, wordmarkCopy) = copies[0]
        val (taglineIndex, taglineCopy) = copies[1]

        // Each `Text(` block runs from its `text =` line to the next sibling, so
        // slice to the *next* copy's line rather than guessing an offset.
        val wordmarkBlock = live.subList(wordmarkIndex, taglineIndex).joinToString("\n")
        val taglineBlock = live.subList(taglineIndex, wordmarkEnd).joinToString("\n")
        val wordmarkDp = textWidthDp(wordmarkBlock, wordmarkCopy)
        val taglineDp = textWidthDp(taglineBlock, taglineCopy)

        // ---- the row's own container ---------------------------------------
        // The root column's modifier chain sits immediately above the header,
        // and is the only `padding(horizontal = …)` on the way there.
        val paddings = allOf(
            "padding\\(horizontal = ([\\d.]+)\\.dp",
            live.subList(maxOf(body, headerStart - 20), headerStart).joinToString("\n"),
        )
        assertTrue(paddings.size == 1, "expected exactly one horizontal padding between " +
            "the start page and its header, read ${paddings.size} — the budget below " +
            "would take the wrong one and pass on a row that overflows")
        val horizontalPadding = paddings.single() * 2

        // ---- the assertion --------------------------------------------------
        val rightGroup = iconCount * iconSize + (iconCount - 1) * gap
        val leftGroup = crest + spacer + maxOf(wordmarkDp, taglineDp)
        val total = leftGroup + rightGroup + horizontalPadding
        val available = minScreenWidthDp - horizontalPadding

        assertTrue(
            total <= minScreenWidthDp,
            "the brand header needs ${fmt(total)}dp but a ${minScreenWidthDp}dp screen " +
                "offers ${fmt(available)}dp of content:\n" +
                "  ${fmt(crest)} crest + ${fmt(spacer)} spacer + " +
                "${fmt(maxOf(wordmarkDp, taglineDp))} wordmark column " +
                "(tagline \"$taglineCopy\" ${fmt(taglineDp)}dp, " +
                "wordmark \"$wordmarkCopy\" ${fmt(wordmarkDp)}dp)\n" +
                "  + ${fmt(rightGroup)} icon group ($iconCount x ${fmt(iconSize)} " +
                "+ ${iconCount - 1} x ${fmt(gap)})\n" +
                "  + ${fmt(horizontalPadding)} padding = ${fmt(total)}dp, which is " +
                "${fmt(total - minScreenWidthDp)}dp over.\n" +
                "  Trim the group, shrink a size, or shorten the copy — do not ship it clipped",
        )

        // ---- and it has to be able to *yield*, not merely fit ---------------
        // The arithmetic above is the resting case. A fourth icon on a narrower
        // screen, or a reader at 130% font scale, has to degrade to a truncated
        // tagline — never to an overlap. That needs the left group constrained.
        assertTrue(
            signatureOf(leftRowIndex).contains("weight("),
            "the left header group must be weighted, or it is measured at its full " +
                "width and the fixed-size icon group is laid out on top of it " +
                "(row is: ${signatureOf(leftRowIndex).trim()})",
        )
        assertTrue(
            signatureOf(wordmarkColumnIndex).contains("weight("),
            "the wordmark column must be weighted, or the tagline is measured at " +
                "${taglineCopy.length} glyphs inside a column the Row has already squeezed " +
                "(column is: ${signatureOf(wordmarkColumnIndex).trim()})",
        )

        assertTrue(
            Regex("maxLines\\s*=\\s*1").containsMatchIn(taglineBlock),
            "the tagline needs maxLines = 1 — unconstrained it wraps onto a second " +
                "line and the header grows instead of yielding",
        )
        assertTrue(
            taglineBlock.contains("TextOverflow.Ellipsis"),
            "the tagline needs TextOverflow.Ellipsis — a glyph sliced at the line " +
                "box edge reads worse than a truncated word",
        )

        // The right group is the fixed half of the budget: weighted, the icons
        // would shrink and clip, which is the worse of the two failures.
        val rightRowIndex = iconRowIndex
        assertTrue(
            !signatureOf(rightRowIndex).contains("weight("),
            "the icon group must stay unweighted so the icons never shrink or clip " +
                "(row is: ${signatureOf(rightRowIndex).trim()})",
        )

        println(
            "brand header at ${minScreenWidthDp}dp: ${fmt(total)}dp used of " +
                "${fmt(minScreenWidthDp)}dp, ${fmt(minScreenWidthDp - total)}dp slack " +
                "(left ${fmt(leftGroup)}dp = $crest + $spacer + " +
                "${fmt(maxOf(wordmarkDp, taglineDp))}, right ${fmt(rightGroup)}dp, " +
                "padding ${fmt(horizontalPadding)}dp)"
        )
    }

    private fun fmt(value: Double): String =
        if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
        else "%.1f".format(value)
}