package app.taho.browser.shell

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class BrowserPersistentState(
    val settings: BrowserSettingsState,
    val searchEngines: List<SearchEngineItem>,
    val topSites: List<TopSiteItem>,
    val bookmarkFolders: List<BookmarkFolderItem>,
    val bookmarks: List<BookmarkItem>,
    val readingList: List<ReadingListItem>,
    val history: List<HistoryEntryItem>,
    val recentlyClosedTabs: List<RecentlyClosedTabItem>,
    val downloads: List<DownloadItemUi>,
    val profiles: List<BrowserProfileUi>,
    val syncedDevices: List<SyncedDeviceUi>,
    val tabGroups: List<TabGroupUi>,
    val sitePermissions: List<SitePermissionEntry>,
    val installedPwas: List<InstalledPwaUi>,
    val offlinePages: List<OfflinePageUi>,
    val collections: List<BrowserCollectionItem>,
    val websiteNotifications: List<WebsiteNotificationItem>,
    val archivedTabs: List<TabArchiveItem>,
    val readerSettings: ReaderSettingsUi,
) : Serializable

internal data class BrowserSensitiveState(
    val passwords: List<SavedPasswordUi>,
    val addresses: List<SavedAddressUi>,
    val payments: List<SavedPaymentUi>,
) : Serializable

/**
 * App-private persistence for normal browser data.
 *
 * The general state file never contains passwords, addresses, or payment data.
 * Sensitive state is serialized only after AES-GCM encryption with a non-exportable
 * Android Keystore key. Corrupt/key-lost vaults fail closed to an empty vault.
 */
internal class TahoBrowserPersistence(context: Context) {
    private val root = File(context.noBackupFilesDir, "browser_state").apply { mkdirs() }
    private val stateFile = File(root, "browser-state-v1.bin")
    private val vaultFile = File(root, "browser-vault-v1.bin")
    private val profileDataFile = File(root, "browser-profiles-v1.bin")

    fun loadState(): BrowserPersistentState? =
        runCatching { readObject(stateFile) as? BrowserPersistentState }.getOrNull()

    fun loadSensitiveState(): BrowserSensitiveState? =
        runCatching {
            if (!vaultFile.exists()) return@runCatching null
            val encrypted = DataInputStream(vaultFile.inputStream().buffered()).use { input ->
                val version = input.readInt()
                require(version == VAULT_VERSION)
                val ivSize = input.readUnsignedByte()
                require(ivSize in 12..32)
                val iv = ByteArray(ivSize).also(input::readFully)
                val cipherText = ByteArray(input.readInt()).also(input::readFully)
                Pair(iv, cipherText)
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(KEY_ALIAS),
                GCMParameterSpec(128, encrypted.first),
            )
            deserialize(cipher.doFinal(encrypted.second)) as? BrowserSensitiveState
        }.getOrNull()

    fun loadProfileData(): Map<String, BrowserProfileLocalData> =
        runCatching {
            if (!profileDataFile.exists()) return@runCatching emptyMap()
            val encrypted = DataInputStream(profileDataFile.inputStream().buffered()).use { input ->
                val version = input.readInt()
                require(version == PROFILE_DATA_VERSION)
                val ivSize = input.readUnsignedByte()
                require(ivSize in 12..32)
                val iv = ByteArray(ivSize).also(input::readFully)
                val cipherSize = input.readInt()
                require(cipherSize in 1..MAX_PROFILE_DATA_BYTES)
                val cipherText = ByteArray(cipherSize).also(input::readFully)
                Pair(iv, cipherText)
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(PROFILE_KEY_ALIAS),
                GCMParameterSpec(128, encrypted.first),
            )
            @Suppress("UNCHECKED_CAST")
            (deserialize(cipher.doFinal(encrypted.second))
                as? Map<String, BrowserProfileLocalData>)
                .orEmpty()
                .filterKeys { PROFILE_ID_PATTERN.matches(it) }
        }.getOrDefault(emptyMap())

    fun saveProfileData(profileData: Map<String, BrowserProfileLocalData>) {
        val safe = profileData.filterKeys { PROFILE_ID_PATTERN.matches(it) }
        val clear = serialize(HashMap(safe))
        require(clear.size <= MAX_PROFILE_DATA_BYTES)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(PROFILE_KEY_ALIAS))
        val cipherText = cipher.doFinal(clear)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(PROFILE_DATA_VERSION)
            data.writeByte(cipher.iv.size)
            data.write(cipher.iv)
            data.writeInt(cipherText.size)
            data.write(cipherText)
        }
        writeAtomically(profileDataFile, output.toByteArray())
    }

    fun save(
        state: BrowserPersistentState,
        sensitive: BrowserSensitiveState,
    ) {
        writeAtomically(stateFile, serialize(state))

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(KEY_ALIAS))
        val cipherText = cipher.doFinal(serialize(sensitive))
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(VAULT_VERSION)
            data.writeByte(cipher.iv.size)
            data.write(cipher.iv)
            data.writeInt(cipherText.size)
            data.write(cipherText)
        }
        writeAtomically(vaultFile, output.toByteArray())
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().buffered().use { it.write(bytes) }
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Unable to commit browser state" }
        }
    }

    private fun serialize(value: Serializable): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(value) }
            buffer.toByteArray()
        }

    private fun deserialize(bytes: ByteArray): Any? =
        ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }

    private fun readObject(file: File): Any? {
        if (!file.exists()) return null
        return ObjectInputStream(file.inputStream().buffered()).use { it.readObject() }
    }

    private companion object {
        const val VAULT_VERSION = 1
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "taho.browser.user-vault.v1"
        const val PROFILE_KEY_ALIAS = "taho.browser.profile-data.v1"
        const val PROFILE_DATA_VERSION = 1
        const val MAX_PROFILE_DATA_BYTES = 64 * 1024 * 1024
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val PROFILE_ID_PATTERN = Regex("""[A-Za-z0-9._-]{1,128}""")
    }
}
