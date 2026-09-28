package app.taho.browser

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.taho.browser.runtime.GeckoRuntimeHolder
import java.net.URI
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

/**
 * Standalone runtime for user-installed web apps discovered through Gecko's
 * validated Web App Manifest callback. It intentionally has no browser chrome.
 */
class TahoPwaActivity : ComponentActivity() {
    private lateinit var session: GeckoSession
    private lateinit var geckoView: GeckoView
    private var canGoBack = false
    private var scope: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startUrl = intent.getStringExtra(EXTRA_START_URL)
            ?.takeIf(::isHttpUrl)
            ?: intent.dataString?.takeIf(::isHttpUrl)
            ?: run {
                finish()
                return
            }
        scope = intent.getStringExtra(EXTRA_SCOPE)?.takeIf(::isHttpUrl)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyManifestTheme(intent.getStringExtra(EXTRA_THEME_COLOR))
        applyDisplayMode(intent.getStringExtra(EXTRA_DISPLAY))

        geckoView = GeckoView(this)
        setContentView(geckoView)

        session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .useTrackingProtection(true)
                .build(),
        )
        attachDelegates()
        session.open(GeckoRuntimeHolder.get(this))
        geckoView.setSession(session)
        session.setPrintDelegate(geckoView.printDelegate)

        if (savedInstanceState == null) {
            session.loadUri(startUrl)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (canGoBack) session.goBack() else finish()
                }
            },
        )
    }

    private fun attachDelegates() {
        session.setNavigationDelegate(
            object : GeckoSession.NavigationDelegate {
                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest,
                ): GeckoResult<AllowOrDeny>? {
                    val url = request.uri
                    if (!isHttpUrl(url)) {
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }

                    val configuredScope = scope
                    if (configuredScope != null && !isWithinScope(url, configuredScope)) {
                        startActivity(
                            Intent(this@TahoPwaActivity, MainActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .setData(Uri.parse(url)),
                        )
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }

                    if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                        startActivity(
                            Intent(this@TahoPwaActivity, MainActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .setData(Uri.parse(url)),
                        )
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    return null
                }

                override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                    canGoBack = value
                }
            },
        )

        session.setContentDelegate(
            object : GeckoSession.ContentDelegate {
                override fun onCloseRequest(session: GeckoSession) {
                    finish()
                }

                override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    if (fullScreen) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                override fun onCrash(session: GeckoSession) {
                    finish()
                }

                override fun onKill(session: GeckoSession) {
                    finish()
                }
            },
        )
    }

    private fun applyDisplayMode(display: String?) {
        if (display.equals("fullscreen", ignoreCase = true)) {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyManifestTheme(raw: String?) {
        val color = raw?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: return
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = color
            window.navigationBarColor = color
        }
    }

    override fun onDestroy() {
        if (::geckoView.isInitialized && geckoView.session != null) {
            geckoView.releaseSession()
        }
        if (::session.isInitialized) {
            session.setPrintDelegate(null)
            runCatching { session.close() }
        }
        super.onDestroy()
    }

    private fun isHttpUrl(raw: String): Boolean =
        runCatching {
            val uri = URI(raw)
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank()
        }.getOrDefault(false)

    private fun isWithinScope(candidate: String, allowedScope: String): Boolean =
        runCatching {
            val url = URI(candidate)
            val scopeUri = URI(allowedScope)
            val sameOrigin =
                url.scheme.equals(scopeUri.scheme, ignoreCase = true) &&
                    url.host.equals(scopeUri.host, ignoreCase = true) &&
                    effectivePort(url) == effectivePort(scopeUri)
            if (!sameOrigin) return@runCatching false

            val scopePath = scopeUri.path.orEmpty().ifBlank { "/" }
            val candidatePath = url.path.orEmpty().ifBlank { "/" }
            candidatePath.startsWith(scopePath)
        }.getOrDefault(false)

    private fun effectivePort(uri: URI): Int =
        if (uri.port >= 0) uri.port
        else if (uri.scheme.equals("https", true)) 443
        else 80

    companion object {
        const val EXTRA_PWA_ID = "app.taho.browser.extra.PWA_ID"
        const val EXTRA_START_URL = "app.taho.browser.extra.PWA_START_URL"
        const val EXTRA_NAME = "app.taho.browser.extra.PWA_NAME"
        const val EXTRA_SCOPE = "app.taho.browser.extra.PWA_SCOPE"
        const val EXTRA_DISPLAY = "app.taho.browser.extra.PWA_DISPLAY"
        const val EXTRA_THEME_COLOR = "app.taho.browser.extra.PWA_THEME_COLOR"
    }
}
