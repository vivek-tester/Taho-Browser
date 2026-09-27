package app.taho.browser.capture.persist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.taho.browser.capture.domain.CaptureQuery
import app.taho.browser.capture.domain.CaptureRepositoryResult
import app.taho.browser.capture.domain.CaptureSessionKind
import app.taho.browser.capture.domain.CaptureSessionLifecycle
import app.taho.browser.capture.domain.Completeness
import app.taho.browser.capture.domain.DurableBody
import app.taho.browser.capture.domain.DurableBodyEncoding
import app.taho.browser.capture.domain.DurableBodyRepresentation
import app.taho.browser.capture.domain.DurableCaptureSession
import app.taho.browser.capture.domain.DurableHeaderValue
import app.taho.browser.capture.domain.DurableQueryValue
import app.taho.browser.capture.domain.DurableTransaction
import app.taho.browser.capture.domain.DurableTransactionState
import app.taho.browser.capture.domain.ObservationSource
import app.taho.browser.capture.domain.Relevance
import app.taho.browser.capture.domain.RelevanceCategory
import app.taho.browser.capture.domain.RetentionPolicy
import app.taho.browser.capture.domain.SensitivePayload
import app.taho.browser.capture.domain.StorageDegradationReason
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomCaptureRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: CaptureDatabase
    private lateinit var repository: RoomCaptureRepository

    private class FakeCipher(
        private val failRead: Boolean = false,
    ) : CaptureCipher {
        override fun encrypt(plaintext: ByteArray): EncryptedCaptureBytes =
            EncryptedCaptureBytes(byteArrayOf(7, 7, 7), plaintext.map { (it.toInt() xor 0x5a).toByte() }.toByteArray())

        override fun decrypt(value: EncryptedCaptureBytes): ByteArray {
            if (failRead) throw CaptureKeyInvalidatedException()
            return value.ciphertext.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        }
    }

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CaptureDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCaptureRepository.forTesting(database, context, FakeCipher())
    }

    @AfterTest
    fun tearDown() {
        repository.close()
    }

    @Test
    fun committedRecordSurvivesRepositoryRecreationAndSearchUsesRedactedColumns() {
        repository.upsertSession(session("s1", RetentionPolicy.KEEP_UNTIL_DELETED))
        val tx = transaction("tx1", "s1", "https://api.example.test/checkout")
        assertIs<CaptureRepositoryResult.Success<Unit>>(repository.commit(tx))
        tx.close()

        val result = assertIs<CaptureRepositoryResult.Success<*>>(
            repository.list(CaptureQuery(search = "checkout")),
        )
        @Suppress("UNCHECKED_CAST")
        val rows = result.value as List<app.taho.browser.capture.domain.DurableTransactionSummary>
        assertEquals(1, rows.size)
        assertEquals("POST", rows.single().method)
    }

    @Test
    fun privateSessionNeverPersistsAnyCaptureRecord() {
        repository.upsertSession(
            DurableCaptureSession(
                id = "private",
                kind = CaptureSessionKind.PRIVATE,
                lifecycle = CaptureSessionLifecycle.ACTIVE,
                retention = RetentionPolicy.PRIVATE,
                createdAtEpochMs = 1,
            ),
        )
        val body = DurableBody(
            representation = DurableBodyRepresentation.JSON,
            encoding = DurableBodyEncoding.UTF8,
            contentType = "application/json",
            charset = "utf-8",
            declaredSize = null,
            capturedSize = 0,
            truncated = false,
            completeness = Completeness.UNAVAILABLE,
            payload = null,
            limitation = "private payload not persisted",
        )
        val tx = DurableTransaction(
            id = "private-tx",
            captureSessionId = "private",
            tahoTabId = "tab",
            attribution = "KNOWN",
            extTabId = 1,
            engineRequestId = "req",
            method = "POST",
            url = "https://example.test/private",
            query = emptyList(),
            requestHeaders = listOf(DurableHeaderValue("Authorization", "••••••••", true)),
            encryptedRequestHeadersSource = null,
            requestBody = body,
            status = 200,
            statusText = "OK",
            responseHeaders = emptyList(),
            encryptedResponseHeadersSource = null,
            responseBody = null,
            state = DurableTransactionState.COMPLETED,
            relevance = Relevance(RelevanceCategory.PRIMARY_API, true, "matches workspace host", 90),
            observationSource = ObservationSource.ENGINE,
            provenanceJson = "{}",
            normalizerVersion = "request/1.0.0",
            createdAtEpochMs = 2,
            updatedAtEpochMs = 3,
            isPrivate = true,
        )
        assertIs<CaptureRepositoryResult.Success<Unit>>(repository.commit(tx))
        assertEquals(0, database.captureDao().transactionCount())
        assertEquals(0, database.captureDao().bodyCount())
        assertEquals(null, database.captureDao().session("private"))
    }

    @Test
    fun interruptedStartedRowsBecomePartial() {
        repository.upsertSession(session("s1", RetentionPolicy.KEEP_UNTIL_DELETED))
        val tx = transaction("started", "s1", "https://example.test/a", DurableTransactionState.STARTED)
        repository.commit(tx)
        tx.close()

        val changed = assertIs<CaptureRepositoryResult.Success<Int>>(
            repository.markInterruptedPartial(99),
        )
        assertEquals(1, changed.value)
        val rows = assertIs<CaptureRepositoryResult.Success<*>>(
            repository.list(CaptureQuery(states = setOf(DurableTransactionState.PARTIAL))),
        )
        assertTrue((rows.value as List<*>).isNotEmpty())
    }

    @Test
    fun retentionDeletesClosedSessionOnlyAndPrivateSessionsButKeepsWorkspace() {
        repository.upsertSession(session("old", RetentionPolicy.SESSION_ONLY, closedAt = 1))
        repository.upsertSession(session("workspace", RetentionPolicy.KEEP_UNTIL_DELETED))
        repository.upsertSession(
            DurableCaptureSession(
                id = "p",
                kind = CaptureSessionKind.PRIVATE,
                lifecycle = CaptureSessionLifecycle.CLOSED,
                retention = RetentionPolicy.PRIVATE,
                createdAtEpochMs = 1,
                closedAtEpochMs = 2,
            ),
        )
        transaction("oldtx", "old", "https://example.test/old").also {
            repository.commit(it)
            it.close()
        }
        transaction("keeptx", "workspace", "https://example.test/keep").also {
            repository.commit(it)
            it.close()
        }

        val sweep = assertIs<CaptureRepositoryResult.Success<app.taho.browser.capture.domain.RetentionSweepResult>>(
            repository.sweepRetention(3L * 24L * 60L * 60L * 1000L),
        )
        assertEquals(2, sweep.value.deletedSessions)
        val rows = assertIs<CaptureRepositoryResult.Success<*>>(
            repository.list(CaptureQuery()),
        )
        val summaries = rows.value as List<*>
        assertEquals(1, summaries.size)
    }

    @Test
    fun keyInvalidationIsVisibleAndDoesNotPretendEmptyBody() {
        val badRepo = RoomCaptureRepository.forTesting(database, context, FakeCipher(failRead = true))
        badRepo.upsertSession(session("s2", RetentionPolicy.KEEP_UNTIL_DELETED))
        val tx = transaction("bodytx", "s2", "https://example.test/body")
        badRepo.commit(tx)
        tx.close()

        val body = badRepo.readBody("bodytx", "REQUEST")
        val degraded = assertIs<CaptureRepositoryResult.Degraded>(body)
        assertEquals(StorageDegradationReason.KEY_INVALIDATED, degraded.reason)
    }

    @Test
    fun clearCaptureDataDoesNotTouchAnythingOutsideCaptureStore() {
        repository.upsertSession(session("s1", RetentionPolicy.KEEP_UNTIL_DELETED))
        transaction("tx1", "s1", "https://example.test/a").also {
            repository.commit(it)
            it.close()
        }
        assertIs<CaptureRepositoryResult.Success<Unit>>(repository.clearCaptureData())
        assertEquals(0, database.captureDao().transactionCount())
        assertEquals(0, database.captureDao().bodyCount())
    }


    @Test
    fun methodRelevanceAndTabFiltersExcludeUnresolvedRows() {
        repository.upsertSession(session("s1", RetentionPolicy.KEEP_UNTIL_DELETED))
        transaction("api", "s1", "https://example.test/api").also {
            repository.commit(it)
            it.close()
        }
        transaction(
            id = "unresolved",
            sessionId = "s1",
            url = "https://example.test/unresolved",
            tabId = null,
            relevance = RelevanceCategory.BUSINESS_API,
        ).also {
            repository.commit(it)
            it.close()
        }

        val result = assertIs<CaptureRepositoryResult.Success<*>>(
            repository.list(
                CaptureQuery(
                    tabId = "tab-1",
                    methods = setOf("POST"),
                    relevance = setOf(RelevanceCategory.PRIMARY_API),
                ),
            ),
        )
        val rows = result.value as List<*>
        assertEquals(1, rows.size)
    }

    @Test
    fun encryptedBodyStoreReportsLowStorageAndMissingFile() {
        val store = EncryptedBodyStore(
            context = context,
            cipher = FakeCipher(),
            inlineLimitBytes = 4,
            availableBytes = { 5L },
        )
        val low = store.write("large", ByteArray(8) { 1 })
        val degraded = assertIs<BodyStoreResult.Degraded>(low)
        assertEquals(StorageDegradationReason.LOW_STORAGE, degraded.reason)

        val missing = store.read(
            inlineIv = null,
            inlineCiphertext = null,
            storageRef = "does-not-exist.bin",
        )
        val missingDegraded = assertIs<BodyStoreResult.Degraded>(missing)
        assertEquals(
            StorageDegradationReason.BODY_FILE_MISSING,
            missingDegraded.reason,
        )
    }

    @Test
    fun abandonedSessionsCloseButCurrentSessionRemainsActive() {
        repository.upsertSession(session("old-active", RetentionPolicy.SESSION_ONLY))
        repository.upsertSession(session("current", RetentionPolicy.SESSION_ONLY))

        val result = assertIs<CaptureRepositoryResult.Success<Int>>(
            repository.closeAbandonedActiveSessions(
                currentSessionId = "current",
                nowEpochMs = 100,
            ),
        )
        assertEquals(1, result.value)
        assertEquals("CLOSED", database.captureDao().session("old-active")?.state)
        assertEquals("ACTIVE", database.captureDao().session("current")?.state)
    }

    @Test
    fun committedRecordSurvivesDatabaseCloseAndReopen() {
        val name = "m5-reopen-" + System.nanoTime() + ".db"
        context.deleteDatabase(name)

        val firstDb = Room.databaseBuilder(
            context,
            CaptureDatabase::class.java,
            name,
        ).allowMainThreadQueries().build()
        val firstRepo = RoomCaptureRepository.forTesting(
            firstDb,
            context,
            FakeCipher(),
        )
        firstRepo.upsertSession(session("reopen-session", RetentionPolicy.KEEP_UNTIL_DELETED))
        val tx = transaction(
            "reopen-tx",
            "reopen-session",
            "https://api.example.test/reopen",
        )
        assertIs<CaptureRepositoryResult.Success<Unit>>(firstRepo.commit(tx))
        tx.close()
        firstRepo.close()

        val secondDb = Room.databaseBuilder(
            context,
            CaptureDatabase::class.java,
            name,
        ).allowMainThreadQueries().build()
        val secondRepo = RoomCaptureRepository.forTesting(
            secondDb,
            context,
            FakeCipher(),
        )
        try {
            val result = assertIs<CaptureRepositoryResult.Success<*>>(
                secondRepo.list(CaptureQuery(search = "reopen")),
            )
            assertEquals(1, (result.value as List<*>).size)
        } finally {
            secondRepo.close()
            context.deleteDatabase(name)
        }
    }


    private fun session(
        id: String,
        retention: RetentionPolicy,
        closedAt: Long? = null,
    ) = DurableCaptureSession(
        id = id,
        kind = CaptureSessionKind.EPHEMERAL,
        lifecycle = if (closedAt == null) CaptureSessionLifecycle.ACTIVE else CaptureSessionLifecycle.CLOSED,
        retention = retention,
        createdAtEpochMs = 0,
        closedAtEpochMs = closedAt,
    )

    private fun transaction(
        id: String,
        sessionId: String,
        url: String,
        state: DurableTransactionState = DurableTransactionState.COMPLETED,
        tabId: String? = "tab-1",
        relevance: RelevanceCategory = RelevanceCategory.PRIMARY_API,
    ): DurableTransaction {
        val payload = "{\"hello\":\"world\"}".encodeToByteArray()
        return DurableTransaction(
            id = id,
            captureSessionId = sessionId,
            tahoTabId = tabId,
            attribution = "KNOWN",
            extTabId = 7,
            engineRequestId = "req-$id",
            method = "POST",
            url = url,
            query = listOf(DurableQueryValue("page", "1", false)),
            requestHeaders = listOf(DurableHeaderValue("Authorization", "••••••••", true)),
            encryptedRequestHeadersSource = SensitivePayload.utf8("Authorization: Bearer raw"),
            requestBody = DurableBody(
                representation = DurableBodyRepresentation.JSON,
                encoding = DurableBodyEncoding.UTF8,
                contentType = "application/json",
                charset = "utf-8",
                declaredSize = payload.size.toLong(),
                capturedSize = payload.size.toLong(),
                truncated = false,
                completeness = Completeness.COMPLETE,
                payload = SensitivePayload.copyOf(payload),
            ),
            status = 200,
            statusText = "OK",
            responseHeaders = emptyList(),
            encryptedResponseHeadersSource = null,
            responseBody = null,
            state = state,
            relevance = Relevance(
                relevance,
                true,
                "matches workspace host",
                93,
            ),
            observationSource = ObservationSource.ENGINE,
            provenanceJson = "{\"safe\":true}",
            normalizerVersion = "request/1.0.0",
            createdAtEpochMs = 1,
            updatedAtEpochMs = 2,
            isPrivate = false,
        )
    }
}
