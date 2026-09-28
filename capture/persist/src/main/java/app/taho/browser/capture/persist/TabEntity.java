package app.taho.browser.capture.persist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Canonical tab persistence table according to Storage Architecture §2.2.
 * displayIndex is stored for presentation ordering only, never attribution.
 */
@Entity(
    tableName = "tab",
    indices = {
        @Index(value = {"extTabId"}, unique = true)
    }
)
public class TabEntity {
    @PrimaryKey
    @NonNull
    public String id;

    @Nullable
    public Integer extTabId;

    @NonNull
    public String sessionState;

    public boolean isPrivate;

    public int displayIndex;

    public long createdAt;

    @Nullable
    public Long closedAt;

    public TabEntity(
        @NonNull String id,
        @Nullable Integer extTabId,
        @NonNull String sessionState,
        boolean isPrivate,
        int displayIndex,
        long createdAt,
        @Nullable Long closedAt
    ) {
        this.id = id;
        this.extTabId = extTabId;
        this.sessionState = sessionState;
        this.isPrivate = isPrivate;
        this.displayIndex = displayIndex;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
    }
}
