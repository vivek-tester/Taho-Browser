package app.taho.browser.testsupport.receiver

import app.taho.browser.contract.ContractViolationCode
import app.taho.browser.contract.ReceiptResult
import app.taho.browser.contract.RequestTransferV1
import app.taho.browser.contract.TransferContractValidator
import app.taho.browser.contract.TransferErrorCode
import app.taho.browser.contract.TransferReceiptV1

data class HarnessImportedRequest(
    val requestId: String,
    val transferId: String,
    val method: String,
    val url: String,
    val importedAt: Long,
)

data class HarnessReceiverSnapshot(
    val stagedImports: List<HarnessImportedRequest>,
    val executionCount: Int,
    val persistenceCount: Int,
)

class TahoReceiverHarness(
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val imports = linkedMapOf<String, HarnessImportedRequest>()

    @Synchronized
    fun receive(
        envelope: RequestTransferV1,
        encodedEnvelopeUtf8Bytes: Long,
    ): TransferReceiptV1 {
        val violations = TransferContractValidator.validate(
            envelope = envelope,
            encodedEnvelopeUtf8Bytes = encodedEnvelopeUtf8Bytes,
        )
        if (violations.isNotEmpty()) {
            val error = when {
                violations.any { it.code == ContractViolationCode.UNSUPPORTED_VERSION } ->
                    TransferErrorCode.TAHO_TRANSFER_UNSUPPORTED_VERSION
                violations.any { it.code == ContractViolationCode.INVALID_SCHEMA } ->
                    TransferErrorCode.TAHO_TRANSFER_INVALID_SCHEMA
                violations.any {
                    it.code == ContractViolationCode.BODY_CAP_EXCEEDED ||
                        it.code == ContractViolationCode.ARTIFACT_CAP_EXCEEDED
                } -> TransferErrorCode.TAHO_TRANSFER_TOO_LARGE
                else -> TransferErrorCode.TAHO_TRANSFER_IMPORT_FAILED
            }
            return TransferReceiptV1(
                transferId = envelope.transferId,
                result = if (error == TransferErrorCode.TAHO_TRANSFER_UNSUPPORTED_VERSION) {
                    ReceiptResult.UNSUPPORTED
                } else {
                    ReceiptResult.REJECTED
                },
                requestId = null,
                importedAt = null,
                errorCode = error,
            )
        }

        imports[envelope.transferId]?.let { existing ->
            return TransferReceiptV1(
                transferId = envelope.transferId,
                result = ReceiptResult.DUPLICATE,
                requestId = existing.requestId,
                importedAt = existing.importedAt,
                errorCode = null,
            )
        }

        val importedAt = clockMillis()
        val imported = HarnessImportedRequest(
            requestId = "harness-request-" + (imports.size + 1),
            transferId = envelope.transferId,
            method = envelope.request.method,
            url = envelope.request.url,
            importedAt = importedAt,
        )
        imports[envelope.transferId] = imported

        return TransferReceiptV1(
            transferId = envelope.transferId,
            result = ReceiptResult.IMPORTED,
            requestId = imported.requestId,
            importedAt = importedAt,
            errorCode = null,
        )
    }

    @Synchronized
    fun snapshot(): HarnessReceiverSnapshot =
        HarnessReceiverSnapshot(
            stagedImports = imports.values.toList(),
            executionCount = 0,
            persistenceCount = 0,
        )
}
