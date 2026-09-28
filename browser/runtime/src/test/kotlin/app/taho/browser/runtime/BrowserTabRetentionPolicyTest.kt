package app.taho.browser.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserTabRetentionPolicyTest {
    @Test
    fun stalePolicyNeverClosesSelectedOrPinnedTabs() {
        val stale = BrowserTabRetentionPolicy.staleTabIds(
            tabs = listOf(
                BrowserTabRetentionCandidate("selected", 1L, selected = true, pinned = false),
                BrowserTabRetentionCandidate("pinned", 1L, selected = false, pinned = true),
                BrowserTabRetentionCandidate("stale", 1L, selected = false, pinned = false),
                BrowserTabRetentionCandidate("fresh", 100L, selected = false, pinned = false),
            ),
            olderThanEpochMs = 50L,
        )

        assertEquals(listOf("stale"), stale)
    }
}
