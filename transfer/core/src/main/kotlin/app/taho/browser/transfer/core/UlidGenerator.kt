package app.taho.browser.transfer.core

import java.math.BigInteger
import java.security.SecureRandom

object UlidGenerator {
    private const val ENCODED_LENGTH = 26
    private const val RANDOM_BYTES = 10
    private const val TIMESTAMP_BYTES = 6
    private const val TIMESTAMP_MASK = 0xFFFFFFFFFFFFL
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private val random = SecureRandom()

    @Synchronized
    fun next(timestampMillis: Long = System.currentTimeMillis()): String {
        require(timestampMillis >= 0) { "timestampMillis must be non-negative" }
        val bytes = ByteArray(TIMESTAMP_BYTES + RANDOM_BYTES)
        var timestamp = timestampMillis and TIMESTAMP_MASK

        for (index in TIMESTAMP_BYTES - 1 downTo 0) {
            bytes[index] = (timestamp and 0xff).toByte()
            timestamp = timestamp ushr 8
        }

        val randomPart = ByteArray(RANDOM_BYTES)
        random.nextBytes(randomPart)
        randomPart.copyInto(bytes, destinationOffset = TIMESTAMP_BYTES)

        var value = BigInteger(1, bytes)
        val radix = BigInteger.valueOf(32L)
        val output = CharArray(ENCODED_LENGTH) { '0' }

        for (index in ENCODED_LENGTH - 1 downTo 0) {
            val division = value.divideAndRemainder(radix)
            output[index] = ALPHABET[division[1].toInt()]
            value = division[0]
        }

        return output.concatToString()
    }
}
