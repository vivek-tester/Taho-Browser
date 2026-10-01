package app.taho.browser.shell

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import java.util.UUID

object TahoBrowserStateStore {
    private var persistence: TahoBrowserPersistence? = null
    private var uiMetaPreferences: android.content.SharedPreferences? = null

    var captureEnabled by mutableStateOf(false)
        private set

    var settings by mutableStateOf(BrowserSettingsState())

    var searchEngines by mutableStateOf(
        listOf(
            SearchEngineItem("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "D"),
            SearchEngineItem("google", "Google", "https://www.google.com/search?q=%s", "G", isDefault = true),
            SearchEngineItem("brave", "Brave Search", "https://search.brave.com/search?q=%s", "B"),
            SearchEngineItem("bing", "Bing", "https://www.bing.com/search?q=%s", "b"),
            SearchEngineItem("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "E"),
        )
    )

    // User-owned browser data starts empty. Built-in search providers above are
    // product configuration, not fabricated browsing activity.
    var topSites by mutableStateOf(emptyList<TopSiteItem>())
    var bookmarkFolders by mutableStateOf(emptyList<BookmarkFolderItem>())
    var bookmarks by mutableStateOf(emptyList<BookmarkItem>())
    var readingList by mutableStateOf(emptyList<ReadingListItem>())
    var history by mutableStateOf(emptyList<HistoryEntryItem>())
    var recentlyClosedTabs by mutableStateOf(emptyList<RecentlyClosedTabItem>())
    var downloads by mutableStateOf(emptyList<DownloadItemUi>())
    var savedPasswords by mutableStateOf(emptyList<SavedPasswordUi>())
    var savedAddresses by mutableStateOf(emptyList<SavedAddressUi>())
    var savedPayments by mutableStateOf(emptyList<SavedPaymentUi>())

    var profiles by mutableStateOf(
        listOf(
            BrowserProfileUi(
                id = "profile_personal",
                name = "Personal",
                avatarGlyph = "👤",
                isActive = true,
                syncEnabled = false,
                syncedDevicesCount = 0,
            ),
        ),
    )
    var syncedDevices by mutableStateOf(emptyList<SyncedDeviceUi>())
    var tabGroups by mutableStateOf(emptyList<TabGroupUi>())
    var pinnedTabIds by mutableStateOf(emptySet<String>())
        private set
    var sitePermissions by mutableStateOf(emptyList<SitePermissionEntry>())
    var siteData by mutableStateOf(emptyList<SiteDataUi>())

    // Extension/PWA lists must reflect engine/platform state. Until a real
    // controller reports entries, the UI truthfully shows them as empty.
    var extensions by mutableStateOf(emptyList<ExtensionUi>())
    var installedPwas by mutableStateOf(emptyList<InstalledPwaUi>())
    var offlinePages by mutableStateOf(emptyList<OfflinePageUi>())
    var collections by mutableStateOf(emptyList<BrowserCollectionItem>())
    var websiteNotifications by mutableStateOf(emptyList<WebsiteNotificationItem>())
    var archivedTabs by mutableStateOf(emptyList<TabArchiveItem>())
    var readerSettings by mutableStateOf(ReaderSettingsUi())

    fun initialize(context: Context) {
        if (persistence != null) return
        val store = TahoBrowserPersistence(context.applicationContext)
        persistence = store
        uiMetaPreferences = context.applicationContext.getSharedPreferences(
            "taho_browser_ui_meta",
            Context.MODE_PRIVATE,
        )
        captureEnabled = uiMetaPreferences
            ?.getBoolean("capture_enabled", false)
            ?: false
        pinnedTabIds = uiMetaPreferences
            ?.getStringSet("pinned_tab_ids", emptySet())
            ?.toSet()
            .orEmpty()

        store.loadState()?.let { saved ->
            settings = saved.settings
            searchEngines = saved.searchEngines.ifEmpty { searchEngines }
            topSites = saved.topSites
            bookmarkFolders = saved.bookmarkFolders
            bookmarks = saved.bookmarks
            readingList = saved.readingList
            history = saved.history
            recentlyClosedTabs = saved.recentlyClosedTabs.filterNot { it.isPrivate }
            downloads = saved.downloads.map { item ->
                if (item.status == TahoDownloadStatus.DOWNLOADING) {
                    item.copy(status = TahoDownloadStatus.FAILED)
                } else {
                    item
                }
            }
            profiles = saved.profiles.ifEmpty { profiles }
            syncedDevices = saved.syncedDevices
            tabGroups = saved.tabGroups
            sitePermissions = saved.sitePermissions
            installedPwas = saved.installedPwas
            offlinePages = saved.offlinePages
            collections = saved.collections
            websiteNotifications = saved.websiteNotifications
            archivedTabs = saved.archivedTabs
            readerSettings = saved.readerSettings
        }

        store.loadSensitiveState()?.let { sensitive ->
            savedPasswords = sensitive.passwords
            savedAddresses = sensitive.addresses
            savedPayments = sensitive.payments
        }
    }

    fun persistNow() {
        val store = persistence ?: return
        runCatching {
            store.save(
                state = BrowserPersistentState(
                    settings = settings,
                    searchEngines = searchEngines,
                    topSites = topSites,
                    bookmarkFolders = bookmarkFolders,
                    bookmarks = bookmarks,
                    readingList = readingList,
                    history = history,
                    recentlyClosedTabs = recentlyClosedTabs.filterNot { it.isPrivate },
                    downloads = downloads,
                    profiles = profiles,
                    syncedDevices = syncedDevices,
                    tabGroups = tabGroups,
                    sitePermissions = sitePermissions,
                    installedPwas = installedPwas,
                    offlinePages = offlinePages,
                    collections = collections,
                    websiteNotifications = websiteNotifications,
                    archivedTabs = archivedTabs,
                    readerSettings = readerSettings,
                ),
                sensitive = BrowserSensitiveState(
                    passwords = savedPasswords,
                    addresses = savedAddresses,
                    payments = savedPayments,
                ),
            )
        }
    }


    internal fun resetInMemoryForTests() {
        persistence = null
        settings = BrowserSettingsState()
        searchEngines = listOf(
            SearchEngineItem("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "D"),
            SearchEngineItem("google", "Google", "https://www.google.com/search?q=%s", "G", isDefault = true),
            SearchEngineItem("brave", "Brave Search", "https://search.brave.com/search?q=%s", "B"),
            SearchEngineItem("bing", "Bing", "https://www.bing.com/search?q=%s", "b"),
            SearchEngineItem("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "E"),
        )
        topSites = emptyList()
        bookmarkFolders = emptyList()
        bookmarks = emptyList()
        readingList = emptyList()
        history = emptyList()
        recentlyClosedTabs = emptyList()
        downloads = emptyList()
        savedPasswords = emptyList()
        savedAddresses = emptyList()
        savedPayments = emptyList()
        profiles = listOf(
            BrowserProfileUi(
                id = "profile_personal",
                name = "Personal",
                avatarGlyph = "👤",
                isActive = true,
                syncEnabled = false,
                syncedDevicesCount = 0,
            ),
        )
        syncedDevices = emptyList()
        tabGroups = emptyList()
        pinnedTabIds = emptySet()
        captureEnabled = false
        uiMetaPreferences = null
        sitePermissions = emptyList()
        siteData = emptyList()
        extensions = emptyList()
        installedPwas = emptyList()
        offlinePages = emptyList()
        collections = emptyList()
        websiteNotifications = emptyList()
        archivedTabs = emptyList()
        readerSettings = ReaderSettingsUi()
    }

    // --- State mutation helpers ---
    fun updateCaptureEnabled(enabled: Boolean) {
        captureEnabled = enabled
        uiMetaPreferences
            ?.edit()
            ?.putBoolean("capture_enabled", enabled)
            ?.apply()
    }

    fun updateSettings(updater: (BrowserSettingsState) -> BrowserSettingsState) {
        settings = updater(settings)
    }

    fun addBookmark(title: String, url: String, folderId: String? = null) {
        bookmarks = bookmarks + BookmarkItem(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { url },
            url = url,
            folderId = folderId,
            isFavorite = true,
        )
    }

    fun removeBookmark(id: String) {
        bookmarks = bookmarks.filterNot { it.id == id }
    }

    fun addReadingListItem(title: String, url: String) {
        readingList = readingList + ReadingListItem(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { url },
            url = url,
        )
    }

    fun removeReadingListItem(id: String) {
        readingList = readingList.filterNot { it.id == id }
    }

    fun toggleReadingListRead(id: String) {
        readingList = readingList.map {
            if (it.id == id) it.copy(isRead = !it.isRead) else it
        }
    }

    fun recordHistory(title: String?, url: String) {
        if (url.isBlank() || url == "about:blank" || url.startsWith("taho://")) return
        val existing = history.find { it.url == url }
        history = if (existing != null) {
            listOf(existing.copy(title = title?.takeIf { it.isNotBlank() } ?: existing.title, timestamp = System.currentTimeMillis(), visitCount = existing.visitCount + 1)) +
                history.filterNot { it.id == existing.id }
        } else {
            listOf(
                HistoryEntryItem(
                    id = UUID.randomUUID().toString(),
                    title = title?.takeIf { it.isNotBlank() } ?: url,
                    url = url,
                )
            ) + history
        }
    }

    fun removeHistoryEntry(id: String) {
        history = history.filterNot { it.id == id }
    }

    fun clearAllHistory() {
        history = emptyList()
    }

    fun recordClosedTab(title: String?, url: String?, isPrivate: Boolean) {
        if (isPrivate || url.isNullOrBlank() || url == "about:blank") return
        recentlyClosedTabs = listOf(
            RecentlyClosedTabItem(
                id = UUID.randomUUID().toString(),
                title = title?.takeIf { it.isNotBlank() } ?: url,
                url = url,
                isPrivate = isPrivate,
            )
        ) + recentlyClosedTabs.take(19)
    }

    fun restoreClosedTab(id: String): RecentlyClosedTabItem? {
        val found = recentlyClosedTabs.find { it.id == id }
        if (found != null) {
            recentlyClosedTabs = recentlyClosedTabs.filterNot { it.id == id }
        }
        return found
    }

    fun addTopSite(title: String, url: String, isPinned: Boolean = false) {
        val glyph = title.take(2).uppercase().ifBlank { "WS" }
        topSites = topSites + TopSiteItem(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            iconGlyph = glyph,
            isPinned = isPinned,
        )
    }

    fun removeTopSite(id: String) {
        topSites = topSites.filterNot { it.id == id }
    }

    fun togglePinTopSite(id: String) {
        topSites = topSites.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
    }

    fun addSavedPassword(domain: String, username: String, pass: String) {
        val weak = pass.length < 8 || pass.all { it.isLetter() } || pass.all { it.isDigit() }
        val reused = savedPasswords.any { it.password == pass }
        savedPasswords = savedPasswords + SavedPasswordUi(
            id = UUID.randomUUID().toString(),
            domain = domain.removePrefix("https://").removePrefix("http://").substringBefore('/'),
            username = username,
            password = pass,
            isWeak = weak,
            isReused = reused,
        )
    }

    fun removeSavedPassword(id: String) {
        savedPasswords = savedPasswords.filterNot { it.id == id }
    }

    fun generateStrongPassword(length: Int = 18, useSpecial: Boolean = true): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
            (if (useSpecial) "!@#$%^&*()-_=+" else "")
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    fun clearBrowsingData(
        clearCache: Boolean,
        clearCookies: Boolean,
        clearHistory: Boolean,
        clearFormData: Boolean,
        clearPasswords: Boolean,
        clearDownloads: Boolean,
        timeRange: String, // "HOUR", "24_HOURS", "7_DAYS", "ALL_TIME"
    ) {
        val cutoff = when (timeRange) {
            "HOUR" -> System.currentTimeMillis() - 1000 * 3600
            "24_HOURS" -> System.currentTimeMillis() - 1000 * 3600 * 24
            "7_DAYS" -> System.currentTimeMillis() - 1000 * 3600 * 24 * 7
            else -> 0L
        }

        if (clearHistory) {
            history = if (cutoff == 0L) emptyList() else history.filter { it.timestamp < cutoff }
            recentlyClosedTabs = if (cutoff == 0L) emptyList() else recentlyClosedTabs.filter { it.closedAt < cutoff }
        }
        if (clearCookies) {
            siteData = if (cutoff == 0L) emptyList() else siteData.dropLast(1)
        }
        if (clearPasswords && cutoff == 0L) {
            savedPasswords = emptyList()
        }
        if (clearDownloads) {
            downloads = if (cutoff == 0L) emptyList() else downloads.filter { it.timestamp < cutoff }
        }
    }

    fun updateSitePermission(origin: String, permission: String, state: String) {
        val existingIndex = sitePermissions.indexOfFirst { it.origin == origin && it.permission == permission }
        sitePermissions = if (existingIndex >= 0) {
            sitePermissions.toMutableList().also {
                it[existingIndex] = it[existingIndex].copy(state = state)
            }
        } else {
            sitePermissions + SitePermissionEntry(origin, permission, state)
        }
    }

    fun resetSitePermissions(origin: String? = null) {
        sitePermissions = if (origin == null) emptyList() else sitePermissions.filterNot { it.origin == origin }
    }

    fun clearSiteDataForOrigin(origin: String) {
        siteData = siteData.filterNot { it.origin.contains(origin, ignoreCase = true) }
    }

    fun switchProfile(profileId: String) {
        profiles = profiles.map {
            it.copy(isActive = it.id == profileId)
        }
        settings = settings.copy(currentProfileId = profileId)
    }

    // --- Pinned Tabs ---
    fun togglePinnedTab(tabId: String, isPrivate: Boolean = false) {
        if (isPrivate) return
        pinnedTabIds = pinnedTabIds.toMutableSet().also { pins ->
            if (!pins.add(tabId)) pins.remove(tabId)
        }
        persistPinnedTabs()
    }

    fun unpinTab(tabId: String) {
        if (tabId !in pinnedTabIds) return
        pinnedTabIds = pinnedTabIds - tabId
        persistPinnedTabs()
    }

    fun prunePinnedTabs(liveTabIds: Set<String>) {
        val pruned = pinnedTabIds.intersect(liveTabIds)
        if (pruned != pinnedTabIds) {
            pinnedTabIds = pruned
            persistPinnedTabs()
        }
    }

    private fun persistPinnedTabs() {
        uiMetaPreferences
            ?.edit()
            ?.putStringSet("pinned_tab_ids", pinnedTabIds)
            ?.apply()
    }

    fun toggleExtension(id: String) {
        extensions = extensions.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
    }

    fun toggleExtensionPrivate(id: String) {
        extensions = extensions.map {
            if (it.id == id) it.copy(allowedInPrivate = !it.allowedInPrivate) else it
        }
    }

    fun upsertDownload(item: DownloadItemUi) {
        downloads = listOf(item) + downloads.filterNot { it.id == item.id }
        persistNow()
    }

    fun pauseResumeDownload(id: String) {
        downloads = downloads.map {
            if (it.id == id) {
                val next = when (it.status) {
                    TahoDownloadStatus.DOWNLOADING -> TahoDownloadStatus.PAUSED
                    TahoDownloadStatus.PAUSED -> TahoDownloadStatus.DOWNLOADING
                    else -> it.status
                }
                it.copy(status = next)
            } else it
        }
    }

    fun cancelDownload(id: String) {
        downloads = downloads.map {
            if (it.id == id) it.copy(status = TahoDownloadStatus.CANCELLED) else it
        }
    }

    fun retryDownload(id: String) {
        downloads = downloads.map {
            if (it.id == id) it.copy(status = TahoDownloadStatus.DOWNLOADING, bytesDownloaded = 0) else it
        }
    }

    fun removeDownload(id: String) {
        downloads = downloads.filterNot { it.id == id }
    }

    // --- Bookmark Folders ---
    fun addBookmarkFolder(name: String, parentFolderId: String? = null) {
        bookmarkFolders = bookmarkFolders + BookmarkFolderItem(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "New Folder" },
            parentFolderId = parentFolderId,
        )
    }

    fun removeBookmarkFolder(id: String) {
        bookmarkFolders = bookmarkFolders.filterNot { it.id == id }
        bookmarks = bookmarks.map {
            if (it.folderId == id) it.copy(folderId = null) else it
        }
    }

    // --- Search Engines ---
    fun addSearchEngine(name: String, queryUrl: String, iconGlyph: String = "⌕") {
        searchEngines = searchEngines + SearchEngineItem(
            id = UUID.randomUUID().toString(),
            name = name,
            queryUrl = if (queryUrl.contains("%s")) queryUrl else "$queryUrl?q=%s",
            iconGlyph = iconGlyph.take(2).ifBlank { "⌕" },
        )
    }

    fun removeSearchEngine(id: String) {
        if (searchEngines.size > 1) {
            searchEngines = searchEngines.filterNot { it.id == id }
            if (settings.defaultSearchEngineId == id) {
                settings = settings.copy(defaultSearchEngineId = searchEngines.first().id)
            }
        }
    }

    // --- Passwords Editing ---
    fun savePassword(domain: String, username: String, pass: String) = addSavedPassword(domain, username, pass)

    fun updateSavedPassword(id: String, username: String, pass: String) {
        val existing = savedPasswords.find { it.id == id }
        updateSavedPassword(id, existing?.domain ?: "", username, pass)
    }

    fun updateSavedPassword(id: String, domain: String, username: String, pass: String) {
        val weak = pass.length < 8 || pass.all { it.isLetter() } || pass.all { it.isDigit() }
        val reused = savedPasswords.any { it.id != id && it.password == pass }
        savedPasswords = savedPasswords.map {
            if (it.id == id) {
                it.copy(
                    domain = domain.removePrefix("https://").removePrefix("http://").substringBefore('/'),
                    username = username,
                    password = pass,
                    isWeak = weak,
                    isReused = reused,
                )
            } else it
        }
    }

    // --- Autofill ---
    fun addSavedAddress(
        label: String,
        fullName: String,
        street: String,
        city: String,
        state: String,
        zipCode: String,
        country: String = "United States",
        phone: String = "",
        email: String = "",
    ) {
        savedAddresses = savedAddresses + SavedAddressUi(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "Address" },
            fullName = fullName,
            street = street,
            city = city,
            state = state,
            zipCode = zipCode,
            country = country,
            phone = phone,
            email = email,
        )
    }

    fun removeSavedAddress(id: String) {
        savedAddresses = savedAddresses.filterNot { it.id == id }
    }

    fun addSavedPayment(cardHolder: String, cardNumber: String, cardExpiry: String, cardType: String) {
        val cleanNumber = cardNumber.replace(" ", "").replace("-", "")
        val masked = if (cleanNumber.length >= 4) {
            "•••• •••• •••• " + cleanNumber.takeLast(4)
        } else "•••• 0000"
        savedPayments = savedPayments + SavedPaymentUi(
            id = UUID.randomUUID().toString(),
            cardHolder = cardHolder,
            cardNumberMasked = masked,
            cardExpiry = cardExpiry,
            cardType = cardType.ifBlank { "Visa" },
        )
    }

    fun removeSavedPayment(id: String) {
        savedPayments = savedPayments.filterNot { it.id == id }
    }

    // --- Offline Pages ---
    fun removeOfflinePage(id: String) {
        offlinePages = offlinePages.filterNot { it.id == id }
    }

    // --- PWAs ---
    fun installPwa(name: String, url: String, iconGlyph: String = "PW") {
        installedPwas = installedPwas + InstalledPwaUi(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Web App" },
            url = url,
            iconGlyph = iconGlyph.take(2).uppercase().ifBlank { "PW" },
        )
    }

    fun uninstallPwa(id: String) {
        installedPwas = installedPwas.filterNot { it.id == id }
    }

    // --- Collections ---
    fun addCollection(name: String, description: String = "") {
        collections = collections + BrowserCollectionItem(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "New Collection" },
            description = description,
        )
    }

    fun removeCollection(id: String) {
        collections = collections.filterNot { it.id == id }
    }

    // --- Website Notifications ---
    fun addWebsiteNotification(origin: String, title: String, message: String) {
        websiteNotifications = listOf(
            WebsiteNotificationItem(
                id = UUID.randomUUID().toString(),
                origin = origin,
                title = title,
                message = message,
            ),
        ) + websiteNotifications
        persistNow()
    }

    fun removeWebsiteNotification(id: String) {
        websiteNotifications = websiteNotifications.filterNot { it.id == id }
    }

    fun clearWebsiteNotifications() {
        websiteNotifications = emptyList()
    }

    // --- Tab Archive ---
    fun archiveTab(title: String, url: String) {
        archivedTabs = listOf(
            TabArchiveItem(
                id = UUID.randomUUID().toString(),
                title = title.ifBlank { url },
                url = url,
            )
        ) + archivedTabs
    }

    fun restoreArchivedTab(id: String): TabArchiveItem? {
        val found = archivedTabs.find { it.id == id }
        if (found != null) {
            archivedTabs = archivedTabs.filterNot { it.id == id }
        }
        return found
    }

    // --- Per-site Zoom & Desktop Mode ---
    fun setZoomForOrigin(origin: String, zoomPercent: Int) {
        val current = settings.perSiteZoomLevels.toMutableMap()
        current[origin] = zoomPercent
        settings = settings.copy(perSiteZoomLevels = current)
    }

    fun getZoomForOrigin(origin: String): Int {
        return settings.perSiteZoomLevels[origin] ?: settings.pageZoomPercent
    }

    fun toggleDesktopModeForOrigin(origin: String) {
        val current = settings.perSiteDesktopModes.toMutableSet()
        if (current.contains(origin)) current.remove(origin) else current.add(origin)
        settings = settings.copy(perSiteDesktopModes = current)
    }

    fun isDesktopModeForOrigin(origin: String): Boolean = settings.perSiteDesktopModes.contains(origin)

    // --- Site Tracking Exceptions ---
    fun toggleTrackingException(origin: String) {
        val current = settings.perSiteTrackingExceptions.toMutableSet()
        if (current.contains(origin)) current.remove(origin) else current.add(origin)
        settings = settings.copy(perSiteTrackingExceptions = current)
    }

    // --- Data Import & Export Helpers ---
    fun exportBookmarksHtml(): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        bookmarks.forEach {
            sb.append("    <DT><A HREF=\"${it.url}\">${it.title}</A>\n")
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    fun exportPasswordsJson(): String {
        val items = savedPasswords.map {
            """{"domain":"${it.domain}","username":"${it.username}","password":"${it.password}"}"""
        }.joinToString(",")
        return "[$items]"
    }

    fun exportSettingsJson(): String {
        return """{"themeMode":"${settings.themeMode}","toolbarPosition":"${settings.toolbarPosition}","trackingProtection":"${settings.trackingProtectionLevel}","httpsOnly":${settings.httpsOnlyMode}}"""
    }

    fun importBookmarksFromHtml(html: String): Int {
        val regex = Regex("""<A HREF="([^"]+)">([^<]+)</A>""", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(html).toList()
        var count = 0
        matches.forEach { match ->
            val url = match.groupValues[1]
            val title = match.groupValues[2]
            if (bookmarks.none { it.url == url }) {
                addBookmark(title, url)
                count++
            }
        }
        return count
    }

    fun importPasswordsFromJson(json: String): Int {
        val regex = Regex(""""domain":"([^"]+)","username":"([^"]+)","password":"([^"]+)"""")
        val matches = regex.findAll(json).toList()
        var count = 0
        matches.forEach { match ->
            val domain = match.groupValues[1]
            val username = match.groupValues[2]
            val pass = match.groupValues[3]
            if (savedPasswords.none { it.domain == domain && it.username == username }) {
                addSavedPassword(domain, username, pass)
                count++
            }
        }
        return count
    }
}

