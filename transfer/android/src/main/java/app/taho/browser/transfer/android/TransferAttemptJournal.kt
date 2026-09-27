package app.taho.browser.transfer.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class PendingTransferJournalEntry(
    val transferId: String,
    val transport: TahoTransferTransport,
    val targetPackage: String,
    val createdElapsedMs: Long,
    val expiresElapsedMs: Long,
    val bootId: String,
)

internal class TransferAttemptJournal(
    context: Context,
    private val clock: TransferArtifactClock =
        AndroidTransferArtifactClock(context.applicationContext),
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    fun begin(
        transferId: String,
        transport: TahoTransferTransport,
        targetPackage: String,
    ) = synchronized(lock) {
        val now = clock.elapsedRealtimeMs()
        val bootId = clock.bootId()
            ?: error("Transfer lifecycle unavailable because boot identity is unavailable")
        val entries = read()
        entries.removeAll { it.transferId == transferId }
        entries += PendingTransferJournalEntry(
            transferId = transferId,
            transport = transport,
            targetPackage = targetPackage,
            createdElapsedMs = now,
            expiresElapsedMs = safeAdd(
                now,
                SecureTransferArtifactStore.ACCESS_TTL_MS,
            ),
            bootId = bootId,
        )
        write(entries.takeLast(MAX_PENDING))
    }

    fun settle(transferId: String) = synchronized(lock) {
        val entries = read()
        val changed = entries.removeAll { it.transferId == transferId }
        if (changed) write(entries)
    }

    fun recoverExpired(): List<PendingTransferJournalEntry> = synchronized(lock) {
        val now = clock.elapsedRealtimeMs()
        val bootId = clock.bootId()
        val entries = read()
        val expired = entries.filter { entry ->
            bootId == null ||
                entry.bootId != bootId ||
                now < entry.createdElapsedMs ||
                now >= entry.expiresElapsedMs
        }
        if (expired.isNotEmpty()) {
            val ids = expired.mapTo(mutableSetOf()) { it.transferId }
            write(entries.filterNot { it.transferId in ids })
        }
        expired
    }

    fun pendingForTesting(): List<PendingTransferJournalEntry> =
        synchronized(lock) { read() }

    private fun read(): MutableList<PendingTransferJournalEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { index ->
                val json = array.getJSONObject(index)
                PendingTransferJournalEntry(
                    transferId = json.getString("transferId"),
                    transport = TahoTransferTransport.valueOf(
                        json.getString("transport"),
                    ),
                    targetPackage = json.getString("targetPackage"),
                    createdElapsedMs = json.getLong("createdElapsedMs"),
                    expiresElapsedMs = json.getLong("expiresElapsedMs"),
                    bootId = json.getString("bootId"),
                ).also { entry ->
                    require(entry.transferId.isNotBlank())
                    require(entry.targetPackage.isNotBlank())
                    require(entry.createdElapsedMs >= 0)
                    require(entry.expiresElapsedMs >= entry.createdElapsedMs)
                }
            }
        }.getOrElse {
            prefs.edit().remove(KEY_ENTRIES).apply()
            mutableListOf()
        }
    }

    private fun write(entries: List<PendingTransferJournalEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("transferId", entry.transferId)
                    put("transport", entry.transport.name)
                    put("targetPackage", entry.targetPackage)
                    put("createdElapsedMs", entry.createdElapsedMs)
                    put("expiresElapsedMs", entry.expiresElapsedMs)
                    put("bootId", entry.bootId)
                },
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).commit()
    }

    private fun safeAdd(value: Long, delta: Long): Long =
        if (Long.MAX_VALUE - value < delta) Long.MAX_VALUE else value + delta

    private companion object {
        const val PREFS = "taho_transfer_attempts"
        const val KEY_ENTRIES = "pending"
        const val MAX_PENDING = 8
    }
}
