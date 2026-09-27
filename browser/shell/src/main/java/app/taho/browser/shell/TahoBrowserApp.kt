package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.taho.browser.capture.domain.CaptureState

data class BrowserUiState(
    val captureState: CaptureState = CaptureState.OFF,
    val relevantCount: Int = 0,
    val omniboxText: String = "Search or enter address",
    val tabCount: Int = 1,
)

@Composable
fun TahoBrowserApp(
    state: BrowserUiState = BrowserUiState(),
    onCaptureClick: () -> Unit = {},
    onOmniboxClick: () -> Unit = {},
    onTabsClick: () -> Unit = {},
) {
    TahoTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TahoBg)
                .systemBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 108.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.captureState != CaptureState.OFF) {
                    CaptureIndicator(
                        state = state.captureState,
                        relevantCount = state.relevantCount,
                        onClick = onCaptureClick,
                    )
                    Spacer(Modifier.size(9.dp))
                }

                Omnibox(
                    value = state.omniboxText,
                    tabCount = state.tabCount,
                    onClick = onOmniboxClick,
                    onTabsClick = onTabsClick,
                )
            }
        }
    }
}

@Composable
private fun CaptureIndicator(
    state: CaptureState,
    relevantCount: Int,
    onClick: () -> Unit,
) {
    val count = relevantCount.coerceAtLeast(0)
    val label = when (state) {
        CaptureState.OBSERVING -> "Capture active"
        CaptureState.CAPTURING ->
            count.toString() + " relevant request" + if (count == 1) "" else "s"
        CaptureState.PAUSED ->
            "Ⅱ Capture paused · " + count + " requests retained"
        CaptureState.LIMITED ->
            if (count == 0) "△ Capture limited" else "△ Capture limited · " + count + " requests"
        CaptureState.ERROR -> "Capture unavailable"
        CaptureState.OFF -> return
    }
    val dotColor = when (state) {
        CaptureState.OBSERVING, CaptureState.CAPTURING -> TahoOk
        CaptureState.LIMITED -> TahoWarn
        CaptureState.ERROR -> TahoError
        CaptureState.PAUSED, CaptureState.OFF -> Color.Transparent
    }
    val showDot = state == CaptureState.OBSERVING ||
        state == CaptureState.CAPTURING ||
        state == CaptureState.ERROR

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xD1161619))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(dotColor),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = TahoText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun Omnibox(
    value: String,
    tabCount: Int,
    onClick: () -> Unit,
    onTabsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xE618181B))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = TahoFaint, fontSize = 14.sp)
        Spacer(Modifier.width(9.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = TahoMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(9.dp))
                .clickable(onClick = onTabsClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tabCount.coerceAtLeast(1).toString(),
                color = TahoText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }
}
