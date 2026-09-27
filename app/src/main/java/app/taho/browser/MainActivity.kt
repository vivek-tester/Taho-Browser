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
import app.taho.browser.runtime.NavigationInput
import app.taho.browser.shell.BrowserTabUiState
import app.taho.browser.shell.BrowserUiState
import app.taho.browser.shell.TahoBrowserApp

class MainActivity : ComponentActivity() {
    private lateinit var controller: app.taho.browser.runtime.BrowserRuntimeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controller = BrowserRuntimeStore.get(this)

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
                    isLoading = snapshot.isLoading,
                    loadFailed = snapshot.loadFailed,
                    crashed = snapshot.crashed,
                    isPrivate = snapshot.isPrivate,
                    canGoBack = snapshot.canGoBack,
                    canGoForward = snapshot.canGoForward,
                    tabs = snapshot.tabs.map { tab ->
                        BrowserTabUiState(
                            id = tab.id,
                            title = tab.title,
                            location = tab.location,
                            isPrivate = tab.isPrivate,
                            isLoading = tab.isLoading,
                            loadFailed = tab.loadFailed,
                            crashed = tab.crashed,
                            selected = tab.id == snapshot.selectedTabId,
                        )
                    },
                ),
                onNavigate = { input ->
                    NavigationInput.resolve(input)?.let { uri ->
                        controller.load(uri = uri)
                    }
                },
                onBack = controller::goBack,
                onForward = controller::goForward,
                onReload = {
                    if (snapshot.crashed) {
                        controller.recoverCrashedTab(snapshot.selectedTabId)
                    } else {
                        controller.reload()
                    }
                },
                onNewTab = { controller.newTab(privateMode = false) },
                onNewPrivateTab = { controller.newTab(privateMode = true) },
                onSelectTab = controller::selectTab,
                onCloseTab = controller::closeTab,
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

    override fun onStop() {
        controller.persistNow()
        super.onStop()
    }
}
