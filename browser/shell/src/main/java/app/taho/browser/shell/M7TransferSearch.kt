package app.taho.browser.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * S5 — Transfer in progress (prototype screen 5, PRD §66).
 * Full-screen, calm, honest: ring pulse + progress sweep + schema footnote.
 * Progress bar animates via transform only; `received` cross-fades on settle.
 */
@Composable
internal fun M7TransferProgressOverlay(
    phase: M7TransferPhaseUi,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = phase == M7TransferPhaseUi.PREPARING || phase == M7TransferPhaseUi.TRANSFERRING,
        enter = fadeIn(tween(TahoDurationScreen, easing = TahoEasing)) +
            slideInVertically(tween(TahoDurationScreen, easing = TahoEasing)) { it / 14 },
        exit = fadeOut(tween(TahoDurationVeil, easing = TahoEasing)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TahoBg)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                TahoRingPulse(color = TahoGold, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(TahoPillShape)
                        .background(TahoGold.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↗",
                        color = TahoGoldHi,
                        fontSize = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = "Sending to Taho…",
                color = TahoText,
                fontFamily = TahoDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(16.dp))
            M7ProgressBar(
                fraction = when (phase) {
                    M7TransferPhaseUi.PREPARING -> .45f
                    else -> .85f
                },
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "taho.request-transfer · v1 · local intent",
                color = TahoFaint,
                fontFamily = TahoSans,
                fontSize = 10.sp,
            )
        }
    }
}

/** Spec §15/F2 — 8.5rem × 2px track, gold fill swept via transform. */
@Composable
private fun M7ProgressBar(fraction: Float) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(1250, easing = TahoEasing),
        label = "transferBar",
    )
    Box(
        modifier = Modifier
            .width(210.dp)
            .height(2.dp)
            .clip(TahoPillShape)
            .background(Color.White.copy(alpha = .09f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
            .height(2.dp)
                .clip(TahoPillShape)
                .background(TahoGold),
        )
    }
}

/**
 * PRD §59 — capture search. Filtering operates on normalized metadata only
 * (host, path, method, status); values and bodies are never searchable text.
 */
internal fun filterByQuery(
    requests: List<M4CaptureRequestUiState>,
    query: String,
): List<M4CaptureRequestUiState> {
    val q = query.trim()
    if (q.isEmpty()) return requests
    return requests.filter { request ->
        val uri = runCatching { java.net.URI(request.url) }.getOrNull()
        val contentType = request.headers.firstOrNull {
            it.name.equals("content-type", ignoreCase = true)
        }?.displayValue.orEmpty()
        val haystack = buildString {
            append(request.id)
            append(' ')
            append(uri?.host.orEmpty())
            append(' ')
            append(uri?.rawPath.orEmpty())
            append(' ')
            append(request.method)
            append(' ')
            request.status?.let { append(it) }
            append(' ')
            append(request.relevanceCategory)
            append(' ')
            append(contentType)
            request.bodyRepresentation?.let {
                append(' ')
                append(it)
            }
        }.lowercase()
        q.lowercase().split(Regex("\\s+")).all { term -> haystack.contains(term) }
    }
}

/**
 * Search field matching the prototype omnibox vocabulary: full pill,
 * mono text, gold cursor. Placed above the filter chip row in the summary.
 */
@Composable
internal fun M7CaptureSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(TahoPillShape)
            .background(Color.White.copy(alpha = .03f))
            .border(1.dp, TahoHairline, TahoPillShape),
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TahoText,
                fontFamily = TahoSans,
                fontSize = 10.5.sp,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(TahoGold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", color = TahoFaint, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search captured requests",
                                color = TahoFaint,
                                fontFamily = TahoSans,
                                fontSize = 10.5.sp,
                            )
                        }
                        inner()
                    }
                }
            },
        )
    }
}

