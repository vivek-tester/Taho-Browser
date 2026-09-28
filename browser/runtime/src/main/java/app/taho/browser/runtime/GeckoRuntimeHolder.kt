package app.taho.browser.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeHolder {
    @Volatile
    private var instance: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "GeckoRuntime must be created from the Android main thread"
        }
        return instance ?: synchronized(this) {
            instance ?: run {
                val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                val settings = GeckoRuntimeSettings.Builder()
                    .consoleOutput(isDebug)
                    .remoteDebuggingEnabled(isDebug)
                    .contentBlocking(
                        ContentBlocking.Settings.Builder()
                            .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
                            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                            .build(),
                    )
                    .build()
                GeckoRuntime.create(context.applicationContext, settings).also { runtime ->
                    instance = runtime
                }
            }
        }
    }
}
