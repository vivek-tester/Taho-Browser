package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

enum class M4SecretPolicyUi {
    PARAMETERIZE,
    MASK,
    EXPLICIT,
}

enum class M4CompletenessUi {
    COMPLETE,
    PARTIAL,
    TRUNCATED,
    UNAVAILABLE,
    NOT_APPLICABLE,
}

data class M4CaptureHeaderUiState(
    val name: String,
    val displayValue: String,
    val sensitive: Boolean,
    val secretCategory: String? = null,
) {
    init {
        require(name.isNotBlank()) { "header name must not be blank" }
        if (sensitive) {
            require(
                displayValue.contains("•") ||
                    displayValue.startsWith("{{"),
            ) {
                "Sensitive UI headers must already be masked or parameterized."
            }
        }
    }
}

data class M4CaptureRequestUiState(
    val id: String,
    val method: String,
    val url: String,
    val status: Int?,
    val durationMs: Long?,
    val category: String = "API",
    val headers: List<M4CaptureHeaderUiState> = emptyList(),
    val requestUrlCompleteness: M4CompletenessUi = M4CompletenessUi.COMPLETE,
    val requestHeadersCompleteness: M4CompletenessUi = M4CompletenessUi.UNAVAILABLE,
    val requestBodyCompleteness: M4CompletenessUi,
    val responseHeadersCompleteness: M4CompletenessUi = M4CompletenessUi.UNAVAILABLE,
    val responseBodyCompleteness: M4CompletenessUi,
    val timingCompleteness: M4CompletenessUi = M4CompletenessUi.UNAVAILABLE,
    val tlsCompleteness: M4CompletenessUi = M4CompletenessUi.UNAVAILABLE,
    val bodyRepresentation: String? = null,
    val bodyLimitation: String? = null,
    val sensitiveCount: Int = 0,
    val fromPrivateSession: Boolean = false,
    val transferBlockedReason: String? = null,
    val explicitPolicyAllowed: Boolean = false,
    val relevanceCategory: String = category,
    val relevantByDefault: Boolean = true,
    val captureSessionId: String? = null,
    val tabId: String? = null,
    val capturedAtEpochMs: Long? = null,
    val sourceProduct: String = "taho-browser",
    val sourceVersion: String? = null,
    val captureEngineVersion: String? = null,
    val normalizerVersion: String? = null,
    val observationSource: String? = null,
    val redirectCount: Int = 0,
    val transactionState: String? = null,
    val requestBodyCapturedBytes: Long? = null,
    val requestBodyDeclaredBytes: Long? = null,
    val safeBodyPreview: String? = null,
    val safeBodyPreviewTruncated: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "request id must not be blank" }
        require(method.isNotBlank()) { "method must not be blank" }
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "capture UI requires an absolute HTTP(S) URL"
        }
        require(sensitiveCount >= 0) { "sensitiveCount must be non-negative" }
    }
}

@Composable
internal fun M4CaptureSummarySheet(
    requests: List<M4CaptureRequestUiState>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
    ) {
        Text("Captured", color = TahoText, fontSize = 19.sp)
        Text(
            text = requests.size.toString() +
                if (requests.size == 1) " relevant request" else " relevant requests",
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(14.dp))

        if (requests.isEmpty()) {
            Text(
                text = "No relevant requests yet. Continue browsing and Taho will surface API activity here.",
                color = TahoFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(requests, key = { it.id }) { request ->
                M4SummaryRow(request = request, onClick = { onSelect(request.id) })
            }
        }
    }
}

@Composable
private fun M4SummaryRow(
    request: M4CaptureRequestUiState,
    onClick: () -> Unit,
) {
    val parsed = runCatching { URI(request.url) }.getOrNull()
    val path = parsed?.rawPath?.takeIf { it.isNotBlank() } ?: "/"
    val host = parsed?.host ?: "unknown host"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = .028f))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = request.method,
                color = TahoGoldHi,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = path,
                modifier = Modifier.weight(1f),
                color = TahoText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            request.status?.let {
                Text(
                    text = it.toString(),
                    color = TahoOk,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = buildString {
                append(host)
                append(" · ")
                append(request.category)
                request.durationMs?.let {
                    append(" · ")
                    append(it)
                    append(" ms")
                }
            },
            color = TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (request.sensitiveCount > 0 ||
            request.requestBodyCompleteness in
            setOf(M4CompletenessUi.PARTIAL, M4CompletenessUi.TRUNCATED)
        ) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = buildString {
                    if (request.sensitiveCount > 0) append("⚑ credential detected")
                    if (
                        request.requestBodyCompleteness in
                        setOf(M4CompletenessUi.PARTIAL, M4CompletenessUi.TRUNCATED)
                    ) {
                        if (isNotEmpty()) append(" · ")
                        append("△ body ")
                        append(request.requestBodyCompleteness.name.lowercase())
                    }
                },
                color = TahoWarn,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
internal fun M4RequestInspectorSheet(
    request: M4CaptureRequestUiState,
    onBack: () -> Unit,
    onSendToTaho: () -> Unit,
) {
    val parsed = runCatching { URI(request.url) }.getOrNull()
    val path = parsed?.rawPath?.takeIf { it.isNotBlank() } ?: "/"
    val host = parsed?.host ?: "unknown host"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = TahoGoldHi,
                fontSize = 18.sp,
            )
            Spacer(Modifier.width(5.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.method + "  " + path,
                    color = TahoText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(host)
                        request.status?.let { append(" · ").append(it) }
                        request.durationMs?.let { append(" · ").append(it).append(" ms") }
                    },
                    color = TahoFaint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        M4EvidenceRow("Request URL", M4CompletenessUi.COMPLETE)
        M4EvidenceRow("Request headers", if (request.headers.isEmpty()) {
            M4CompletenessUi.UNAVAILABLE
        } else {
            M4CompletenessUi.COMPLETE
        })
        M4EvidenceRow("Request body", request.requestBodyCompleteness)
        M4EvidenceRow("Response body", request.responseBodyCompleteness)

        request.bodyRepresentation?.let { representation ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Body type: " + representation.lowercase(),
                color = TahoMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }
        request.bodyLimitation?.let { limitation ->
            Spacer(Modifier.height(5.dp))
            Text(
                text = limitation,
                color = TahoWarn,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }

        if (request.headers.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Headers",
                color = TahoMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(7.dp))
            request.headers.forEach { header ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Text(
                        text = header.name,
                        modifier = Modifier.weight(.42f),
                        color = TahoFaint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = header.displayValue +
                            if (header.sensitive) "  sensitive" else "",
                        modifier = Modifier.weight(.58f),
                        color = if (header.sensitive) TahoWarn else TahoText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (request.responseBodyCompleteness == M4CompletenessUi.UNAVAILABLE) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Response bodies are unavailable on this capture path. The request can still be reviewed for transfer.",
                color = TahoFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }

        request.transferBlockedReason?.let { reason ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = reason,
                color = TahoWarn,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }

        Spacer(Modifier.height(18.dp))
        M4PrimaryButton(
            label = "Send to Taho ↗",
            enabled = request.transferBlockedReason == null,
            onClick = onSendToTaho,
        )
    }
}

@Composable
private fun M4EvidenceRow(
    label: String,
    completeness: M4CompletenessUi,
) {
    val (prefix, color) = when (completeness) {
        M4CompletenessUi.COMPLETE -> "✓ complete" to TahoOk
        M4CompletenessUi.PARTIAL -> "△ partial" to TahoWarn
        M4CompletenessUi.TRUNCATED -> "△ truncated" to TahoWarn
        M4CompletenessUi.UNAVAILABLE -> "— not captured" to TahoFaint
        M4CompletenessUi.NOT_APPLICABLE -> "— not applicable" to TahoFaint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Text(
            text = prefix,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
    }
}

@Composable
internal fun M4SendConfirmationSheet(
    request: M4CaptureRequestUiState,
    selectedPolicy: M4SecretPolicyUi,
    onPolicySelected: (M4SecretPolicyUi) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val host = runCatching { URI(request.url).host }.getOrNull() ?: "request"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
    ) {
        Text("Send to Taho?", color = TahoText, fontSize = 19.sp)
        Text(
            text = request.method + " · " + host,
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "✓ URL · Method · Query — included",
            color = TahoOk,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "✓ Non-sensitive headers — normalized",
            color = TahoOk,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        if (request.sensitiveCount > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "⛨ " + request.sensitiveCount + " sensitive field" +
                    if (request.sensitiveCount == 1) " — protected" else "s — protected",
                color = TahoWarn,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Credential policy",
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            M4PolicyButton(
                label = "Parameterize",
                active = selectedPolicy == M4SecretPolicyUi.PARAMETERIZE,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = { onPolicySelected(M4SecretPolicyUi.PARAMETERIZE) },
            )
            M4PolicyButton(
                label = "Mask",
                active = selectedPolicy == M4SecretPolicyUi.MASK,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = { onPolicySelected(M4SecretPolicyUi.MASK) },
            )
            M4PolicyButton(
                label = "Explicit",
                active = selectedPolicy == M4SecretPolicyUi.EXPLICIT,
                enabled = request.explicitPolicyAllowed,
                modifier = Modifier.weight(1f),
                onClick = { onPolicySelected(M4SecretPolicyUi.EXPLICIT) },
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when (selectedPolicy) {
                M4SecretPolicyUi.PARAMETERIZE ->
                    "Credentials leave as placeholders such as {{AUTH_TOKEN}}."
                M4SecretPolicyUi.MASK ->
                    "Credentials leave masked; the imported request is not executable as-is."
                M4SecretPolicyUi.EXPLICIT ->
                    "Explicit live credentials require separate consent and are never selected by default."
            },
            color = if (selectedPolicy == M4SecretPolicyUi.EXPLICIT) TahoWarn else TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )

        if (!request.explicitPolicyAllowed) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Explicit is unavailable for this capture because no consent-safe live secret source is retained.",
                color = TahoFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            M4SecondaryButton(
                label = "Cancel",
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            M4PrimaryButton(
                label = "Send to Taho",
                enabled = selectedPolicy != M4SecretPolicyUi.EXPLICIT ||
                    request.explicitPolicyAllowed,
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Import ≠ execute. Taho must open this as an unsaved request and nothing runs automatically.",
            color = TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun M4PolicyButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) TahoGold.copy(alpha = .16f)
                else Color.White.copy(alpha = .03f),
            )
            .border(
                1.dp,
                if (active) TahoGold.copy(alpha = .55f)
                else Color.White.copy(alpha = .10f),
                RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> TahoFaint
                active -> TahoGoldHi
                else -> TahoMuted
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun M4PrimaryButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) TahoGold else Color.White.copy(alpha = .04f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) TahoBg else TahoFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun M4SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = .035f))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = TahoText,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}
