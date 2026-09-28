package app.taho.browser.shell

data class BrowserPasswordCsvRecord(
    val url: String,
    val username: String,
    val password: String,
)

data class BrowserPasswordCsvParseResult(
    val records: List<BrowserPasswordCsvRecord>,
    val rejectedRows: Int,
    val format: String,
)

data class BrowserPasswordCsvImportResult(
    val format: String,
    val imported: Int,
    val skippedExisting: Int,
    val rejectedRows: Int,
)

object BrowserPasswordCsv {
    fun parse(raw: String): BrowserPasswordCsvParseResult {
        val rows = parseRows(raw)
        if (rows.isEmpty()) {
            return BrowserPasswordCsvParseResult(emptyList(), 0, "unknown")
        }

        val header = rows.first().map { it.trim().lowercase() }
        val urlIndex = firstIndex(header, "url", "origin", "hostname")
        val usernameIndex = firstIndex(header, "username", "user", "login")
        val passwordIndex = firstIndex(header, "password", "pass")
        val format = when {
            "formactionorigin" in header || "httprealm" in header -> "Firefox"
            "name" in header && "url" in header && "password" in header -> "Chrome"
            else -> "CSV"
        }

        if (urlIndex < 0 || usernameIndex < 0 || passwordIndex < 0) {
            return BrowserPasswordCsvParseResult(emptyList(), (rows.size - 1).coerceAtLeast(0), format)
        }

        val records = mutableListOf<BrowserPasswordCsvRecord>()
        var rejected = 0
        rows.drop(1).forEach { row ->
            val url = row.getOrNull(urlIndex)?.trim().orEmpty()
            val username = row.getOrNull(usernameIndex)?.trim().orEmpty()
            val password = row.getOrNull(passwordIndex).orEmpty()
            if (url.isBlank() || password.isBlank()) {
                rejected++
                return@forEach
            }
            val normalizedUrl = normalizeUrl(url)
            if (normalizedUrl == null) {
                rejected++
                return@forEach
            }
            records += BrowserPasswordCsvRecord(
                url = normalizedUrl,
                username = username,
                password = password,
            )
        }

        return BrowserPasswordCsvParseResult(records, rejected, format)
    }

    private fun normalizeUrl(raw: String): String? =
        runCatching {
            val prepared = raw.trim().let {
                if ("://" in it) it else "https://$it"
            }
            val uri = java.net.URI(prepared)
            if (
                (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank()
            ) {
                uri.toString()
            } else {
                null
            }
        }.getOrNull()

    private fun firstIndex(header: List<String>, vararg names: String): Int {
        names.forEach { name ->
            val index = header.indexOf(name)
            if (index >= 0) return index
        }
        return -1
    }

    /**
     * Minimal RFC 4180 reader: supports quoted fields, escaped quotes, CRLF,
     * embedded commas, and embedded newlines.
     */
    internal fun parseRows(raw: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        fun finishField() {
            row += field.toString()
            field.setLength(0)
        }

        fun finishRow() {
            finishField()
            if (row.any(String::isNotEmpty)) rows += row.toList()
            row.clear()
        }

        while (index < raw.length) {
            val ch = raw[index]
            when {
                quoted && ch == '"' && index + 1 < raw.length && raw[index + 1] == '"' -> {
                    field.append('"')
                    index += 2
                    continue
                }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> finishField()
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && index + 1 < raw.length && raw[index + 1] == '\n') {
                        index++
                    }
                    finishRow()
                }
                else -> field.append(ch)
            }
            index++
        }

        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }
}
