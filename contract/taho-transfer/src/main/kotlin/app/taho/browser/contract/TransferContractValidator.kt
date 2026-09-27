package app.taho.browser.contract

import java.util.Base64

enum class ContractViolationCode {
    INVALID_SCHEMA,
    UNSUPPORTED_VERSION,
    INVALID_TRANSFER_ID,
    INVALID_TIMESTAMP,
    INVALID_METHOD,
    INVALID_URL,
    INVALID_HEADER,
    INVALID_REDACTION,
    INVALID_BODY,
    BODY_CAP_EXCEEDED,
    ARTIFACT_CAP_EXCEEDED,
    SECURITY_REVIEW_REQUIRED,
}

data class ContractViolation(
    val code: ContractViolationCode,
    val path: String,
    val message: String,
)

object TransferContractValidator {
    private val ulid = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
    private val method = Regex("^[A-Z][A-Z0-9!#$%&'*+.^_|~-]*$")

    fun validate(
        envelope: RequestTransferV1,
        encodedEnvelopeUtf8Bytes: Long,
    ): List<ContractViolation> {
        val violations = mutableListOf<ContractViolation>()

        if (envelope.schema != TAHO_REQUEST_TRANSFER_SCHEMA) {
            violations += violation(
                ContractViolationCode.INVALID_SCHEMA,
                "schema",
                "Schema must match exactly.",
            )
        }
        if (
            envelope.version != TAHO_REQUEST_TRANSFER_VERSION ||
            envelope.minimumReaderVersion > TAHO_REQUEST_TRANSFER_VERSION
        ) {
            violations += violation(
                ContractViolationCode.UNSUPPORTED_VERSION,
                "version",
                "Envelope requires an unsupported reader version.",
            )
        }
        if (!ulid.matches(envelope.transferId)) {
            violations += violation(
                ContractViolationCode.INVALID_TRANSFER_ID,
                "transferId",
                "transferId must be a canonical ULID.",
            )
        }
        if (envelope.issuedAt < 0 || envelope.capture.timestamp < 0 || envelope.provenance.capturedAt < 0) {
            violations += violation(
                ContractViolationCode.INVALID_TIMESTAMP,
                "issuedAt",
                "Epoch timestamps must be non-negative.",
            )
        }
        if (!method.matches(envelope.request.method)) {
            violations += violation(
                ContractViolationCode.INVALID_METHOD,
                "request.method",
                "HTTP method is malformed.",
            )
        }
        if (!looksLikeTransferUrl(envelope.request.url)) {
            violations += violation(
                ContractViolationCode.INVALID_URL,
                "request.url",
                "Transfer URL must be absolute HTTP(S).",
            )
        }

        envelope.request.query.forEachIndexed { index, query ->
            validateProtectedValue(
                redacted = query.redacted,
                policy = query.policy,
                path = "request.query[$index]",
                violations = violations,
            )
        }

        envelope.request.headers.forEachIndexed { index, header ->
            if (header.name.isBlank() || containsControl(header.name) || containsControl(header.value)) {
                violations += violation(
                    ContractViolationCode.INVALID_HEADER,
                    "request.headers[$index]",
                    "Header names and values must not contain control characters.",
                )
            }
            validateProtectedValue(
                redacted = header.redacted,
                policy = header.policy,
                path = "request.headers[$index]",
                violations = violations,
            )
        }

        envelope.request.body?.let { body -> validateBody(body, violations) }

        if (envelope.security.secretCount < 0) {
            violations += violation(
                ContractViolationCode.INVALID_REDACTION,
                "security.secretCount",
                "secretCount must be non-negative.",
            )
        }
        if (envelope.security.reviewRequired) {
            violations += violation(
                ContractViolationCode.SECURITY_REVIEW_REQUIRED,
                "security.reviewRequired",
                "Unclassified or opaque sensitive content must be reviewed before transfer.",
            )
        }

        if (encodedEnvelopeUtf8Bytes < 0) {
            violations += violation(
                ContractViolationCode.INVALID_BODY,
                "$",
                "Encoded envelope byte count must be non-negative.",
            )
        } else if (encodedEnvelopeUtf8Bytes > ContractLimits.ARTIFACT_PLAINTEXT_BYTES) {
            violations += violation(
                ContractViolationCode.ARTIFACT_CAP_EXCEEDED,
                "$",
                "Serialized envelope exceeds the configured artifact cap.",
            )
        }

        return violations
    }

    private fun validateBody(
        body: TransferBody,
        violations: MutableList<ContractViolation>,
    ) {
        if (body.size < 0 || body.declaredSize != null && body.declaredSize < 0) {
            violations += violation(
                ContractViolationCode.INVALID_BODY,
                "request.body.size",
                "Body sizes must be non-negative.",
            )
        }
        if (body.size > ContractLimits.CAPTURED_BODY_BYTES) {
            violations += violation(
                ContractViolationCode.BODY_CAP_EXCEEDED,
                "request.body.size",
                "Captured body exceeds the configured capture cap.",
            )
        }
        if (body.truncated && body.completeness != Completeness.TRUNCATED) {
            violations += violation(
                ContractViolationCode.INVALID_BODY,
                "request.body.completeness",
                "A truncated body must carry TRUNCATED completeness.",
            )
        }

        when (body.encoding) {
            BodyEncoding.UTF8 -> {
                if (body.content == null || body.uri != null) {
                    violations += invalidEncoding("UTF8 requires content and forbids uri.")
                } else if (body.content.toByteArray(Charsets.UTF_8).size.toLong() != body.size) {
                    violations += invalidSize()
                }
            }

            BodyEncoding.BASE64 -> {
                if (body.content == null || body.uri != null) {
                    violations += invalidEncoding("BASE64 requires content and forbids uri.")
                } else {
                    val decodedSize = runCatching {
                        Base64.getDecoder().decode(body.content).size.toLong()
                    }.getOrNull()
                    if (decodedSize == null || decodedSize != body.size) {
                        violations += invalidSize()
                    }
                }
            }

            BodyEncoding.FILE_URI -> {
                if (body.uri.isNullOrBlank() || body.content != null) {
                    violations += invalidEncoding("FILE_URI requires uri and forbids inline content.")
                }
            }
        }

        if (body.representation == BodyRepresentation.BINARY && body.encoding != BodyEncoding.FILE_URI) {
            violations += invalidEncoding("BINARY bodies must use FILE_URI.")
        }

        body.parts.forEachIndexed { index, part ->
            if (part.size < 0) {
                violations += violation(
                    ContractViolationCode.INVALID_BODY,
                    "request.body.parts[$index].size",
                    "Multipart part size must be non-negative.",
                )
            }
            if (part.encoding == BodyEncoding.FILE_URI && part.uri.isNullOrBlank()) {
                violations += violation(
                    ContractViolationCode.INVALID_BODY,
                    "request.body.parts[$index].uri",
                    "FILE_URI multipart parts require a uri.",
                )
            }
        }
    }

    private fun validateProtectedValue(
        redacted: Boolean,
        policy: SecretPolicy?,
        path: String,
        violations: MutableList<ContractViolation>,
    ) {
        when (policy) {
            SecretPolicy.PARAMETERIZE, SecretPolicy.MASK ->
                if (!redacted) {
                    violations += violation(
                        ContractViolationCode.INVALID_REDACTION,
                        path,
                        "PARAMETERIZE and MASK values must be marked redacted.",
                    )
                }

            SecretPolicy.EXPLICIT ->
                if (redacted) {
                    violations += violation(
                        ContractViolationCode.INVALID_REDACTION,
                        path,
                        "EXPLICIT values must describe the transferred live value.",
                    )
                }

            null -> Unit
        }
    }

    private fun looksLikeTransferUrl(value: String): Boolean =
        value.startsWith("https://") || value.startsWith("http://")

    private fun containsControl(value: String): Boolean =
        value.any { it == '\r' || it == '\n' || it.code < 0x20 || it.code == 0x7f }

    private fun invalidEncoding(message: String) = violation(
        ContractViolationCode.INVALID_BODY,
        "request.body.encoding",
        message,
    )

    private fun invalidSize() = violation(
        ContractViolationCode.INVALID_BODY,
        "request.body.size",
        "Body size must equal the actual decoded bytes transferred.",
    )

    private fun violation(
        code: ContractViolationCode,
        path: String,
        message: String,
    ) = ContractViolation(code, path, message)
}
