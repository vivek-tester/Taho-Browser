package app.taho.browser.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TahoFeaturesTest {

    @Test
    fun testDefaultStateInitialization() {
        val store = TahoBrowserStateStore
        assertNotNull(store.settings)
        assertEquals(TahoThemeMode.DARK, store.settings.themeMode)
        assertEquals(TahoToolbarPosition.BOTTOM, store.settings.toolbarPosition)
        assertEquals(TahoTrackingProtectionLevel.STRICT, store.settings.trackingProtectionLevel)
        assertTrue(store.settings.httpsOnlyMode)
        assertTrue(store.settings.blockTrackers)
        assertTrue(store.settings.blockThirdPartyCookies)
        assertTrue(store.settings.fingerprintingProtection)
        assertTrue(store.searchEngines.isNotEmpty())
        assertTrue(store.topSites.isEmpty())
        assertTrue(store.savedPasswords.isEmpty())
        assertEquals(1, store.profiles.size)
        assertFalse(store.profiles.single().syncEnabled)
    }

    @Test
    fun testBookmarkAdditionAndRemoval() {
        val store = TahoBrowserStateStore
        val initialCount = store.bookmarks.size
        val testUrl = "https://test-site.org/docs"
        val testTitle = "Test Docs Site"

        store.addBookmark(testTitle, testUrl)
        val added = store.bookmarks.find { it.url == testUrl }
        assertNotNull(added)
        assertEquals(testTitle, added.title)
        assertTrue(added.isFavorite)

        store.removeBookmark(added.id)
        assertFalse(store.bookmarks.any { it.url == testUrl })
        assertEquals(initialCount, store.bookmarks.size)
    }

    @Test
    fun testReadingListWorkflow() {
        val store = TahoBrowserStateStore
        val initialCount = store.readingList.size
        val testUrl = "https://article.example/security"
        val testTitle = "Security Article"

        store.addReadingListItem(testTitle, testUrl)
        val item = store.readingList.find { it.url == testUrl }
        assertNotNull(item)
        assertFalse(item.isRead)

        store.toggleReadingListRead(item.id)
        val updated = store.readingList.find { it.id == item.id }
        assertNotNull(updated)
        assertTrue(updated.isRead)

        store.removeReadingListItem(item.id)
        assertEquals(initialCount, store.readingList.size)
    }

    @Test
    fun testHistoryRecordingAndRevisit() {
        val store = TahoBrowserStateStore
        val testUrl = "https://unique-history-target.test/page"
        val title1 = "First Visit"
        val title2 = "Second Visit"

        store.recordHistory(title1, testUrl)
        val firstRecord = store.history.find { it.url == testUrl }
        assertNotNull(firstRecord)
        assertEquals(1, firstRecord.visitCount)

        store.recordHistory(title2, testUrl)
        val secondRecord = store.history.find { it.url == testUrl }
        assertNotNull(secondRecord)
        assertEquals(2, secondRecord.visitCount)
        assertEquals(title2, secondRecord.title)

        store.removeHistoryEntry(secondRecord.id)
        assertFalse(store.history.any { it.url == testUrl })
    }

    @Test
    fun testRecentlyClosedTabRestore() {
        val store = TahoBrowserStateStore
        val testUrl = "https://tab-to-close.org"
        val testTitle = "Closing Tab"

        store.recordClosedTab(testTitle, testUrl, isPrivate = false)
        val closed = store.recentlyClosedTabs.find { it.url == testUrl }
        assertNotNull(closed)
        assertEquals(testTitle, closed.title)

        val restored = store.restoreClosedTab(closed.id)
        assertNotNull(restored)
        assertEquals(testUrl, restored.url)
        assertFalse(store.recentlyClosedTabs.any { it.id == closed.id })
    }

    @Test
    fun privateTabsNeverEnterRecentlyClosedHistory() {
        val store = TahoBrowserStateStore
        val before = store.recentlyClosedTabs.size
        store.recordClosedTab(
            title = "Private",
            url = "https://private.example",
            isPrivate = true,
        )
        assertEquals(before, store.recentlyClosedTabs.size)
        assertFalse(store.recentlyClosedTabs.any { it.url == "https://private.example" })
    }

    @Test
    fun testPasswordGeneratorAndClassification() {
        val store = TahoBrowserStateStore
        val generated = store.generateStrongPassword(length = 20, useSpecial = true)
        assertEquals(20, generated.length)

        store.addSavedPassword("banking.example.com", "user@example.com", "weak")
        val weakEntry = store.savedPasswords.find { it.domain == "banking.example.com" }
        assertNotNull(weakEntry)
        assertTrue(weakEntry.isWeak)

        store.removeSavedPassword(weakEntry.id)
        assertFalse(store.savedPasswords.any { it.domain == "banking.example.com" })
    }

    @Test
    fun testSitePermissionsUpdateAndReset() {
        val store = TahoBrowserStateStore
        val origin = "https://camera-app.local"

        store.updateSitePermission(origin, "Camera", "ALLOW")
        val perm = store.sitePermissions.find { it.origin == origin && it.permission == "Camera" }
        assertNotNull(perm)
        assertEquals("ALLOW", perm.state)

        store.updateSitePermission(origin, "Camera", "BLOCK")
        val updated = store.sitePermissions.find { it.origin == origin && it.permission == "Camera" }
        assertNotNull(updated)
        assertEquals("BLOCK", updated.state)

        store.resetSitePermissions(origin)
        assertFalse(store.sitePermissions.any { it.origin == origin })
    }

    @Test
    fun testDownloadStatusTransitions() {
        val store = TahoBrowserStateStore
        val dl = DownloadItemUi(
            id = "test-download",
            fileName = "artifact.bin",
            url = "https://downloads.test/artifact.bin",
            bytesDownloaded = 128,
            totalBytes = 1024,
            status = TahoDownloadStatus.DOWNLOADING,
        )
        store.upsertDownload(dl)

        store.pauseResumeDownload(dl.id)
        val toggled = store.downloads.find { it.id == dl.id }
        assertNotNull(toggled)

        store.cancelDownload(dl.id)
        val cancelled = store.downloads.find { it.id == dl.id }
        assertNotNull(cancelled)
        assertEquals(TahoDownloadStatus.CANCELLED, cancelled.status)

        store.retryDownload(dl.id)
        val retried = store.downloads.find { it.id == dl.id }
        assertNotNull(retried)
        assertEquals(TahoDownloadStatus.DOWNLOADING, retried.status)
        assertEquals(0L, retried.bytesDownloaded)
    }

    @Test
    fun testProfileSwitching() {
        val store = TahoBrowserStateStore
        store.profiles = store.profiles.filterNot { it.id == "profile_work" } +
            BrowserProfileUi(
                id = "profile_work",
                name = "Work",
                avatarGlyph = "W",
            )
        store.switchProfile("profile_work")

        val active = store.profiles.find { it.isActive }
        assertNotNull(active)
        assertEquals("profile_work", active.id)
        assertEquals("profile_work", store.settings.currentProfileId)

        // Switch back to personal
        store.switchProfile("profile_personal")
        val backActive = store.profiles.find { it.isActive }
        assertNotNull(backActive)
        assertEquals("profile_personal", backActive.id)
    }

    @Test
    fun testClearBrowsingData() {
        val store = TahoBrowserStateStore
        store.recordHistory("Temp Page", "https://temp-clear.test")
        assertTrue(store.history.any { it.url == "https://temp-clear.test" })

        store.clearBrowsingData(
            clearCache = true,
            clearCookies = true,
            clearHistory = true,
            clearFormData = false,
            clearPasswords = false,
            clearDownloads = false,
            timeRange = "ALL_TIME",
        )

        assertTrue(store.history.isEmpty())
    }

    @Test
    fun testBookmarkFoldersAndHtmlExportImport() {
        val store = TahoBrowserStateStore
        store.addBookmarkFolder("Research")
        val folder = store.bookmarkFolders.last { it.name == "Research" }
        store.addBookmark(
            "ArXiv Paper",
            "https://arxiv.org/abs/2301.00001",
            folderId = folder.id,
        )

        val bm = store.bookmarks.find { it.url == "https://arxiv.org/abs/2301.00001" }
        assertNotNull(bm)
        assertEquals(folder.id, bm.folderId)

        val html = store.exportBookmarksHtml()
        assertTrue(html.contains("ArXiv Paper"))
        assertTrue(html.contains("https://arxiv.org/abs/2301.00001"))

        val importHtml = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>
            <DL><p>
            <DT><A HREF="https://imported-sample.org">Imported Sample</A>
            </DL><p>
        """.trimIndent()
        val importedCount = store.importBookmarksFromHtml(importHtml)
        assertTrue(importedCount >= 1)
        assertTrue(store.bookmarks.any { it.url == "https://imported-sample.org" })

        store.removeBookmark(bm.id)
    }

    @Test
    fun testPasswordEditingAndJsonExportImport() {
        val store = TahoBrowserStateStore
        store.savePassword("test-auth.org", "alice", "SuperSecret123!")
        val saved = store.savedPasswords.find { it.domain == "test-auth.org" }
        assertNotNull(saved)
        assertEquals("alice", saved.username)

        store.updateSavedPassword(saved.id, "alice_updated", "NewStrongP@ss999!")
        val updated = store.savedPasswords.find { it.id == saved.id }
        assertNotNull(updated)
        assertEquals("alice_updated", updated.username)
        assertEquals("NewStrongP@ss999!", updated.password)

        val exportedJson = store.exportPasswordsJson()
        assertTrue(exportedJson.contains("test-auth.org"))
        assertTrue(exportedJson.contains("alice_updated"))

        val importJson = """[{"domain":"import-cred.com","username":"bob","password":"BobSecretPassword!"}]"""
        val count = store.importPasswordsFromJson(importJson)
        assertEquals(1, count)
        assertTrue(store.savedPasswords.any { it.domain == "import-cred.com" })

        store.removeSavedPassword(saved.id)
    }

    @Test
    fun testSavedAddressAndPaymentCrud() {
        val store = TahoBrowserStateStore
        val initialAddressCount = store.savedAddresses.size
        val initialPaymentCount = store.savedPayments.size

        store.addSavedAddress("HQ", "Ada Lovelace", "123 Logic Way", "London", "Greater London", "SW1A 1AA")
        val addr = store.savedAddresses.find { it.fullName == "Ada Lovelace" }
        assertNotNull(addr)
        assertEquals("HQ", addr.label)

        store.removeSavedAddress(addr.id)
        assertEquals(initialAddressCount, store.savedAddresses.size)

        store.addSavedPayment("Ada Lovelace", "4111222233334444", "12/28", "Visa")
        val pay = store.savedPayments.find { it.cardHolder == "Ada Lovelace" }
        assertNotNull(pay)
        assertTrue(pay.cardNumberMasked.contains("4444"))

        store.removeSavedPayment(pay.id)
        assertEquals(initialPaymentCount, store.savedPayments.size)
    }

    @Test
    fun testCustomSearchEngineCrud() {
        val store = TahoBrowserStateStore
        val initialCount = store.searchEngines.size
        store.addSearchEngine("SearXNG", "https://searx.be/search?q=%s")

        val added = store.searchEngines.find { it.name == "SearXNG" }
        assertNotNull(added)
        assertEquals("https://searx.be/search?q=%s", added.queryUrl)

        store.removeSearchEngine(added.id)
        assertEquals(initialCount, store.searchEngines.size)
    }

    @Test
    fun testOfflinePagesAndPwasManager() {
        val store = TahoBrowserStateStore
        val offlinePage = OfflinePageUi(
            id = "test-off-1",
            title = "Cached Spec",
            url = "https://spec.taho.app",
            sizeBytes = 204800L,
        )
        store.offlinePages = store.offlinePages + offlinePage
        assertTrue(store.offlinePages.any { it.id == "test-off-1" })

        store.removeOfflinePage("test-off-1")
        assertFalse(store.offlinePages.any { it.id == "test-off-1" })

        store.installPwa("Taho Chat PWA", "https://chat.taho.app", "https://chat.taho.app/")
        val pwa = store.installedPwas.find { it.name == "Taho Chat PWA" }
        assertNotNull(pwa)

        store.uninstallPwa(pwa.id)
        assertFalse(store.installedPwas.any { it.name == "Taho Chat PWA" })
    }

    @Test
    fun testCollectionsAndNotifications() {
        val store = TahoBrowserStateStore
        store.addCollection("Quantum Research", "Papers and notebooks on QEC")
        val col = store.collections.find { it.name == "Quantum Research" }
        assertNotNull(col)
        assertEquals("Papers and notebooks on QEC", col.description)

        store.removeCollection(col.id)
        assertFalse(store.collections.any { it.id == col.id })

        store.addWebsiteNotification(
            origin = "https://notify.test",
            title = "Test notification",
            message = "Created by the test",
        )
        val notifCount = store.websiteNotifications.size
        assertTrue(notifCount >= 1)
        val notif = store.websiteNotifications.first()
        store.removeWebsiteNotification(notif.id)
        assertEquals(notifCount - 1, store.websiteNotifications.size)

        store.clearWebsiteNotifications()
        assertTrue(store.websiteNotifications.isEmpty())
    }

    @Test
    fun testTabArchiveAndRestore() {
        val store = TahoBrowserStateStore
        store.archiveTab("Archived Article", "https://news.ycombinator.com")
        val archived = store.archivedTabs.find { it.url == "https://news.ycombinator.com" }
        assertNotNull(archived)
        assertEquals("Archived Article", archived.title)

        val restored = store.restoreArchivedTab(archived.id)
        assertNotNull(restored)
        assertEquals("https://news.ycombinator.com", restored.url)
        assertFalse(store.archivedTabs.any { it.id == archived.id })
    }

    @Test
    fun testPerSiteZoomDesktopAndTrackingExceptions() {
        val store = TahoBrowserStateStore
        val origin = "https://responsive.design.test"

        assertEquals(100, store.getZoomForOrigin(origin))
        store.setZoomForOrigin(origin, 175)
        assertEquals(175, store.getZoomForOrigin(origin))

        assertFalse(store.isDesktopModeForOrigin(origin))
        store.toggleDesktopModeForOrigin(origin)
        assertTrue(store.isDesktopModeForOrigin(origin))
        store.toggleDesktopModeForOrigin(origin)
        assertFalse(store.isDesktopModeForOrigin(origin))

        assertFalse(store.settings.perSiteTrackingExceptions.contains(origin))
        store.toggleTrackingException(origin)
        assertTrue(store.settings.perSiteTrackingExceptions.contains(origin))
        store.toggleTrackingException(origin)
        assertFalse(store.settings.perSiteTrackingExceptions.contains(origin))
    }
}
