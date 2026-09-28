package app.taho.browser.capture.persist;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {
        CaptureSessionEntity.class,
        CaptureTransactionEntity.class,
        CaptureBodyEntity.class,
        TabEntity.class,
        StreamFrameEntity.class,
        TransferRecordEntity.class,
        MetaEntity.class
    },
    version = 1,
    exportSchema = true
)
public abstract class CaptureDatabase extends RoomDatabase {
    public abstract CaptureDao captureDao();
}
