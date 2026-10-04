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
     * spread across seven shell files instead. Replacing them is a design
     * decision, not a rename, so this is a ratchet rather than a zero: the count
     * may only fall. A literal added to any file, waived or not, still fails.
     */
    @Test
    fun noHardcodedColourLiteralsInProductionCode() {
        assertShellSourcesVisible()
        val waived = mutableListOf<String>()
        val unwaived = mutableListOf<String>()
        sources().forEach { file ->
            if (file.name in TOKEN_DEFINING_FILES) return@forEach
            literalColourLines(file).forEach { line ->
                if (file.name in PENDING_LITERAL_CLEANUP) waived += "${file.name}:${line.number}"
                else unwaived += "${file.name}:${line.number}: ${line.text}"
            }
        }
        assertEquals(emptyList(), unwaived, "hardcoded colour literal in shell production code")
        assertTrue(
            waived.size <= KNOWN_LITERAL_BASELINE,
            "literal-colour ratchet moved: ${waived.size} outstanding literals, " +
                "baseline $KNOWN_LITERAL_BASELINE. Tokenise them, or update the " +
                "baseline deliberately -- never by adding to the waiver list.",
        )
        // Tripwire: a waiver naming a file that no longer has literals is dead
        // weight, and would let a later edit re-introduce one invisibly.
        val dead = PENDING_LITERAL_CLEANUP.filter { name ->
            sources().none { it.name == name } ||
                sources().first { it.name == name }.let { literalColourLines(it).isEmpty() }
        }
        assertEquals(emptyList(), dead, "stale entry in PENDING_LITERAL_CLEANUP")
    }

    // ---- helpers -------------------------------------------------------

    private data class TextRoleCall(val name: String, val arguments: String, val line: Int)

    private companion object {
        val TOKEN_DEFINING_FILES = setOf("TahoTheme.kt")

        /** Outstanding literals as of Task 3; see the KDoc on the test for why this is a ratchet. */
        const val KNOWN_LITERAL_BASELINE = 67

        val PENDING_LITERAL_CLEANUP = setOf(
            "M7ProductUx.kt",
            "M7TransferSearch.kt",
            "TahoBrowserApp.kt",
            "TahoPageOverlays.kt",
            "TahoSettingsHub.kt",
            "TahoStartPage.kt",
            "TahoTabsOverview.kt",
        )

        val BANNED_TEXT_COLOUR = Regex("color\\s*=\\s*(ash|stone)\\b")

        val TEXT_ROLE_OPENERS = Regex("(?<![A-Za-z0-9_.])(Text|TextStyle|SpanStyle)\\s*\\(")

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