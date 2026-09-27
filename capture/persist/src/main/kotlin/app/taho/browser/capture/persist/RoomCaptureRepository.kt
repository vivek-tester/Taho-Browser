package app.taho.browser.capture.persist

import android.content.Context
import androidx.room.Room
import app.taho.browser.capture.domain.CaptureQuery
import app.taho.browser.capture.domain.CaptureRepository
import app.taho.browser.capture.domain.CaptureRepositoryResult
import app.taho.browser.capture.domain.Completeness
import app.taho.browser.capture.domain.DurableBody
import app.taho.browser.capture.domain.DurableCaptureSession
import app.taho.browser.capture.domain.DurableTransaction
import app.taho.browser.capture.domain.DurableTransactionState
import app.taho.browser.capture.domain.DurableTransactionSummary
import app.taho.browser.capture.domain.ObservationSource
import app.taho.browser.capture.domain.Relevance
import app.taho.browser.capture.domain.RelevanceCategory
import app.taho.browser.capture.domain.RetentionSweepResult
import app.taho.browser.capture.domain.StorageDegradationReason
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class RoomCaptureRepository private constructor(
    private val database: CaptureDatabase,
    private val cipher: CaptureCipher,
    private val bodyStore: EncryptedBodyStore,
) : CaptureRepository {
    private val dao = database.captureDao()

    override fun upsertSession(
        session: DurableCaptureSession,
    ): CaptureRepositoryResult<Unit> =
        guarded {
            dao.upsertSession(
                CaptureSessionEntity(
                    session.id,
                    session.kind.name,
                    session.lifecycle.name,
                    session.label,
                    session.targetHost,
                    session.retention.name,
                    session.createdAtEpochMs,
                    session.closedAtEpochMs,
                ),
            )
            Unit
        }

    override fun commit(
        transaction: DurableTransaction,
    ): CaptureRepositoryResult<Unit> =
        guarded {
            val requestHeaderCipher = encrypt(transaction.encryptedRequestHeadersSource)
            val responseHeaderCipher = encrypt(transaction.encryptedResponseHeadersSource)

            val requestBody = storeBody(transaction.id, "REQUEST", transaction.requestBody)
            if (requestBody is BodyStoreResult.Degraded) {
                return CaptureRepositoryResult.Degraded(requestBody.reason)
            }
            val responseBody = storeBody(transaction.id, "RESPONSE", transaction.responseBody)
            if (responseBody is BodyStoreResult.Degraded) {
                cleanupStoredBody(requestBody)
                return CaptureRepositoryResult.Degraded(responseBody.reason)
            }

            val req = (requestBody as BodyStoreResult.Success).value
            val resp = (responseBody as BodyStoreResult.Success).value
            val entity = CaptureTransactionEntity(
                transaction.id,
                transaction.captureSessionId,
                transaction.tahoTabId,
                transaction.attribution,
                transaction.extTabId,
                transaction.engineRequestId,
                transaction.method,
                transaction.url,
                queryJson(transaction),
                requestHeaderCipher?.iv,
                requestHeaderCipher?.ciphertext,
                headersJson(transaction.requestHeaders),
                req?.id,
                transaction.requestBody?.completeness?.name ?: Completeness.NOT_APPLICABLE.name,
                transaction.status,
                transaction.statusText,
                responseHeaderCipher?.iv,
                responseHeaderCipher?.ciphertext,
                headersJson(transaction.responseHeaders),
                resp?.id,
                transaction.responseBody?.completeness?.name ?: Completeness.UNAVAILABLE.name,
                transaction.state.name,
                transaction.relevance.category.name,
                transaction.relevance.reason,
                transaction.relevance.isFirstParty,
                transaction.observationSource.name,
                transaction.provenanceJson,
                transaction.normalizerVersion,
                transaction.createdAtEpochMs,
                transaction.updatedAtEpochMs,
                transaction.isPrivate,
            )

            database.runInTransaction {
                dao.upsertTransaction(entity)
                if (req != null) dao.upsertBody(req)
                if (resp != null) dao.upsertBody(resp)
            }
            Unit
        }

    override fun list(
        query: CaptureQuery,
    ): CaptureRepositoryResult<List<DurableTransactionSummary>> =
        guarded {
            val search = query.search?.trim()?.lowercase(Locale.ROOT).orEmpty()
            dao.recentTransactions(query.limit.coerceAtMost(2_000))
                .asSequence()
                .filter { row -> query.tabId == null || row.tahoTabId == query.tabId }
                .filter { row ->
                    query.relevance.isEmpty() ||
                        RelevanceCategory.valueOf(row.relevanceCategory) in query.relevance
                }
                .filter { row ->
                    query.methods.isEmpty() ||
                        row.method.uppercase(Locale.ROOT) in
                        query.methods.map { it.uppercase(Locale.ROOT) }.toSet()
                }
                .filter { row ->
                    query.states.isEmpty() ||
                        DurableTransactionState.valueOf(row.state) in query.states
                }
                .filter { row ->
                    search.isEmpty() || searchable(row).contains(search)
                }
                .map(::summary)
                .toList()
        }

    override fun markInterruptedPartial(
        nowEpochMs: Long,
    ): CaptureRepositoryResult<Int> =
        guarded { dao.markInterruptedPartial(nowEpochMs) }

    override fun closeSession(
        sessionId: String,
        closedAtEpochMs: Long,
    ): CaptureRepositoryResult<Unit> =
        guarded {
            dao.closeSession(sessionId, closedAtEpochMs)
            Unit
        }

    override fun clearCaptureData(): CaptureRepositoryResult<Unit> =
        guarded {
            database.clearAllTables()
            bodyStore.clear()
            Unit
        }

    override fun sweepRetention(
        nowEpochMs: Long,
    ): CaptureRepositoryResult<RetentionSweepResult> =
        guarded {
            val cutoff = nowEpochMs - 24L * 60L * 60L * 1000L
            val ids = dao.expiredSessionIds(cutoff)
            val txCount = if (ids.isEmpty()) 0 else dao.countTransactionsForSessions(ids)
            val bodyCount = if (ids.isEmpty()) 0 else dao.countBodiesForSessions(ids)
            val deletedSessions = if (ids.isEmpty()) 0 else dao.deleteSessions(ids)
            dao.deleteOrphanBodies()
            dao.deleteOrphanTransactions()
            RetentionSweepResult(
                deletedSessions = deletedSessions,
                deletedTransactions = txCount,
                deletedBodies = bodyCount,
            )
        }

    fun readBody(
        transactionId: String,
        side: String,
    ): CaptureRepositoryResult<ByteArray?> {
        val row = dao.body(transactionId, side)
            ?: return CaptureRepositoryResult.Success(null)
        return when (
            val result = bodyStore.read(
                inlineIv = row.iv,
                inlineCiphertext = row.ciphertext,
                storageRef = row.storageRef,
            )
        ) {
            is BodyStoreResult.Success -> CaptureRepositoryResult.Success(result.value)
            is BodyStoreResult.Degraded -> CaptureRepositoryResult.Degraded(result.reason)
        }
    }

    fun close() {
        database.close()
    }

    private fun storeBody(
        transactionId: String,
        side: String,
        body: DurableBody?,
    ): BodyStoreResult<CaptureBodyEntity?> {
        if (body == null) return BodyStoreResult.Success(null)
        val bytes = body.payload?.copyBytesForPersistence()
        val stored = bodyStore.write("$transactionId-$side", bytes)
        bytes?.fill(0)
        if (stored is BodyStoreResult.Degraded) return stored

        val value = (stored as BodyStoreResult.Success).value
        val id = "$transactionId-$side"
        val row = when (value) {
            is StoredBodyPayload.Inline -> CaptureBodyEntity(
                id,
                transactionId,
                side,
                body.contentType,
                body.charset,
                body.encoding.name,
                null,
                body.declaredSize,
                body.capturedSize,
                body.truncated,
                body.representation.name,
                body.completeness.name,
                value.encrypted.iv,
                value.encrypted.ciphertext,
                body.limitation,
                System.currentTimeMillis(),
            )

            is StoredBodyPayload.FileBacked -> CaptureBodyEntity(
                id,
                transactionId,
                side,
                body.contentType,
                body.charset,
                body.encoding.name,
                value.storageRef,
                body.declaredSize,
                body.capturedSize,
                body.truncated,
                body.representation.name,
                body.completeness.name,
                null,
                null,
                body.limitation,
                System.currentTimeMillis(),
            )

            StoredBodyPayload.None -> CaptureBodyEntity(
                id,
                transactionId,
                side,
                body.contentType,
                body.charset,
                body.encoding.name,
                null,
                body.declaredSize,
                body.capturedSize,
                body.truncated,
                body.representation.name,
                body.completeness.name,
                null,
                null,
                body.limitation,
                System.currentTimeMillis(),
            )
        }
        return BodyStoreResult.Success(row)
    }

    private fun cleanupStoredBody(result: BodyStoreResult<CaptureBodyEntity?>) {
        val row = (result as? BodyStoreResult.Success)?.value ?: return
        bodyStore.delete(row.storageRef)
    }

    private fun encrypt(payload: app.taho.browser.capture.domain.SensitivePayload?): EncryptedCaptureBytes? {
        if (payload == null) return null
        val bytes = payload.copyBytesForPersistence()
        return try {
            cipher.encrypt(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun queryJson(transaction: DurableTransaction): String =
        JSONArray().apply {
            transaction.query.forEach { value ->
                put(
                    JSONObject().apply {
                        put("name", value.name)
                        put("value", value.displayValue)
                        put("redacted", value.redacted)
                    },
                )
            }
        }.toString()

    private fun headersJson(
        values: List<app.taho.browser.capture.domain.DurableHeaderValue>,
    ): String =
        JSONArray().apply {
            values.forEach { value ->
                put(
                    JSONObject().apply {
                        put("name", value.name)
                        put("value", value.displayValue)
                        put("redacted", value.redacted)
                    },
                )
            }
        }.toString()

    private fun summary(row: CaptureTransactionEntity): DurableTransactionSummary =
        DurableTransactionSummary(
            id = row.id,
            captureSessionId = row.captureSessionId,
            tahoTabId = row.tahoTabId,
            method = row.method,
            url = row.url,
            status = row.status,
            state = DurableTransactionState.valueOf(row.state),
            relevance = Relevance(
                category = RelevanceCategory.valueOf(row.relevanceCategory),
                isFirstParty = row.firstParty,
                reason = row.relevanceReason,
                score = 0,
            ),
            requestBodyCompleteness = Completeness.valueOf(row.reqCompleteness),
            responseBodyCompleteness = Completeness.valueOf(row.respCompleteness),
            createdAtEpochMs = row.createdAt,
        )

    private fun searchable(row: CaptureTransactionEntity): String =
        buildString {
            append(row.method.lowercase(Locale.ROOT))
            append(' ')
            append(row.url.lowercase(Locale.ROOT))
            append(' ')
            append(row.status?.toString().orEmpty())
            append(' ')
            append(row.relevanceCategory.lowercase(Locale.ROOT))
            append(' ')
            append(row.reqHeadersRedacted?.lowercase(Locale.ROOT).orEmpty())
        }

    private inline fun <T> guarded(block: () -> T): CaptureRepositoryResult<T> =
        try {
            CaptureRepositoryResult.Success(block())
        } catch (_: CaptureKeyInvalidatedException) {
            CaptureRepositoryResult.Degraded(StorageDegradationReason.KEY_INVALIDATED)
        } catch (error: android.database.sqlite.SQLiteDatabaseCorruptException) {
            CaptureRepositoryResult.Degraded(
                StorageDegradationReason.DATABASE_CORRUPT,
                error.javaClass.simpleName,
            )
        } catch (error: Throwable) {
            CaptureRepositoryResult.Degraded(
                StorageDegradationReason.CORRUPT_RECORD,
                error.javaClass.simpleName,
            )
        }

    companion object {
        fun open(context: Context): RoomCaptureRepository {
            val app = context.applicationContext
            val database = Room.databaseBuilder(
                app,
                CaptureDatabase::class.java,
                "taho-capture-v1.db",
            ).build()
            val cipher = AndroidKeystoreCaptureCipher()
            return RoomCaptureRepository(
                database = database,
                cipher = cipher,
                bodyStore = EncryptedBodyStore(app, cipher),
            )
        }

        fun forTesting(
            database: CaptureDatabase,
            context: Context,
            cipher: CaptureCipher,
        ): RoomCaptureRepository =
            RoomCaptureRepository(
                database = database,
                cipher = cipher,
                bodyStore = EncryptedBodyStore(context, cipher),
            )
    }
}
