package app.taho.browser.capture.persist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Canonical stream_frame persistence table according to Storage Architecture §2.5.
 * Stores WebSocket frames and SSE chunks linked to transactions.
 */
@Entity(
    tableName = "stream_frame",
    foreignKeys = @ForeignKey(
        entity = CaptureTransactionEntity.class,
        parentColumns = "id",
        childColumns = "transactionId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index(value = {"transactionId", "at"})
    }
)
public class StreamFrameEntity {
    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String transactionId;

    @NonNull
    public String direction;

    @Nullable
    public String opcode;

    @Nullable
    public String bodyRef;

    public long payloadSize;

    public long at;

    public StreamFrameEntity(
        @NonNull String id,
        @NonNull String transactionId,
        @NonNull String direction,
        @Nullable String opcode,
        @Nullable String bodyRef,
        long payloadSize,
        long at
    ) {
        this.id = id;
        this.transactionId = transactionId;
        this.direction = direction;
        this.opcode = opcode;
        this.bodyRef = bodyRef;
        this.payloadSize = payloadSize;
        this.at = at;
    }
}
