package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A destructive control must not be one tap deep.
 *
 * `StartShortcutTile` renders `× Remove` as a **sibling immediately below** the
 * tile's own label — 6dp under an ellipsised 9.5sp title, inside a 72dp-wide
 * tile — so a finger aimed loosely at the shortcut lands on Remove. There is no
 * confirmation and no undo: `TahoBrowserStateStore.removeTopSite` is an
 * unfiltered `filterNot { it.id == id }`, and re-adding mints a fresh UUID, so
 * the tile is recoverable only by retyping the address.
 *
 * The fix reuses the two-step pattern already in this codebase — `M7ProductUx.kt`
 * swaps a secondary button for a question plus Cancel and a primary action,
 * held in a `confirming…` flag. This asserts that shape *structurally*, by
 * locating the `if (!confirming…) { … } else { … }` and checking which branch
 * `onRemove` sits in. A count over the whole tile cannot tell "declared and
 * invoked once, behind a guard" from "invoked on every tap", which is the defect.
 *
 * Every scan is over **live lines only**, and every region is brace-matched from
 * an asserted anchor: a commented-out control leaves its identifier in the file,
 * and `onRemove = { … }` at the call site sits upstream of the tile body, so an
 * unbounded or non-live scan would find the handler "invoked" and pass vacuously.
 */
class TahoShortcutRemovalConfirmTest {

    private val live: List<String> = File("src/main/java/app/taho/browser/shell/TahoStartPage.kt")
        .readText()
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
     * Brace matching rather than a `substringBefore("\nprivate fun ")` boundary:
     * a string boundary cannot tell the end of an `else` branch from the end of a
     * lambda nested inside it, which is exactly the distinction under test.
     */
    private fun endOf(from: Int, startDepth: Int = 0): Int {
        var depth = startDepth
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
        throw AssertionError("unbalanced braces from line ${from + 1}")
    }

    /**
     * `StartShortcutTile`'s own lines.
     *
     * The anchor assertion is load-bearing. `substringAfter` on a missing anchor
     * returns the whole remainder of the file rather than throwing, and in that
     * remainder the *call site* carries `onRemove = { … }` — which would make
     * every scan below pass on the very identifier it is testing.
     */
    private fun tile(): IntRange {
        val anchor = live.indexOfFirst { it.startsWith("private fun StartShortcutTile(") }
        assertTrue(
            anchor >= 0,
            "anchor lost — StartShortcutTile was renamed or moved, so every scan " +
                "below would silently widen to the whole file",
        )
        return anchor until endOf(anchor)
    }

    /**
     * The two branch ranges of the confirmation guard, as `from until to`.
     *
     * Scoped to [tile] and asserted unique: a guard that could not be found
     * leaves `resting` empty, and an empty range matches zero `onRemove` — the
     * test would then pass on a tile that deletes on every tap.
     */
    private fun branches(inside: IntRange): Pair<IntRange, IntRange> {
        val guard = inside.firstOrNull {
            Regex("if \\(!confirming\\w*\\)").containsMatchIn(live[it])
        }
        assertTrue(
            guard != null,
            "expected an `if (!confirming…)` guard inside StartShortcutTile — the " +
                "two-step pattern this reuses (M7ProductUx's `confirmingClear`) is " +
                "what makes removal safe, and without it the band deletes on one tap",
        )
        // The line that closes the then-branch. It has to be found by watching
        // depth go to zero, NOT with endOf(): on a `} else {` line the depth dips
        // to 0 and returns to 1 within that same line, so endOf's end-of-line
        // `depth == 0` test never fires there and it walks straight past the
        // whole if/else. That is why this scans for the transient zero.
        var depth = 1 // the guard's own `{`
        var closedLine = -1
        for (at in guard + 1 until inside.last + 1) {
            var reachedZero = false
            for (ch in withoutStrings(live[at])) {
                when (ch) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) reachedZero = true
                    }
                }
            }
            if (reachedZero) {
                closedLine = at
                break
            }
        }
        assertTrue(
            closedLine >= 0 && live[closedLine].contains("else"),
            "line ${guard + 1}'s guard has no `} else {` (closed on line " +
                "${closedLine + 1}) — a confirmation that cannot be cancelled or " +
                "confirmed is not a two-step pattern",
        )
        // The else block's own `{` sits on the `} else {` line, so the scan below
        // resumes at depth 1 rather than looking for a fresh opening brace.
        return ((guard + 1) until closedLine) to
            ((closedLine + 1) until endOf(closedLine + 1, startDepth = 1))
    }

    private fun IntRange.text(): String = joinToString("\n") { live[it] }

    private fun IntRange.lines(): String = "${first + 1}..${last + 1}"

    @Test
    fun removingAShortcutRequiresConfirmation() {
        val tile = tile()
        val (resting, confirming) = branches(tile)

        assertTrue(
            !resting.isEmpty(),
            "the resting branch is empty — the tile would have no visible Remove " +
                "affordance, which is the defect this series opened with",
        )
        assertTrue(
            !confirming.isEmpty(),
            "the confirmation branch is empty — there is nothing to confirm with",
        )

        // The defect: reachable from the resting branch, i.e. on a single tap.
        assertTrue(
            Regex("\\bonRemove\\b").findAll(resting.text()).count() == 0,
            "onRemove is reachable from the RESTING branch (lines ${resting.lines()}) " +
                "— one tap destroys the shortcut with no confirmation and no undo. " +
                "Only the confirm control may call it",
        )

        // And the fix: reachable at all, inside the guard.
        assertTrue(
            Regex("\\bonRemove\\b").findAll(confirming.text()).count() >= 1,
            "onRemove is never invoked in the confirmation branch (lines " +
                "${confirming.lines()}) — removal has become unreachable, which is " +
                "the same defect class in reverse",
        )

        // A named cancel path and a named confirm path, both announced: `role` plus
        // a distinct `contentDescription`, so a screen reader says which it is on.
        assertTrue(
            Regex("role\\s*=\\s*Role\\.Button").containsMatchIn(confirming.text()),
            "the confirmation controls must declare role = Role.Button",
        )
        for (label in listOf("Cancel", "Confirm")) {
            assertTrue(
                Regex("contentDescription\\s*=\\s*\"$label").containsMatchIn(confirming.text()),
                "the $label control must announce itself with a contentDescription " +
                    "starting \"$label\" — two controls the reader cannot tell apart " +
                    "are worse than one",
            )
        }

        // The resting affordance stays: removal must not become gesture-only,
        // which is what the original fix was for.
        assertTrue(
            Regex("contentDescription\\s*=\\s*\"Remove").containsMatchIn(resting.text()),
            "the resting state must still offer a visible Remove affordance, " +
                "announced as \"Remove …\"",
        )

        // No stale flag in a composable a lazy list may recycle onto a different
        // shortcut: the flag has to be keyed on the shortcut's identity.
        assertTrue(
            Regex("remember\\w*\\(item\\.id\\)").containsMatchIn(tile.text()),
            "the confirming flag must be keyed on item.id — a plain `remember` " +
                "survives a recycled slot, so scrolling one shortcut into " +
                "confirming would arm a *different* shortcut's deletion",
        )
    }
}