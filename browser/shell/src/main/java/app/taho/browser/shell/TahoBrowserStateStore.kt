package app.taho.browser.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import java.util.UUID

object TahoBrowserStateStore {
    var settings by mutableStateOf(BrowserSettingsState())

    var searchEngines by mutableStateOf(
        listOf(
            SearchEngineItem("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "⌕", isDefault = true),
            SearchEngineItem("google", "Google", "https://www.google.com/search?q=%s", "G"),
            SearchEngineItem("brave", "Brave Search", "https://search.brave.com/search?q=%s", "B"),
            SearchEngineItem("bing", "Bing", "https://www.bing.com/search?q=%s", "b"),
            SearchEngineItem("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "E"),
        )
    )

    var topSites by mutableStateOf(
        listOf(
            TopSiteItem("site-1", "GitHub", "https://github.com", "GH", isPinned = true, visitCount = 42),
            TopSiteItem("site-2", "DuckDuckGo", "https://duckduckgo.com", "DDG", isPinned = true, visitCount = 38),
            TopSiteItem("site-3", "MDN Web Docs", "https://developer.mozilla.org", "MDN", isPinned = true, visitCount = 29),
            TopSiteItem("site-4", "Reddit", "https://reddit.com", "RD", isPinned = true, visitCount = 25),
            TopSiteItem("site-5", "Hacker News", "https://news.ycombinator.com", "HN", isPinned = false, visitCount = 20),
            TopSiteItem("site-6", "Wikipedia", "https://en.wikipedia.org", "WP", isPinned = false, visitCount = 18),
            TopSiteItem("site-7", "ArXiv", "https://arxiv.org", "AX", isPinned = false, visitCount = 14),
            TopSiteItem("site-8", "Taho Docs", "https://taho.app/docs", "TH", isPinned = true, visitCount = 12),
        )
    )

    var bookmarkFolders by mutableStateOf(
        listOf(
            BookmarkFolderItem("f-mobile", "Mobile Bookmarks"),
            BookmarkFolderItem("f-dev", "Developer Tools", "f-mobile"),
            BookmarkFolderItem("f-security", "AppSec & Privacy", "f-mobile"),
        )
    )

    var bookmarks by mutableStateOf(
        listOf(
            BookmarkItem("bm-1", "Taho GitHub Repository", "https://github.com/taho-browser", "f-dev", isFavorite = true),
            BookmarkItem("bm-2", "GeckoView API Reference", "https://mozilla.github.io/geckoview/", "f-dev", isFavorite = true),
            BookmarkItem("bm-3", "OWASP Mobile Security", "https://owasp.org/www-project-mobile-top-10/", "f-security", isFavorite = true),
            BookmarkItem("bm-4", "Compose Multiplatform Docs", "https://jetbrains.com/compose", "f-dev", isFavorite = false),
            BookmarkItem("bm-5", "W3C Web Standards", "https://w3.org", "f-mobile", isFavorite = false),
        )
    )

    var readingList by mutableStateOf(
        listOf(
            ReadingListItem("rl-1", "Zero Trust Architecture for Native Browsers", "https://blog.taho.app/zero-trust", isRead = false),
            ReadingListItem("rl-2", "Understanding HTTP/3 and QUIC Protocols", "https://cloudflare.com/learning/http3", isRead = true),
            ReadingListItem("rl-3", "Android Memory Isolation in GeckoView", "https://mozilla.org/research/gecko-isolation", isRead = false),
        )
    )

    var history by mutableStateOf(
        listOf(
            HistoryEntryItem("h-1", "GitHub: Taho Architecture Specifications", "https://github.com/taho/architecture", System.currentTimeMillis() - 1000 * 60 * 12),
            HistoryEntryItem("h-2", "Kotlin Language Documentation", "https://kotlinlang.org/docs/home.html", System.currentTimeMillis() - 1000 * 60 * 45),
            HistoryEntryItem("h-3", "MDN: Subresource Integrity (SRI)", "https://developer.mozilla.org/en-US/docs/Web/Security/SRI", System.currentTimeMillis() - 1000 * 60 * 120),
            HistoryEntryItem("h-4", "IETF: Transport Layer Security (TLS 1.3)", "https://datatracker.ietf.org/doc/html/rfc8446", System.currentTimeMillis() - 1000 * 3600 * 5),
            HistoryEntryItem("h-5", "Android Open Source Project Security Bulletins", "https://source.android.com/security/bulletin", System.currentTimeMillis() - 1000 * 3600 * 22),
        )
    )

    var recentlyClosedTabs by mutableStateOf(
        listOf(
            RecentlyClosedTabItem("rc-1", "GeckoView JNI Bindings", "https://mozilla.org/geckoview-jni", System.currentTimeMillis() - 1000 * 60 * 10),
            RecentlyClosedTabItem("rc-2", "Material Design 3 Compose Guidelines", "https://m3.material.io/develop/android/jetpack-compose", System.currentTimeMillis() - 1000 * 60 * 35),
            RecentlyClosedTabItem("rc-3", "Cloudflare DNS over HTTPS Endpoint Info", "https://1.1.1.1/dns-query", System.currentTimeMillis() - 1000 * 3600 * 2),
        )
    )

    var downloads by mutableStateOf(
        listOf(
            DownloadItemUi("dl-1", "taho-spec-v2.pdf", "https://taho.app/downloads/taho-spec-v2.pdf", 4520000, 4520000, TahoDownloadStatus.COMPLETED, localPath = "/storage/emulated/0/Download/taho-spec-v2.pdf", mimeType = "application/pdf"),
            DownloadItemUi("dl-2", "geckoview-arm64.aar", "https://maven.mozilla.org/geckoview.aar", 18200000, 32000000, TahoDownloadStatus.DOWNLOADING, mimeType = "application/octet-stream"),
            DownloadItemUi("dl-3", "ca-certificates-bundle.pem", "https://curl.se/ca/cacert.pem", 240000, 240000, TahoDownloadStatus.COMPLETED, localPath = "/storage/emulated/0/Download/cacert.pem", mimeType = "text/plain"),
            DownloadItemUi("dl-4", "dataset-corpus.tar.gz", "https://datasets.internal/archive.tar.gz", 1200000, 89000000, TahoDownloadStatus.PAUSED, mimeType = "application/gzip"),
        )
    )

    var savedPasswords by mutableStateOf(
        listOf(
            SavedPasswordUi("pw-1", "github.com", "developer@taho.app", "ghp_secureKey8920!x", isCompromised = false, isWeak = false, isReused = false),
            SavedPasswordUi("pw-2", "console.aws.amazon.com", "admin-prod", "K9#mQ2\$vxL9@10a", isCompromised = false, isWeak = false, isReused = false),
            SavedPasswordUi("pw-3", "legacy-forum.org", "user123", "password123", isCompromised = true, isWeak = true, isReused = true),
            SavedPasswordUi("pw-4", "gitlab.company.net", "team-lead", "Tr0ub4dor&3Taho", isCompromised = false, isWeak = false, isReused = false),
        )
    )

    var savedAddresses by mutableStateOf(
        listOf(
            SavedAddressUi("addr-1", "Home", "Taho Engineer", "742 Evergreen Terrace", "San Francisco", "CA", "94102", "United States", "+1 415 555 0199", "dev@taho.app"),
            SavedAddressUi("addr-2", "Work / Lab", "Taho Security Division", "100 Innovation Way", "Palo Alto", "CA", "94301", "United States", "+1 650 555 0142", "secops@taho.app"),
        )
    )

    var savedPayments by mutableStateOf(
        listOf(
            SavedPaymentUi("pay-1", "Taho Corporate", "•••• •••• •••• 4242", "12/28", "Visa"),
            SavedPaymentUi("pay-2", "Dev Operations", "•••• •••• •••• 8812", "09/29", "Mastercard"),
        )
    )

    var profiles by mutableStateOf(
        listOf(
            BrowserProfileUi("profile_personal", "Personal", "👤", isActive = true, syncEnabled = true, syncedDevicesCount = 3),
            BrowserProfileUi("profile_work", "Work / Development", "💼", isActive = false, syncEnabled = true, syncedDevicesCount = 2),
            BrowserProfileUi("profile_guest", "Guest Profile", "🕶", isGuest = true, isActive = false, syncEnabled = false, syncedDevicesCount = 0),
        )
    )

    var syncedDevices by mutableStateOf(
        listOf(
            SyncedDeviceUi("dev-1", "Pixel 9 Pro (This device)", "Phone", System.currentTimeMillis()),
            SyncedDeviceUi("dev-2", "ThinkPad X1 Carbon", "Desktop", System.currentTimeMillis() - 1000 * 60 * 8),
            SyncedDeviceUi("dev-3", "iPad Air Workstation", "Tablet", System.currentTimeMillis() - 1000 * 3600 * 18),
        )
    )

    var tabGroups by mutableStateOf(
        listOf(
            TabGroupUi("grp-1", "Research & Specs", 0xFFE2B44A),
            TabGroupUi("grp-2", "Production APIs", 0xFF4FBFA3),
            TabGroupUi("grp-3", "Security Audit", 0xFFE06A5A),
        )
    )

    var sitePermissions by mutableStateOf(
        listOf(
            SitePermissionEntry("https://github.com", "Clipboard", "ALLOW"),
            SitePermissionEntry("https://github.com", "Notification", "ALLOW"),
            SitePermissionEntry("https://meet.google.com", "Camera", "ALLOW"),
            SitePermissionEntry("https://meet.google.com", "Microphone", "ALLOW"),
            SitePermissionEntry("https://untrusted-site.xyz", "Location", "BLOCK"),
            SitePermissionEntry("https://untrusted-site.xyz", "Pop-ups", "BLOCK"),
        )
    )

    var siteData by mutableStateOf(
        listOf(
            SiteDataUi("github.com", 14, 8420000),
            SiteDataUi("duckduckgo.com", 6, 2100000),
            SiteDataUi("developer.mozilla.org", 8, 4800000),
            SiteDataUi("reddit.com", 22, 16900000),
            SiteDataUi("arxiv.org", 4, 1200000),
        )
    )

    var extensions by mutableStateOf(
        listOf(
            ExtensionUi("ext-1", "uBlock Origin", "1.58.0", "Raymond Hill", "Efficient wide-spectrum content blocker for speed and privacy.", isEnabled = true, allowedInPrivate = true, canBlockContent = true, permissions = listOf("webRequest", "storage", "tabs")),
            ExtensionUi("ext-2", "Privacy Badger", "2024.5.17", "EFF", "Automatically learns to block invisible trackers.", isEnabled = true, allowedInPrivate = true, canBlockContent = true, permissions = listOf("webRequest", "cookies")),
            ExtensionUi("ext-3", "Dark Reader", "4.9.82", "Alexander Shutau", "Inverts colors and applies OLED pitch black styles to all sites.", isEnabled = true, allowedInPrivate = false, canBlockContent = false, permissions = listOf("storage")),
            ExtensionUi("ext-4", "Bitwarden Password Manager", "2024.6.1", "Bitwarden Inc.", "Secure open-source password vault integration.", isEnabled = true, allowedInPrivate = true, canBlockContent = false, permissions = listOf("activeTab", "storage")),
        )
    )

    var installedPwas by mutableStateOf(
        listOf(
            InstalledPwaUi("pwa-1", "Taho Studio", "https://studio.taho.app", "TS"),
            InstalledPwaUi("pwa-2", "GitHub Mobile Web", "https://github.com", "GH"),
        )
    )

    var offlinePages by mutableStateOf(
        listOf(
            OfflinePageUi("off-1", "Zero Trust Architecture Technical Whitepaper", "https://blog.taho.app/zero-trust", System.currentTimeMillis() - 1000 * 3600 * 48, 1240000),
            OfflinePageUi("off-2", "GeckoView Android Integration Contract", "https://mozilla.github.io/geckoview/contract", System.currentTimeMillis() - 1000 * 3600 * 72, 890000),
        )
    )

    var collections by mutableStateOf(
        listOf(
            BrowserCollectionItem("col-1", "Security Audits", "OWASP mobile checklists and verification contracts", 4, 12),
            BrowserCollectionItem("col-2", "API Specs & RFCs", "HTTP/3, QUIC, and Subresource Integrity standards", 8, 24),
            BrowserCollectionItem("col-3", "UI & Design Systems", "Taho Display tokens and responsive layouts", 6, 2),
        )
    )

    var websiteNotifications by mutableStateOf(
        listOf(
            WebsiteNotificationItem("notif-1", "github.com", "Security Alert", "Personal access token expires in 3 days."),
            WebsiteNotificationItem("notif-2", "blog.taho.app", "New Article", "Zero Trust Architecture for Native Browsers published."),
        )
    )

    var archivedTabs by mutableStateOf(
        listOf(
            TabArchiveItem("arch-1", "WebAssembly Component Model Guide", "https://component-model.bytecodealliance.org"),
            TabArchiveItem("arch-2", "Android Keystore System Architecture", "https://developer.android.com/training/articles/keystore"),
        )
    )

    var readerSettings by mutableStateOf(ReaderSettingsUi())

    // --- State mutation helpers ---
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
        if (url.isNullOrBlank() || url == "about:blank") return
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

