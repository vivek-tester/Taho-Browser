package app.taho.browser.capture.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class JsonBodySecretScanResult(
    val assessment: SecretAssessment,
    val requiresReview: Boolean,
)

object JsonBodySecretScanner {
    private val sensitiveNames = setOf(
        "password",
        "passwd",
        "token",
        "secret",
        "apikey",
        "api-key",
        "clientsecret",
        "client-secret",
        "access-token",
        "x-access-token",
        "session-id",
    )

    fun scan(
        transactionKey: String,
        rawJson: String,
    ): JsonBodySecretScanResult {
        if (rawJson.isBlank()) {
            return JsonBodySecretScanResult(
                assessment = SecretAssessment.NONE,
                requiresReview = false,
            )
        }

        val root = parseRoot(rawJson)
            ?: return JsonBodySecretScanResult(
                assessment = SecretAssessment.NONE,
                requiresReview = true,
            )

        val fields = mutableListOf<RawObservedField>()
        var opaqueSensitiveContainer = false

        fun visit(value: Any?, path: String) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = if (value.isNull(key)) null else value.get(key)
                        val childPath = if (path.isEmpty()) "$." + key else path + "." + key
                        val normalized = normalize(key)

                        if (normalized in sensitiveNames) {
                            when (child) {
                                null -> Unit
                                is JSONObject, is JSONArray ->
                                    opaqueSensitiveContainer = true
                                else -> fields += RawObservedField.BodyJson(
                                    path = childPath,
                                    fieldName = key,
                                    value = RawObservedValue.copyOf(
                                        child.toString().toByteArray(Charsets.UTF_8),
                                    ),
                                )
                            }
                        } else if (child is String && looksSecretShaped(child)) {
                            // Unknown field names with credential-shaped values are not
                            // classified into a category here; M4 fails closed instead.
                            opaqueSensitiveContainer = true
                        }

                        if (child is JSONObject || child is JSONArray) {
                            visit(child, childPath)
                        }
                    }
                }

                is JSONArray -> {
                    repeat(value.length()) { index ->
                        if (!value.isNull(index)) {
                            visit(value.get(index), path + "[" + index + "]")
                        }
                    }
                }
            }
        }

        visit(root, "")

        return try {
            JsonBodySecretScanResult(
                assessment = SecretDetector.assess(
                    transactionKey = transactionKey,
                    fields = fields,
                ),
                requiresReview = opaqueSensitiveContainer,
            )
        } finally {
            fields.forEach { it.value.close() }
        }
    }

    private fun parseRoot(raw: String): Any? =
        runCatching {
            val trimmed = raw.trim()
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> null
            }
        }.getOrNull()

    private fun normalize(value: String): String =
        value
            .trim()
            .lowercase(Locale.ROOT)
            .replace('_', '-')

    private fun looksSecretShaped(value: String): Boolean {
        if (value.startsWith("Bearer ", ignoreCase = true) ||
            value.startsWith("Basic ", ignoreCase = true)
        ) {
            return true
        }

        if (JWT.matches(value) || AWS_ACCESS_KEY.matches(value)) return true
        if (value.length !in 32..256 || value.any(Char::isWhitespace)) return false

        val hasLetter = value.any(Char::isLetter)
        val hasDigit = value.any(Char::isDigit)
        val hasTokenSymbol = value.any { it in "-_+/=." }
        return hasLetter && hasDigit && (hasTokenSymbol || value.length >= 40)
    }

    private val JWT = Regex(
        """^eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$""",
    )
    private val AWS_ACCESS_KEY = Regex("""^AKIA[A-Z0-9]{12,28}$""")
}
