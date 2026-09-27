package app.taho.browser.runtime

import android.content.Context
import android.os.Looper
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
            instance ?: GeckoRuntime.create(
                context.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .consoleOutput(false)
                    .build(),
            ).also { runtime -> instance = runtime }
        }
    }
}
