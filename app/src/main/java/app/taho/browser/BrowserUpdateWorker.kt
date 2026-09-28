package app.taho.browser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class BrowserUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val result = runCatching {
            BrowserReleaseApi.check(applicationContext)
        }.getOrElse {
            return Result.retry()
        }

        if (result.error != null) return Result.retry()
        if (!result.updateAvailable) return Result.success()

        val version = result.latestVersion ?: return Result.success()
        val releaseUrl = result.releaseUrl ?: return Result.success()
        val preferences = applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        if (preferences.getString(KEY_LAST_NOTIFIED_VERSION, null) == version) {
            return Result.success()
        }

        if (showUpdateNotification(version, releaseUrl)) {
            preferences.edit()
                .putString(KEY_LAST_NOTIFIED_VERSION, version)
                .apply()
        }
        return Result.success()
    }

    private fun showUpdateNotification(
        version: String,
        releaseUrl: String,
    ): Boolean {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val system = applicationContext.getSystemService(NotificationManager::class.java)
            system?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Taho Browser updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Verified Taho Browser release notifications"
                },
            )
        }

        val openRelease = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
            .setPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            version.hashCode(),
            openRelease,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Taho Browser update available")
            .setContentText("Version $version is available.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        return runCatching {
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val UNIQUE_WORK = "taho-browser-update-check"
        private const val CHANNEL_ID = "taho_browser_updates"
        private const val NOTIFICATION_ID = 0x5442
        private const val PREFERENCES_NAME = "taho_browser_updates"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BrowserUpdateWorker>(
                24,
                TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
