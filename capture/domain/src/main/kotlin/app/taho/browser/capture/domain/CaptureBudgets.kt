package app.taho.browser.capture.domain

object ByteUnits {
    const val KIB: Long = 1024L
    const val MIB: Long = 1024L * KIB
}

data class CaptureBudgets(
    val inlineBodyCiphertextBytes: Long = 64L * ByteUnits.KIB,
    val maxBodyBytes: Long = 8L * ByteUnits.MIB,
    val unparsedQueueDepth: Int = 512,
    val parsedQueueDepth: Int = 2_048,
    val portReadRatePerSecond: Int = 4_000,
    val maxStreamFrames: Int = 2_000,
    val maxTransactionsPerSession: Int = 50_000,
    val maxTransactionsTotal: Int = 250_000,
    val captureDbSoftBytes: Long = 512L * ByteUnits.MIB,
    val directTransferEnvelopeBytes: Long = 256L * ByteUnits.KIB,
    val transferArtifactBytes: Long = 32L * ByteUnits.MIB,
) {
    init {
        require(inlineBodyCiphertextBytes > 0)
        require(maxBodyBytes > 0)
        require(unparsedQueueDepth > 0)
        require(parsedQueueDepth > 0)
        require(portReadRatePerSecond > 0)
        require(maxStreamFrames > 0)
        require(maxTransactionsPerSession > 0)
        require(maxTransactionsTotal >= maxTransactionsPerSession)
        require(captureDbSoftBytes > 0)
        require(directTransferEnvelopeBytes > 0)
        require(transferArtifactBytes >= directTransferEnvelopeBytes)
    }
}

enum class IngressMessageType(val payloadCapBytes: Int) {
    TAB_REGISTER(4 * 1024),
    TAB_TEARDOWN(1 * 1024),
    TX_START(32 * 1024),
    TX_REQ_HEADERS(32 * 1024),
    TX_REQ_BODY(256 * 1024),
    TX_RESP_START(16 * 1024),
    TX_RESP_BODY(256 * 1024),
    TX_REDIRECT(16 * 1024),
    TX_COMPLETE(8 * 1024),
    TX_ERROR(8 * 1024),
    WS_HANDSHAKE(16 * 1024),
    WS_FRAME(32 * 1024),
    SSE_OPEN(8 * 1024),
    CONSOLE(8 * 1024),
    DOM_SIGNAL(16 * 1024),
    HEARTBEAT(1 * 1024),
    DIAG(8 * 1024),
}

sealed interface PayloadBudgetVerdict {
    data object Accept : PayloadBudgetVerdict

    data class Oversize(
        val actualBytes: Int,
        val capBytes: Int,
    ) : PayloadBudgetVerdict
}

object PayloadBudgetGate {
    fun check(
        type: IngressMessageType,
        payloadBytes: Int,
    ): PayloadBudgetVerdict {
        require(payloadBytes >= 0) { "payloadBytes must be non-negative" }
        return if (payloadBytes <= type.payloadCapBytes) {
            PayloadBudgetVerdict.Accept
        } else {
            PayloadBudgetVerdict.Oversize(
                actualBytes = payloadBytes,
                capBytes = type.payloadCapBytes,
            )
        }
    }
}

sealed interface BodyBudgetVerdict {
    data class Accepted(val capturedBytes: Long) : BodyBudgetVerdict

    data class Truncated(
        val capturedBytes: Long,
        val attemptedChunkBytes: Int,
        val capBytes: Long,
    ) : BodyBudgetVerdict

    data class AlreadyTruncated(
        val capturedBytes: Long,
        val capBytes: Long,
    ) : BodyBudgetVerdict
}

/**
 * Tracks the cumulative body ceiling without ever partially accepting a chunk.
 */
class BodyBudgetTracker(
    private val capBytes: Long = CaptureBudgets().maxBodyBytes,
) {
    var capturedBytes: Long = 0
        private set

    var truncated: Boolean = false
        private set

    init {
        require(capBytes > 0) { "capBytes must be positive" }
    }

    fun offerChunk(chunkBytes: Int): BodyBudgetVerdict {
        require(chunkBytes >= 0) { "chunkBytes must be non-negative" }

        if (truncated) {
            return BodyBudgetVerdict.AlreadyTruncated(
                capturedBytes = capturedBytes,
                capBytes = capBytes,
            )
        }

        if (chunkBytes.toLong() > capBytes - capturedBytes) {
            truncated = true
            return BodyBudgetVerdict.Truncated(
                capturedBytes = capturedBytes,
                attemptedChunkBytes = chunkBytes,
                capBytes = capBytes,
            )
        }

        capturedBytes += chunkBytes
        return BodyBudgetVerdict.Accepted(capturedBytes)
    }
}
