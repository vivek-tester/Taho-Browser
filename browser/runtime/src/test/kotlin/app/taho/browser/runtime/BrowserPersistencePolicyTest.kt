package app.taho.browser.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserPersistencePolicyTest {
    @Test
    fun privateTabsAndTheirSessionStateNeverReachDiskModel() {
        val state = BrowserPersistencePolicy.stateForDisk(
            tabs = listOf(
                BrowserPersistableTab(
                    id = "normal",
                    title = "Normal",
                    location = "https://example.com",
                    serializedSessionState = "normal-state",
                    isPrivate = false,
                    lastAccessedAtEpochMs = 77L,
                ),
                BrowserPersistableTab(
                    id = "private",
                    title = "Private",
                    location = "https://private.example",
                    serializedSessionState = "private-secret-state",
                    isPrivate = true,
                ),
            ),
            selectedTabId = "private",
        )

        assertEquals(listOf("normal"), state.tabs.map { it.id })
        assertEquals("normal-state", state.tabs.single().serializedSessionState)
        assertEquals(77L, state.tabs.single().lastAccessedAtEpochMs)
        assertNull(state.selectedTabId)
    }

    @Test
    fun selectedNormalTabAndOrderArePreserved() {
        val state = BrowserPersistencePolicy.stateForDisk(
            tabs = listOf(
                BrowserPersistableTab("a", null, "https://a.test", "state-a", false),
                BrowserPersistableTab("b", null, "https://b.test", "state-b", false),
            ),
            selectedTabId = "b",
        )

        assertEquals(listOf("a", "b"), state.tabs.map { it.id })
        assertEquals("b", state.selectedTabId)
    }
}
