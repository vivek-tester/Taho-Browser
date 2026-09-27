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
}
