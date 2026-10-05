package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A shortcut the user cannot take off the page is a permanent row.
 * `onTogglePin` and `onRemove` were declared parameters of
 * `StartShortcutTile` that the composable never referenced, so the two live
 * lambdas passed at its call site — `TahoBrowserStateStore.togglePinTopSite`
 * and `.removeTopSite` — were unreachable from the UI. Only `onClick` was wired.
 *
 * These assert on production source because the defect *is* the reachability of
 * a declared handler. A test that re-derives the rule inside itself cannot fail
 * for any production change, and a raw text count can be defeated by commenting
 * a control out while leaving the identifier sitting in the file. So every scan
 * here drops line comments and KDoc bodies first, and every count demands the
 * identifier *twice* — declared once, used once.
 */
class TahoShortcutLifecycleTest {

    private val startPage =
        File("src/main/java/app/taho/browser/shell/TahoStartPage.kt").readText()

    /**
     * Line comments and KDoc bodies removed. A mention of a control in prose is
     * documentation; only a live line can wire one.
     */
    private val live: String = startPage
        .lineSequence()
        .map { it.trim() }
        .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
        .joinToString("\n")

    /**
     * `StartShortcutTile`'s own body, bounded at the next top-level composable so
     * nothing downstream can satisfy a scan.
     *
     * The anchor assertion is load-bearing. `substringAfter` on a missing anchor
     * returns the whole remainder of the file rather than throwing, and in that
     * remainder the call site carries its own `onTogglePin = { … }` and
     * `onRemove = { … }` — which would make every count here pass vacuously.
     */
    private fun tileBody(): String {
        val anchor = "private fun StartShortcutTile("
        assertTrue(
            live.contains(anchor),
            "anchor lost — StartShortcutTile was renamed or moved, so every scan " +
                "below would silently widen to the whole file",
        )
        return live.substringAfter(anchor).substringBefore("\nprivate fun ")
    }

    /**
     * The argument lists passed to `StartShortcutTile(...)`, declaration included.
     *
     * Deliberately *not* index-pinned. The declaration and the call site are both
     * spelled the same and their order is an accident of where the composable
     * sits in the file, so the only stable question is: does *some* invocation
     * hand over the live store call.
     */
    private fun invocations(): List<String> {
        val matches = Regex("StartShortcutTile\\(").findAll(live).toList()
        assertTrue(
            matches.size >= 2,
            "expected a call site as well as the declaration, saw ${matches.size} " +
                "mention(s) — the tile may no longer be composed at all",
        )
        return matches.map { match ->
            val from = match.range.last
            live.substring(from, (from + 600).coerceAtMost(live.length))
        }
    }

    private fun intLiteralOf(pattern: String, body: String): Int =
        Regex(pattern).findAll(body)
            .map { it.groupValues[1].toInt() }
            .maxOrNull() ?: 0

    /**
     * Declared *and* used, end to end: the composable must invoke the parameter
     * and the call site must still be feeding it a live store call. One live
     * occurrence in the tile is the signature alone, which is the pre-fix state.
     */
    @Test
    fun theTileInvokesItsRemoveHandler() {
        val occurrences = Regex("\\bonRemove\\b").findAll(tileBody()).count()
        assertTrue(
            occurrences >= 2,
            "StartShortcutTile must invoke onRemove, not merely declare it " +
                "(saw $occurrences live occurrence(s); 1 means declaration only)",
        )
        assertTrue(
            invocations().any { it.contains("TahoBrowserStateStore.removeTopSite") },
            "removeTopSite must still be wired at StartShortcutTile's call site, " +
                "or invoking onRemove would reach nothing",
        )
    }

    @Test
    fun theTileInvokesItsPinToggle() {
        val occurrences = Regex("\\bonTogglePin\\b").findAll(tileBody()).count()
        assertTrue(
            occurrences >= 2,
            "StartShortcutTile must invoke onTogglePin, not merely declare it " +
                "(saw $occurrences live occurrence(s); 1 means declaration only)",
        )
        assertTrue(
            invocations().any { it.contains("TahoBrowserStateStore.togglePinTopSite") },
            "togglePinTopSite must still be wired at StartShortcutTile's call site, " +
                "or invoking onTogglePin would reach nothing",
        )
    }

    /**
     * Removal must be *offered*, not merely possible. A long press alone is a
     * hidden gesture nobody can discover, and a long press labelled only for a
     * screen reader is still hidden to a sighted finger. Three things have to
     * exist together for the feature to be real: a named Remove control, an
     * accessibility label on the long press, and the pin state in the name.
     */
    @Test
    fun removalIsOfferedAndThePinGestureIsAnnounced() {
        val tile = tileBody()
        assertTrue(
            Regex("contentDescription\\s*=\\s*\"Remove").containsMatchIn(tile),
            "a visible remove control must announce itself as \"Remove …\" — a " +
                "long press with no visible affordance is not discoverable",
        )
        assertTrue(
            tile.contains("onLongClickLabel"),
            "the unpin gesture must carry an accessibility label, or it exists " +
                "only for fingers that already know about it",
        )
        assertTrue(
            tile.contains("\", pinned\""),
            "the pinned state must appear in the semantics label so the announced " +
                "name says what a long press will do",
        )
    }

    /**
     * The plan requires every fixed control to be reachable by touch at ≥48dp.
     * The tile is 72dp wide and 52dp of that is the glyph box, so a remove
     * control cannot overlap the tile's own target: it has to occupy its own
     * band. This asserts the band exists and is large enough.
     *
     * Scoped to the tile rather than the file so it cannot be satisfied by some
     * unrelated control elsewhere in TahoStartPage.kt.
     */
    @Test
    fun theRemoveControlMeetsTheMinimumTouchTarget() {
        val tile = tileBody()
        val width = intLiteralOf("\\.width\\((\\d+)\\.dp\\)", tile)
        val height = intLiteralOf("\\.height\\((\\d+)\\.dp\\)", tile)
        assertTrue(
            width >= 48 && height >= 48,
            "the remove control must declare a ≥48dp × ≥48dp hit area; the tile " +
                "declares ${width}dp wide and at most ${height}dp tall",
        )
    }
}