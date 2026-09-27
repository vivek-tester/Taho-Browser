package app.taho.browser.contract

import org.json.JSONArray
import org.json.JSONObject

object TransferEnvelopeJson {
    fun encode(envelope: RequestTransferV1): String =
        JSONObject().apply {
            put("schema", envelope.schema)
            put("version", envelope.version)
            put("minimumReaderVersion", envelope.minimumReaderVersion)
            put("transferId", envelope.transferId)
            put("issuedAt", envelope.issuedAt)
            put("source", sourceJson(envelope.source))
            put("request", requestJson(envelope.request))
            put("capture", captureJson(envelope.capture))
            put("security", securityJson(envelope.security))
            put("provenance", provenanceJson(envelope.provenance))
        }.toString()

    private fun sourceJson(source: TransferSource) =
        JSONObject().apply {
            put("product", source.product)
            put("appVersion", source.appVersion)
            put("engine", source.engine)
            put("captureSessionId", source.captureSessionId)
            putNullable("tabId", source.tabId)
            put("observation", source.observation.name)
        }

    private fun requestJson(request: TransferRequest) =
        JSONObject().apply {
            put("id", request.id)
            put("method", request.method)
            put("url", request.url)
            put("query", JSONArray().apply {
                request.query.forEach { put(protectedValueJson(it)) }
            })
            put("headers", JSONArray().apply {
                request.headers.forEach { put(protectedValueJson(it)) }
            })
            putNullable("body", request.body?.let(::bodyJson))
        }

    private fun protectedValueJson(value: TransferQueryParameter) =
        JSONObject().apply {
            put("name", value.name)
            put("value", value.value)
            put("redacted", value.redacted)
            putNullable("secretCategory", value.secretCategory)
            putNullable("policy", value.policy?.name)
        }

    private fun protectedValueJson(value: TransferHeader) =
        JSONObject().apply {
            put("name", value.name)
            put("value", value.value)
            put("redacted", value.redacted)
            putNullable("secretCategory", value.secretCategory)
            putNullable("policy", value.policy?.name)
        }

    private fun bodyJson(body: TransferBody) =
        JSONObject().apply {
            put("representation", body.representation.name)
            putNullable("contentType", body.contentType)
            putNullable("charset", body.charset)
            put("encoding", body.encoding.name)
            put("size", body.size)
            putNullable("declaredSize", body.declaredSize)
            put("truncated", body.truncated)
            put("completeness", body.completeness.name)
            putNullable("content", body.content)
            putNullable("uri", body.uri)
            put("parts", JSONArray().apply {
                body.parts.forEach { part ->
                    put(
                        JSONObject().apply {
                            put("name", part.name)
                            putNullable("filename", part.filename)
                            putNullable("contentType", part.contentType)
                            put("encoding", part.encoding.name)
                            putNullable("value", part.value)
                            putNullable("uri", part.uri)
                            put("size", part.size)
                            put("truncated", part.truncated)
                        },
                    )
                }
            })
        }

    private fun captureJson(capture: TransferCapture) =
        JSONObject().apply {
            put("timestamp", capture.timestamp)
            putNullable("durationMs", capture.durationMs)
            putNullable("status", capture.status)
            putNullable("statusText", capture.statusText)
            putNullable("initiator", capture.initiator)
            putNullable(
                "timing",
                capture.timing?.let { timing ->
                    JSONObject().apply {
                        putNullable("ttfbMs", timing.ttfbMs)
                        putNullable("totalMs", timing.totalMs)
                    }
                },
            )
            put(
                "completeness",
                JSONObject().apply {
                    put("requestUrl", capture.completeness.requestUrl.name)
                    put("requestHeaders", capture.completeness.requestHeaders.name)
                    put("requestBody", capture.completeness.requestBody.name)
                    put("responseHeaders", capture.completeness.responseHeaders.name)
                    put("responseBody", capture.completeness.responseBody.name)
                    put("timing", capture.completeness.timing.name)
                    put("tlsInfo", capture.completeness.tlsInfo.name)
                },
            )
        }

    private fun securityJson(security: TransferSecurity) =
        JSONObject().apply {
            put("secretPolicy", security.secretPolicy.name)
            put("containsSensitiveData", security.containsSensitiveData)
            put("secretCount", security.secretCount)
            put("fromPrivateSession", security.fromPrivateSession)
            put("reviewRequired", security.reviewRequired)
            put("findings", JSONArray().apply {
                security.findings.forEach { finding ->
                    put(
                        JSONObject().apply {
                            put("location", finding.location)
                            put("category", finding.category)
                            put("policy", finding.policy.name)
                        },
                    )
                }
            })
        }

    private fun provenanceJson(provenance: SafeProvenance) =
        JSONObject().apply {
            put("normalizerVersion", provenance.normalizerVersion)
            put("captureEngineVersion", provenance.captureEngineVersion)
            put("capturedAt", provenance.capturedAt)
            put("redirectCount", provenance.redirectCount)
        }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }
}

object TransferReceiptJson {
    fun encode(receipt: TransferReceiptV1): String =
        JSONObject().apply {
            put("transferId", receipt.transferId)
            put("result", receipt.result.name)
            putNullable("requestId", receipt.requestId)
            putNullable("importedAt", receipt.importedAt)
            putNullable("errorCode", receipt.errorCode?.name)
        }.toString()

    fun decode(raw: String): TransferReceiptV1? =
        runCatching {
            val json = JSONObject(raw)
            TransferReceiptV1(
                transferId = json.getString("transferId"),
                result = ReceiptResult.valueOf(json.getString("result")),
                requestId = json.optNullableString("requestId"),
                importedAt = if (json.has("importedAt") && !json.isNull("importedAt")) {
                    json.getLong("importedAt")
                } else {
                    null
                },
                errorCode = json.optNullableString("errorCode")?.let(TransferErrorCode::valueOf),
            )
        }.getOrNull()

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null
}
