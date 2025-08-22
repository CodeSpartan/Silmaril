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
import org.koin.logger.slf4jLogger
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.view.AppMenuBar
import org.koin.core.logger.Level
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.TextMacrosManager
import ru.adan.silmaril.view.GroupWindow
import ru.adan.silmaril.view.MobsWindow

fun main() {
    startKoin {
        slf4jLogger(Level.DEBUG) // logback.xml actually limits this to INFO
        modules(appModule)
    }

    val logger = KotlinLogging.logger {}

    // it's a Composable
    application {
        KoinContext {
            val settingsManager: SettingsManager = koinInject()
            val settings by settingsManager.settings.collectAsState()
            val profileManager: ProfileManager = koinInject()
            val textMacrosManager: TextMacrosManager = koinInject()
            val outputWindowModel: OutputWindowModel = koinInject()
            val mapModel: MapModel = koinInject()
            val loreManager: LoreManager = koinInject()

            val showMapWindow = remember { mutableStateOf(settingsManager.getFloatingWindowState("MapWindow").show) }
            val showAdditionalOutputWindow =
                remember { mutableStateOf(settingsManager.getFloatingWindowState("AdditionalOutput").show) }
            val showGroupWindow = remember { mutableStateOf(settingsManager.getFloatingWindowState("GroupWindow").show) }
            val showMobsWindow = remember { mutableStateOf(settingsManager.getFloatingWindowState("MobsWindow").show) }
            val mainWindowState = rememberWindowState(
                placement = settings.windowSettings.windowPlacement,
                position = settings.windowSettings.windowPosition,
                size = settings.windowSettings.windowSize
            )

            //var selectedTabIndex by remember { mutableStateOf(0) }
            val showProfileDialog = remember { mutableStateOf(false) }

            // Main Window
            Window(
                onCloseRequest = {
                    cleanupOnExit(mapModel, profileManager, settingsManager, textMacrosManager, loreManager, outputWindowModel)
                    exitApplication()
                },
                onPreviewKeyEvent = profileManager::onHotkeyKey,
                state = mainWindowState,
                title = "Silmaril",
                icon = painterResource(Res.drawable.icon),
            ) {
                AppMenuBar(
                    showMapWindow = showMapWindow,
                    showAdditionalOutputWindow = showAdditionalOutputWindow,
                    showGroupWindow = showGroupWindow,
                    showMobsWindow = showMobsWindow,
                    showProfileDialog = showProfileDialog,
                    onExit = {
                        cleanupOnExit(mapModel, profileManager, settingsManager, textMacrosManager, loreManager, outputWindowModel)
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
                    selectedTabIndex = profileManager.selectedTabIndex.value,
                    onTabSelected = { profileManager.switchWindow(it) },
                    onTabClose = { profileManager.selectedTabIndex.value = it }
                )

                // Map widget
                FloatingWindow(showMapWindow, window, "MapWindow")
                {
                    HoverManagerProvider(window) {
                        MapWindow(profileManager.currentClient.value, logger)
                    }
                }

                // Additional output widget
                FloatingWindow(showAdditionalOutputWindow, window, "AdditionalOutput")
                {
                    AdditionalOutputWindow(outputWindowModel, logger)
                }

                // Group widget
                FloatingWindow(showGroupWindow, window, "GroupWindow")
                {
                    HoverManagerProvider(window) {
                        GroupWindow(profileManager.currentClient.value, logger)
                    }
                }

                // Mob widget
                FloatingWindow(showMobsWindow, window, "MobsWindow")
                {
                    HoverManagerProvider(window) {
                        MobsWindow(profileManager.currentClient.value, logger)
                    }
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

fun cleanupOnExit(
    mapModel: MapModel,
    profileManager: ProfileManager,
    settingsManager: SettingsManager,
    textMacrosManager: TextMacrosManager,
    loreManager: LoreManager,
    outputWindowModel: OutputWindowModel,
) {
    textMacrosManager.cleanup()
    mapModel.cleanup()
    profileManager.cleanup()
    settingsManager.cleanup()
    loreManager.cleanup()
    outputWindowModel.cleanup()
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