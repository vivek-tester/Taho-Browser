package app.taho.browser.capture.domain

import java.util.Arrays

enum class SecretSeverity {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class Confidence {
    LOW,
    HIGH,
}

enum class SecretCategory {
    AUTHORIZATION,
    BEARER_TOKEN,
    COOKIE,
    API_KEY,
    JWT,
    BASIC_AUTH,
    CSRF_TOKEN,
    SESSION_ID,
    CLIENT_SECRET,
    PASSWORD,
    QUERY_TOKEN,
}

sealed interface SecretLocation {
    val stableKey: String

    data class Header(val name: String) : SecretLocation {
        override val stableKey: String = "HEADER:" + name.lowercase()
    }

    data class Query(val key: String) : SecretLocation {
        override val stableKey: String = "QUERY:" + key.lowercase()
    }

    data class Cookie(val name: String) : SecretLocation {
        override val stableKey: String = "COOKIE:" + name.lowercase()
    }

    data class BodyJsonPath(val path: String) : SecretLocation {
        override val stableKey: String = "BODY_JSON:" + path
    }

    data class BodyFormField(val name: String) : SecretLocation {
        override val stableKey: String = "BODY_FORM:" + name.lowercase()
    }

    data object UrlUserInfo : SecretLocation {
        override val stableKey: String = "URL_USERINFO"
    }

    data class Jwt(val source: String) : SecretLocation {
        override val stableKey: String = "JWT:" + source
    }
}

data class SecretFinding(
    val id: String,
    val location: SecretLocation,
    val category: SecretCategory,
    val confidence: Confidence,
    val evidence: String,
)

data class SecretAssessment(
    val findings: List<SecretFinding>,
    val highestSeverity: SecretSeverity,
) {
    companion object {
        val NONE = SecretAssessment(
            findings = emptyList(),
            highestSeverity = SecretSeverity.NONE,
        )
    }
}

data class SecretRef(
    val id: String,
    val category: SecretCategory,
)

enum class SecretPolicy {
    PARAMETERIZE,
    MASK,
    EXPLICIT,
}

/**
 * Owns raw observed bytes without ever exposing them through toString().
 *
 * The capture ingress owns this value and must close it once classification/
 * representation conversion is complete.
 */
class RawObservedValue private constructor(
    bytes: ByteArray,
) : AutoCloseable {
    private val bytes: ByteArray = bytes.copyOf()
    private var closed = false

    val size: Int
        get() = bytes.size

    internal fun <T> inspect(block: (ByteArray) -> T): T {
        check(!closed) { "RawObservedValue has been cleared" }
        return block(bytes)
    }

    override fun close() {
        if (closed) return
        Arrays.fill(bytes, 0)
        closed = true
    }

    override fun toString(): String =
        "RawObservedValue(size=$size, redacted)"

    companion object {
        fun copyOf(bytes: ByteArray): RawObservedValue =
            RawObservedValue(bytes)
    }
}

/**
 * Classified plaintext container. It is deliberately opaque outside this module.
 */
class SecretValue private constructor(
    val category: SecretCategory,
    bytes: ByteArray,
) : AutoCloseable {
    private val bytes: ByteArray = bytes.copyOf()
    private var closed = false

    val size: Int
        get() = bytes.size

    internal fun <T> inspect(block: (ByteArray) -> T): T {
        check(!closed) { "SecretValue has been cleared" }
        return block(bytes)
    }

    override fun close() {
        if (closed) return
        Arrays.fill(bytes, 0)
        closed = true
    }

    override fun toString(): String =
        "SecretValue(category=$category, redacted)"

    companion object {
        fun copyOf(
            category: SecretCategory,
            bytes: ByteArray,
        ): SecretValue = SecretValue(category, bytes)
    }
}

sealed interface RawObservedField {
    val value: RawObservedValue
    val location: SecretLocation
    val ruleName: String

    data class Header(
        val name: String,
        override val value: RawObservedValue,
    ) : RawObservedField {
        override val location: SecretLocation = SecretLocation.Header(name)
        override val ruleName: String = name
    }

    data class Query(
        val key: String,
        override val value: RawObservedValue,
    ) : RawObservedField {
        override val location: SecretLocation = SecretLocation.Query(key)
        override val ruleName: String = key
    }

    data class Cookie(
        val name: String,
        override val value: RawObservedValue,
    ) : RawObservedField {
        override val location: SecretLocation = SecretLocation.Cookie(name)
        override val ruleName: String = name
    }

    data class BodyJson(
        val path: String,
        val fieldName: String,
        override val value: RawObservedValue,
    ) : RawObservedField {
        override val location: SecretLocation = SecretLocation.BodyJsonPath(path)
        override val ruleName: String = fieldName
    }

    data class BodyForm(
        val name: String,
        override val value: RawObservedValue,
    ) : RawObservedField {
        override val location: SecretLocation = SecretLocation.BodyFormField(name)
        override val ruleName: String = name
    }
}

data class MaskedSecret(
    val ref: SecretRef,
    val displayValue: String,
)

object SecretPolicyDefaults {
    fun forCategory(category: SecretCategory): SecretPolicy =
        if (category == SecretCategory.COOKIE) {
            SecretPolicy.MASK
        } else {
            SecretPolicy.PARAMETERIZE
        }
}

object SecretMasker {
    fun mask(finding: SecretFinding): MaskedSecret {
        val display = when (finding.category) {
            SecretCategory.BEARER_TOKEN -> "Bearer ••••••••"
            SecretCategory.BASIC_AUTH -> "Basic ••••••••"
            else -> "••••••••"
        }
        return MaskedSecret(
            ref = SecretRef(finding.id, finding.category),
            displayValue = display,
        )
    }
}
