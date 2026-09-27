package app.taho.browser.observation

import app.taho.browser.capture.domain.BodySupportLimitation
import app.taho.browser.capture.domain.CaptureBudgets
import app.taho.browser.capture.domain.Completeness as DomainCompleteness
import app.taho.browser.capture.domain.DurableBody
import app.taho.browser.capture.domain.DurableBodyEncoding
import app.taho.browser.capture.domain.DurableBodyRepresentation
import app.taho.browser.capture.domain.DurableHeaderValue
import app.taho.browser.capture.domain.DurableQueryValue
import app.taho.browser.capture.domain.DurableRedirectHop
import app.taho.browser.capture.domain.DurableTransaction
import app.taho.browser.capture.domain.DurableTransactionState
import app.taho.browser.capture.domain.NormalizedHeader
import app.taho.browser.capture.domain.NormalizedHeaderValue
import app.taho.browser.capture.domain.ObservationSource as DomainObservationSource
import app.taho.browser.capture.domain.RawObservedField
import app.taho.browser.capture.domain.RawObservedValue
import app.taho.browser.capture.domain.RelevanceClassifier
import app.taho.browser.capture.domain.RelevanceInput
import app.taho.browser.capture.domain.RequestBodySupport
import app.taho.browser.capture.domain.SecretAssessment
import app.taho.browser.capture.domain.SecretDetector
import app.taho.browser.capture.domain.SecretFinding
import app.taho.browser.capture.domain.SecretLocation
import app.taho.browser.capture.domain.SecretRef
import app.taho.browser.capture.domain.SecretSeverity
import app.taho.browser.capture.domain.SensitivePayload
import app.taho.browser.contract.BodyEncoding
import app.taho.browser.contract.BodyRepresentation
import app.taho.browser.contract.CaptureCompleteness
import app.taho.browser.contract.Completeness as ContractCompleteness
import app.taho.browser.contract.TransferBody
import app.taho.browser.transfer.core.M4CapturedRequest
import app.taho.browser.transfer.core.M4FieldValue
import app.taho.browser.transfer.core.M4QueryInput
import app.taho.browser.transfer.core.UlidGenerator
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

class M4InMemoryCaptureAssembler(
    private val appVersion: String,
    private val engineVersion: String,
    private val captureSessionId: String,
    private val maxCompleted: Int = 256,
    private val transferIdFactory: () -> String = { UlidGenerator.next() },
    private val onDurableRecord: (DurableTransaction) -> Unit = { it.close() },
) {
    private data class RedirectEvidence(
        val statusCode: Int,
        val fromUrl: String,
        val toUrl: String,
        val reportedAt: Double?,
    )

    private data class MutableTransaction(
        val key: String,
        val engineRequestId: String,
        var tahoTabId: String?,
        var isPrivate: Boolean,
        var extTabId: Int? = null,
        var targetHost: String? = null,
        var url: String? = null,
        var method: String? = null,
        var resourceType: String? = null,
        var startAt: Double? = null,
        var requestHeaders: List<ProductionObservationMessage.HeaderValue>? = null,
        var requestBody: ByteArray? = null,
        var requestBodyBuffer: ByteArrayOutputStream? = null,
        var requestBodyExpectedChunk: Int = 0,
        var requestBodyChunkCount: Int? = null,
        var requestBodyTruncated: Boolean = false,
        var requestBodyInvalid: Boolean = false,
        var requestFormData: Map<String, List<String>>? = null,
        var statusCode: Int? = null,
        var statusText: String? = null,
        var responseHeaders: List<ProductionObservationMessage.HeaderValue>? = null,
        var completeAt: Double? = null,
        var sequenceGap: Boolean = false,
        val redirects: MutableList<RedirectEvidence> = mutableListOf(),
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
                engineRequestId = requestId,
                tahoTabId = event.tahoTabId,
                isPrivate = event.isPrivate == true,
                targetHost = event.targetHost,
            )
        }

        if (tx.tahoTabId == null && event.tahoTabId != null) {
            tx.tahoTabId = event.tahoTabId
            tx.isPrivate = event.isPrivate == true
        }
        if (tx.targetHost == null && event.targetHost != null) {
            tx.targetHost = event.targetHost
        }
        tx.sequenceGap = tx.sequenceGap || event.sequenceGap

        when (message) {
            is ProductionObservationMessage.TxStart -> {
                tx.extTabId = message.extTabId
                tx.url = message.url
                tx.method = message.method
                tx.resourceType = message.resourceType
                tx.startAt = message.reportedAt
            }

            is ProductionObservationMessage.TxRequestHeaders -> {
                tx.extTabId = message.extTabId
                tx.requestHeaders = message.headers
            }

            is ProductionObservationMessage.TxRequestBody -> {
                tx.extTabId = message.extTabId
                if (message.formData.isNotEmpty()) {
                    tx.requestBody = null
                    tx.requestBodyBuffer = null
                    tx.requestBodyExpectedChunk = 0
                    tx.requestBodyChunkCount = null
                    tx.requestBodyTruncated = false
                    tx.requestBodyInvalid = false
                    tx.requestFormData = message.formData
                        .mapValues { (_, values) -> values.toList() }
                } else if (message.bytes.isNotEmpty()) {
                    acceptBodyChunk(tx, message)
                }
            }

            is ProductionObservationMessage.TxRedirect -> {
                tx.extTabId = message.extTabId
                val from = tx.url
                if (from != null) {
                    tx.redirects += RedirectEvidence(
                        statusCode = message.statusCode,
                        fromUrl = from,
                        toUrl = message.redirectUrl,
                        reportedAt = message.reportedAt,
                    )
                }
                tx.url = message.redirectUrl
            }

            is ProductionObservationMessage.TxResponseStart -> {
                tx.extTabId = message.extTabId
                tx.statusCode = message.statusCode
                tx.statusText = message.statusText
                tx.responseHeaders = message.headers
            }

            is ProductionObservationMessage.TxComplete -> {
                tx.extTabId = message.extTabId
                tx.completeAt = message.reportedAt
                finalize(key, tx, DurableTransactionState.COMPLETED)
            }

            is ProductionObservationMessage.TxError -> {
                tx.extTabId = message.extTabId
                finalize(key, tx, DurableTransactionState.FAILED)
            }

            is ProductionObservationMessage.Hello,
            is ProductionObservationMessage.TabRegister -> Unit
        }
    }

    private fun acceptBodyChunk(
        tx: MutableTransaction,
        message: ProductionObservationMessage.TxRequestBody,
    ) {
        val expectedCount = tx.requestBodyChunkCount
        if (
            message.chunkIndex != tx.requestBodyExpectedChunk ||
            expectedCount != null && expectedCount != message.chunkCount
        ) {
            tx.requestBodyInvalid = true
            tx.requestBodyBuffer = null
            limited = true
            return
        }

        if (tx.requestBodyChunkCount == null) {
            tx.requestBodyChunkCount = message.chunkCount
        }

        val buffer = tx.requestBodyBuffer ?: ByteArrayOutputStream().also {
            tx.requestBodyBuffer = it
        }
        val maxBytes = CaptureBudgets().maxBodyBytes
        if (message.bytes.size.toLong() > maxBytes - buffer.size().toLong()) {
            tx.requestBodyTruncated = true
            tx.requestBodyInvalid = true
            tx.requestBodyBuffer = null
            limited = true
            return
        }

        buffer.write(message.bytes)
        tx.requestBodyExpectedChunk += 1
        tx.requestBodyTruncated = tx.requestBodyTruncated || message.truncated

        if (message.isFinal) {
            if (
                tx.requestBodyExpectedChunk != message.chunkCount ||
                message.observedTotalBytes != null &&
                !message.truncated &&
                message.observedTotalBytes != buffer.size().toLong()
            ) {
                tx.requestBodyInvalid = true
                tx.requestBodyBuffer = null
                limited = true
                return
            }

            tx.requestBody = buffer.toByteArray()
            tx.requestBodyBuffer = null
        }
    }

    private fun finalize(
        key: String,
        tx: MutableTransaction,
        terminalState: DurableTransactionState,
    ) {
        active.remove(key)

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
        val responseHeaderResult = classifyHeaders(
            transactionKey = key + "|response",
            headers = tx.responseHeaders.orEmpty(),
        )
        val queryResult = classifyQuery(key, parsedUrl.query)

        val findings = mutableListOf<SecretFinding>()
        findings += headerResult.findings
        findings += queryResult.findings

        val contentType = tx.requestHeaders
            .orEmpty()
            .firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }
            ?.value

        var reviewRequired =
            headerResult.reviewRequired ||
                queryResult.reviewRequired ||
                parsedUrl.hadUserInfo ||
                tx.sequenceGap

        val bodyless = method.equals("GET", true) || method.equals("HEAD", true)
        val bodySupport = if (bodyless) {
            null
        } else {
            RequestBodySupport.classify(
                transactionKey = key,
                contentType = contentType,
                bytes = tx.requestBody,
                formData = tx.requestFormData,
            )
        }

        bodySupport?.let { support ->
            findings += support.secretAssessment.findings
            if (
                support.limitation != BodySupportLimitation.NONE ||
                support.representation != DurableBodyRepresentation.JSON
            ) {
                reviewRequired = true
            }
        }

        val requestBodyCompleteness = when {
            bodyless -> ContractCompleteness.NOT_APPLICABLE
            bodySupport == null -> ContractCompleteness.UNAVAILABLE
            else -> ContractCompleteness.valueOf(bodySupport.completeness.name)
        }

        val transferBody = bodySupport
            ?.takeIf {
                it.representation == DurableBodyRepresentation.JSON &&
                    it.transferSafeText != null &&
                    it.limitation == BodySupportLimitation.NONE
            }
            ?.let { support ->
                val text = requireNotNull(support.transferSafeText)
                TransferBody(
                    representation = BodyRepresentation.JSON,
                    contentType = support.contentType,
                    charset = support.charset ?: "utf-8",
                    encoding = BodyEncoding.UTF8,
                    size = text.toByteArray(Charsets.UTF_8).size.toLong(),
                    declaredSize = null,
                    truncated = false,
                    completeness = ContractCompleteness.COMPLETE,
                    content = text,
                )
            }

        val duration = if (tx.startAt != null && tx.completeAt != null) {
            max(0.0, tx.completeAt!! - tx.startAt!!).toLong()
        } else {
            null
        }
        val assessment = combineAssessment(findings)
        val capturedAt = System.currentTimeMillis()
        val durable = durableTransaction(
            tx = tx,
            parsedUrl = parsedUrl,
            method = method,
            headerResult = headerResult,
            responseHeaderResult = responseHeaderResult,
            queryResult = queryResult,
            bodySupport = bodySupport,
            state = terminalState,
            capturedAt = capturedAt,
            contentType = contentType,
        )
        onDurableRecord(durable)

        val tabId = tx.tahoTabId
        if (tabId == null) {
            limited = true
            return
        }

        completed.addFirst(
            M4CapturedRequest(
                transferId = transferIdFactory(),
                issuedAt = capturedAt,
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
                    requestUrl = ContractCompleteness.COMPLETE,
                    requestHeaders = if (tx.requestHeaders != null) {
                        ContractCompleteness.COMPLETE
                    } else {
                        ContractCompleteness.UNAVAILABLE
                    },
                    requestBody = requestBodyCompleteness,
                    responseHeaders = if (tx.responseHeaders != null) {
                        ContractCompleteness.COMPLETE
                    } else {
                        ContractCompleteness.UNAVAILABLE
                    },
                    responseBody = ContractCompleteness.UNAVAILABLE,
                    timing = if (duration != null) {
                        ContractCompleteness.PARTIAL
                    } else {
                        ContractCompleteness.UNAVAILABLE
                    },
                    tlsInfo = ContractCompleteness.UNAVAILABLE,
                ),
                secretAssessment = assessment,
                fromPrivateSession = tx.isPrivate,
                reviewRequired = reviewRequired || terminalState != DurableTransactionState.COMPLETED,
                capturedAt = capturedAt,
                redirectCount = tx.redirects.size,
                bodyRepresentation = bodySupport?.representation?.let(::contractRepresentation),
                bodyLimitation = bodyLimitation(bodySupport),
            ),
        )

        while (completed.size > maxCompleted) {
            completed.removeLast()
        }
    }

    private fun durableTransaction(
        tx: MutableTransaction,
        parsedUrl: ParsedUrl,
        method: String,
        headerResult: HeaderClassification,
        responseHeaderResult: HeaderClassification,
        queryResult: QueryClassification,
        bodySupport: app.taho.browser.capture.domain.CapturedBodySupport?,
        state: DurableTransactionState,
        capturedAt: Long,
        contentType: String?,
    ): DurableTransaction {
        val requestPayload = when {
            tx.isPrivate -> null
            tx.requestBody != null -> SensitivePayload.copyOf(tx.requestBody!!)
            tx.requestFormData != null ->
                SensitivePayload.utf8(encodeFormData(tx.requestFormData!!))
            else -> null
        }

        val durableBody = if (method.equals("GET", true) || method.equals("HEAD", true)) {
            null
        } else {
            val support = bodySupport ?: RequestBodySupport.classify(
                transactionKey = tx.key,
                contentType = contentType,
                bytes = null,
            )
            DurableBody(
                representation = support.representation,
                encoding = support.encoding,
                contentType = support.contentType,
                charset = support.charset,
                declaredSize = null,
                capturedSize = support.capturedSize,
                truncated = support.completeness == DomainCompleteness.TRUNCATED,
                completeness = if (tx.isPrivate && support.completeness == DomainCompleteness.COMPLETE) {
                    DomainCompleteness.COMPLETE
                } else {
                    support.completeness
                },
                payload = requestPayload,
                limitation = bodyLimitation(support),
            )
        }

        val relevance = RelevanceClassifier.classify(
            RelevanceInput(
                url = parsedUrl.baseUrl,
                resourceType = tx.resourceType,
                method = method,
                targetHost = tx.targetHost,
                contentType = contentType,
            ),
        )

        val redirects = tx.redirects.mapNotNull { hop ->
            val from = parseUrl(hop.fromUrl)?.baseUrl ?: return@mapNotNull null
            val to = parseUrl(hop.toUrl)?.baseUrl ?: return@mapNotNull null
            DurableRedirectHop(
                statusCode = hop.statusCode,
                fromUrl = from,
                toUrl = to,
                atEpochMs = hop.reportedAt?.toLong(),
            )
        }

        return DurableTransaction(
            id = tx.key,
            captureSessionId = captureSessionId,
            tahoTabId = tx.tahoTabId,
            attribution = if (tx.tahoTabId == null) "UNRESOLVED" else "KNOWN",
            extTabId = tx.extTabId,
            engineRequestId = tx.engineRequestId,
            method = method,
            url = parsedUrl.baseUrl,
            query = durableQuery(queryResult.query),
            requestHeaders = durableHeaders(headerResult.normalized),
            encryptedRequestHeadersSource = if (tx.isPrivate) {
                null
            } else {
                sensitiveHeaders(tx.requestHeaders.orEmpty())
            },
            requestBody = durableBody,
            status = tx.statusCode,
            statusText = tx.statusText,
            responseHeaders = durableHeaders(responseHeaderResult.normalized),
            encryptedResponseHeadersSource = if (tx.isPrivate) {
                null
            } else {
                sensitiveHeaders(tx.responseHeaders.orEmpty())
            },
            responseBody = null,
            redirects = redirects,
            state = state,
            relevance = relevance,
            observationSource = DomainObservationSource.ENGINE,
            provenanceJson = JSONObject().apply {
                put("captureSessionId", captureSessionId)
                put("transactionId", tx.key)
                put("capturedAt", capturedAt)
                put("engineVersion", engineVersion)
                put("redirectCount", redirects.size)
                put("normalizerVersion", app.taho.browser.capture.domain.RequestNormalizer.VERSION)
            }.toString(),
            normalizerVersion = app.taho.browser.capture.domain.RequestNormalizer.VERSION,
            createdAtEpochMs = tx.startAt?.toLong() ?: capturedAt,
            updatedAtEpochMs = capturedAt,
            isPrivate = tx.isPrivate,
        )
    }

    private fun sensitiveHeaders(
        headers: List<ProductionObservationMessage.HeaderValue>,
    ): SensitivePayload? {
        if (headers.isEmpty()) return null
        val raw = JSONArray().apply {
            headers.forEach { header ->
                put(
                    JSONObject().apply {
                        put("name", header.name)
                        put("value", header.value)
                    },
                )
            }
        }.toString()
        return SensitivePayload.utf8(raw)
    }

    private fun encodeFormData(formData: Map<String, List<String>>): String =
        formData.entries.flatMap { (name, values) ->
            if (values.isEmpty()) listOf(name to "") else values.map { name to it }
        }.joinToString("&") { (name, value) ->
            URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" +
                URLEncoder.encode(value, StandardCharsets.UTF_8)
        }

    private fun durableHeaders(
        headers: List<NormalizedHeader>,
    ): List<DurableHeaderValue> =
        headers.map { header ->
            when (val value = header.value) {
                is NormalizedHeaderValue.Public ->
                    DurableHeaderValue(header.name, value.value, redacted = false)
                is NormalizedHeaderValue.Protected ->
                    DurableHeaderValue(
                        header.name,
                        maskFor(value.ref.category),
                        redacted = true,
                    )
            }
        }

    private fun durableQuery(
        query: List<M4QueryInput>,
    ): List<DurableQueryValue> =
        query.map { item ->
            when (val value = item.value) {
                is M4FieldValue.Public ->
                    DurableQueryValue(item.name, value.value, redacted = false)
                is M4FieldValue.Protected ->
                    DurableQueryValue(
                        item.name,
                        maskFor(value.ref.category),
                        redacted = true,
                    )
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
                val name = header.name.lowercase()
                looksSecuritySensitive(name) &&
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

    private fun contractRepresentation(
        representation: DurableBodyRepresentation,
    ): BodyRepresentation =
        BodyRepresentation.valueOf(representation.name)

    private fun bodyLimitation(
        support: app.taho.browser.capture.domain.CapturedBodySupport?,
    ): String? {
        if (support == null) return null
        return when {
            support.limitation == BodySupportLimitation.NONE &&
                support.representation == DurableBodyRepresentation.JSON -> null
            support.limitation == BodySupportLimitation.NONE ->
                support.representation.name.lowercase().replaceFirstChar(Char::uppercase) +
                    " captured; direct transfer for this body type is not enabled in M5."
            support.limitation == BodySupportLimitation.BODY_NOT_OBSERVED ->
                "Request body was not exposed by GeckoView for this request."
            support.limitation == BodySupportLimitation.MULTIPART_PARTS_UNAVAILABLE ->
                "Multipart bytes were observed, but structured parts are unavailable on this capture path."
            support.limitation == BodySupportLimitation.OPAQUE_BINARY ->
                "Binary body captured as opaque encrypted data; inline transfer is unavailable."
            support.limitation == BodySupportLimitation.SECRET_REVIEW_REQUIRED ->
                "Body contains credential-shaped fields and is blocked from direct transfer."
            support.limitation == BodySupportLimitation.MALFORMED_FORM ->
                "Form body could not be parsed completely."
            else ->
                "Body text could not be parsed completely."
        }
    }

    private fun maskFor(
        category: app.taho.browser.capture.domain.SecretCategory,
    ): String =
        when (category) {
            app.taho.browser.capture.domain.SecretCategory.BEARER_TOKEN ->
                "Bearer ••••••••"
            app.taho.browser.capture.domain.SecretCategory.BASIC_AUTH ->
                "Basic ••••••••"
            else -> "••••••••"
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
            is ProductionObservationMessage.TxRedirect -> message.requestId
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
            is ProductionObservationMessage.TxRedirect -> message.connectionId
            is ProductionObservationMessage.TxResponseStart -> message.connectionId
            is ProductionObservationMessage.TxComplete -> message.connectionId
            is ProductionObservationMessage.TxError -> message.connectionId
            is ProductionObservationMessage.TabRegister -> null
        }
}
