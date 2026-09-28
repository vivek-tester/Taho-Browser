package app.taho.browser.runtime

enum class BrowserTrackingLevel { STANDARD, STRICT, CUSTOM }
enum class BrowserCookiePolicy { BLOCK_THIRD_PARTY, BLOCK_ALL, ALLOW_ALL }
enum class BrowserSecureDnsMode { OFF, FIRST, ONLY }
enum class BrowserWebColorScheme { SYSTEM, DARK, LIGHT }

data class BrowserRuntimePreferences(
    val trackingLevel: BrowserTrackingLevel,
    val blockTrackers: Boolean,
    val blockFingerprinting: Boolean,
    val blockCryptomining: Boolean,
    val blockSocialTrackers: Boolean,
    val cookiePolicy: BrowserCookiePolicy,
    val safeBrowsingEnabled: Boolean,
    val phishingProtectionEnabled: Boolean,
    val httpsOnlyEnabled: Boolean,
    val globalPrivacyControlEnabled: Boolean,
    val secureDnsMode: BrowserSecureDnsMode,
    val secureDnsUri: String?,
    val javascriptEnabled: Boolean,
    val forceUserScalable: Boolean,
    val fontScale: Float,
    val forceAccessibilityTree: Boolean,
    val webColorScheme: BrowserWebColorScheme,
    val suspendBackgroundMedia: Boolean,
)
