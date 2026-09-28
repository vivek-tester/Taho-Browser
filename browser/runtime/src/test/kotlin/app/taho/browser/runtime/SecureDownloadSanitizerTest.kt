package app.taho.browser.runtime

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureDownloadSanitizerTest {
    @Test
    fun pathTraversalIsStripped() {
        val sanitized = SecureDownloadSanitizer.sanitizeFilename("../../../etc/passwd")
        assertEquals("passwd", sanitized)
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains(".."))
    }

    @Test
    fun controlCharactersAndIllegalFilesystemCharactersReplaced() {
        val sanitized = SecureDownloadSanitizer.sanitizeFilename("report\u0000:test*<file>?.pdf")
        assertEquals("report_test_file_.pdf", sanitized)
    }

    @Test
    fun leadingDotsStrippedToPreventHiddenFiles() {
        val sanitized = SecureDownloadSanitizer.sanitizeFilename("...hidden.zip")
        assertEquals("hidden.zip", sanitized)
    }

    @Test
    fun nullOrEmptyFilenameUsesDefault() {
        assertEquals("download", SecureDownloadSanitizer.sanitizeFilename(null))
        assertEquals("download", SecureDownloadSanitizer.sanitizeFilename("   "))
    }

    @Test
    fun appPrivateDirectoryEnforcesAntiTraversal() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "taho-test-dl")
        tempDir.mkdirs()
        try {
            val file = SecureDownloadSanitizer.getAppPrivateDownloadFile(tempDir, "safe-file.json")
            assertTrue(file.canonicalPath.startsWith(File(tempDir, "downloads").canonicalPath))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
