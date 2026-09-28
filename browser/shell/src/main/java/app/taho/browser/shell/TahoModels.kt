package app.taho.browser.shell

import java.io.Serializable

data class ReaderPageContentUi(
    val text: String,
    val wordCount: Int,
    val language: String,
    val isGated: Boolean,
)

enum class TahoThemeMode { SYSTEM, DARK, LIGHT }
enum class TahoToolbarPosition { BOTTOM, TOP }
enum class TahoHomePageMode { START_PAGE, CUSTOM_URL }
enum class TahoTrackingProtectionLevel { STANDARD, STRICT, CUSTOM }
enum class TahoCookiePolicy { BLOCK_THIRD_PARTY, BLOCK_ALL, ALLOW_ALL }
enum class TahoSecureDns { CLOUDFLARE, QUAD9, GOOGLE, CUSTOM, OFF }
enum class TahoStartupBehavior { PREVIOUS_TABS, START_PAGE, CUSTOM_PAGE }
enum class TahoAutoCloseTabs { NEVER, AFTER_1_DAY, AFTER_1_WEEK, AFTER_1_MONTH }
enum class TahoDownloadStatus { DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }
enum class TahoReaderTheme { OLED_BLACK, SEPIA, LIGHT }

data class SearchEngineItem(
    val id: String,
    val name: String,
    val queryUrl: String,
    val iconGlyph: String,
    val isDefault: Boolean = false,
) : Serializable

data class TopSiteItem(
    val id: String,
    val title: String,
    val url: String,
    val iconGlyph: String,
    val isPinned: Boolean = false,
    val visitCount: Int = 1,
) : Serializable

data class BookmarkFolderItem(
    val id: String,
    val name: String,
    val parentFolderId: String? = null,
) : Serializable

data class BookmarkItem(
    val id: String,
    val title: String,
    val url: String,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
) : Serializable

data class ReadingListItem(
    val id: String,
    val title: String,
    val url: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isOfflineCached: Boolean = true,
) : Serializable

data class HistoryEntryItem(
    val id: String,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1,
) : Serializable

data class RecentlyClosedTabItem(
    val id: String,
    val title: String,
    val url: String,
    val closedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false,
) : Serializable

data class DownloadItemUi(
    val id: String,
    val fileName: String,
    val url: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: TahoDownloadStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val localPath: String? = null,
    val mimeType: String = "application/octet-stream",
) : Serializable {
    val progress: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class SavedPasswordUi(
    val id: String,
    val domain: String,
    val username: String,
    val password: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val isCompromised: Boolean = false,
    val isWeak: Boolean = false,
    val isReused: Boolean = false,
) : Serializable

data class SavedAddressUi(
    val id: String,
    val label: String,
    val fullName: String,
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String,
    val phone: String,
    val email: String,
) : Serializable

data class SavedPaymentUi(
    val id: String,
    val cardHolder: String,
    val cardNumberMasked: String,
    val cardExpiry: String,
    val cardType: String,
) : Serializable

data class BrowserProfileUi(
    val id: String,
    val name: String,
    val avatarGlyph: String,
    val isGuest: Boolean = false,
    val isActive: Boolean = false,
    val syncEnabled: Boolean = false,
    val syncedDevicesCount: Int = 0,
) : Serializable

data class SyncedDeviceUi(
    val id: String,
    val name: String,
    val deviceType: String, // "Phone", "Tablet", "Desktop"
    val lastSyncTime: Long = System.currentTimeMillis(),
) : Serializable

data class TabGroupUi(
    val id: String,
    val name: String,
    val colorHex: Long = 0xFFE2B44A,
    val tabIds: List<String> = emptyList(),
) : Serializable

data class SitePermissionEntry(
    val origin: String,
    val permission: String, // "Location", "Camera", "Microphone", "Notification", "Clipboard", "Storage", "Pop-ups", "Autoplay"
    val state: String = "ALLOW", // "ALLOW", "BLOCK", "ASK"
) : Serializable

data class SiteDataUi(
    val origin: String,
    val cookieCount: Int,
    val storageSizeBytes: Long,
) : Serializable

data class ExtensionUi(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val isEnabled: Boolean = true,
    val allowedInPrivate: Boolean = false,
    val canBlockContent: Boolean = false,
    val permissions: List<String> = emptyList(),
) : Serializable

data class InstalledPwaUi(
    val id: String,
    val name: String,
    val url: String,
    val iconGlyph: String,
    val installedAt: Long = System.currentTimeMillis(),
) : Serializable

data class OfflinePageUi(
    val id: String,
    val title: String,
    val url: String,
    val savedAt: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0L,
    val localPath: String? = null,
    val mimeType: String = "application/pdf",
) : Serializable

data class ReaderSettingsUi(
    val fontSizeSp: Int = 16,
    val fontFamily: String = "Serif", // "Sans", "Serif", "Mono"
    val theme: TahoReaderTheme = TahoReaderTheme.OLED_BLACK,
    val lineSpacingMultiplier: Float = 1.4f,
) : Serializable

data class BrowserSettingsState(
    // Appearance
    val themeMode: TahoThemeMode = TahoThemeMode.DARK,
    val accentColorHex: Long = 0xFFE2B44A,
    val toolbarPosition: TahoToolbarPosition = TahoToolbarPosition.BOTTOM,
    val homePageMode: TahoHomePageMode = TahoHomePageMode.START_PAGE,
    val customHomePageUrl: String = "https://duckduckgo.com",

    // Search
    val defaultSearchEngineId: String = "duckduckgo",
    val searchSuggestionsEnabled: Boolean = true,
    val addressBarSuggestionsEnabled: Boolean = true,
    val autocompleteEnabled: Boolean = true,

    // Privacy & Security
    val trackingProtectionLevel: TahoTrackingProtectionLevel = TahoTrackingProtectionLevel.STRICT,
    val blockTrackers: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val fingerprintingProtection: Boolean = true,
    val cryptominingProtection: Boolean = true,
    val socialTrackerProtection: Boolean = true,
    val perSiteTrackingExceptions: Set<String> = emptySet(),
    val httpsOnlyMode: Boolean = true,
    val safeBrowsingEnabled: Boolean = true,
    val dangerousDownloadProtection: Boolean = true,
    val phishingProtection: Boolean = true,
    val popupBlockerEnabled: Boolean = true,
    val redirectBlockingEnabled: Boolean = true,
    val autoplayBlockingEnabled: Boolean = true,
    val javascriptEnabled: Boolean = true,
    val cookiePolicy: TahoCookiePolicy = TahoCookiePolicy.BLOCK_THIRD_PARTY,
    val doNotTrack: Boolean = true,
    val globalPrivacyControl: Boolean = true,
    val secureDns: TahoSecureDns = TahoSecureDns.CLOUDFLARE,
    val customDnsProvider: String = "",
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,

    // Accessibility
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val fontScalingPercent: Int = 100,
    val screenReaderOptimized: Boolean = true,
    val forceZoomEnabled: Boolean = false,

    // Language & Region
    val browserLanguage: String = "English (US)",
    val preferredWebLanguage: String = "en-US, en",
    val translationTargetLanguage: String = "English",
    val autoTranslateEnabled: Boolean = false,

    // Performance & Media
    val memorySaverEnabled: Boolean = true,
    val inactiveTabDiscardMinutes: Int = 30,
    val preloadingEnabled: Boolean = true,
    val hardwareAccelerationEnabled: Boolean = true,
    val backgroundAudioEnabled: Boolean = true,
    val pictureInPictureEnabled: Boolean = true,
    val fullscreenControlsEnabled: Boolean = true,
    val videoAutoplaySetting: String = "Block Audio & Video",

    // Session & Startup
    val startupBehavior: TahoStartupBehavior = TahoStartupBehavior.START_PAGE,
    val customStartupUrl: String = "",
    val sessionRestorePrompt: Boolean = true,
    val autoCloseTabs: TahoAutoCloseTabs = TahoAutoCloseTabs.NEVER,

    // Passwords & Autofill
    val passwordAutofillEnabled: Boolean = true,
    val passwordSavePromptEnabled: Boolean = true,
    val passwordUpdatePromptEnabled: Boolean = true,
    val biometricLockForPasswords: Boolean = true,
    val addressAutofillEnabled: Boolean = true,
    val paymentAutofillEnabled: Boolean = true,

    // Profiles & Sync
    val currentProfileId: String = "profile_personal",
    val syncEnabled: Boolean = false,
    val syncBookmarks: Boolean = true,
    val syncHistory: Boolean = true,
    val syncPasswords: Boolean = true,
    val syncOpenTabs: Boolean = true,
    val syncSettings: Boolean = true,

    // Start Page Customization
    val startPageBackground: String = "AMOLED_BLACK", // "AMOLED_BLACK", "CARBON_GRID", "DEEP_NAVY", "GOLD_RADIAL"
    val showTopSitesOnStartPage: Boolean = true,
    val showRecentTabsOnStartPage: Boolean = true,
    val showQuickSearchOnStartPage: Boolean = true,
    val showPrivacyStatsOnStartPage: Boolean = true,

    // Private Browsing
    val privateTabLock: Boolean = false,
    val biometricLockForPrivateTabs: Boolean = false,
    val clearPrivateTabsOnExit: Boolean = true,

    // Page Zoom & Display
    val pageZoomPercent: Int = 100,
    val perSiteZoomLevels: Map<String, Int> = emptyMap(),
    val perSiteDesktopModes: Set<String> = emptySet(),

    // Diagnostics & Notifications
    val crashReportingEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,

    // Onboarding
    val hasCompletedOnboarding: Boolean = true,
) : Serializable

data class BrowserCollectionItem(
    val id: String,
    val name: String,
    val description: String = "",
    val linkCount: Int = 0,
    val captureFrameCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) : Serializable

data class WebsiteNotificationItem(
    val id: String,
    val origin: String,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
) : Serializable

data class TabArchiveItem(
    val id: String,
    val title: String,
    val url: String,
    val archivedAt: Long = System.currentTimeMillis(),
) : Serializable

