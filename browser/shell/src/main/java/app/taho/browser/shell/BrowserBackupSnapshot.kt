package app.taho.browser.shell

import java.io.Serializable

/**
 * Portable snapshot of browser data owned by Taho itself.
 *
 * Gecko-managed cookies, cache, IndexedDB, service-worker storage and internal
 * permission databases are intentionally excluded. The Browser architecture
 * treats those as engine-owned state rather than application records.
 */
data class BrowserBackupSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val settings: BrowserSettingsState,
    val searchEngines: List<SearchEngineItem>,
    val topSites: List<TopSiteItem>,
    val bookmarkFolders: List<BookmarkFolderItem>,
    val bookmarks: List<BookmarkItem>,
    val readingList: List<ReadingListItem>,
    val history: List<HistoryEntryItem>,
    val recentlyClosedTabs: List<RecentlyClosedTabItem>,
    val downloads: List<DownloadItemUi>,
    val savedPasswords: List<SavedPasswordUi>,
    val savedAddresses: List<SavedAddressUi>,
    val savedPayments: List<SavedPaymentUi>,
    val profiles: List<BrowserProfileUi>,
    val tabGroups: List<TabGroupUi>,
    val sitePermissions: List<SitePermissionEntry>,
    val installedPwas: List<InstalledPwaUi>,
    val offlinePages: List<OfflinePageUi>,
    val collections: List<BrowserCollectionItem>,
    val websiteNotifications: List<WebsiteNotificationItem>,
    val archivedTabs: List<TabArchiveItem>,
    val readerSettings: ReaderSettingsUi,
) : Serializable {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class BrowserBackupRestoreSummary(
    val bookmarks: Int,
    val historyEntries: Int,
    val passwords: Int,
    val addresses: Int,
    val paymentCards: Int,
    val downloads: Int,
    val offlinePages: Int,
) : Serializable
