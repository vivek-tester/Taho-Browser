package app.taho.browser

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.taho.browser.runtime.BrowserRuntimeController
import app.taho.browser.runtime.BrowserRuntimeStore
import app.taho.browser.runtime.BrowserSitePermissionKind
import app.taho.browser.runtime.BrowserSurfaceView
import app.taho.browser.runtime.NavigationInput
import app.taho.browser.shell.BrowserTabUiState
import app.taho.browser.shell.BrowserUiState
import app.taho.browser.shell.SitePermissionUiState
import app.taho.browser.shell.TahoBrowserApp

class MainActivity : ComponentActivity() {
    private lateinit var controller: BrowserRuntimeController
    private var activeAndroidPermissionRequestId: String? = null
    private var activeAndroidPermissions: List<String> = emptyList()

    private val androidPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val requestId = activeAndroidPermissionRequestId
        val requested = activeAndroidPermissions
        activeAndroidPermissionRequestId = null
        activeAndroidPermissions = emptyList()

        if (requestId != null) {
            controller.resolveAndroidPermissions(
                requestId = requestId,
                granted = permissionsSatisfied(requested, grants),
            )
        }
    }

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
            val androidPermission = snapshot.androidPermissionRequest

            androidx.compose.runtime.LaunchedEffect(androidPermission?.id) {
                val request = androidPermission ?: return@LaunchedEffect
                activeAndroidPermissionRequestId = request.id
                activeAndroidPermissions = request.permissions

                if (controller.claimAndroidPermissionRequest(request.id)) {
                    runCatching {
                        androidPermissionLauncher.launch(request.permissions.toTypedArray())
                    }.onFailure {
                        activeAndroidPermissionRequestId = null
                        activeAndroidPermissions = emptyList()
                        controller.resolveAndroidPermissions(request.id, granted = false)
                    }
                }
            }

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
                    sitePermission = snapshot.sitePermission?.let { permission ->
                        val copy = permissionCopy(permission.kind)
                        SitePermissionUiState(
                            id = permission.id,
                            origin = permission.origin,
                            title = copy.first,
                            detail = copy.second,
                            isPrivate = permission.isPrivate,
                        )
                    },
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
                onSitePermissionDecision = controller::resolveSitePermission,
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

    private fun permissionCopy(kind: BrowserSitePermissionKind): Pair<String, String> =
        when (kind) {
            BrowserSitePermissionKind.LOCATION ->
                "Allow location access?" to
                    "This site wants to access your device location."

            BrowserSitePermissionKind.PERSISTENT_STORAGE ->
                "Allow persistent site storage?" to
                    "This site wants to keep site data persistently on this device."

            BrowserSitePermissionKind.CAMERA ->
                "Allow camera access?" to
                    "This site wants to use your camera."

            BrowserSitePermissionKind.MICROPHONE ->
                "Allow microphone access?" to
                    "This site wants to use your microphone."

            BrowserSitePermissionKind.CAMERA_AND_MICROPHONE ->
                "Allow camera and microphone?" to
                    "This site wants to use your camera and microphone."
        }

    private fun permissionsSatisfied(
        requested: List<String>,
        grants: Map<String, Boolean>,
    ): Boolean {
        if (requested.isEmpty()) return false

        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true

        return requested.all { permission ->
            when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION ->
                    if (Manifest.permission.ACCESS_COARSE_LOCATION in requested) {
                        fineGranted || coarseGranted
                    } else {
                        fineGranted
                    }

                else -> grants[permission] == true
            }
        }
    }
}
