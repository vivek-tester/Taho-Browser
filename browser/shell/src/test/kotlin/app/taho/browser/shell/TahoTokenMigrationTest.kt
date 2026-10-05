package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 3 guards. These scan the shell's own production sources, which is the
 * only way to catch a leftover literal or a missed remap.
 */
class TahoTokenMigrationTest {

    private val shellDir: File =
        File("src/main/java/app/taho/browser/shell")

    private fun sources(): List<File> =
        shellDir.listFiles { f: File -> f.name.endsWith(".kt") }?.toList() ?: emptyList()

    /** Tripwire: a moved or renamed source directory would make every scan below pass vacuously. */
    private fun assertShellSourcesVisible() {
        assertTrue(
            sources().size >= 8,
            "expected the shell's production sources under ${shellDir.path}, saw ${sources().size}",
        )
    }

    @Test
    fun noLegacyTokenNamesRemain() {
        assertShellSourcesVisible()
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

    /**
     * Review Focus item 2: the failure mode where a well-meaning edit puts a
     * label in a tier that cannot carry a string, and nothing else catches it.
     *
     * ash is 3.25-3.57:1 and stone is 2.51:1 -- both fail WCAG AA 4.5:1. They may
     * appear as a colour argument anywhere, but never as the colour of the three
     * constructs in this shell that actually render glyphs.
     *
     * The check balances parentheses instead of scanning line by line. Compose
     * arguments are almost always one-per-line here, so a line-scoped regex
     * would miss nearly every real occurrence.
     *
     * Limitation: it matches the literal `color = ash` / `color = stone` only.
     * A call that passes an indirect reference -- e.g.
     * `OmniboxLeadingGlyph`'s `Text("◐", color = markColor)` -- escapes this
     * guard entirely, so it does NOT prove no ash-coloured string exists
     * repo-wide. That claim rests on those references resolving to non-ash
     * tokens, which nothing here verifies.
     */
    @Test
    fun ashAndStoneAreNeverUsedOnTextRoles() {
        assertShellSourcesVisible()
        val offenders = mutableListOf<String>()
        sources().forEach { file ->
            val text = file.readText()
            textRoleCalls(text).forEach { call ->
                if (BANNED_TEXT_COLOUR.containsMatchIn(call.arguments)) {
                    offenders += "${file.name}:${call.line}"
                }
            }
        }
        assertEquals(emptyList(), offenders, "ash/stone used as a text colour")
    }

    /**
     * Spec §8 requires this guard. It is the test that would have caught the
     * TahoFaint defect: a colour written inline never passes through the token
     * layer, so it never gets contrast-checked.
     *
     * TahoTheme.kt is exempt because it *defines* the tokens. The brief also
     * exempted "TahoConsole.kt", which does not exist in this module -- the
     * alpha-composited washes the brief expected to find declared there are
     * spread across six shell files instead. Replacing them is a design
     * decision, not a rename, so this is a ratchet rather than a zero.
     *
     * The ratchet is per-file, not a total. A single scalar cannot express "no
     * new debt in any file": neutralising a literal in one waived file would
     * buy budget to add one to another, and the count would not move.
     */
    @Test
    fun noHardcodedColourLiteralsInProductionCode() {
        assertShellSourcesVisible()
        val unwaived = mutableListOf<String>()
        val outstanding = mutableMapOf<String, Int>()
        sources().forEach { file ->
            if (file.name in TOKEN_DEFINING_FILES) return@forEach
            val lines = literalColourLines(file)
            if (lines.isEmpty()) return@forEach
            outstanding[file.name] = lines.size
            if (file.name !in PENDING_LITERAL_CLEANUP) {
                lines.forEach { line -> unwaived += "${file.name}:${line.number}: ${line.text}" }
            }
        }
        assertEquals(emptyList(), unwaived, "hardcoded colour literal in shell production code")
        // Per-file ratchet, naming the file that moved in either direction.
        val drifted = outstanding.keys.sorted().filter {
            KNOWN_LITERAL_BASELINE[it] != outstanding[it]
        }.map { "$it: ${outstanding[it]} literals, baseline ${KNOWN_LITERAL_BASELINE[it]}" }
        assertEquals(
            emptyList(),
            drifted,
            "literal-colour ratchet moved per file. Tokenise the literals and " +
                "lower that file's entry, or raise it deliberately with a reason -- " +
                "never by taking budget from another file's entry, and never by " +
                "adding a literal to hold a count up.",
        )
        // The two structures gate the same set of files; if they drift apart one
        // of them is silently inert.
        assertEquals(
            PENDING_LITERAL_CLEANUP.sorted(),
            KNOWN_LITERAL_BASELINE.keys.sorted(),
            "PENDING_LITERAL_CLEANUP and KNOWN_LITERAL_BASELINE must name the same files",
        )
        // Tripwire: a waiver naming a file that no longer has literals is dead
        // weight, and would let a later edit re-introduce one invisibly.
        val dead = PENDING_LITERAL_CLEANUP.filter { name ->
            sources().none { it.name == name } ||
                sources().first { it.name == name }.let { literalColourLines(it).isEmpty() }
        }
        assertEquals(emptyList(), dead, "stale entry in PENDING_LITERAL_CLEANUP")
    }

    /**
     * Tripwire for the exemption list itself. Adding a production filename to
     * [TOKEN_DEFINING_FILES] would move those literals out of the count
     * entirely, with no baseline change at all -- so the exemption is pinned
     * to the one file that actually defines tokens.
     */
    @Test
    fun onlyTheTokenDefiningFileIsExemptFromTheLiteralGuard() {
        assertEquals(
            setOf("TahoTheme.kt"),
            TOKEN_DEFINING_FILES,
            "only the token-defining file may be exempt from the literal guard",
        )
    }

    // ---- helpers -------------------------------------------------------

    private data class TextRoleCall(val name: String, val arguments: String, val line: Int)

    private companion object {
        /**
         * Pinned by `onlyTheTokenDefiningFileIsExemptFromTheLiteralGuard`: adding a
         * name here moves that file's literals out of the count with no baseline
         * change, which is the same as deleting the guard for that file.
         */
        val TOKEN_DEFINING_FILES = setOf("TahoTheme.kt")

        /**
         * Outstanding literals per file as of Task 3. Each entry pins that file's
         * own debt, so retiring one file's literal cannot buy budget for another's
         * increase. Populated from the measured counts, not estimated.
         *
         * Keys must equal [PENDING_LITERAL_CLEANUP]; the test asserts it.
         */
        val KNOWN_LITERAL_BASELINE = mapOf(
            "M7ProductUx.kt" to 21,
            "M7TransferSearch.kt" to 2,
            "TahoBrowserApp.kt" to 21,
            "TahoPageOverlays.kt" to 10,
            "TahoSettingsHub.kt" to 3,
            "TahoStartPage.kt" to 8,
        )

        val PENDING_LITERAL_CLEANUP = setOf(
            "M7ProductUx.kt",
            "M7TransferSearch.kt",
            "TahoBrowserApp.kt",
            "TahoPageOverlays.kt",
            "TahoSettingsHub.kt",
            "TahoStartPage.kt",
        )

        val BANNED_TEXT_COLOUR = Regex("color\\s*=\\s*(ash|stone)\\b")

        /**
         * The lookbehind deliberately omits `.`: a qualified call such as
         * `androidx.compose.ui.text.TextStyle(` must still be scanned, because
         * that is exactly how a fully-qualified call site slips past. It keeps
         * `[A-Za-z0-9_]` so an identifier like `myText(` is still excluded.
         */
        val TEXT_ROLE_OPENERS = Regex("(?<![A-Za-z0-9_])(Text|TextStyle|SpanStyle)\\s*\\(")

        val LITERAL_PATTERNS = listOf(
            Regex("Color\\(0x[0-9A-Fa-f]{6,8}\\)"),
            Regex("Color\\.(Black|White|Red|Green|Blue|Gray|Yellow)\\b"),
            Regex("toInt\\(\\)\\s*or\\s*0x"),
        )
    }

    /**
     * Every `Text(` / `TextStyle(` / `SpanStyle(` call in [source], with its
     * argument list sliced out of the text and its starting line number.
     * String literals and comments are skipped while counting parentheses so a
     * `")"` inside a caption cannot end a call early.
     */
    private fun textRoleCalls(source: String): List<TextRoleCall> {
        val calls = mutableListOf<TextRoleCall>()
        var opener = TEXT_ROLE_OPENERS.find(source)
        while (opener != null) {
            val open = source.indexOf('(', opener.range.first)
            var depth = 0
            var i = open
            var inString = false
            var inLineComment = false
            var inBlockComment = false
            while (i < source.length) {
                val c = source[i]
                val next = if (i + 1 < source.length) source[i + 1] else ' '
                when {
                    inLineComment -> if (c == '\n') inLineComment = false
                    inBlockComment -> if (c == '*' && next == '/') { inBlockComment = false; i++ }
                    inString -> when {
                        c == '\\' -> i++
                        c == '"' -> inString = false
                    }
                    c == '"' -> inString = true
                    c == '/' && next == '/' -> { inLineComment = true; i++ }
                    c == '/' && next == '*' -> { inBlockComment = true; i++ }
                    c == '(' -> depth++
                    c == ')' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                i++
            }
            if (depth > 0) break // unterminated source; stop rather than guess
            calls += TextRoleCall(
                name = opener.groupValues[1],
                arguments = source.substring(open + 1, i),
                line = source.take(open).count { ch -> ch == '\n' } + 1,
            )
            opener = TEXT_ROLE_OPENERS.find(source, opener.range.first + 1)
        }
        return calls
    }

    private data class LiteralLine(val number: Int, val text: String)

    private fun literalColourLines(file: File): List<LiteralLine> =
        file.readLines().mapIndexedNotNull { i, line ->
            val isLiteral = LITERAL_PATTERNS.any { it.containsMatchIn(line) }
            val trimmed = line.trimStart()
            // Ignore comments: a literal quoted in prose is documentation.
            if (!isLiteral || trimmed.startsWith("//") || trimmed.startsWith("*")) null
            else LiteralLine(i + 1, trimmed)
        }
}
