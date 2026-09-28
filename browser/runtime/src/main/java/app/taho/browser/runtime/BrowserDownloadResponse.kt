package app.taho.browser.runtime

import java.io.InputStream

/**
 * Ownership of [body] transfers to the consumer. It must always be closed.
 */
data class BrowserDownloadResponse(
    val tabId: String,
    val uri: String,
    val headers: Map<String, String>,
    val statusCode: Int,
    val body: InputStream,
)
