package app.taho.browser.transfer.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

class SecureTransferArtifactProvider : ContentProvider() {
    private lateinit var store: SecureTransferArtifactStore

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        store = SecureTransferArtifactStore(appContext)
        return true
    }

    override fun getType(uri: Uri): String? =
        if (transferId(uri) != null) MIME_TYPE else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw FileNotFoundException("Transfer artifacts are read-only")
        }

        val id = transferId(uri)
            ?: throw FileNotFoundException("Unknown transfer artifact")
        val appContext = context?.applicationContext
            ?: throw FileNotFoundException("Provider unavailable")
        val callerPackages = appContext.packageManager
            .getPackagesForUid(Binder.getCallingUid())
            .orEmpty()
            .toSet()

        when (val claim = store.claim(id, callerPackages)) {
            is TransferArtifactClaim.Denied -> {
                when (claim.reason) {
                    TransferArtifactClaimFailure.WRONG_PACKAGE ->
                        throw SecurityException("Transfer artifact access denied")
                    TransferArtifactClaimFailure.EXPIRED,
                    TransferArtifactClaimFailure.REBOOTED ->
                        throw FileNotFoundException("Transfer artifact expired")
                    else ->
                        throw FileNotFoundException("Transfer artifact unavailable")
                }
            }
            is TransferArtifactClaim.Granted -> Unit
        }

        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        Thread(
            {
                runCatching {
                    store.openDecrypted(id).use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                            input.copyTo(output, DEFAULT_BUFFER_SIZE)
                        }
                    }
                }.onFailure {
                    runCatching { writeSide.closeWithError("transfer stream failed") }
                }
            },
            "taho-transfer-artifact",
        ).apply {
            isDaemon = true
            start()
        }

        return readSide
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun transferId(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != PATH_ARTIFACT) return null
        return segments[1].takeIf {
            it.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))
        }
    }

    companion object {
        const val PATH_ARTIFACT = "artifact"
        const val MIME_TYPE = "application/vnd.taho.request-transfer+json"

        fun authority(packageName: String): String =
            packageName + ".transfer.artifacts"

        fun uri(packageName: String, transferId: String): Uri =
            Uri.Builder()
                .scheme("content")
                .authority(authority(packageName))
                .appendPath(PATH_ARTIFACT)
                .appendPath(transferId)
                .build()
    }
}
