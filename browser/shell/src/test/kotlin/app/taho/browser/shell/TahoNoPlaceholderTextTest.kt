package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A placeholder word must never reach the screen.
 *
 * `SettingsBookmarksPage` rendered every folder chip as the literal
 * `"📁 undefined"`: the folder's name was never interpolated, so
 * `BookmarkFolderItem.name` was simply absent from the UI. Nothing
 * failed, nothing warned, and the string type was happy — a `Text` whose
 * argument was a finished literal rather than a template.
 *
 * This guards the whole class rather than the one site. A word like
 * `undefined`, `null`, `NaN` or `TODO` inside a `Text` string literal is
 * always a bug: a real value would have been interpolated, and a
 * deliberate word would not be one of these. Kotlin will not catch it —
 * `"${x}"` and a hardcoded string are the same type — so nothing but a
 * test can.
 *
 * ## Why the scan filters comments
 *
 * The first version of this test flagged its own explanatory comment,
 * which quotes the offending string verbatim. Every source-scanning test
 * in this series has now been defeated by text that is present but not
 * live: a commented-out control satisfied a raw occurrence count, and a
 * KDoc body satisfied an anchor. So lines are filtered to **live code
 * only** before any literal is read — a comment that documents the bug
 * must not be able to re-trigger it.
 */
class TahoNoPlaceholderTextTest {

    private val words = Regex("\\b(undefined|null|NaN|TODO|FIXME)\\b")

    private val sourceDir = File("src/main/java/app/taho/browser/shell")

    /**
     * Every string literal inside a `Text(` call's argument list, with the
     * file and line it came from.
     *
     * A `Text(` call is gathered over the lines that follow it up to its
     * closing paren, because this codebase writes them both inline
     * (`Text("…", color = …)`) and across eight or more lines
     * (`Text(\n text = "…",\n color = …,\n)`).
     */
    private fun literalsInTextCalls(): List<Pair<String, String>> {
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val found = mutableListOf<Pair<String, String>>()

        sourceDir.listFiles { f: File -> f.name.endsWith(".kt") }?.forEach { file ->
            val lines = file.readText().lines()
            // Live code only — see the class comment. A trailing `//` is
            // stripped too, so `color = x // "undefined"` cannot trip it.
            val live = lines.map { line ->
                line.substringBefore("//").trim()
            }.filterNot {
                it.startsWith("*") || it.startsWith("/*") || it.isEmpty()
            }

            live.forEachIndexed { index, line ->
                if (!line.contains("Text(")) return@forEachIndexed
                val block = live
                    .drop(index)
                    .take(10)
                    .takeWhile { !it.startsWith(")") }
                    .joinToString("\n")
                literal.findAll(block).forEach { match ->
                    found += file.name to match.groupValues[1]
                }
            }
        }
        return found
    }

    @Test
    fun noTextCallRendersAPlaceholderWord() {
        val offenders = literalsInTextCalls()
            .filter { (_, literal) -> words.containsMatchIn(literal) }
            .map { (file, literal) -> "$file: \"$literal\"" }

        assertEquals(
            emptyList(),
            offenders,
            "a Text() argument is a hardcoded placeholder word rather than an " +
                "interpolated value, so the user sees it verbatim",
        )
    }

    /**
     * The specific regression, pinned separately from the class guard.
     *
     * `BookmarkFolderItem.name` exists (`TahoModels.kt:42`) and the chip
     * ignored it. Asserting on the interpolated form catches a reversion
     * that merely renames the placeholder word.
     */
    @Test
    fun bookmarkFolderChipsShowTheFolderName() {
        val hub = File("src/main/java/app/taho/browser/shell/TahoSettingsHub.kt")
            .readText()
            .lineSequence()
            .map { it.substringBefore("//").trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.isEmpty() }
            .toList()

        val folderLoop = hub.indexOfFirst { it.startsWith("folders.forEach") }
        assertTrue(
            folderLoop >= 0,
            "the folder loop is gone, so this test is measuring nothing",
        )

        val chip = hub.drop(folderLoop).take(20).joinToString("\n")
        assertTrue(
            Regex("\\.name").containsMatchIn(chip),
            "the folder chip must interpolate the folder's name; found no " +
                "`.name` reference within 20 lines of the loop head",
        )
    }
}