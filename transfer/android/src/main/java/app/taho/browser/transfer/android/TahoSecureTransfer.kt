package app.taho.browser.transfer.android

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ResultReceiver
import app.taho.browser.contract.BodyEncoding
import app.taho.browser.transfer.core.M4PreparedTransfer

enum class TahoTransferTransport {
    DIRECT_INTENT,
    ARTIFACT_URI,
}

data class TahoTransferDispatch(
    val intent: Intent,
    val transport: TahoTransferTransport,
    val artifactUri: Uri?,
)

class TahoSecureTransferCoordinator(
    context: Context,
    private val artifactStore: SecureTransferArtifactStore =
        SecureTransferArtifactStore(context.applicationContext),
) {
    private val appContext = context.applicationContext

    fun isTargetAvailable(target: TahoDirectTransferTarget): Boolean =
        Intent(target.action)
            .setPackage(target.packageName)
            .resolveActivity(appContext.packageManager) != null

    fun prepareDispatch(
        target: TahoDirectTransferTarget,
        prepared: M4PreparedTransfer,
        resultReceiver: ResultReceiver? = null,
    ): TahoTransferDispatch {
        require(isTargetAvailable(target)) {
            "Compatible Taho receiver is unavailable"
        }

        val receiptPendingIntent = receiptPendingIntent(prepared.envelope.transferId)
        val requiresArtifact =
            prepared.encodedUtf8Bytes.toLong() >
                app.taho.browser.contract.ContractLimits.DIRECT_ENVELOPE_UTF8_BYTES ||
                hasFileUri(prepared)

        if (!requiresArtifact) {
            val intent = TahoDirectTransferIntentFactory.create(
                target = target,
                prepared = prepared,
                resultReceiver = resultReceiver,
            ).putExtra(
                TahoDirectTransferContract.EXTRA_RECEIPT_PENDING_INTENT,
                receiptPendingIntent,
            )
            return TahoTransferDispatch(
                intent = intent,
                transport = TahoTransferTransport.DIRECT_INTENT,
                artifactUri = null,
            )
        }

        val transferId = prepared.envelope.transferId
        artifactStore.create(
            transferId = transferId,
            targetPackage = target.packageName,
            plaintext = prepared.encodedJson.toByteArray(Charsets.UTF_8),
        )
        val uri = SecureTransferArtifactProvider.uri(
            appContext.packageName,
            transferId,
        )

        try {
            appContext.grantUriPermission(
                target.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val intent = Intent(target.action)
                .setPackage(target.packageName)
                .putExtra(
                    TahoDirectTransferContract.EXTRA_TRANSFER_VERSION,
                    prepared.envelope.version,
                )
                .putExtra(
                    TahoDirectTransferContract.EXTRA_TRANSFER_ID,
                    transferId,
                )
                .putExtra(
                    TahoDirectTransferContract.EXTRA_CONTENT_URI,
                    uri,
                )
                .putExtra(
                    TahoDirectTransferContract.EXTRA_RECEIPT_PENDING_INTENT,
                    receiptPendingIntent,
                )
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .also { outgoing ->
                    outgoing.clipData = ClipData.newUri(
                        appContext.contentResolver,
                        "Taho transfer",
                        uri,
                    )
                    if (resultReceiver != null) {
                        outgoing.putExtra(
                            TahoDirectTransferContract.EXTRA_RESULT_RECEIVER,
                            resultReceiver,
                        )
                    }
                }

            return TahoTransferDispatch(
                intent = intent,
                transport = TahoTransferTransport.ARTIFACT_URI,
                artifactUri = uri,
            )
        } catch (t: Throwable) {
            artifactStore.settle(transferId)
            throw t
        }
    }

    fun settle(transferId: String) {
        val uri = SecureTransferArtifactProvider.uri(
            appContext.packageName,
            transferId,
        )
        runCatching {
            appContext.revokeUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        artifactStore.settle(transferId)
    }

    fun cancel(transferId: String) {
        settle(transferId)
    }

    private fun receiptPendingIntent(transferId: String): PendingIntent {
        val intent = Intent(
            appContext,
            TransferReceiptReceiver::class.java,
        )
            .setAction(TransferReceiptReceiver.ACTION_RECEIPT)
            .putExtra(
                TransferReceiptReceiver.EXTRA_EXPECTED_TRANSFER_ID,
                transferId,
            )
        return PendingIntent.getBroadcast(
            appContext,
            transferId.hashCode(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun hasFileUri(prepared: M4PreparedTransfer): Boolean {
        val body = prepared.envelope.request.body ?: return false
        if (body.encoding == BodyEncoding.FILE_URI) return true
        return body.parts.any { it.encoding == BodyEncoding.FILE_URI }
    }
}
