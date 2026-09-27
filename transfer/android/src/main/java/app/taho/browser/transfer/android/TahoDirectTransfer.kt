package app.taho.browser.transfer.android

import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import app.taho.browser.contract.ContractLimits
import app.taho.browser.contract.TransferReceiptJson
import app.taho.browser.contract.TransferReceiptV1
import app.taho.browser.transfer.core.M4PreparedTransfer

data class TahoDirectTransferTarget(
    val packageName: String,
    val action: String,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(action.isNotBlank()) { "action must not be blank" }
    }
}

object TahoDirectTransferContract {
    const val EXTRA_TRANSFER_VERSION = "app.taho.extra.TRANSFER_VERSION"
    const val EXTRA_TRANSFER_ID = "app.taho.extra.TRANSFER_ID"
    const val EXTRA_PAYLOAD = "app.taho.extra.TRANSFER_PAYLOAD"
    const val EXTRA_CONTENT_URI = "app.taho.extra.TRANSFER_CONTENT_URI"
    const val EXTRA_RECEIPT = "app.taho.extra.TRANSFER_RECEIPT"
    const val EXTRA_RESULT_RECEIVER = "app.taho.extra.RESULT_RECEIVER"
    const val EXTRA_RECEIPT_PENDING_INTENT = "app.taho.extra.RECEIPT_PENDING_INTENT"
}

object TahoDirectTransferIntentFactory {
    fun create(
        target: TahoDirectTransferTarget,
        prepared: M4PreparedTransfer,
        resultReceiver: ResultReceiver? = null,
    ): Intent {
        require(
            prepared.encodedUtf8Bytes <= ContractLimits.DIRECT_ENVELOPE_UTF8_BYTES,
        ) {
            "Direct transfer exceeds configured envelope budget"
        }

        return Intent(target.action)
            .setPackage(target.packageName)
            .putExtra(
                TahoDirectTransferContract.EXTRA_TRANSFER_VERSION,
                prepared.envelope.version,
            )
            .putExtra(
                TahoDirectTransferContract.EXTRA_TRANSFER_ID,
                prepared.envelope.transferId,
            )
            .putExtra(
                TahoDirectTransferContract.EXTRA_PAYLOAD,
                prepared.encodedJson,
            )
            .also { intent ->
                if (resultReceiver != null) {
                    intent.putExtra(
                        TahoDirectTransferContract.EXTRA_RESULT_RECEIVER,
                        resultReceiver,
                    )
                }
            }
    }

    fun parseReceipt(data: Intent?): TransferReceiptV1? {
        val raw = data?.getStringExtra(TahoDirectTransferContract.EXTRA_RECEIPT)
            ?: return null
        return TransferReceiptJson.decode(raw)
    }

    fun parseReceipt(bundle: Bundle?): TransferReceiptV1? {
        val raw = bundle?.getString(TahoDirectTransferContract.EXTRA_RECEIPT)
            ?: return null
        return TransferReceiptJson.decode(raw)
    }
}
