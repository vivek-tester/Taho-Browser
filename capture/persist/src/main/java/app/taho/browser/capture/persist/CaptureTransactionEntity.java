package app.taho.browser.capture.persist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "capture_transaction",
    foreignKeys = @ForeignKey(
        entity = CaptureSessionEntity.class,
        parentColumns = "id",
        childColumns = "captureSessionId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index("captureSessionId"),
        @Index("tahoTabId"),
        @Index("relevanceCategory"),
        @Index("state"),
        @Index("method"),
        @Index(value = {"extTabId", "engineRequestId"})
    }
)
public class CaptureTransactionEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String captureSessionId;
    @Nullable public String tahoTabId;
    @NonNull public String attribution;
    @Nullable public Integer extTabId;
    @Nullable public String engineRequestId;

    @NonNull public String method;
    @NonNull public String url;
    @Nullable public String queryJson;

    @Nullable public byte[] reqHeadersIv;
    @Nullable public byte[] reqHeadersCiphertext;
    @Nullable public String reqHeadersRedacted;
    @Nullable public String reqBodyRef;
    @NonNull public String reqCompleteness;

    @Nullable public Integer status;
    @Nullable public String statusText;

    @Nullable public byte[] respHeadersIv;
    @Nullable public byte[] respHeadersCiphertext;
    @Nullable public String respHeadersRedacted;
    @Nullable public String respBodyRef;
    @NonNull public String respCompleteness;

    @NonNull public String state;
    @NonNull public String relevanceCategory;
    @NonNull public String relevanceReason;
    public boolean firstParty;
    @NonNull public String observationSource;
    @NonNull public String provenanceJson;
    @NonNull public String normalizerVersion;
    public long createdAt;
    public long updatedAt;
    public boolean isPrivate;

    public CaptureTransactionEntity(
        @NonNull String id,
        @NonNull String captureSessionId,
        @Nullable String tahoTabId,
        @NonNull String attribution,
        @Nullable Integer extTabId,
        @Nullable String engineRequestId,
        @NonNull String method,
        @NonNull String url,
        @Nullable String queryJson,
        @Nullable byte[] reqHeadersIv,
        @Nullable byte[] reqHeadersCiphertext,
        @Nullable String reqHeadersRedacted,
        @Nullable String reqBodyRef,
        @NonNull String reqCompleteness,
        @Nullable Integer status,
        @Nullable String statusText,
        @Nullable byte[] respHeadersIv,
        @Nullable byte[] respHeadersCiphertext,
        @Nullable String respHeadersRedacted,
        @Nullable String respBodyRef,
        @NonNull String respCompleteness,
        @NonNull String state,
        @NonNull String relevanceCategory,
        @NonNull String relevanceReason,
        boolean firstParty,
        @NonNull String observationSource,
        @NonNull String provenanceJson,
        @NonNull String normalizerVersion,
        long createdAt,
        long updatedAt,
        boolean isPrivate
    ) {
        this.id = id;
        this.captureSessionId = captureSessionId;
        this.tahoTabId = tahoTabId;
        this.attribution = attribution;
        this.extTabId = extTabId;
        this.engineRequestId = engineRequestId;
        this.method = method;
        this.url = url;
        this.queryJson = queryJson;
        this.reqHeadersIv = reqHeadersIv;
        this.reqHeadersCiphertext = reqHeadersCiphertext;
        this.reqHeadersRedacted = reqHeadersRedacted;
        this.reqBodyRef = reqBodyRef;
        this.reqCompleteness = reqCompleteness;
        this.status = status;
        this.statusText = statusText;
        this.respHeadersIv = respHeadersIv;
        this.respHeadersCiphertext = respHeadersCiphertext;
        this.respHeadersRedacted = respHeadersRedacted;
        this.respBodyRef = respBodyRef;
        this.respCompleteness = respCompleteness;
        this.state = state;
        this.relevanceCategory = relevanceCategory;
        this.relevanceReason = relevanceReason;
        this.firstParty = firstParty;
        this.observationSource = observationSource;
        this.provenanceJson = provenanceJson;
        this.normalizerVersion = normalizerVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isPrivate = isPrivate;
    }
}
