package app.taho.browser

import android.content.Context
import app.taho.browser.capture.persist.RoomCaptureRepository
import app.taho.browser.transfer.core.UlidGenerator

object CapturePersistenceStore {
    @Volatile
    private var repository: RoomCaptureRepository? = null

    @Volatile
    private var processCaptureSessionId: String? = null

    fun repository(context: Context): RoomCaptureRepository =
        repository ?: synchronized(this) {
            repository ?: RoomCaptureRepository.open(context.applicationContext)
                .also { repository = it }
        }

    fun captureSessionId(): String =
        processCaptureSessionId ?: synchronized(this) {
            processCaptureSessionId ?: UlidGenerator.next()
                .also { processCaptureSessionId = it }
        }
}
