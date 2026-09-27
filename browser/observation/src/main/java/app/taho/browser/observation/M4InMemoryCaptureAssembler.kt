package app.taho.browser.observation

import app.taho.browser.capture.domain.Confidence
import app.taho.browser.capture.domain.JsonBodySecretScanner
import app.taho.browser.capture.domain.NormalizedHeader
import app.taho.browser.capture.domain.NormalizedHeaderValue
import app.taho.browser.capture.domain.RawObservedField
import app.taho.browser.capture.domain.RawObservedValue
import app.taho.browser.capture.domain.SecretAssessment
import app.taho.browser.capture.domain.SecretDetector
import app.taho.browser.capture.domain.SecretFinding
import app.taho.browser.capture.domain.SecretLocation
import app.taho.browser.capture.domain.SecretRef
import app.taho.browser.capture.domain.SecretSeverity
import app.taho.browser.contract.BodyEncoding
import app.taho.browser.contract.BodyRepresentation
import app.taho.browser.contract.CaptureCompleteness
import app.taho.browser.contract.Completeness
import app.taho.browser.contract.TransferBody
import app.taho.browser.transfer.core.M4CapturedRequest
import app.taho.browser.transfer.core.M4FieldValue
import app.taho.browser.transfer.core.M4QueryInput
import app.taho.browser.transfer.core.UlidGenerator
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

class M4InMemoryCaptureAssembler(
    private val appVersion: String,
    private val engineVersion: String,
    private val captureSessionId: String,
    private val maxCompleted: Int = 256,
    private val transferIdFactory: () -> String = { UlidGenerator.next() },
) {
    private data class MutableTransaction(
        val key: String,
        var tahoTabId: String?,
        var isPrivate: Boolean,
        var url: String? = null,
        var method: String? = null,
        var resourceType: String? = null,
        var startAt: Double? = null,
        var requestHeaders: List<ProductionObservationMessage.HeaderValue>? = null,
        var requestBody: ByteArray? = null,
        var statusCode: Int? = null,
        var statusText: String? = null,
        var responseHeadersSeen: Boolean = false,
        var completeAt: Double? = null,
        var sequenceGap: Boolean = false,
        var failed: Boolean = false,
    )

    private val active = linkedMapOf<String, MutableTransaction>()
    private val completed = ArrayDeque<M4CapturedRequest>()
    private var limited = false

    fun accept(event: ProductionObservationEvent) {
        when (event) {
            ProductionObservationEvent.GateBlocked,
            is ProductionObservationEvent.ExtensionFailed,
            is ProductionObservationEvent.Rejected -> {
                limited = true
            }

            is ProductionObservationEvent.Bulk -> {
                if (event.sequenceGap) limited = true
                acceptBulk(event)
            }

            is ProductionObservationEvent.ExtensionReady,
            is ProductionObservationEvent.TabBound -> Unit
        }
    }

    fun isLimited(): Boolean = limited

    fun requestsForTab(tahoTabId: String): List<M4CapturedRequest> =
        completed.filter { it.tabId == tahoTabId }

    fun allCompleted(): List<M4CapturedRequest> = completed.toList()

    private fun acceptBulk(event: ProductionObservationEvent.Bulk) {
        val message = event.message
        if (message is ProductionObservationMessage.Hello) return

        val requestId = requestIdOf(message) ?: return
        val connectionId = connectionIdOf(message) ?: return
        val key = connectionId + "|" + requestId

        val tx = active.getOrPut(key) {
            MutableTransaction(
                key = key,
                tahoTabId = event.tahoTabId,
                isPrivate = event.isPrivate == true,
            )
        }

        if (tx.tahoTabId == null && event.tahoTabId != null) {
            tx.tahoTabId = event.tahoTabId
            tx.isPrivate = event.isPrivate == true
        }

        when (message) {
            is ProductionObservationMessage.TxStart -> {
                tx.url = message.url
                tx.method = message.method
                tx.resourceType = message.resourceType
                tx.startAt = message.reportedAt
            }

            is ProductionObservationMessage.TxRequestHeaders ->
                tx.requestHeaders = message.headers

            is ProductionObservationMessage.TxRequestBody ->
                tx.requestBody = message.bytes.copyOf()

            is ProductionObservationMessage.TxResponseStart -> {
                tx.statusCode = message.statusCode
                tx.statusText = message.statusText
                tx.responseHeadersSeen = true
            }

            is ProductionObservationMessage.TxComplete -> {
                tx.completeAt = message.reportedAt
                finalize(key, tx)
            }

            is ProductionObservationMessage.TxError -> {
                tx.failed = true
                active.remove(key)
            }

            is ProductionObservationMessage.Hello,
            is ProductionObservationMessage.TabRegister -> Unit
        }
    }

    private fun finalize(
        key: String,
        tx: MutableTransaction,
    ) {
        active.remove(key)
        val tabId = tx.tahoTabId ?: run {
            limited = true
            return
        }
        val url = tx.url ?: run {
            limited = true
            return
        }
        val method = tx.method ?: run {
            limited = true
            return
        }

        val parsedUrl = parseUrl(url) ?: run {
            limited = true
            return
        }

        val headerResult = classifyHeaders(key, tx.requestHeaders.orEmpty())
        val queryResult = classifyQuery(key, parsedUrl.query)
        val findings = mutableListOf<SecretFinding>()
        findings += headerResult.findings
        findings += queryResult.findings

        val contentType = headerResult.normalized
            .firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.let { value -> (value as? NormalizedHeaderValue.Public)?.value }

        var reviewRequired =
            headerResult.reviewRequired ||
                queryResult.reviewRequired ||
                parsedUrl.hadUserInfo ||
                tx.sequenceGap

        val requestBodyCompleteness: Completeness
        val transferBody: TransferBody?

        if (method.equals("GET", true) || method.equals("HEAD", true)) {
            requestBodyCompleteness = Completeness.NOT_APPLICABLE
            transferBody = null
        } else if (tx.requestBody == null) {
            requestBodyCompleteness = Completeness.UNAVAILABLE
            transferBody = null
        } else {
            requestBodyCompleteness = Completeness.COMPLETE
            val bytes = tx.requestBody!!
            val jsonLike = contentType?.lowercase()?.contains("json") == true
            if (!jsonLike) {
                reviewRequired = true
                transferBody = null
            } else {
                val text = runCatching {
                    bytes.toString(StandardCharsets.UTF_8)
                }.getOrNull()
                if (text == null) {
                    reviewRequired = true
                    transferBody = null
                } else {
                    val bodyScan = JsonBodySecretScanner.scan(key, text)
                    findings += bodyScan.assessment.findings
                    if (bodyScan.requiresReview || bodyScan.assessment.findings.isNotEmpty()) {
                        reviewRequired = true
                    }
                    transferBody = TransferBody(
                        representation = BodyRepresentation.JSON,
                        contentType = contentType,
                        charset = "utf-8",
                        encoding = BodyEncoding.UTF8,
                        size = bytes.size.toLong(),
                        declaredSize = null,
                        truncated = false,
                        completeness = Completeness.COMPLETE,
                        content = text,
                    )
                }
            }
        }

        val duration = if (tx.startAt != null && tx.completeAt != null) {
            max(0.0, tx.completeAt!! - tx.startAt!!).toLong()
        } else {
            null
        }

        val assessment = combineAssessment(findings)

        completed.addFirst(
            M4CapturedRequest(
                transferId = transferIdFactory(),
                issuedAt = System.currentTimeMillis(),
                appVersion = appVersion,
                engineVersion = engineVersion,
                captureSessionId = captureSessionId,
                tabId = tabId,
                transactionId = key,
                method = method,
                url = parsedUrl.baseUrl,
                query = queryResult.query,
                headers = headerResult.normalized,
                body = transferBody,
                status = tx.statusCode,
                statusText = tx.statusText,
                durationMs = duration,
                initiator = tx.resourceType,
                completeness = CaptureCompleteness(
                    requestUrl = Completeness.COMPLETE,
                    requestHeaders = if (tx.requestHeaders != null) {
                        Completeness.COMPLETE
                    } else {
                        Completeness.UNAVAILABLE
                    },
                    requestBody = requestBodyCompleteness,
                    responseHeaders = if (tx.responseHeadersSeen) {
                        Completeness.COMPLETE
                    } else {
                        Completeness.UNAVAILABLE
                    },
                    responseBody = Completeness.UNAVAILABLE,
                    timing = if (duration != null) Completeness.PARTIAL else Completeness.UNAVAILABLE,
                    tlsInfo = Completeness.UNAVAILABLE,
                ),
                secretAssessment = assessment,
                fromPrivateSession = tx.isPrivate,
                reviewRequired = reviewRequired,
                capturedAt = System.currentTimeMillis(),
                redirectCount = 0,
            ),
        )

        while (completed.size > maxCompleted) {
            completed.removeLast()
        }
    }

    private data class HeaderClassification(
        val normalized: List<NormalizedHeader>,
        val findings: List<SecretFinding>,
        val reviewRequired: Boolean,
    )

    private fun classifyHeaders(
        transactionKey: String,
        headers: List<ProductionObservationMessage.HeaderValue>,
    ): HeaderClassification {
        val rawFields = headers.map { header ->
            RawObservedField.Header(
                name = header.name,
                value = RawObservedValue.copyOf(
                    header.value.toByteArray(Charsets.UTF_8),
                ),
            )
        }

        return try {
            val assessment = SecretDetector.assess(transactionKey, rawFields)
            val byLocation = assessment.findings.associateBy { it.location.stableKey }
            val normalized = headers.map { header ->
                val finding = byLocation[SecretLocation.Header(header.name).stableKey]
                NormalizedHeader(
                    name = header.name,
                    value = if (finding == null) {
                        NormalizedHeaderValue.Public(header.value)
                    } else {
                        NormalizedHeaderValue.Protected(
                            SecretRef(finding.id, finding.category),
                        )
                    },
                )
            }
            val review = headers.any { header ->
                val key = header.name.lowercase()
                looksSecuritySensitive(key) &&
                    byLocation[SecretLocation.Header(header.name).stableKey] == null
            }
            HeaderClassification(normalized, assessment.findings, review)
        } finally {
            rawFields.forEach { it.value.close() }
        }
    }

    private data class QueryClassification(
        val query: List<M4QueryInput>,
        val findings: List<SecretFinding>,
        val reviewRequired: Boolean,
    )

    private fun classifyQuery(
        transactionKey: String,
        query: List<Pair<String, String>>,
    ): QueryClassification {
        val rawFields = query.map { (name, value) ->
            RawObservedField.Query(
                key = name,
                value = RawObservedValue.copyOf(value.toByteArray(Charsets.UTF_8)),
            )
        }

        return try {
            val assessment = SecretDetector.assess(transactionKey, rawFields)
            val byLocation = assessment.findings.associateBy { it.location.stableKey }
            val safe = query.map { (name, value) ->
                val finding = byLocation[SecretLocation.Query(name).stableKey]
                M4QueryInput(
                    name = name,
                    value = if (finding == null) {
                        M4FieldValue.Public(value)
                    } else {
                        M4FieldValue.Protected(
                            SecretRef(finding.id, finding.category),
                        )
                    },
                )
            }
            val review = query.any { (name, _) ->
                looksSecuritySensitive(name.lowercase()) &&
                    byLocation[SecretLocation.Query(name).stableKey] == null
            }
            QueryClassification(safe, assessment.findings, review)
        } finally {
            rawFields.forEach { it.value.close() }
        }
    }

    private data class ParsedUrl(
        val baseUrl: String,
        val query: List<Pair<String, String>>,
        val hadUserInfo: Boolean,
    )

    private fun parseUrl(raw: String): ParsedUrl? =
        runCatching {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            if ((scheme != "http" && scheme != "https") || host == null) {
                return@runCatching null
            }
            val base = buildString {
                append(scheme)
                append("://")
                append(host)
                if (uri.port >= 0) append(":").append(uri.port)
                append(uri.rawPath ?: "")
            }
            val query = uri.rawQuery
                ?.split("&")
                ?.filter { it.isNotEmpty() }
                ?.map { pair ->
                    decode(pair.substringBefore("=")) to
                        decode(pair.substringAfter("=", ""))
                }
                ?: emptyList()
            ParsedUrl(base, query, uri.rawUserInfo != null)
        }.getOrNull()

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    private fun combineAssessment(findings: List<SecretFinding>): SecretAssessment {
        if (findings.isEmpty()) return SecretAssessment.NONE
        val unique = findings.distinctBy { it.id }
        return SecretAssessment(
            findings = unique,
            highestSeverity = unique.maxBy { it.category.severityRank() }.category.severity(),
        )
    }

    private fun app.taho.browser.capture.domain.SecretCategory.severityRank(): Int =
        severity().ordinal

    private fun app.taho.browser.capture.domain.SecretCategory.severity(): SecretSeverity =
        when (this) {
            app.taho.browser.capture.domain.SecretCategory.PASSWORD,
            app.taho.browser.capture.domain.SecretCategory.CLIENT_SECRET ->
                SecretSeverity.CRITICAL

            app.taho.browser.capture.domain.SecretCategory.AUTHORIZATION,
            app.taho.browser.capture.domain.SecretCategory.BEARER_TOKEN,
            app.taho.browser.capture.domain.SecretCategory.COOKIE,
            app.taho.browser.capture.domain.SecretCategory.API_KEY,
            app.taho.browser.capture.domain.SecretCategory.JWT,
            app.taho.browser.capture.domain.SecretCategory.BASIC_AUTH,
            app.taho.browser.capture.domain.SecretCategory.SESSION_ID ->
                SecretSeverity.HIGH

            app.taho.browser.capture.domain.SecretCategory.CSRF_TOKEN,
            app.taho.browser.capture.domain.SecretCategory.QUERY_TOKEN ->
                SecretSeverity.MEDIUM
        }

    private fun looksSecuritySensitive(name: String): Boolean =
        listOf(
            "auth",
            "token",
            "key",
            "secret",
            "password",
            "session",
            "cookie",
        ).any(name::contains)

    private fun requestIdOf(message: ProductionObservationMessage): String? =
        when (message) {
            is ProductionObservationMessage.TxStart -> message.requestId
            is ProductionObservationMessage.TxRequestHeaders -> message.requestId
            is ProductionObservationMessage.TxRequestBody -> message.requestId
            is ProductionObservationMessage.TxResponseStart -> message.requestId
            is ProductionObservationMessage.TxComplete -> message.requestId
            is ProductionObservationMessage.TxError -> message.requestId
            is ProductionObservationMessage.Hello,
            is ProductionObservationMessage.TabRegister -> null
        }

    private fun connectionIdOf(message: ProductionObservationMessage): String? =
        when (message) {
            is ProductionObservationMessage.Hello -> message.connectionId
            is ProductionObservationMessage.TxStart -> message.connectionId
            is ProductionObservationMessage.TxRequestHeaders -> message.connectionId
            is ProductionObservationMessage.TxRequestBody -> message.connectionId
            is ProductionObservationMessage.TxResponseStart -> message.connectionId
            is ProductionObservationMessage.TxComplete -> message.connectionId
            is ProductionObservationMessage.TxError -> message.connectionId
            is ProductionObservationMessage.TabRegister -> null
        }
}
