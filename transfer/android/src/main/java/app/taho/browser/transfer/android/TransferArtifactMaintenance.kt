package app.taho.browser.transfer.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class TransferArtifactMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        runCatching {
            SecureTransferArtifactStore(applicationContext).sweep()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
}

object TransferArtifactMaintenance {
    private const val WORK_NAME = "taho-transfer-artifact-maintenance"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<TransferArtifactMaintenanceWorker>(
            1,
            TimeUnit.HOURS,
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
