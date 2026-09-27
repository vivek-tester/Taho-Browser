package app.taho.browser.capture.persist

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CaptureMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = RoomCaptureRepository.open(applicationContext)
        return try {
            repository.markInterruptedPartial(System.currentTimeMillis())
            when (repository.sweepRetention(System.currentTimeMillis())) {
                is app.taho.browser.capture.domain.CaptureRepositoryResult.Success -> Result.success()
                is app.taho.browser.capture.domain.CaptureRepositoryResult.Degraded -> Result.retry()
            }
        } finally {
            repository.close()
        }
    }
}

object CaptureMaintenance {
    private const val WORK_NAME = "taho-capture-maintenance"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CaptureMaintenanceWorker>(
            24,
            TimeUnit.HOURS,
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
