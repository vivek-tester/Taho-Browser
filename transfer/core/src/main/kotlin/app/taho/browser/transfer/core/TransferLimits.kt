package app.taho.browser.transfer.core

/**
 * Initial engineering budgets from the build plan. They are targets, not measured engine guarantees.
 */
object TransferLimits {
    const val DIRECT_TRANSFER_BYTES: Int = 256 * 1024
    const val BODY_CAPTURE_CAP_BYTES: Int = 8 * 1024 * 1024
    const val ARTIFACT_CAP_BYTES: Int = 32 * 1024 * 1024

    fun requiresLargePayloadPath(encodedBytes: Long): Boolean {
        require(encodedBytes >= 0) { "encodedBytes must be non-negative" }
        return encodedBytes > DIRECT_TRANSFER_BYTES
    }
}
