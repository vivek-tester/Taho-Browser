package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `onOpenTabs` is passed live from TahoBrowserApp — `{ showTabs = true }` — into
 * a parameter the start page never reads, so the new-tab page had no route to
 * the tab switcher at all.
 *
 * The scan is on production source because the defect *is* the reachability of a
 * declared handler. Three guards, each paid for by a failure in an earlier task:
 *
 *  1. anchored on `fun TahoStartPage(`, so nothing above the composable — the
 *     KDoc above it, anything an editor leaves behind — can satisfy the count;
 *  2. counted over **live lines only**, so a control that is present but
 *     commented out is invisible to it;
 *  3. counted with `>= 2`, because one live occurrence is the parameter
 *     declaration and nothing else, which is exactly the pre-fix state.
 *
 * The body is also bounded at the next top-level declaration: this file holds
 * four more private composables after `TahoStartPage` (`StartHeaderIcon`,
 * `StartShortcutTile`, `StartWidgetToggle`, `ShieldMetric`), and an unbounded
 * `substringAfter` would let one of them satisfy the count on the start page's
 * behalf.
 */
class TahoStartPageRouteTest {

    /** Line comments and KDoc bodies removed. Only a live line can wire a control. */
    private val live: String =
        File("src/main/java/app/taho/browser/shell/TahoStartPage.kt")
            .readText()
            .lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
            .joinToString("\n")

    private fun startPageBody(): String {
        val anchor = "fun TahoStartPage("
        assertTrue(
            live.contains(anchor),
            "anchor lost — TahoStartPage was renamed or moved, so the scan below " +
                "would silently widen to the whole file and pass on unrelated code",
        )
        val body = live.substringAfter(anchor)
        val end = listOf("\n@Composable", "\nprivate fun ", "\ninternal fun ", "\nfun ")
            .map { body.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: body.length
        return body.substring(0, end)
    }

    @Test
    fun theStartPageInvokesItsTabSwitcherRoute() {
        val occurrences = Regex("\\bonOpenTabs\\b").findAll(startPageBody()).count()
        assertTrue(
            occurrences >= 2,
            "onOpenTabs must be declared AND invoked, not merely declare it " +
                "(saw $occurrences live occurrence(s); 1 means declaration only)",
        )
    }
}
