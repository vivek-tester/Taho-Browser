package app.taho.browser.observation

import app.taho.browser.capture.domain.IngressMessageType
import org.json.JSONObject
import java.util.Base64

enum class ObservationLane { IDENTITY, BULK }

sealed interface ProductionObservationMessage {
    val type: String

    data class Hello(
        val connectionId: String,
        val sequence: Long,
        val protocolVersion: Int,
    ) : ProductionObservationMessage {
        override val type: String = "HELLO"
    }

    data class TabRegister(
        val extTabId: Int,
        val url: String,
        val readyState: String?,
    ) : ProductionObservationMessage {
        override val type: String = "TAB_REGISTER"

        override fun toString(): String =
            "TabRegister(extTabId=$extTabId,url=<redacted>,readyState=$readyState)"
    }

    data class TxStart(
        val connectionId: String,
        val sequence: Long,
        val eventId: String,
        val requestId: String,
        val extTabId: Int,
        val frameId: Int,
        val documentId: String?,
        val url: String,
        val method: String,
        val resourceType: String?,
        val reportedAt: Double?,
    ) : ProductionObservationMessage {
        override val type: String = "TX_START"

        override fun toString(): String =
            "TxStart(conn=$connectionId,seq=$sequence,eventId=$eventId," +
                "requestId=$requestId,extTabId=$extTabId,frameId=$frameId," +
                "documentId=$documentId,url=<redacted>,method=$method,type=$resourceType)"
    }

    data class HeaderValue(
        val name: String,
        val value: String,
    ) {
        override fun toString(): String =
            "HeaderValue(name=$name,value=<redacted>,bytes=" +
                value.toByteArray(Charsets.UTF_8).size + ")"
    }

    data class TxRequestHeaders(
        val connectionId: String,
        val sequence: Long,
        val eventId: String,
        val requestId: String,
        val extTabId: Int,
        val headers: List<HeaderValue>,
    ) : ProductionObservationMessage {
        override val type: String = "TX_REQ_HEADERS"

        override fun toString(): String =
            "TxRequestHeaders(conn=$connectionId,seq=$sequence,eventId=$eventId," +
                "requestId=$requestId,extTabId=$extTabId,headers=" +
                headers.joinToString(prefix="[", postfix="]") + ")"
    }

    data class TxRequestBody(
        val connectionId: String,
        val sequence: Long,
        val eventId: String,
        val requestId: String,
        val extTabId: Int,
        val bytes: ByteArray = ByteArray(0),
        val formData: Map<String, List<String>> = emptyMap(),
    ) : ProductionObservationMessage {
        override val type: String = "TX_REQ_BODY"

        override fun toString(): String =
            "TxRequestBody(conn=$connectionId,seq=$sequence,eventId=$eventId," +
                "requestId=$requestId,extTabId=$extTabId,bytes=" + bytes.size +
                ",formFields=" + formData.size + ")"
    }

    data class TxRedirect(
        val connectionId: String,
        val sequence: Long,
        val eventId: String,
        val requestId: String,
        val extTabId: Int,
        val statusCode: Int,
        val redirectUrl: String,
        val reportedAt: Double?,
    ) : ProductionObservationMessage {
        override val type: String = "TX_REDIRECT"

        override fun toString(): String =
            "TxRedirect(conn=$connectionId,seq=$sequence,eventId=$eventId," +
                "requestId=$requestId,extTabId=$extTabId,statusCode=$statusCode," +
                "redirectUrl=<redacted>)"
    }

    data class TxResponseStart(
        val connectionId: String,
        val sequence: Long,
        val eventId: String,
        val requestId: String,
        val extTabId: Int,
        val statusCode: Int,
        val statusText: String?,
        val headers: List<HeaderValue>,
        val reportedAt: Double?,
    ) : ProductionObservationMessage {
        override val type: String = "TX_RESP_START"

        override fun toString(): String =
            "TxResponseStart(conn=$connectionId,seq=$sequence,eventId=$eventId," +
                "requestId=$requestId,extTabId=$extTabId,statusCode=$statusCode," +
                "headers=" + headers.joinToString(prefix="[", postfix="]") + ")"
    }

    data class TxComplete(
        val connectionId: String,
        val sequence: Long,
        val eventId: String,
        val requestId: String,
        val extTabId: Int,
        val reportedAt: Double?,
    ) : ProductionObservationMessage {
        override val type: String = "TX_COMPLETE"
    }

    data class TxError(
        val connectionId: String,
        val sequence: Long,
        val eventId: String,
        val requestId: String,
        val extTabId: Int,
        val error: String?,
    ) : ProductionObservationMessage {
        override val type: String = "TX_ERROR"

        override fun toString(): String =
            "TxError(conn=$connectionId,seq=$sequence,eventId=$eventId," +
                "requestId=$requestId,extTabId=$extTabId,error=<redacted>)"
    }
}

enum class ObservationRejectReason {
    UNKNOWN_TYPE,
    WRONG_LANE,
    OVERSIZE,
    MALFORMED,
    INVALID_FIELD,
}

sealed interface ObservationParseResult {
    data class Accepted(val message: ProductionObservationMessage) : ObservationParseResult
    data class Rejected(
        val reason: ObservationRejectReason,
        val typeHint: String? = null,
    ) : ObservationParseResult
}

object ProductionObservationProtocol {
    private const val HELLO_CAP_BYTES = 4 * 1024
    private const val TYPE_SCAN_CHARS = 1024
    private const val MAX_URL_CHARS = 16 * 1024
    private const val MAX_HEADER_COUNT = 200
    private const val MAX_HEADER_NAME_CHARS = 256
    private const val MAX_HEADER_VALUE_CHARS = 16 * 1024
    private const val MAX_ID_CHARS = 200
    private const val MAX_ERROR_CHARS = 512

    private val typeRegex = Regex("\"type\"\\s*:\\s*\"([A-Z_]+)\"")
    private val methodRegex = Regex("^[A-Z][A-Z0-9!#$%&'*+.^_|~-]*$")

    fun parse(raw: String, lane: ObservationLane): ObservationParseResult {
        val type = typeRegex
            .find(raw.take(TYPE_SCAN_CHARS))
            ?.groupValues
            ?.getOrNull(1)
            ?: return ObservationParseResult.Rejected(ObservationRejectReason.UNKNOWN_TYPE)

        val expectedLane = laneFor(type)
            ?: return ObservationParseResult.Rejected(
                ObservationRejectReason.UNKNOWN_TYPE,
                type,
            )
        if (expectedLane != lane) {
            return ObservationParseResult.Rejected(
                ObservationRejectReason.WRONG_LANE,
                type,
            )
        }

        val bytes = raw.toByteArray(Charsets.UTF_8).size
        if (bytes > capBytes(type)) {
            return ObservationParseResult.Rejected(
                ObservationRejectReason.OVERSIZE,
                type,
            )
        }

        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return ObservationParseResult.Rejected(
                ObservationRejectReason.MALFORMED,
                type,
            )

        return runCatching {
            val message = when (type) {
                "HELLO" -> ProductionObservationMessage.Hello(
                    connectionId = requiredId(json, "conn"),
                    sequence = positiveSequence(json),
                    protocolVersion = json.getInt("protocol"),
                )

                "TAB_REGISTER" -> {
                    val extTabId = json.getInt("extTabId")
                    require(extTabId >= 0)
                    ProductionObservationMessage.TabRegister(
                        extTabId = extTabId,
                        url = requiredHttpUrl(json.getString("url")),
                        readyState = json.optNullableString("readyState")?.take(64),
                    )
                }

                "TX_START" -> ProductionObservationMessage.TxStart(
                    connectionId = requiredId(json, "conn"),
                    sequence = positiveSequence(json),
                    eventId = requiredId(json, "id"),
                    requestId = requiredId(json, "reqId"),
                    extTabId = extTabId(json),
                    frameId = json.optInt("frameId", -1),
                    documentId = json.optNullableString("docId")?.take(MAX_ID_CHARS),
                    url = requiredHttpUrl(json.getString("url")),
                    method = json.getString("method").also { require(methodRegex.matches(it)) },
                    resourceType = json.optNullableString("resourceType")?.take(64),
                    reportedAt = json.optNullableDouble("ts"),
                )

                "TX_REQ_HEADERS" -> ProductionObservationMessage.TxRequestHeaders(
                    connectionId = requiredId(json, "conn"),
                    sequence = positiveSequence(json),
                    eventId = requiredId(json, "id"),
                    requestId = requiredId(json, "reqId"),
                    extTabId = extTabId(json),
                    headers = parseHeaders(json),
                )

                "TX_REQ_BODY" -> {
                    val bodyKind = json.optString("bodyKind", "RAW")
                    val decoded = if (bodyKind == "RAW") {
                        Base64.getDecoder().decode(json.getString("base64")).also {
                            require(it.size <= IngressMessageType.TX_REQ_BODY.payloadCapBytes)
                        }
                    } else {
                        ByteArray(0)
                    }
                    val formData = if (bodyKind == "FORM") {
                        parseFormData(json)
                    } else {
                        emptyMap()
                    }
                    require(decoded.isNotEmpty() || formData.isNotEmpty())
                    ProductionObservationMessage.TxRequestBody(
                        connectionId = requiredId(json, "conn"),
                        sequence = positiveSequence(json),
                        eventId = requiredId(json, "id"),
                        requestId = requiredId(json, "reqId"),
                        extTabId = extTabId(json),
                        bytes = decoded,
                        formData = formData,
                    )
                }

                "TX_REDIRECT" -> {
                    val statusCode = json.getInt("statusCode")
                    require(statusCode in 300..399)
                    ProductionObservationMessage.TxRedirect(
                        connectionId = requiredId(json, "conn"),
                        sequence = positiveSequence(json),
                        eventId = requiredId(json, "id"),
                        requestId = requiredId(json, "reqId"),
                        extTabId = extTabId(json),
                        statusCode = statusCode,
                        redirectUrl = requiredHttpUrl(json.getString("redirectUrl")),
                        reportedAt = json.optNullableDouble("ts"),
                    )
                }

                "TX_RESP_START" -> {
                    val statusCode = json.getInt("statusCode")
                    require(statusCode in 100..999)
                    ProductionObservationMessage.TxResponseStart(
                        connectionId = requiredId(json, "conn"),
                        sequence = positiveSequence(json),
                        eventId = requiredId(json, "id"),
                        requestId = requiredId(json, "reqId"),
                        extTabId = extTabId(json),
                        statusCode = statusCode,
                        statusText = json.optNullableString("statusText")?.take(256),
                        headers = parseHeaders(json),
                        reportedAt = json.optNullableDouble("ts"),
                    )
                }

                "TX_COMPLETE" -> ProductionObservationMessage.TxComplete(
                    connectionId = requiredId(json, "conn"),
                    sequence = positiveSequence(json),
                    eventId = requiredId(json, "id"),
                    requestId = requiredId(json, "reqId"),
                    extTabId = extTabId(json),
                    reportedAt = json.optNullableDouble("ts"),
                )

                "TX_ERROR" -> ProductionObservationMessage.TxError(
                    connectionId = requiredId(json, "conn"),
                    sequence = positiveSequence(json),
                    eventId = requiredId(json, "id"),
                    requestId = requiredId(json, "reqId"),
                    extTabId = extTabId(json),
                    error = json.optNullableString("error")?.take(MAX_ERROR_CHARS),
                )

                else -> error("unreachable")
            }
            ObservationParseResult.Accepted(message)
        }.getOrElse {
            ObservationParseResult.Rejected(
                ObservationRejectReason.INVALID_FIELD,
                type,
            )
        }
    }

    private fun laneFor(type: String): ObservationLane? =
        when (type) {
            "TAB_REGISTER" -> ObservationLane.IDENTITY
            "HELLO",
            "TX_START",
            "TX_REQ_HEADERS",
            "TX_REQ_BODY",
            "TX_REDIRECT",
            "TX_RESP_START",
            "TX_COMPLETE",
            "TX_ERROR" -> ObservationLane.BULK
            else -> null
        }

    private fun capBytes(type: String): Int =
        when (type) {
            "HELLO" -> HELLO_CAP_BYTES
            "TAB_REGISTER" -> IngressMessageType.TAB_REGISTER.payloadCapBytes
            "TX_START" -> IngressMessageType.TX_START.payloadCapBytes
            "TX_REQ_HEADERS" -> IngressMessageType.TX_REQ_HEADERS.payloadCapBytes
            "TX_REQ_BODY" -> IngressMessageType.TX_REQ_BODY.payloadCapBytes
            "TX_REDIRECT" -> IngressMessageType.TX_REDIRECT.payloadCapBytes
            "TX_RESP_START" -> IngressMessageType.TX_RESP_START.payloadCapBytes
            "TX_COMPLETE" -> IngressMessageType.TX_COMPLETE.payloadCapBytes
            "TX_ERROR" -> IngressMessageType.TX_ERROR.payloadCapBytes
            else -> 0
        }

    private fun requiredId(json: JSONObject, name: String): String =
        json.getString(name).also {
            require(it.isNotBlank() && it.length <= MAX_ID_CHARS)
        }

    private fun positiveSequence(json: JSONObject): Long =
        json.getLong("seq").also { require(it > 0) }

    private fun extTabId(json: JSONObject): Int =
        json.getInt("tabId").also { require(it >= -1) }

    private fun requiredHttpUrl(raw: String): String {
        require(raw.length <= MAX_URL_CHARS)
        require(raw.startsWith("https://") || raw.startsWith("http://"))
        return raw
    }

    private fun parseHeaders(json: JSONObject): List<ProductionObservationMessage.HeaderValue> {
        val array = json.optJSONArray("headers") ?: return emptyList()
        require(array.length() <= MAX_HEADER_COUNT)
        return buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val name = item.getString("name")
                val value = item.optString("value", "")
                require(name.isNotBlank() && name.length <= MAX_HEADER_NAME_CHARS)
                require(value.length <= MAX_HEADER_VALUE_CHARS)
                require(!containsControl(name) && !containsControl(value))
                add(ProductionObservationMessage.HeaderValue(name, value))
            }
        }
    }

    private fun parseFormData(json: JSONObject): Map<String, List<String>> {
        val array = json.optJSONArray("form") ?: return emptyMap()
        require(array.length() <= 200)
        val result = linkedMapOf<String, List<String>>()
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            val name = item.getString("name")
            require(name.isNotBlank() && name.length <= MAX_HEADER_NAME_CHARS)
            val values = item.optJSONArray("values")
            val parsed = buildList {
                if (values != null) {
                    require(values.length() <= 32)
                    repeat(values.length()) { valueIndex ->
                        val value = values.getString(valueIndex)
                        require(value.length <= MAX_HEADER_VALUE_CHARS)
                        require(!containsControl(value))
                        add(value)
                    }
                }
            }
            result[name] = parsed
        }
        return result
    }

    private fun containsControl(value: String): Boolean =
        value.any { it == '\r' || it == '\n' || it.code < 0x20 || it.code == 0x7f }

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (has(name) && !isNull(name)) getDouble(name) else null
}
