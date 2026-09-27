package app.taho.browser.transfer.core

import app.taho.browser.capture.domain.NormalizableRequest
import app.taho.browser.capture.domain.NormalizedHeader
import app.taho.browser.capture.domain.NormalizedHeaderValue
import app.taho.browser.capture.domain.RequestNormalizer
import app.taho.browser.capture.domain.SecretAssessment
import app.taho.browser.capture.domain.SecretCategory
import app.taho.browser.capture.domain.SecretFinding
import app.taho.browser.capture.domain.SecretLocation
import app.taho.browser.capture.domain.SecretPolicy
import app.taho.browser.capture.domain.SecretPolicyDefaults
import app.taho.browser.capture.domain.SecretRef
import app.taho.browser.contract.BodyEncoding
import app.taho.browser.contract.CaptureCompleteness
import app.taho.browser.contract.Completeness
import app.taho.browser.contract.ContractLimits
import app.taho.browser.contract.ObservationSource
import app.taho.browser.contract.RequestTransferV1
import app.taho.browser.contract.SafeProvenance
import app.taho.browser.contract.SecretFinding as ContractSecretFinding
import app.taho.browser.contract.SecretPolicy as ContractSecretPolicy
import app.taho.browser.contract.TransferBody
import app.taho.browser.contract.TransferCapture
import app.taho.browser.contract.TransferContractValidator
import app.taho.browser.contract.TransferEnvelopeJson
import app.taho.browser.contract.TransferHeader
import app.taho.browser.contract.TransferQueryParameter
import app.taho.browser.contract.TransferRequest
import app.taho.browser.contract.TransferSecurity
import app.taho.browser.contract.TransferSource

sealed interface M4FieldValue {
    data class Public(val value: String) : M4FieldValue
    data class Protected(val ref: SecretRef) : M4FieldValue
}

data class M4QueryInput(
    val name: String,
    val value: M4FieldValue,
)

data class M4CapturedRequest(
    val transferId: String,
    val issuedAt: Long,
    val appVersion: String,
    val engineVersion: String,
    val captureSessionId: String,
    val tabId: String?,
    val transactionId: String,
    val method: String,
    val url: String,
    val query: List<M4QueryInput>,
    val headers: List<NormalizedHeader>,
    val body: TransferBody?,
    val status: Int?,
    val statusText: String?,
    val durationMs: Long?,
    val initiator: String?,
    val completeness: CaptureCompleteness,
    val secretAssessment: SecretAssessment,
    val fromPrivateSession: Boolean,
    val reviewRequired: Boolean,
    val capturedAt: Long,
    val redirectCount: Int,
    val bodyRepresentation: BodyRepresentation? = body?.representation,
    val bodyLimitation: String? = null,
    val observation: ObservationSource = ObservationSource.ENGINE,
)

data class M4DisplayHeader(
    val name: String,
    val displayValue: String,
    val sensitive: Boolean,
)

data class M4DisplayRequest(
    val method: String,
    val url: String,
    val status: Int?,
    val durationMs: Long?,
    val headers: List<M4DisplayHeader>,
    val requestBodyCompleteness: Completeness,
    val responseBodyCompleteness: Completeness,
    val bodyRepresentation: BodyRepresentation?,
    val bodyLimitation: String?,
    val sensitiveCount: Int,
)

data class M4PreparedTransfer(
    val envelope: RequestTransferV1,
    val encodedJson: String,
    val encodedUtf8Bytes: Int,
    val display: M4DisplayRequest,
)

enum class M4PreparationBlock {
    REVIEW_REQUIRED,
    EXPLICIT_SECRET_UNAVAILABLE,
    INVALID_CONTRACT,
    REQUIRES_LARGE_PAYLOAD_M6,
}

sealed interface M4PreparationResult {
    data class Prepared(val value: M4PreparedTransfer) : M4PreparationResult
    data class Blocked(
        val reason: M4PreparationBlock,
        val details: List<String> = emptyList(),
    ) : M4PreparationResult
}

fun interface ExplicitSecretProvider {
    fun reveal(ref: SecretRef): String?
}

object M4TransferPreparer {
    fun projectDisplay(input: M4CapturedRequest): M4DisplayRequest {
        val normalized = RequestNormalizer.normalize(
            NormalizableRequest(
                url = input.url,
                method = input.method.uppercase(),
                headers = input.headers,
            ),
        )
        val displayHeaders = normalized.headers.map { header ->
            when (val value = header.value) {
                is NormalizedHeaderValue.Public ->
                    M4DisplayHeader(header.name, value.value, sensitive = false)
                is NormalizedHeaderValue.Protected ->
                    M4DisplayHeader(
                        header.name,
                        maskFor(value.ref.category),
                        sensitive = true,
                    )
            }
        }
        return M4DisplayRequest(
            method = normalized.method,
            url = normalized.url,
            status = input.status,
            durationMs = input.durationMs,
            headers = displayHeaders,
            requestBodyCompleteness = input.completeness.requestBody,
            responseBodyCompleteness = input.completeness.responseBody,
            bodyRepresentation = input.bodyRepresentation,
            bodyLimitation = input.bodyLimitation,
            sensitiveCount = input.secretAssessment.findings.size,
        )
    }

    fun prepare(
        input: M4CapturedRequest,
        requestedPolicy: SecretPolicy = SecretPolicy.PARAMETERIZE,
        explicitSecretProvider: ExplicitSecretProvider? = null,
    ): M4PreparationResult {
        if (input.reviewRequired) {
            return M4PreparationResult.Blocked(M4PreparationBlock.REVIEW_REQUIRED)
        }

        val normalized = RequestNormalizer.normalize(
            NormalizableRequest(
                url = input.url,
                method = input.method.uppercase(),
                headers = input.headers,
            ),
        )

        val findingById = input.secretAssessment.findings.associateBy { it.id }

        fun policyFor(ref: SecretRef): SecretPolicy =
            if (ref.category == SecretCategory.COOKIE && requestedPolicy != SecretPolicy.EXPLICIT) {
                SecretPolicyDefaults.forCategory(ref.category)
            } else {
                requestedPolicy
            }

        fun protectedValue(ref: SecretRef): Triple<String, Boolean, ContractSecretPolicy>? {
            val policy = policyFor(ref)
            return when (policy) {
                SecretPolicy.PARAMETERIZE ->
                    Triple(parameterName(ref.category), true, ContractSecretPolicy.PARAMETERIZE)

                SecretPolicy.MASK ->
                    Triple(maskFor(ref.category), true, ContractSecretPolicy.MASK)

                SecretPolicy.EXPLICIT -> {
                    val live = explicitSecretProvider?.reveal(ref) ?: return null
                    Triple(live, false, ContractSecretPolicy.EXPLICIT)
                }
            }
        }

        val query = buildList {
            for (item in input.query) {
                when (val value = item.value) {
                    is M4FieldValue.Public -> add(
                        TransferQueryParameter(
                            name = item.name,
                            value = value.value,
                            redacted = false,
                        ),
                    )

                    is M4FieldValue.Protected -> {
                        val transformed = protectedValue(value.ref)
                            ?: return M4PreparationResult.Blocked(
                                M4PreparationBlock.EXPLICIT_SECRET_UNAVAILABLE,
                            )
                        add(
                            TransferQueryParameter(
                                name = item.name,
                                value = transformed.first,
                                redacted = transformed.second,
                                secretCategory = value.ref.category.name,
                                policy = transformed.third,
                            ),
                        )
                    }
                }
            }
        }

        val headers = buildList {
            for (header in normalized.headers) {
                when (val value = header.value) {
                    is NormalizedHeaderValue.Public -> add(
                        TransferHeader(
                            name = header.name,
                            value = value.value,
                            redacted = false,
                        ),
                    )

                    is NormalizedHeaderValue.Protected -> {
                        val transformed = protectedValue(value.ref)
                            ?: return M4PreparationResult.Blocked(
                                M4PreparationBlock.EXPLICIT_SECRET_UNAVAILABLE,
                            )
                        add(
                            TransferHeader(
                                name = header.name,
                                value = transformed.first,
                                redacted = transformed.second,
                                secretCategory = value.ref.category.name,
                                policy = transformed.third,
                            ),
                        )
                    }
                }
            }
        }

        val findings = input.secretAssessment.findings.map { finding ->
            ContractSecretFinding(
                location = finding.location.stableKey,
                category = finding.category.name,
                policy = contractPolicy(policyFor(SecretRef(finding.id, finding.category))),
            )
        }

        val envelope = RequestTransferV1(
            transferId = input.transferId,
            issuedAt = input.issuedAt,
            source = TransferSource(
                appVersion = input.appVersion,
                engine = input.engineVersion,
                captureSessionId = input.captureSessionId,
                tabId = input.tabId,
                observation = input.observation,
            ),
            request = TransferRequest(
                id = input.transactionId,
                method = normalized.method,
                url = normalized.url,
                query = query,
                headers = headers,
                body = input.body,
            ),
            capture = TransferCapture(
                timestamp = input.capturedAt,
                durationMs = input.durationMs,
                status = input.status,
                statusText = input.statusText,
                initiator = input.initiator,
                timing = null,
                completeness = input.completeness,
            ),
            security = TransferSecurity(
                secretPolicy = contractPolicy(requestedPolicy),
                containsSensitiveData = findings.isNotEmpty(),
                secretCount = findings.size,
                fromPrivateSession = input.fromPrivateSession,
                reviewRequired = false,
                findings = findings,
            ),
            provenance = SafeProvenance(
                normalizerVersion = normalized.normalizerVersion,
                captureEngineVersion = input.engineVersion,
                capturedAt = input.capturedAt,
                redirectCount = input.redirectCount,
            ),
        )

        val encoded = TransferEnvelopeJson.encode(envelope)
        val bytes = encoded.toByteArray(Charsets.UTF_8).size
        val violations = TransferContractValidator.validate(
            envelope = envelope,
            encodedEnvelopeUtf8Bytes = bytes.toLong(),
        )

        if (violations.isNotEmpty()) {
            return M4PreparationResult.Blocked(
                reason = M4PreparationBlock.INVALID_CONTRACT,
                details = violations.map { it.path + ": " + it.code.name },
            )
        }
        if (bytes > ContractLimits.DIRECT_ENVELOPE_UTF8_BYTES) {
            return M4PreparationResult.Blocked(
                M4PreparationBlock.REQUIRES_LARGE_PAYLOAD_M6,
            )
        }

        return M4PreparationResult.Prepared(
            M4PreparedTransfer(
                envelope = envelope,
                encodedJson = encoded,
                encodedUtf8Bytes = bytes,
                display = projectDisplay(input),
            ),
        )
    }

    private fun contractPolicy(policy: SecretPolicy): ContractSecretPolicy =
        ContractSecretPolicy.valueOf(policy.name)

    private fun parameterName(category: SecretCategory): String =
        when (category) {
            SecretCategory.API_KEY -> "{{API_KEY}}"
            SecretCategory.CSRF_TOKEN -> "{{CSRF_TOKEN}}"
            SecretCategory.SESSION_ID -> "{{SESSION_ID}}"
            SecretCategory.CLIENT_SECRET -> "{{CLIENT_SECRET}}"
            SecretCategory.PASSWORD -> "{{PASSWORD}}"
            SecretCategory.QUERY_TOKEN -> "{{QUERY_TOKEN}}"
            SecretCategory.COOKIE -> "{{COOKIE}}"
            else -> "{{AUTH_TOKEN}}"
        }

    private fun maskFor(category: SecretCategory): String =
        when (category) {
            SecretCategory.BEARER_TOKEN -> "Bearer ••••••••"
            SecretCategory.BASIC_AUTH -> "Basic ••••••••"
            else -> "••••••••"
        }
}
