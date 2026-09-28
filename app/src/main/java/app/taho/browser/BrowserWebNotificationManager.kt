package app.taho.browser

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate

class BrowserWebNotificationManager(
    context: Context,
    private val runtime: GeckoRuntime,
    private val notificationsEnabled: () -> Boolean,
    private val onRecorded: (origin: String, title: String, text: String) -> Unit,
) : WebNotificationDelegate {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        createChannel()
        runtime.setWebNotificationDelegate(this)
    }

    override fun onShowNotification(notification: WebNotification) {
        mainHandler.post {
            if (!notificationsEnabled() || !hasSystemPermission()) {
                notification.dismiss()
                return@post
            }

            val id = notificationId(notification)
            val clickIntent = actionIntent(
                notification = notification,
                action = WebNotificationActionReceiver.ACTION_CLICK,
            )
            val dismissIntent = actionIntent(
                notification = notification,
                action = WebNotificationActionReceiver.ACTION_DISMISS,
            )

            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(
                    notification.title
                        ?.takeIf(String::isNotBlank)
                        ?: displayOrigin(notification.origin),
                )
                .setContentText(notification.text.orEmpty())
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(notification.text.orEmpty()),
                )
                .setSubText(displayOrigin(notification.origin))
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setSilent(notification.silent)
                .setContentIntent(
                    PendingIntent.getBroadcast(
                        appContext,
                        id,
                        clickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        appContext,
                        id xor DISMISS_REQUEST_MASK,
                        dismissIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )

            if (notification.requireInteraction) {
                builder.setOngoing(false).setTimeoutAfter(0L)
            }

            val shown = runCatching {
                notificationManager.notify(id, builder.build())
                true
            }.getOrDefault(false)

            if (!shown) {
                notification.dismiss()
                return@post
            }

            notification.show()
            if (!notification.privateBrowsing) {
                onRecorded(
                    notification.origin,
                    notification.title.orEmpty(),
                    notification.text.orEmpty(),
                )
            }
        }
    }

    override fun onCloseNotification(notification: WebNotification) {
        mainHandler.post {
            notificationManager.cancel(notificationId(notification))
            runCatching { notification.dismiss() }
        }
    }

    fun close() {
        runtime.setWebNotificationDelegate(null)
    }

    private fun actionIntent(
        notification: WebNotification,
        action: String,
    ): Intent =
        Intent(appContext, WebNotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(WebNotificationActionReceiver.EXTRA_NOTIFICATION, notification)
            .putExtra(WebNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId(notification))
            .putExtra(WebNotificationActionReceiver.EXTRA_SOURCE, notification.source)

    private fun hasSystemPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Website notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications created by websites in Taho Browser"
            },
        )
    }

    private fun notificationId(notification: WebNotification): Int =
        (notification.origin + "|" + notification.tag)
            .hashCode()
            .and(Int.MAX_VALUE)

    private fun displayOrigin(origin: String): String =
        runCatching {
            val uri = java.net.URI(origin)
            uri.host?.takeIf(String::isNotBlank) ?: origin
        }.getOrDefault(origin)

    private companion object {
        const val CHANNEL_ID = "taho_web_notifications"
        const val DISMISS_REQUEST_MASK = 0x4E4F
    }
}
