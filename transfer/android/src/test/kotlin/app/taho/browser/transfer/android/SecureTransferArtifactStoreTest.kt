package app.taho.browser.transfer.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.taho.browser.contract.CaptureCompleteness
import app.taho.browser.contract.Completeness
import app.taho.browser.contract.ObservationSource
import app.taho.browser.contract.RequestTransferV1
import app.taho.browser.contract.SafeProvenance
import app.taho.browser.contract.SecretPolicy
import app.taho.browser.contract.TransferCapture
import app.taho.browser.contract.TransferRequest
import app.taho.browser.contract.TransferSecurity
import app.taho.browser.contract.TransferSource
import app.taho.browser.transfer.core.M4DisplayRequest
import app.taho.browser.transfer.core.M4PreparedTransfer
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureTransferArtifactStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val roots = mutableListOf<File>()

    private class FakeClock(
        var now: Long = 1_000,
        var boot: String? = "boot-1",
    ) : TransferArtifactClock {
        override fun elapsedRealtimeMs(): Long = now
        override fun bootId(): String? = boot
    }

    private class FakeCipher : TransferArtifactCipher {
        override fun encryptToFile(plaintext: ByteArray, destination: File) {
            destination.parentFile?.mkdirs()
            destination.writeBytes(
                plaintext.map { (it.toInt() xor 0x5a).toByte() }.toByteArray(),
            )
        }

        override fun openDecrypted(file: File): InputStream =
            ByteArrayInputStream(
                file.readBytes()
                    .map { (it.toInt() xor 0x5a).toByte() }
                    .toByteArray(),
            )
    }

    @AfterTest
    fun tearDown() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun authorizedReadIsOneTimeAndEncryptedAtRest() {
        val clock = FakeClock()
        val root = root()
        val store = store(root, clock)
        val payload = "secret transfer envelope".encodeToByteArray()

        store.create(
            transferId = TRANSFER_ID,
            targetPackage = TAHO_PACKAGE,
            plaintext = payload,
        )

        val artifact = root.listFiles()
            .orEmpty()
            .single { it.name.endsWith(".artifact") }
        assertFalse(artifact.readBytes().contentEquals(payload))

        assertIs<TransferArtifactClaim.Granted>(
            store.claim(TRANSFER_ID, setOf(TAHO_PACKAGE)),
        )
        assertContentEquals(payload, store.openDecrypted(TRANSFER_ID).use { it.readBytes() })

        val second = assertIs<TransferArtifactClaim.Denied>(
            store.claim(TRANSFER_ID, setOf(TAHO_PACKAGE)),
        )
        assertEquals(
            TransferArtifactClaimFailure.ALREADY_CONSUMED,
            second.reason,
        )
    }

    @Test
    fun wrongPackageAndExpiredOrRebootedArtifactsFailClosed() {
        val clock = FakeClock()
        val store = store(root(), clock)
        store.create(TRANSFER_ID, TAHO_PACKAGE, byteArrayOf(1, 2, 3))

        val wrong = assertIs<TransferArtifactClaim.Denied>(
            store.claim(TRANSFER_ID, setOf("attacker.example")),
        )
        assertEquals(TransferArtifactClaimFailure.WRONG_PACKAGE, wrong.reason)

        clock.now += SecureTransferArtifactStore.ACCESS_TTL_MS
        val expired = assertIs<TransferArtifactClaim.Denied>(
            store.claim(TRANSFER_ID, setOf(TAHO_PACKAGE)),
        )
        assertEquals(TransferArtifactClaimFailure.EXPIRED, expired.reason)

        store.settle(TRANSFER_ID)
        clock.now = 2_000
        store.create(TRANSFER_ID, TAHO_PACKAGE, byteArrayOf(4, 5, 6))
        clock.boot = "boot-2"
        val rebooted = assertIs<TransferArtifactClaim.Denied>(
            store.claim(TRANSFER_ID, setOf(TAHO_PACKAGE)),
        )
        assertEquals(TransferArtifactClaimFailure.REBOOTED, rebooted.reason)
    }

    @Test
    fun accessExpiryDoesNotDependOnCleanupSchedule() {
        val clock = FakeClock()
        val store = store(root(), clock)
        store.create(TRANSFER_ID, TAHO_PACKAGE, byteArrayOf(7))

        clock.now += SecureTransferArtifactStore.ACCESS_TTL_MS + 1
        assertTrue(store.contains(TRANSFER_ID))
        assertEquals(0, store.sweep())
        assertTrue(store.contains(TRANSFER_ID))

        clock.now =
            1_000 + SecureTransferArtifactStore.ABANDONED_CLEANUP_MS + 1
        assertEquals(1, store.sweep())
        assertFalse(store.contains(TRANSFER_ID))
    }

    @Test
    fun fiveMiBArtifactIntentNeverCarriesEnvelopePayloadExtra() {
        val prepared = preparedTransfer(5 * 1024 * 1024)
        val receiptIntent = Intent(context, TransferReceiptReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            7,
            receiptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val uri = SecureTransferArtifactProvider.uri(
            context.packageName,
            prepared.envelope.transferId,
        )

        val intent = TahoArtifactTransferIntentFactory.create(
            context = context,
            target = TahoDirectTransferTarget(
                packageName = TAHO_PACKAGE,
                action = "com.eternal.taho.action.IMPORT_TAHO_REQUEST",
            ),
            prepared = prepared,
            uri = uri,
            receiptPendingIntent = pendingIntent,
        )

        assertEquals(
            uri,
            intent.getParcelableExtra(TahoDirectTransferContract.EXTRA_CONTENT_URI),
        )
        assertFalse(intent.hasExtra(TahoDirectTransferContract.EXTRA_PAYLOAD))
        assertTrue(
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    private fun store(
        root: File,
        clock: FakeClock,
    ) = SecureTransferArtifactStore(
        context = context,
        cipher = FakeCipher(),
        clock = clock,
        root = root,
    )

    private fun root(): File =
        File(context.cacheDir, "m6-artifact-" + System.nanoTime()).also {
            roots += it
        }

    private fun preparedTransfer(payloadBytes: Int): M4PreparedTransfer {
        val envelope = RequestTransferV1(
            transferId = TRANSFER_ID,
            issuedAt = 1,
            source = TransferSource(
                appVersion = "test",
                engine = "test",
                captureSessionId = "capture",
                tabId = "tab",
                observation = ObservationSource.ENGINE,
            ),
            request = TransferRequest(
                id = "request",
                method = "POST",
                url = "https://example.test/api",
            ),
            capture = TransferCapture(
                timestamp = 1,
                durationMs = null,
                status = null,
                statusText = null,
                initiator = null,
                timing = null,
                completeness = CaptureCompleteness(
                    requestUrl = Completeness.COMPLETE,
                    requestHeaders = Completeness.COMPLETE,
                    requestBody = Completeness.COMPLETE,
                    responseHeaders = Completeness.UNAVAILABLE,
                    responseBody = Completeness.UNAVAILABLE,
                    timing = Completeness.UNAVAILABLE,
                    tlsInfo = Completeness.UNAVAILABLE,
                ),
            ),
            security = TransferSecurity(
                secretPolicy = SecretPolicy.PARAMETERIZE,
                containsSensitiveData = false,
                secretCount = 0,
                fromPrivateSession = false,
                reviewRequired = false,
            ),
            provenance = SafeProvenance(
                normalizerVersion = "request/1",
                captureEngineVersion = "test",
                capturedAt = 1,
                redirectCount = 0,
            ),
        )
        val encoded = "x".repeat(payloadBytes)
        return M4PreparedTransfer(
            envelope = envelope,
            encodedJson = encoded,
            encodedUtf8Bytes = encoded.toByteArray().size,
            display = M4DisplayRequest(
                method = "POST",
                url = "https://example.test/api",
                status = null,
                durationMs = null,
                headers = emptyList(),
                requestBodyCompleteness = Completeness.COMPLETE,
                responseBodyCompleteness = Completeness.UNAVAILABLE,
                bodyRepresentation = null,
                bodyLimitation = null,
                sensitiveCount = 0,
            ),
        )
    }

    private companion object {
        const val TRANSFER_ID = "01J8ZQ4M2K7X9V3B8N0P4R6T8Y"
        const val TAHO_PACKAGE = "com.eternal.taho"
    }
}
