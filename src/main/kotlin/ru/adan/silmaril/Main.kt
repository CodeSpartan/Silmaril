package ru.adan.silmaril

import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ru.adan.silmaril.model.SettingsManager
import java.awt.Dimension
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.*
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.icon
import ru.adan.silmaril.view.AdditionalOutputWindow
import ru.adan.silmaril.view.FloatingWindow
import ru.adan.silmaril.view.HoverManagerProvider
import ru.adan.silmaril.view.MainWindow
import ru.adan.silmaril.view.MapWindow
import ru.adan.silmaril.view.Tab
import ru.adan.silmaril.view.TabbedView
import ru.adan.silmaril.view.small_dialogs.ProfileDialog
import org.koin.core.context.startKoin
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.view.AppMenuBar

fun main() {
    startKoin {
        printLogger() // Optional: helps see what Koin is doing
        modules(appModule)
    }

    // it's a Composable
    application {
        KoinContext {
            val settingsManager: SettingsManager = koinInject()
            val settings by settingsManager.settings.collectAsState()
            val profileManager: ProfileManager = koinInject()
            val mapModel: MapModel = koinInject()

            val showMapWindow = remember { mutableStateOf(settingsManager.getFloatingWindowState("MapWindow").show) }
            val showAdditionalOutputWindow =
                remember { mutableStateOf(settingsManager.getFloatingWindowState("AdditionalOutput").show) }
            val mainWindowState = rememberWindowState(
                placement = settings.windowSettings.windowPlacement,
                position = settings.windowSettings.windowPosition,
                size = settings.windowSettings.windowSize
            )

            var selectedTabIndex by remember { mutableStateOf(0) }
            val showProfileDialog = remember { mutableStateOf(false) }

            // Main Window
            Window(
                onCloseRequest = {
                    cleanupOnExit(mapModel, profileManager, settingsManager)
                    exitApplication()
                },
                state = mainWindowState,
                title = "Silmaril",
                icon = painterResource(Res.drawable.icon),
            ) {
                AppMenuBar(
                    showMapWindow = showMapWindow,
                    showAdditionalOutputWindow = showAdditionalOutputWindow,
                    showProfileDialog = showProfileDialog,
                    onExit = {
                        cleanupOnExit(mapModel, profileManager, settingsManager)
                        exitApplication()
                    }
                )
                window.minimumSize = Dimension(800, 600)

                // watch for resize, move, fullscreen toggle and save into settings
                SignUpToWindowEvents(mainWindowState)

                // Stability is achieved through keys in TabbedView for each tab
                val tabs = profileManager.gameWindows.value.values.map { profile ->
                    Tab(
                        // TabbedView will provide isFocused and thisTabId to Tabs when it composes them
                        title = profile.profileName,
                        content = { isFocused, thisTabId ->
                            HoverManagerProvider(window) {
                                MainWindow(profile.mainViewModel, window, isFocused, thisTabId)
                            }
                        }
                    )
                }
                TabbedView(
                    tabs = tabs,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { newIndex -> selectedTabIndex = newIndex },
                    onTabClose = { newIndex -> selectedTabIndex = newIndex }
                )

                // Map widget
                FloatingWindow(showMapWindow, window, settingsManager, "MapWindow")
                {
                    HoverManagerProvider(window) {
                        MapWindow(profileManager.currentClient.value)
                    }
                }

                // Additional output widget
                FloatingWindow(showAdditionalOutputWindow, window, settingsManager, "AdditionalOutput")
                {
                    AdditionalOutputWindow(profileManager.currentMainViewModel.value)
                }

                // Hidden by default, "Open new game window from profile" dialog
                ProfileDialog(
                    showProfileDialog, profileManager.gameWindows.value,
                    onAddWindow = { windowName -> profileManager.addProfile(windowName) }
                )
            }

            LaunchedEffect(Unit) { // launch only on first composition, when the program starts
                mapModel.initMaps(profileManager)
            }
        }
    }
}

fun cleanupOnExit(mapModel: MapModel, profileManager: ProfileManager, settingsManager: SettingsManager) {
    mapModel.cleanup()
    profileManager.cleanup()
    settingsManager.cleanup()
}

@Composable
private fun SignUpToWindowEvents(state: WindowState) {
    val settingsManager: SettingsManager = koinInject()
    LaunchedEffect(state) {
        snapshotFlow { state.size }
            .onEach{ onWindowStateUpdated(state, settingsManager)}
            .launchIn(this)

        snapshotFlow { state.position }
            .filter { it.isSpecified }
            .onEach{ onWindowStateUpdated(state, settingsManager)}
            .launchIn(this)

        snapshotFlow { state.placement }
            .onEach{ onWindowStateUpdated(state, settingsManager)}
            .launchIn(this)
    }
}

private fun onWindowStateUpdated(state: WindowState, settings: SettingsManager) {
    settings.updateWindowState(state)
}