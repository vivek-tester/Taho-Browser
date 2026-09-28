package app.taho.browser.capture.persist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Canonical transfer_record persistence table according to Storage Architecture §2.6.
 * Records state, policy, transport, envelope byte metrics, and receipt of transfers to Taho.
 */
@Entity(
    tableName = "transfer_record",
    foreignKeys = @ForeignKey(
        entity = CaptureTransactionEntity.class,
        parentColumns = "id",
        childColumns = "transactionId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index(value = {"state", "createdAt"}),
        @Index(value = {"transactionId"})
    }
)
public class TransferRecordEntity {
    @PrimaryKey
    @NonNull
    public String transferId;

    @NonNull
    public String transactionId;

    @NonNull
    public String state;

    @NonNull
    public String secretPolicy;

    @Nullable
    public String transport;

    @Nullable
    public Long envelopeBytes;

    @NonNull
    public String targetPackage;

    @Nullable
    public String receiptJson;

    @Nullable
    public String errorCode;

    @Nullable
    public String artifactPath;

    public long createdAt;

    @Nullable
    public Long settledAt;

    public TransferRecordEntity(
        @NonNull String transferId,
        @NonNull String transactionId,
        @NonNull String state,
        @NonNull String secretPolicy,
        @Nullable String transport,
        @Nullable Long envelopeBytes,
        @NonNull String targetPackage,
        @Nullable String receiptJson,
        @Nullable String errorCode,
        @Nullable String artifactPath,
        long createdAt,
        @Nullable Long settledAt
    ) {
        this.transferId = transferId;
        this.transactionId = transactionId;
        this.state = state;
        this.secretPolicy = secretPolicy;
        this.transport = transport;
        this.envelopeBytes = envelopeBytes;
        this.targetPackage = targetPackage;
        this.receiptJson = receiptJson;
        this.errorCode = errorCode;
        this.artifactPath = artifactPath;
        this.createdAt = createdAt;
        this.settledAt = settledAt;
    }
}
