package app.taho.browser.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@Composable
fun TahoBrowserApp() {
    TahoTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TahoBg)
                .systemBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 108.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Browser runtime starting…",
                    color = TahoFaint,
                    fontSize = 13.sp,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CaptureIndicator(label = "Capture active")
                Spacer(Modifier.height(9.dp))
                Omnibox(url = "Search or enter address", tabCount = 1)
            }
        }
    }
}

@Composable
private fun CaptureIndicator(label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xD1161619))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(999.dp))
            .clickable { }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(TahoOk)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = TahoText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
private fun Omnibox(url: String, tabCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xE618181B))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = TahoFaint, fontSize = 14.sp)
        Spacer(Modifier.width(9.dp))
        Text(
            text = url,
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
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(tabCount.toString(), color = TahoText, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}
