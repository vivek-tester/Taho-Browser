package app.taho.browser.capture.persist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "capture_body",
    foreignKeys = @ForeignKey(
        entity = CaptureTransactionEntity.class,
        parentColumns = "id",
        childColumns = "transactionId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index("transactionId"),
        @Index(value = {"transactionId", "side"}, unique = true)
    }
)
public class CaptureBodyEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String transactionId;
    @NonNull public String side;
    @Nullable public String contentType;
    @Nullable public String charsetName;
    @NonNull public String encoding;
    @Nullable public String storageRef;
    @Nullable public Long declaredSize;
    public long capturedSize;
    public boolean truncated;
    @NonNull public String representation;
    @NonNull public String completeness;
    @Nullable public byte[] iv;
    @Nullable public byte[] ciphertext;
    @Nullable public String limitation;
    public long createdAt;

    public CaptureBodyEntity(
        @NonNull String id,
        @NonNull String transactionId,
        @NonNull String side,
        @Nullable String contentType,
        @Nullable String charsetName,
        @NonNull String encoding,
        @Nullable String storageRef,
        @Nullable Long declaredSize,
        long capturedSize,
        boolean truncated,
        @NonNull String representation,
        @NonNull String completeness,
        @Nullable byte[] iv,
        @Nullable byte[] ciphertext,
        @Nullable String limitation,
        long createdAt
    ) {
        this.id = id;
        this.transactionId = transactionId;
        this.side = side;
        this.contentType = contentType;
        this.charsetName = charsetName;
        this.encoding = encoding;
        this.storageRef = storageRef;
        this.declaredSize = declaredSize;
        this.capturedSize = capturedSize;
        this.truncated = truncated;
        this.representation = representation;
        this.completeness = completeness;
        this.iv = iv;
        this.ciphertext = ciphertext;
        this.limitation = limitation;
        this.createdAt = createdAt;
    }
}
