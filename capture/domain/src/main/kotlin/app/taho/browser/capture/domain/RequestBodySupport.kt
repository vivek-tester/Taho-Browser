package app.taho.browser.capture.domain

import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class BodySupportLimitation {
    NONE,
    BODY_NOT_OBSERVED,
    MALFORMED_TEXT,
    MALFORMED_FORM,
    MULTIPART_PARTS_UNAVAILABLE,
    OPAQUE_BINARY,
    SECRET_REVIEW_REQUIRED,
}

data class CapturedBodySupport(
    val representation: DurableBodyRepresentation,
    val encoding: DurableBodyEncoding,
    val contentType: String?,
    val charset: String?,
    val completeness: Completeness,
    val capturedSize: Long,
    val transferSafeText: String?,
    val limitation: BodySupportLimitation,
    val secretAssessment: SecretAssessment,
)

object RequestBodySupport {
    fun classify(
        transactionKey: String,
        contentType: String?,
        bytes: ByteArray?,
        formData: Map<String, List<String>>? = null,
    ): CapturedBodySupport {
        if (bytes == null && formData == null) {
            return CapturedBodySupport(
                representation = DurableBodyRepresentation.BINARY,
                encoding = DurableBodyEncoding.BINARY,
                contentType = contentType,
                charset = null,
                completeness = Completeness.UNAVAILABLE,
                capturedSize = 0,
                transferSafeText = null,
                limitation = BodySupportLimitation.BODY_NOT_OBSERVED,
                secretAssessment = SecretAssessment.NONE,
            )
        }

        val mime = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val charset = contentType
            ?.split(';')
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.ifBlank { null }

        if (formData != null) {
            return classifyFormMap(transactionKey, contentType, charset, formData)
        }

        val payload = bytes ?: ByteArray(0)
        val text = runCatching { payload.toString(StandardCharsets.UTF_8) }.getOrNull()

        return when {
            mime == "application/graphql" -> textBody(
                DurableBodyRepresentation.GRAPHQL,
                contentType,
                charset ?: "utf-8",
                payload,
                text,
            )

            mime == "application/x-www-form-urlencoded" ->
                classifyUrlEncodedForm(transactionKey, contentType, charset, payload, text)

            mime.startsWith("multipart/") ->
                CapturedBodySupport(
                    representation = DurableBodyRepresentation.MULTIPART,
                    encoding = DurableBodyEncoding.BINARY,
                    contentType = contentType,
                    charset = charset,
                    completeness = Completeness.COMPLETE,
                    capturedSize = payload.size.toLong(),
                    transferSafeText = null,
                    limitation = BodySupportLimitation.MULTIPART_PARTS_UNAVAILABLE,
                    secretAssessment = SecretAssessment.NONE,
                )

            mime == "application/json" || mime.endsWith("+json") ->
                classifyJson(transactionKey, contentType, charset, payload, text)

            isTextLike(mime) -> textBody(
                DurableBodyRepresentation.TEXT,
                contentType,
                charset ?: "utf-8",
                payload,
                text,
            )

            else -> CapturedBodySupport(
                representation = DurableBodyRepresentation.BINARY,
                encoding = DurableBodyEncoding.BINARY,
                contentType = contentType,
                charset = null,
                completeness = Completeness.COMPLETE,
                capturedSize = payload.size.toLong(),
                transferSafeText = null,
                limitation = BodySupportLimitation.OPAQUE_BINARY,
                secretAssessment = SecretAssessment.NONE,
            )
        }
    }

    private fun classifyJson(
        transactionKey: String,
        contentType: String?,
        charset: String?,
        payload: ByteArray,
        text: String?,
    ): CapturedBodySupport {
        if (text == null) {
            return malformed(DurableBodyRepresentation.JSON, contentType, payload.size)
        }
        val parsed = runCatching { JSONObject(text) }.getOrNull()
            ?: return malformed(DurableBodyRepresentation.JSON, contentType, payload.size)

        val scan = JsonBodySecretScanner.scan(transactionKey, text)
        val graphql = parsed.has("query") && parsed.opt("query") is String
        val unsafe = scan.requiresReview || scan.assessment.findings.isNotEmpty()

        return CapturedBodySupport(
            representation = if (graphql) {
                DurableBodyRepresentation.GRAPHQL
            } else {
                DurableBodyRepresentation.JSON
            },
            encoding = DurableBodyEncoding.UTF8,
            contentType = contentType,
            charset = charset ?: "utf-8",
            completeness = Completeness.COMPLETE,
            capturedSize = payload.size.toLong(),
            transferSafeText = if (unsafe) null else text,
            limitation = if (unsafe) {
                BodySupportLimitation.SECRET_REVIEW_REQUIRED
            } else {
                BodySupportLimitation.NONE
            },
            secretAssessment = scan.assessment,
        )
    }

    private fun classifyUrlEncodedForm(
        transactionKey: String,
        contentType: String?,
        charset: String?,
        payload: ByteArray,
        text: String?,
    ): CapturedBodySupport {
        if (text == null) {
            return malformed(DurableBodyRepresentation.FORM, contentType, payload.size)
        }
        val fields = runCatching {
            text.split('&')
                .filter(String::isNotEmpty)
                .map { pair ->
                    decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
                }
        }.getOrNull() ?: return malformed(
            DurableBodyRepresentation.FORM,
            contentType,
            payload.size,
            BodySupportLimitation.MALFORMED_FORM,
        )

        return formResult(transactionKey, contentType, charset, payload.size, fields)
    }

    private fun classifyFormMap(
        transactionKey: String,
        contentType: String?,
        charset: String?,
        formData: Map<String, List<String>>,
    ): CapturedBodySupport {
        val fields = formData.entries.flatMap { (name, values) ->
            if (values.isEmpty()) listOf(name to "") else values.map { name to it }
        }
        val encodedSize = fields.sumOf { (name, value) ->
            name.encodeToByteArray().size + value.encodeToByteArray().size + 2
        }
        return formResult(transactionKey, contentType, charset, encodedSize, fields)
    }

    private fun formResult(
        transactionKey: String,
        contentType: String?,
        charset: String?,
        size: Int,
        fields: List<Pair<String, String>>,
    ): CapturedBodySupport {
        val rawFields = fields.map { (name, value) ->
            RawObservedField.BodyForm(
                name = name,
                value = RawObservedValue.copyOf(value.encodeToByteArray()),
            )
        }
        return try {
            val assessment = SecretDetector.assess(transactionKey, rawFields)
            val safe = assessment.findings.isEmpty()
            val normalized = if (safe) {
                fields.joinToString("&") { (name, value) ->
                    encode(name) + "=" + encode(value)
                }
            } else {
                null
            }
            CapturedBodySupport(
                representation = DurableBodyRepresentation.FORM,
                encoding = DurableBodyEncoding.UTF8,
                contentType = contentType,
                charset = charset ?: "utf-8",
                completeness = Completeness.COMPLETE,
                capturedSize = size.toLong(),
                transferSafeText = normalized,
                limitation = if (safe) {
                    BodySupportLimitation.NONE
                } else {
                    BodySupportLimitation.SECRET_REVIEW_REQUIRED
                },
                secretAssessment = assessment,
            )
        } finally {
            rawFields.forEach { it.value.close() }
        }
    }

    private fun textBody(
        representation: DurableBodyRepresentation,
        contentType: String?,
        charset: String?,
        payload: ByteArray,
        text: String?,
    ): CapturedBodySupport =
        if (text == null) {
            malformed(representation, contentType, payload.size)
        } else {
            CapturedBodySupport(
                representation = representation,
                encoding = DurableBodyEncoding.UTF8,
                contentType = contentType,
                charset = charset,
                completeness = Completeness.COMPLETE,
                capturedSize = payload.size.toLong(),
                transferSafeText = text,
                limitation = BodySupportLimitation.NONE,
                secretAssessment = SecretAssessment.NONE,
            )
        }

    private fun malformed(
        representation: DurableBodyRepresentation,
        contentType: String?,
        size: Int,
        limitation: BodySupportLimitation = BodySupportLimitation.MALFORMED_TEXT,
    ) = CapturedBodySupport(
        representation = representation,
        encoding = DurableBodyEncoding.BINARY,
        contentType = contentType,
        charset = null,
        completeness = Completeness.PARTIAL,
        capturedSize = size.toLong(),
        transferSafeText = null,
        limitation = limitation,
        secretAssessment = SecretAssessment.NONE,
    )

    private fun isTextLike(mime: String): Boolean =
        mime.startsWith("text/") ||
            mime == "application/xml" ||
            mime == "application/javascript" ||
            mime == "application/sql"

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
}
