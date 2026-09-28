package app.taho.browser.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserNavigationPolicyTest {
    @Test
    fun ordinaryHttpNavigationStaysInBrowser() {
        val decision = BrowserNavigationPolicy.decide(
            uri = "https://example.com/path",
            targetNewWindow = false,
            hasUserGesture = false,
            isRedirect = false,
            externalRequestPending = false,
        )
        assertEquals(BrowserNavigationDisposition.ALLOW_IN_BROWSER, decision.disposition)
    }

    @Test
    fun userInitiatedBlankTargetCreatesBrowserTab() {
        val decision = BrowserNavigationPolicy.decide(
            uri = "https://example.com/new",
            targetNewWindow = true,
            hasUserGesture = true,
            isRedirect = false,
            externalRequestPending = false,
        )
        assertEquals(BrowserNavigationDisposition.OPEN_NEW_TAB, decision.disposition)
    }

    @Test
    fun popupWithoutUserGestureIsDenied() {
        val decision = BrowserNavigationPolicy.decide(
            uri = "https://example.com/popup",
            targetNewWindow = true,
            hasUserGesture = false,
            isRedirect = false,
            externalRequestPending = false,
        )
        assertEquals(BrowserNavigationDisposition.DENY, decision.disposition)
    }

    @Test
    fun allowlistedExternalSchemeNeedsUserGestureAndNoRedirect() {
        val allowed = BrowserNavigationPolicy.decide(
            uri = "mailto:person@example.com",
            targetNewWindow = false,
            hasUserGesture = true,
            isRedirect = false,
            externalRequestPending = false,
        )
        val redirected = BrowserNavigationPolicy.decide(
            uri = "mailto:person@example.com",
            targetNewWindow = false,
            hasUserGesture = true,
            isRedirect = true,
            externalRequestPending = false,
        )
        assertEquals(BrowserNavigationDisposition.REQUEST_EXTERNAL_APP, allowed.disposition)
        assertEquals(BrowserNavigationDisposition.DENY, redirected.disposition)
    }

    @Test
    fun intentSchemeAllowedWithUserGestureAndDeniedOnRedirect() {
        val allowed = BrowserNavigationPolicy.decide(
            uri = "intent://scan/#Intent;scheme=zxing;end",
            targetNewWindow = false,
            hasUserGesture = true,
            isRedirect = false,
            externalRequestPending = false,
        )
        val redirected = BrowserNavigationPolicy.decide(
            uri = "intent://scan/#Intent;scheme=zxing;end",
            targetNewWindow = false,
            hasUserGesture = true,
            isRedirect = true,
            externalRequestPending = false,
        )
        assertEquals(BrowserNavigationDisposition.REQUEST_EXTERNAL_APP, allowed.disposition)
        assertEquals(BrowserNavigationDisposition.DENY, redirected.disposition)
    }

    @Test
    fun arbitraryAndScriptSchemesFailClosed() {
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/secret.txt",
            "data:text/html,<h1>evil</h1>",
            "custom-scheme://payload",
        ).forEach { uri ->
            val decision = BrowserNavigationPolicy.decide(
                uri = uri,
                targetNewWindow = false,
                hasUserGesture = true,
                isRedirect = false,
                externalRequestPending = false,
            )
            assertEquals(BrowserNavigationDisposition.DENY, decision.disposition, uri)
        }
    }

    @Test
    fun permissionOriginContainsOnlySchemeHostAndPort() {
        assertEquals(
            "https://example.com:8443",
            BrowserNavigationPolicy.displayOrigin(
                "https://user:pass@example.com:8443/private?q=secret#fragment",
            ),
        )
    }

    @Test
    fun malformedPermissionOriginDoesNotEchoUntrustedValue() {
        assertEquals(
            "Unknown origin",
            BrowserNavigationPolicy.displayOrigin("not a valid origin?token=secret"),
        )
    }
}
