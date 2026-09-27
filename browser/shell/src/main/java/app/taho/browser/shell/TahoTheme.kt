package app.taho.browser.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val TahoBg = Color(0xFF050505)
internal val TahoSheet = Color(0xFF0D0D10)
internal val TahoGold = Color(0xFFE2B44A)
internal val TahoGoldHi = Color(0xFFF0CD7E)
internal val TahoText = Color(0xFFF3F0E9)
internal val TahoMuted = Color(0xFF98948A)
internal val TahoFaint = Color(0xFF6B675F)
internal val TahoOk = Color(0xFF5FBF8A)
internal val TahoWarn = Color(0xFFE0A64A)
internal val TahoError = Color(0xFFE06A5A)

private val Colors = darkColorScheme(
    background = TahoBg,
    surface = TahoSheet,
    primary = TahoGold,
    onPrimary = TahoBg,
    onBackground = TahoText,
    onSurface = TahoText,
    secondary = TahoGoldHi,
    error = TahoError,
)

@Composable
internal fun TahoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
