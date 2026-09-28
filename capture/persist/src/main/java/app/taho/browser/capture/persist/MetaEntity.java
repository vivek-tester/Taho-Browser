package app.taho.browser.capture.persist;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Canonical meta persistence table according to Storage Architecture §2.7.
 * Stores schema version, engine version, and sweeper cursor.
 */
@Entity(tableName = "meta")
public class MetaEntity {
    @PrimaryKey
    @NonNull
    public String key;

    @NonNull
    public String value;

    public MetaEntity(@NonNull String key, @NonNull String value) {
        this.key = key;
        this.value = value;
    }
}
