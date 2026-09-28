package app.taho.browser

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.mozilla.geckoview.WebNotification

class WebNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notification = notificationFrom(intent) ?: return
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val manager = context.getSystemService(NotificationManager::class.java)

        when (intent.action) {
            ACTION_CLICK -> {
                runCatching { notification.click() }
                runCatching { notification.dismiss() }
                if (id >= 0) manager?.cancel(id)

                val source = intent.getStringExtra(EXTRA_SOURCE)
                    ?.takeIf(::isHttpUrl)
                if (source != null && !notification.privateBrowsing) {
                    runCatching {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .setData(Uri.parse(source))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }

            ACTION_DISMISS -> {
                runCatching { notification.dismiss() }
                if (id >= 0) manager?.cancel(id)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun notificationFrom(intent: Intent): WebNotification? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_NOTIFICATION, WebNotification::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_NOTIFICATION)
        }

    private fun isHttpUrl(raw: String): Boolean =
        runCatching {
            val uri = java.net.URI(raw)
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
                !uri.host.isNullOrBlank()
        }.getOrDefault(false)

    companion object {
        const val ACTION_CLICK = "app.taho.browser.action.WEB_NOTIFICATION_CLICK"
        const val ACTION_DISMISS = "app.taho.browser.action.WEB_NOTIFICATION_DISMISS"
        const val EXTRA_NOTIFICATION = "app.taho.browser.extra.WEB_NOTIFICATION"
        const val EXTRA_NOTIFICATION_ID = "app.taho.browser.extra.WEB_NOTIFICATION_ID"
        const val EXTRA_SOURCE = "app.taho.browser.extra.WEB_NOTIFICATION_SOURCE"
    }
}
