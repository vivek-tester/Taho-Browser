package app.taho.browser.transfer.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.taho.browser.contract.TransferReceiptJson
import app.taho.browser.contract.TransferReceiptV1

object TransferReceiptRecoveryStore {
    private const val PREFS = "taho_transfer_receipts"
    private const val KEY_LATEST = "latest"

    fun record(context: Context, receipt: TransferReceiptV1) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LATEST, TransferReceiptJson.encode(receipt))
            .apply()
    }

    fun consumeLatest(context: Context): TransferReceiptV1? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LATEST, null) ?: return null
        prefs.edit().remove(KEY_LATEST).apply()
        return TransferReceiptJson.decode(raw)
    }

    fun clearIfMatches(context: Context, transferId: String) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_LATEST, null)
            ?.let(TransferReceiptJson::decode)
        if (current?.transferId == transferId) {
            prefs.edit().remove(KEY_LATEST).apply()
        }
    }
}

class TransferReceiptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RECEIPT) return

        val expectedTransferId =
            intent.getStringExtra(EXTRA_EXPECTED_TRANSFER_ID).orEmpty()
        val rawReceipt =
            intent.getStringExtra(TahoDirectTransferContract.EXTRA_RECEIPT)
                ?: return
        val receipt = TransferReceiptJson.decode(rawReceipt) ?: return
        if (expectedTransferId.isBlank() || receipt.transferId != expectedTransferId) {
            return
        }

        val appContext = context.applicationContext
        runCatching {
            appContext.revokeUriPermission(
                SecureTransferArtifactProvider.uri(
                    appContext.packageName,
                    receipt.transferId,
                ),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        SecureTransferArtifactStore(appContext).settle(receipt.transferId)
        TransferReceiptRecoveryStore.record(appContext, receipt)
    }

    companion object {
        const val ACTION_RECEIPT =
            "app.taho.browser.action.TRANSFER_RECEIPT"
        const val EXTRA_EXPECTED_TRANSFER_ID =
            "app.taho.browser.extra.EXPECTED_TRANSFER_ID"
    }
}
