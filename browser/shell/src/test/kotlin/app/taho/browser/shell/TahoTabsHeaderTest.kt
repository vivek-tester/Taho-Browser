package app.taho.browser.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `TahoTabsOverviewSheet` is handed a working `onOpenSettings` by
 * `TahoBrowserApp` (`showTabs = false; settingsInitialSubPage = MAIN;
 * showSettings = true`) and never calls it, so Settings has no route from the
 * tab switcher despite the callback existing. `onCloseOverview` was dropped the
 * same way — declared, passed, ignored.
 *
 * Both are instances of the one defect class this plan exists to close, so the
 * second test below is deliberately general: it fails for *any* declared-and-
 * never-invoked callback on this sheet, not only for the two named here.
 */
class TahoTabsHeaderTest {

    private val tabsFile = File("src/main/java/app/taho/browser/shell/TahoTabsOverview.kt")

    /**
     * The sheet's whole body, live lines only.
     *
     * The anchor assertion is load-bearing: `substringAfter` on a missing anchor
     * returns the entire remainder of the file rather than throwing, and this
     * file's trailing private composables carry their own callbacks, so a lost
     * anchor would make every scan below pass on unrelated code.
     */
    private fun sheetBody(): String {
        val anchor = "fun TahoTabsOverviewSheet("
        val live = tabsFile.readText()
            .lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
            .joinToString("\n")
        assertTrue(
            live.contains(anchor),
            "anchor lost — TahoTabsOverviewSheet was renamed or moved, so every " +
                "scan below would silently widen to the whole file",
        )
        return live.substringAfter(anchor)
    }

    /**
     * Anchored on the header rather than on the file, because "the identifier
     * appears somewhere in the sheet" would be satisfied by the signature — the
     * declaration sits above the search bar too. So the header must carry it
     * *twice*: the parameter and an invocation. One means declared-and-ignored.
     */
    @Test
    fun theHeaderInvokesTheSettingsCallback() {
        val body = sheetBody()
        val header = body.substringBefore("⌕")
        val occurrences = Regex("\\bonOpenSettings\\b").findAll(header).count()
        assertTrue(
            occurrences >= 2,
            "the tab switcher's header must invoke onOpenSettings, not merely " +
                "declare it (saw $occurrences mention(s) above the search bar; " +
                "Settings is unreachable from this surface otherwise)",
        )
        assertTrue(
            Regex("contentDescription\\s*=\\s*\"Settings\"").containsMatchIn(header),
            "the settings control must announce itself as \"Settings\"",
        )
    }

    /**
     * The general form of this plan's four defects: a callback parameter that a
     * composable accepts and never calls. One live occurrence is the signature;
     * the pre-fix sheet had *two* such parameters (`onCloseOverview` and
     * `onOpenSettings`) and this test caught both.
     */
    @Test
    fun noCallbackParameterOfThisSheetIsDeclaredAndNeverInvoked() {
        val body = sheetBody()
        val signature = body.substringBefore(") {")
        val declared = Regex("\\bon[A-Z]\\w*\\s*:").findAll(signature)
            .map { it.value.substringBefore(":").trim() }
            .toList()
        assertTrue(
            declared.isNotEmpty(),
            "no callback parameters parsed out of the signature — the pattern " +
                "or the anchor drifted, and this test would pass vacuously",
        )
        val dead = declared.filter { name ->
            Regex("\\b$name\\b").findAll(body).count() < 2
        }
        assertTrue(
            dead.isEmpty(),
            "declared-and-never-invoked callback parameter(s): ${dead.joinToString()} — " +
                "either wire them or delete them and their call-site arguments",
        )
    }
}