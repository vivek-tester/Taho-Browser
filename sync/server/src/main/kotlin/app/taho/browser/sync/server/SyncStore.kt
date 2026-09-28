package app.taho.browser.sync.server

import app.taho.browser.sync.OpaqueSyncState
import app.taho.browser.sync.SendTabEnvelope
import app.taho.browser.sync.SyncDevice
import java.io.File
import java.io.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal data class AccountRecord(
    val id: String,
    val email: String,
    val passwordSaltBase64: String,
    val passwordHashBase64: String,
    val syncSaltBase64: String,
    val tokenHashes: Set<String> = emptySet(),
    val devices: Map<String, SyncDevice> = emptyMap(),
    val state: OpaqueSyncState? = null,
    val sendTabs: List<SendTabEnvelope> = emptyList(),
) : Serializable

private data class StoreSnapshot(
    val accounts: Map<String, AccountRecord>,
) : Serializable

internal class SyncStore(
    private val file: File,
) {
    private val random = SecureRandom()
    private var accounts: Map<String, AccountRecord> = load()

    @Synchronized
    fun register(email: String, password: CharArray): Pair<AccountRecord, String> {
        val normalized = email.trim().lowercase()
        require(accounts.values.none { it.email == normalized }) { "Account already exists" }

        val passwordSalt = ByteArray(32).also(random::nextBytes)
        val syncSalt = ByteArray(32).also(random::nextBytes)
        val account = AccountRecord(
            id = UUID.randomUUID().toString(),
            email = normalized,
            passwordSaltBase64 = Base64.getEncoder().encodeToString(passwordSalt),
            passwordHashBase64 = hashPassword(password, passwordSalt),
            syncSaltBase64 = Base64.getEncoder().encodeToString(syncSalt),
        )
        val (withToken, token) = issueToken(account)
        accounts = accounts + (withToken.id to withToken)
        persist()
        return withToken to token
    }

    @Synchronized
    fun signIn(email: String, password: CharArray): Pair<AccountRecord, String>? {
        val normalized = email.trim().lowercase()
        val account = accounts.values.firstOrNull { it.email == normalized } ?: return null
        val salt = Base64.getDecoder().decode(account.passwordSaltBase64)
        val actual = hashPassword(password, salt)
        if (!MessageDigest.isEqual(
                Base64.getDecoder().decode(actual),
                Base64.getDecoder().decode(account.passwordHashBase64),
            )
        ) return null

        val (updated, token) = issueToken(account)
        accounts = accounts + (updated.id to updated)
        persist()
        return updated to token
    }

    @Synchronized
    fun authenticate(token: String): AccountRecord? {
        val hash = tokenHash(token)
        return accounts.values.firstOrNull { hash in it.tokenHashes }
    }

    @Synchronized
    fun upsertDevice(accountId: String, device: SyncDevice): AccountRecord {
        val account = accounts.getValue(accountId)
        val updated = account.copy(devices = account.devices + (device.id to device))
        accounts = accounts + (accountId to updated)
        persist()
        return updated
    }

    @Synchronized
    fun listDevices(accountId: String): List<SyncDevice> =
        accounts.getValue(accountId).devices.values.sortedByDescending { it.lastSeenEpochMs }

    @Synchronized
    fun getState(accountId: String): OpaqueSyncState? =
        accounts.getValue(accountId).state

    @Synchronized
    fun putState(
        accountId: String,
        expectedRevision: Long?,
        ciphertextBase64: String,
        now: Long,
    ): OpaqueSyncState {
        val account = accounts.getValue(accountId)
        val current = account.state
        if (expectedRevision != null && current?.revision != expectedRevision) {
            throw RevisionConflict(current?.revision ?: 0L)
        }
        val next = OpaqueSyncState(
            revision = (current?.revision ?: 0L) + 1L,
            updatedAtEpochMs = now,
            ciphertextBase64 = ciphertextBase64,
        )
        accounts = accounts + (accountId to account.copy(state = next))
        persist()
        return next
    }

    @Synchronized
    fun enqueueTab(
        accountId: String,
        fromDeviceId: String,
        targetDeviceId: String,
        ciphertextBase64: String,
        now: Long,
    ): SendTabEnvelope {
        val account = accounts.getValue(accountId)
        require(targetDeviceId in account.devices) { "Unknown target device" }
        require(fromDeviceId in account.devices) { "Unknown source device" }
        val message = SendTabEnvelope(
            id = UUID.randomUUID().toString(),
            fromDeviceId = fromDeviceId,
            targetDeviceId = targetDeviceId,
            createdAtEpochMs = now,
            ciphertextBase64 = ciphertextBase64,
        )
        val retained = (account.sendTabs + message).takeLast(200)
        accounts = accounts + (accountId to account.copy(sendTabs = retained))
        persist()
        return message
    }

    @Synchronized
    fun consumeTabs(accountId: String, deviceId: String): List<SendTabEnvelope> {
        val account = accounts.getValue(accountId)
        require(deviceId in account.devices) { "Unknown device" }
        val matches = account.sendTabs.filter { it.targetDeviceId == deviceId }
        if (matches.isNotEmpty()) {
            val ids = matches.mapTo(mutableSetOf()) { it.id }
            accounts = accounts + (
                accountId to account.copy(sendTabs = account.sendTabs.filterNot { it.id in ids })
                )
            persist()
        }
        return matches
    }

    private fun issueToken(account: AccountRecord): Pair<AccountRecord, String> {
        val raw = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val hashes = (account.tokenHashes + tokenHash(token)).takeLastSet(16)
        return account.copy(tokenHashes = hashes) to token
    }

    private fun hashPassword(password: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            Base64.getEncoder().encodeToString(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .encoded,
            )
        } finally {
            spec.clearPassword()
        }
    }

    private fun tokenHash(token: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
        )

    private fun load(): Map<String, AccountRecord> =
        runCatching {
            if (!file.exists()) return@runCatching emptyMap()
            java.io.ObjectInputStream(file.inputStream().buffered()).use { input ->
                (input.readObject() as? StoreSnapshot)?.accounts.orEmpty()
            }
        }.getOrDefault(emptyMap())

    private fun persist() {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        java.io.ObjectOutputStream(temp.outputStream().buffered()).use {
            it.writeObject(StoreSnapshot(accounts))
        }
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Unable to commit sync store" }
        }
    }

    private fun Set<String>.takeLastSet(max: Int): Set<String> =
        if (size <= max) this else drop(size - max).toSet()
}

internal class RevisionConflict(
    val currentRevision: Long,
) : IllegalStateException("Sync revision conflict")
