package app.taho.browser.runtime

data class BrowserWebAppManifest(
    val name: String,
    val shortName: String?,
    val startUrl: String,
    val scope: String?,
    val display: String?,
    val themeColor: String?,
    val backgroundColor: String?,
)
