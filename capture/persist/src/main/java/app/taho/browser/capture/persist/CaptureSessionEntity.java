package app.taho.browser.capture.persist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "capture_session",
    indices = {
        @Index(value = {"state", "createdAt"}),
        @Index(value = {"retention", "closedAt"})
    }
)
public class CaptureSessionEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String kind;
    @NonNull public String state;
    @Nullable public String label;
    @Nullable public String targetHost;
    @NonNull public String retention;
    public long createdAt;
    @Nullable public Long closedAt;

    public CaptureSessionEntity(
        @NonNull String id,
        @NonNull String kind,
        @NonNull String state,
        @Nullable String label,
        @Nullable String targetHost,
        @NonNull String retention,
        long createdAt,
        @Nullable Long closedAt
    ) {
        this.id = id;
        this.kind = kind;
        this.state = state;
        this.label = label;
        this.targetHost = targetHost;
        this.retention = retention;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
    }
}
