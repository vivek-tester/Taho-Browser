package app.taho.browser.capture.domain

import java.security.MessageDigest
import java.util.Locale

object SecretDetector {
    const val RULE_VERSION: String = "secret/1.0.0"

    fun assess(
        transactionKey: String,
        fields: List<RawObservedField>,
        hasUrlUserInfo: Boolean = false,
    ): SecretAssessment {
        require(transactionKey.isNotBlank()) {
            "transactionKey must be stable and non-blank"
        }

        val findings = mutableListOf<SecretFinding>()

        fields.forEachIndexed { ordinal, field ->
            classify(field)?.let { classified ->
                findings += SecretFinding(
                    id = stableFindingId(
                        transactionKey = transactionKey,
                        location = field.location,
                        category = classified.category,
                        ordinal = ordinal,
                    ),
                    location = field.location,
                    category = classified.category,
                    confidence = classified.confidence,
                    evidence = classified.evidence,
                )
            }
        }

        if (hasUrlUserInfo) {
            findings += SecretFinding(
                id = stableFindingId(
                    transactionKey = transactionKey,
                    location = SecretLocation.UrlUserInfo,
                    category = SecretCategory.BASIC_AUTH,
                    ordinal = fields.size,
                ),
                location = SecretLocation.UrlUserInfo,
                category = SecretCategory.BASIC_AUTH,
                confidence = Confidence.HIGH,
                evidence = "URL contains a userinfo component",
            )
        }

        if (findings.isEmpty()) return SecretAssessment.NONE

        return SecretAssessment(
            findings = findings,
            highestSeverity = findings.maxOf { severityOf(it.category) },
        )
    }

    private data class Classification(
        val category: SecretCategory,
        val confidence: Confidence,
        val evidence: String,
    )

    private fun classify(field: RawObservedField): Classification? {
        val normalized = normalizeName(field.ruleName)
        val direct = directCategory(field, normalized)

        if (direct != null) {
            val category = refineAuthCategory(direct, field.value)
            return Classification(
                category = category,
                confidence = Confidence.HIGH,
                evidence = directEvidence(field, normalized, category),
            )
        }

        if (!isAuthLikeName(normalized)) return null

        return field.value.inspect { bytes ->
            when {
                startsWithAsciiIgnoreCase(bytes, "Bearer ") ->
                    Classification(
                        SecretCategory.BEARER_TOKEN,
                        Confidence.LOW,
                        "auth-like field contains bearer-scheme shaped data",
                    )

                startsWithAsciiIgnoreCase(bytes, "Basic ") ->
                    Classification(
                        SecretCategory.BASIC_AUTH,
                        Confidence.LOW,
                        "auth-like field contains basic-auth shaped data",
                    )

                looksLikeJwt(bytes) ->
                    Classification(
                        SecretCategory.JWT,
                        Confidence.LOW,
                        "auth-like field contains JWT-shaped data",
                    )

                looksLikeAwsAccessKey(bytes) ->
                    Classification(
                        SecretCategory.API_KEY,
                        Confidence.LOW,
                        "auth-like field contains AWS access-key shaped data",
                    )

                looksLikeHighEntropyToken(bytes) ->
                    Classification(
                        SecretCategory.API_KEY,
                        Confidence.LOW,
                        "auth-like field contains a long token-shaped value",
                    )

                else -> null
            }
        }
    }

    private fun directCategory(
        field: RawObservedField,
        name: String,
    ): SecretCategory? {
        if (field is RawObservedField.Cookie) {
            return SecretCategory.COOKIE
        }

        return when (name) {
            "authorization", "proxy-authorization" ->
                SecretCategory.AUTHORIZATION
            "cookie", "set-cookie" ->
                SecretCategory.COOKIE
            "x-api-key", "api-key", "apikey" ->
                SecretCategory.API_KEY
            "x-auth-token", "x-access-token", "access-token" ->
                if (field is RawObservedField.Query) {
                    SecretCategory.QUERY_TOKEN
                } else {
                    SecretCategory.BEARER_TOKEN
                }
            "x-csrf-token", "x-xsrf-token", "csrf-token", "xsrf-token" ->
                SecretCategory.CSRF_TOKEN
            "x-session-id", "session-id" ->
                SecretCategory.SESSION_ID
            "client-secret", "clientsecret", "secret" ->
                SecretCategory.CLIENT_SECRET
            "password", "passwd" ->
                SecretCategory.PASSWORD
            "token" ->
                if (field is RawObservedField.Query ||
                    field is RawObservedField.BodyJson ||
                    field is RawObservedField.BodyForm
                ) {
                    SecretCategory.QUERY_TOKEN
                } else {
                    SecretCategory.BEARER_TOKEN
                }
            else -> null
        }
    }

    private fun refineAuthCategory(
        direct: SecretCategory,
        value: RawObservedValue,
    ): SecretCategory {
        if (direct != SecretCategory.AUTHORIZATION &&
            direct != SecretCategory.BEARER_TOKEN
        ) {
            return direct
        }

        return value.inspect { bytes ->
            when {
                startsWithAsciiIgnoreCase(bytes, "Bearer ") ->
                    SecretCategory.BEARER_TOKEN
                startsWithAsciiIgnoreCase(bytes, "Basic ") ->
                    SecretCategory.BASIC_AUTH
                looksLikeJwt(bytes) ->
                    SecretCategory.JWT
                else -> direct
            }
        }
    }

    private fun directEvidence(
        field: RawObservedField,
        normalizedName: String,
        category: SecretCategory,
    ): String {
        val location = when (field) {
            is RawObservedField.Header -> "header"
            is RawObservedField.Query -> "query parameter"
            is RawObservedField.Cookie -> "cookie"
            is RawObservedField.BodyJson -> "JSON body field"
            is RawObservedField.BodyForm -> "form body field"
        }

        val suffix = when (category) {
            SecretCategory.BEARER_TOKEN -> " with bearer scheme"
            SecretCategory.BASIC_AUTH -> " with basic-auth scheme"
            SecretCategory.JWT -> " with JWT-shaped value"
            else -> ""
        }

        return location + " name '" + normalizedName + "'" + suffix
    }

    private fun normalizeName(name: String): String =
        name
            .trim()
            .lowercase(Locale.ROOT)
            .replace('_', '-')

    private fun isAuthLikeName(name: String): Boolean =
        AUTHISH_TERMS.any(name::contains)

    private fun startsWithAsciiIgnoreCase(
        bytes: ByteArray,
        prefix: String,
    ): Boolean {
        if (bytes.size < prefix.length) return false
        for (index in prefix.indices) {
            val actual = bytes[index].toInt() and 0xff
            val expected = prefix[index].code
            if (asciiLower(actual) != asciiLower(expected)) return false
        }
        return true
    }

    private fun looksLikeJwt(bytes: ByteArray): Boolean {
        if (bytes.size < 24) return false
        if (!startsWithAsciiIgnoreCase(bytes, "eyJ")) return false

        var dots = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            if (value == '.'.code) {
                dots += 1
            } else if (!isBase64UrlByte(value)) {
                return false
            }
        }
        return dots == 2
    }

    private fun looksLikeAwsAccessKey(bytes: ByteArray): Boolean {
        if (bytes.size < 16 || bytes.size > 32) return false
        if (!startsWithAsciiIgnoreCase(bytes, "AKIA")) return false
        return bytes.drop(4).all { byte ->
            val value = byte.toInt() and 0xff
            value in 'A'.code..'Z'.code || value in '0'.code..'9'.code
        }
    }

    private fun looksLikeHighEntropyToken(bytes: ByteArray): Boolean {
        if (bytes.size !in 32..256) return false

        var letters = 0
        var digits = 0
        var symbols = 0

        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            when {
                value in 'a'.code..'z'.code ||
                    value in 'A'.code..'Z'.code -> letters += 1
                value in '0'.code..'9'.code -> digits += 1
                value == '-'.code ||
                    value == '_'.code ||
                    value == '+'.code ||
                    value == '/'.code ||
                    value == '='.code ||
                    value == '.'.code -> symbols += 1
                else -> return false
            }
        }

        return letters > 0 &&
            digits > 0 &&
            (symbols > 0 || bytes.size >= 40)
    }

    private fun isBase64UrlByte(value: Int): Boolean =
        value in 'a'.code..'z'.code ||
            value in 'A'.code..'Z'.code ||
            value in '0'.code..'9'.code ||
            value == '-'.code ||
            value == '_'.code ||
            value == '.'.code

    private fun asciiLower(value: Int): Int =
        if (value in 'A'.code..'Z'.code) {
            value + ('a'.code - 'A'.code)
        } else {
            value
        }

    private fun severityOf(category: SecretCategory): SecretSeverity =
        when (category) {
            SecretCategory.PASSWORD,
            SecretCategory.CLIENT_SECRET ->
                SecretSeverity.CRITICAL

            SecretCategory.AUTHORIZATION,
            SecretCategory.BEARER_TOKEN,
            SecretCategory.COOKIE,
            SecretCategory.API_KEY,
            SecretCategory.JWT,
            SecretCategory.BASIC_AUTH,
            SecretCategory.SESSION_ID ->
                SecretSeverity.HIGH

            SecretCategory.CSRF_TOKEN,
            SecretCategory.QUERY_TOKEN ->
                SecretSeverity.MEDIUM
        }

    private fun stableFindingId(
        transactionKey: String,
        location: SecretLocation,
        category: SecretCategory,
        ordinal: Int,
    ): String {
        val material = (
            RULE_VERSION + "|" +
                transactionKey + "|" +
                location.stableKey + "|" +
                category.name + "|" +
                ordinal
            ).encodeToByteArray()
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(material)
        return digest
            .take(12)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
    }

    private val AUTHISH_TERMS = listOf(
        "auth",
        "token",
        "key",
        "secret",
        "password",
        "passwd",
        "session",
        "csrf",
        "xsrf",
        "cookie",
    )
}
