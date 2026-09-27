package app.taho.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.taho.browser.runtime.BrowserRuntimeStore
import app.taho.browser.runtime.BrowserSurfaceView
import app.taho.browser.shell.BrowserUiState
import app.taho.browser.shell.TahoBrowserApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = BrowserRuntimeStore.get(this)

        setContent {
            var snapshot by remember { mutableStateOf(controller.snapshot()) }

            DisposableEffect(controller) {
                controller.setListener { snapshot = it }
                onDispose { controller.setListener(null) }
            }

            val visibleLocation = snapshot.location
                ?.takeUnless { it == "about:blank" }
                ?: "Search or enter address"
            val selectedTabId = snapshot.selectedTabId

            TahoBrowserApp(
                state = BrowserUiState(
                    omniboxText = visibleLocation,
                    tabCount = snapshot.tabCount,
                ),
                browserContent = {
                    AndroidView(
                        factory = { context ->
                            BrowserSurfaceView(context).also { surface ->
                                controller.bind(selectedTabId, surface)
                            }
                        },
                        update = { surface ->
                            controller.bind(selectedTabId, surface)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }
}
