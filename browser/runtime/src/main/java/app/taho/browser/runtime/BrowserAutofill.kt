package app.taho.browser.runtime

/**
 * Runtime-facing representations deliberately avoid Compose/storage-module
 * dependencies. The app bridges these records to its encrypted browser vault.
 */
data class BrowserStoredLogin(
    val id: String,
    val domain: String,
    val username: String,
    val password: String,
)

data class BrowserStoredAddress(
    val id: String,
    val label: String,
    val fullName: String,
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val phone: String,
    val email: String,
)

data class BrowserStoredCreditCard(
    val id: String,
    val cardholderName: String,
    val number: String,
    val expirationMonth: String,
    val expirationYear: String,
)

interface BrowserAutofillStore {
    val loginAutofillEnabled: Boolean
    val passwordSavePromptEnabled: Boolean
    val addressAutofillEnabled: Boolean
    val paymentAutofillEnabled: Boolean

    fun loginsForDomain(domain: String): List<BrowserStoredLogin>
    fun allLogins(): List<BrowserStoredLogin>
    fun addresses(): List<BrowserStoredAddress>
    fun creditCards(): List<BrowserStoredCreditCard>

    fun saveLogin(login: BrowserStoredLogin)
    fun markLoginUsed(id: String)
    fun saveAddress(address: BrowserStoredAddress)
    fun saveCreditCard(card: BrowserStoredCreditCard)
}

enum class BrowserAutofillPromptKind {
    LOGIN_SAVE,
    LOGIN_SELECT,
    ADDRESS_SAVE,
    ADDRESS_SELECT,
    CREDIT_CARD_SAVE,
    CREDIT_CARD_SELECT,
}

data class BrowserAutofillPromptOption(
    val index: Int,
    val title: String,
    val subtitle: String?,
)

data class BrowserAutofillPrompt(
    val id: String,
    val tabId: String,
    val origin: String?,
    val kind: BrowserAutofillPromptKind,
    val options: List<BrowserAutofillPromptOption>,
)
