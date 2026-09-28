package app.taho.browser.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserSessionCodecTest {
    @Test
    fun normalSessionStateRoundTripsAsOpaqueGeckoState() {
        val original = PersistedBrowserState(
            tabs = listOf(
                PersistedBrowserTab(
                    id = "tab-1",
                    title = "Example",
                    location = "https://example.com",
                    serializedSessionState = "{opaque-gecko-state}",
                    lastAccessedAtEpochMs = 123456789L,
                ),
            ),
            selectedTabId = "tab-1",
        )

        assertEquals(original, BrowserSessionCodec.decode(BrowserSessionCodec.encode(original)))
    }

    @Test
    fun malformedAndUnknownSchemaStateFailClosed() {
        assertNull(BrowserSessionCodec.decode("not-json"))
        assertNull(
            BrowserSessionCodec.decode(
                """{"version":99,"tabs":[],"selectedTabId":null}""",
            ),
        )
    }

    @Test
    fun duplicateTabsAreDroppedAndInvalidSelectionIsCleared() {
        val decoded = BrowserSessionCodec.decode(
            """{
              "version":1,
              "tabs":[
                {"id":"same","title":"first","location":"https://a.test","sessionState":"a"},
                {"id":"same","title":"second","location":"https://b.test","sessionState":"b"}
              ],
              "selectedTabId":"missing"
            }""".trimIndent(),
        )!!

        assertEquals(1, decoded.tabs.size)
        assertEquals("first", decoded.tabs.single().title)
        assertNull(decoded.selectedTabId)
    }

    @Test
    fun versionOneStateRemainsReadableWithoutActivityTimestamp() {
        val decoded = BrowserSessionCodec.decode(
            """{
              "version":1,
              "tabs":[
                {"id":"legacy","title":"Legacy","location":"https://legacy.test","sessionState":"state"}
              ],
              "selectedTabId":"legacy"
            }""".trimIndent(),
        )!!

        assertEquals("legacy", decoded.selectedTabId)
        assertNull(decoded.tabs.single().lastAccessedAtEpochMs)
    }
}
