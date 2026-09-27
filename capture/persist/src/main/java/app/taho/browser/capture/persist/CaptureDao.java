package app.taho.browser.capture.persist;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSession(CaptureSessionEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertTransaction(CaptureTransactionEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertBody(CaptureBodyEntity entity);

    @Query("SELECT * FROM capture_transaction ORDER BY createdAt DESC LIMIT :limit")
    List<CaptureTransactionEntity> recentTransactions(int limit);

    @Query("SELECT * FROM capture_body WHERE transactionId = :transactionId AND side = :side LIMIT 1")
    CaptureBodyEntity body(String transactionId, String side);

    @Query("SELECT * FROM capture_session WHERE id = :id LIMIT 1")
    CaptureSessionEntity session(String id);

    @Query("SELECT id FROM capture_session WHERE retention = 'PRIVATE' OR (retention = 'SESSION_ONLY' AND closedAt IS NOT NULL AND closedAt <= :cutoff)")
    List<String> expiredSessionIds(long cutoff);

    @Query("SELECT COUNT(*) FROM capture_transaction WHERE captureSessionId IN (:sessionIds)")
    int countTransactionsForSessions(List<String> sessionIds);

    @Query("SELECT COUNT(*) FROM capture_body WHERE transactionId IN (SELECT id FROM capture_transaction WHERE captureSessionId IN (:sessionIds))")
    int countBodiesForSessions(List<String> sessionIds);

    @Query("DELETE FROM capture_session WHERE id IN (:sessionIds)")
    int deleteSessions(List<String> sessionIds);

    @Query("UPDATE capture_session SET state = 'CLOSED', closedAt = :closedAt WHERE id = :sessionId")
    int closeSession(String sessionId, long closedAt);

    @Query("UPDATE capture_transaction SET state = 'PARTIAL', updatedAt = :now WHERE state = 'STARTED'")
    int markInterruptedPartial(long now);

    @Query("UPDATE capture_session SET state = 'CLOSED', closedAt = :now WHERE state = 'ACTIVE'")
    int closeAbandonedActiveSessions(long now);

    @Query("DELETE FROM capture_body WHERE transactionId NOT IN (SELECT id FROM capture_transaction)")
    int deleteOrphanBodies();

    @Query("DELETE FROM capture_transaction WHERE captureSessionId NOT IN (SELECT id FROM capture_session)")
    int deleteOrphanTransactions();

    @Query("SELECT COUNT(*) FROM capture_transaction")
    int transactionCount();

    @Query("SELECT COUNT(*) FROM capture_body")
    int bodyCount();
}
