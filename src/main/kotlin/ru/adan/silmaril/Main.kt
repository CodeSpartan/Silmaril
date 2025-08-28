package ru.adan.silmaril

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ru.adan.silmaril.model.SettingsManager
import java.awt.Dimension
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
import ru.adan.silmaril.view.hovertooltips.HoverManagerProvider
import ru.adan.silmaril.view.MainWindow
import ru.adan.silmaril.view.MapWindow
import ru.adan.silmaril.view.Tab
import ru.adan.silmaril.view.TabbedView
import ru.adan.silmaril.view.small_dialogs.ProfileDialog
import org.koin.core.context.startKoin
import org.koin.logger.slf4jLogger
import ru.adan.silmaril.model.ProfileManager
import org.koin.core.logger.Level
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.styling.TitleBarStyle
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.TextMacrosManager
import ru.adan.silmaril.view.GroupWindow
import ru.adan.silmaril.view.MobsWindow

@OptIn(ExperimentalLayoutApi::class)
fun main() {
    startKoin {
        slf4jLogger(Level.DEBUG) // logback.xml actually limits this to INFO
        modules(appModule)
    }

    val logger = KotlinLogging.logger {}

    val themeDefinition = JewelTheme.darkThemeDefinition()

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
            val roomDataManager: RoomDataManager = koinInject()

            val showTitleMenu: MutableState<Boolean> = remember { mutableStateOf(false) }

            val showMapWindow = remember { mutableStateOf(settingsManager.getFloatingWindowState("MapWindow").show) }
            val showAdditionalOutputWindow =
                remember { mutableStateOf(settingsManager.getFloatingWindowState("AdditionalOutput").show) }
            val showGroupWindow =
                remember { mutableStateOf(settingsManager.getFloatingWindowState("GroupWindow").show) }
            val showMobsWindow = remember { mutableStateOf(settingsManager.getFloatingWindowState("MobsWindow").show) }
            val mainWindowState = rememberWindowState(
                placement = settings.windowSettings.windowPlacement,
                position = settings.windowSettings.windowPosition,
                size = settings.windowSettings.windowSize
            )

            val showProfileDialog = remember { mutableStateOf(false) }

            IntUiTheme(
                theme = themeDefinition,
                styling = ComponentStyling.default().decoratedWindow(
                    titleBarStyle = TitleBarStyle.dark(),
                ),
                swingCompatMode = true,
            ) {
                DecoratedWindow(
                    onCloseRequest = {
                        cleanupOnExit(mapModel, profileManager, settingsManager, textMacrosManager, loreManager, outputWindowModel, roomDataManager)
                        exitApplication()
                     },
                    onPreviewKeyEvent = profileManager::onHotkeyKey,
                    state = mainWindowState,
                    title = "Silmaril",
                    icon = painterResource(Res.drawable.icon),
                    content = {

                        TitleBarView(
                            showMapWindow = showMapWindow,
                            showAdditionalOutputWindow = showAdditionalOutputWindow,
                            showGroupWindow = showGroupWindow,
                            showMobsWindow = showMobsWindow,
                            showProfileDialog = showProfileDialog,
                            showTitleMenu = showTitleMenu,
                            selectedTabIndex = profileManager.selectedTabIndex,
                            onExit = {
                                cleanupOnExit(mapModel, profileManager, settingsManager, textMacrosManager, loreManager, outputWindowModel, roomDataManager)
                                exitApplication()
                            },
                        )

                        window.minimumSize = remember { Dimension(800, 600) }

                        // watch for resize, move, fullscreen toggle and save into settings
                        SignUpToWindowEvents(mainWindowState)

                        // Stability is achieved through keys in TabbedView for each tab
                        val tabs = profileManager.gameWindows.value.values.map { profile ->
                            Tab(
                                // TabbedView will provide isFocused and thisTabId to Tabs when it composes them
                                title = profile.profileName,
                                content = { isFocused, thisTabId ->
                                    HoverManagerProvider(window) {
                                        MainWindow(profile.mainViewModel, window, isFocused, thisTabId, logger)
                                    }
                                }
                            )
                        }
                        TabbedView(
                            tabs = tabs,
                            selectedTabIndex = profileManager.selectedTabIndex.value,
                        )

                        // Map widget
                        FloatingWindow(showMapWindow, showTitleMenu, window, "MapWindow")
                        {
                            HoverManagerProvider(window) {
                                MapWindow(profileManager.currentMapViewModel.value, profileManager, logger)
                            }
                        }

                        // Additional output widget
                        FloatingWindow(showAdditionalOutputWindow, showTitleMenu, window, "AdditionalOutput")
                        {
                            AdditionalOutputWindow(outputWindowModel, logger)
                        }

                        // Group widget
                        FloatingWindow(showGroupWindow, showTitleMenu, window, "GroupWindow")
                        {
                            HoverManagerProvider(window) {
                                GroupWindow(profileManager.currentClient.value, logger)
                            }
                        }

                        // Mob widget
                        FloatingWindow(showMobsWindow, showTitleMenu, window, "MobsWindow")
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

                },)
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
    roomDataManager: RoomDataManager,
) {
    textMacrosManager.cleanup()
    mapModel.cleanup()
    profileManager.cleanup()
    settingsManager.cleanup()
    loreManager.cleanup()
    outputWindowModel.cleanup()
    roomDataManager.cleanup()
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