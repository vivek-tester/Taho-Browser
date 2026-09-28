package app.taho.browser.shell

data class ExtensionMarketplaceItemUi(
    val id: String,
    val name: String,
    val summary: String,
    val author: String,
    val version: String,
    val rating: Double?,
    val users: Long?,
    val installUrl: String,
    val detailUrl: String?,
)
