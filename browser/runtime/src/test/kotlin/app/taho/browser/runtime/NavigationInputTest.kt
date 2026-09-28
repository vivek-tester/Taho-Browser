package app.taho.browser.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationInputTest {
    @Test
    fun blankInputDoesNothing() {
        assertNull(NavigationInput.resolve("   "))
    }

    @Test
    fun preservesHttpAndHttpsUrls() {
        assertEquals(
            "https://example.com/a?b=1",
            NavigationInput.resolve("https://example.com/a?b=1"),
        )
        assertEquals(
            "http://localhost:8080/test",
            NavigationInput.resolve("http://localhost:8080/test"),
        )
    }

    @Test
    fun hostLikeInputGetsHttps() {
        assertEquals(
            "https://example.com/path",
            NavigationInput.resolve("example.com/path"),
        )
        assertEquals(
            "https://localhost:3000",
            NavigationInput.resolve("localhost:3000"),
        )
    }

    @Test
    fun plainWordsBecomeSearches() {
        assertEquals(
            "https://www.google.com/search?q=taho%20browser",
            NavigationInput.resolve("taho browser"),
        )
    }

    @Test
    fun mailtoInputIsSearchedInsteadOfBeingTreatedAsAHost() {
        assertEquals(
            "https://www.google.com/search?q=mailto%3Aperson%40example.com",
            NavigationInput.resolve("mailto:person@example.com"),
        )
    }

    @Test
    fun scriptSchemesAreNotNavigatedDirectly() {
        assertEquals(
            "https://www.google.com/search?q=javascript%3Aalert%281%29",
            NavigationInput.resolve("javascript:alert(1)"),
        )
    }

    @Test
    fun aboutBlankIsAllowedForTheInternalEmptyTab() {
        assertEquals("about:blank", NavigationInput.resolve("about:blank"))
    }

    @Test
    fun selectedSearchEngineTemplateIsUsedForQueries() {
        assertEquals(
            "https://duckduckgo.com/?q=taho%20browser",
            NavigationInput.resolve(
                "taho browser",
                searchUrlTemplate = "https://duckduckgo.com/?q=%s",
            ),
        )
    }

    @Test
    fun unsafeOrMalformedSearchTemplateFallsBackToDefault() {
        assertEquals(
            "https://www.google.com/search?q=taho",
            NavigationInput.resolve(
                "taho",
                searchUrlTemplate = "http://search.invalid/?q=%s",
            ),
        )
        assertEquals(
            "https://www.google.com/search?q=taho",
            NavigationInput.resolve(
                "taho",
                searchUrlTemplate = "https://search.invalid/no-placeholder",
            ),
        )
    }
}
