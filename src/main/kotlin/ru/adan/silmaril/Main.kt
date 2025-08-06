package ru.adan.silmaril

import androidx.compose.material.Icon
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.component.get
import ru.adan.silmaril.model.FileLogger
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.Profile
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.icon
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.view.AdditionalOutputWindow
import ru.adan.silmaril.view.FloatingWindow
import ru.adan.silmaril.view.HoverManagerProvider
import ru.adan.silmaril.view.MainWindow
import ru.adan.silmaril.view.MapWindow
import ru.adan.silmaril.view.Tab
import ru.adan.silmaril.view.TabbedView
import ru.adan.silmaril.view.small_dialogs.ProfileDialog
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.visual_styles.StyleManager
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import ru.adan.silmaril.model.ProfileManager
import org.koin.core.component.get

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

            fun exitApp() {
                mapModel.cleanup()
                profileManager.cleanup()
                settingsManager.cleanup()
                exitApplication()
            }

            // Main Window
            Window(
                onCloseRequest = {
                    exitApp()
                },
                state = mainWindowState,
                title = "Silmaril",
                icon = painterResource(Res.drawable.icon),
            ) {
                MenuBar {
                    Menu("Файл", mnemonic = 'Ф') {
                        Item("Выход", mnemonic = 'В') {
                            exitApp()
                        }
                    }
                    Menu("Вид", mnemonic = 'В') {
                        Menu("Шрифт", mnemonic = 'Ш') {
                            FontManager.fontFamilies.keys.filter { it != "RobotoClassic" && it != "SourceCodePro" }
                                .forEach { key ->
                                    CheckboxItem(
                                        text = key,
                                        checked = settings.font == key,
                                        onCheckedChange = {
                                            settingsManager.updateFont(key)
                                        }
                                    )
                                }
                        }
                        Menu("Цветовая тема", mnemonic = 'Ц') {
                            StyleManager.styles.keys.forEach { key ->
                                CheckboxItem(
                                    text = key,
                                    checked = settings.colorStyle == key,
                                    onCheckedChange = {
                                        settingsManager.updateColorStyle(key)
                                    }
                                )
                            }
                        }
                        CheckboxItem(
                            text = "Карта",
                            mnemonic = 'К',
                            checked = showMapWindow.value,
                            onCheckedChange = {
                                showMapWindow.value = it
                                settingsManager.updateFloatingWindowState("MapWindow", it)
                            }
                        )
                        CheckboxItem(
                            text = "Окно вывода",
                            mnemonic = 'О',
                            checked = showAdditionalOutputWindow.value,
                            onCheckedChange = {
                                showAdditionalOutputWindow.value = it
                                settingsManager.updateFloatingWindowState("AdditionalOutput", it)
                            }
                        )
                        Item("Добавить окно", mnemonic = 'Д', onClick = { showProfileDialog.value = true })
                    }
                    Menu(profileManager.currentProfileName.value, mnemonic = profileManager.currentProfileName.value.first()) {
                        CheckboxItem(
                            text = "Авто-переподкл.",
                            mnemonic = 'А',
                            checked = settingsManager.settings.value.autoReconnect,
                            onCheckedChange = { settingsManager.toggleAutoReconnect(it) }
                        )
                        Item("Группы", mnemonic = 'Г', onClick = { /*showGroupsDialog.value = true*/ })
                    }
                }
                window.minimumSize = Dimension(800, 600)

                // watch for resize, move, fullscreen toggle and save into settings
                SignUpToWindowEvents(mainWindowState, settingsManager)

                // Stability is achieved through keys in TabbedView for each tab
                val tabs = profileManager.gameWindows.value.values.map { profile ->
                    Tab(
                        // TabbedView will provide isFocused and thisTabId to Tabs when it composes them
                        title = profile.profileName,
                        content = { isFocused, thisTabId ->
                            HoverManagerProvider(window) {
                                MainWindow(profile.mainViewModel, settingsManager, window, isFocused, thisTabId)
                            }
                        }
                    )
                }
                TabbedView(
                    tabs = tabs,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { newIndex, tabName ->
                        profileManager.currentClient.value = profileManager.gameWindows.value[tabName]!!.client
                        profileManager.currentMainViewModel.value = profileManager.gameWindows.value[tabName]!!.mainViewModel
                        selectedTabIndex = newIndex
                        profileManager.currentProfileName.value = tabName.capitalized()
                    },
                    onTabClose = { index, tabName ->
                        profileManager.gameWindows.value[tabName]?.onCloseWindow()
                        profileManager.assignNewWindowsTemp(profileManager.gameWindows.value.filterKeys { it != tabName }.toMap())
                        // if we're closing the currently opened tab, switch to the first available one
                        if (index == selectedTabIndex) {
                            val firstValidProfile = profileManager.gameWindows.value.values.first()
                            profileManager.currentClient.value = firstValidProfile.client
                            profileManager.currentMainViewModel.value = firstValidProfile.mainViewModel

                            val firstAvailableTabIndex = tabs.indexOfFirst { it.title == firstValidProfile.profileName }
                            selectedTabIndex =
                                if (firstAvailableTabIndex > index) firstAvailableTabIndex - 1 else firstAvailableTabIndex
                            profileManager.currentProfileName.value = firstValidProfile.profileName.capitalized()
                        }
                        // if we're closing a tab to the left of current, the current id will need to be adjusted to the left
                        else {
                            if (selectedTabIndex > index) {
                                selectedTabIndex--
                            }
                        }
                    }
                )

                // Map widget
                // FloatingWindow will provide real OwnerWindow down the line
                FloatingWindow(showMapWindow, window, settingsManager, "MapWindow")
                {
                    HoverManagerProvider(window) {
                        MapWindow(profileManager.currentClient.value, settingsManager)
                    }
                }

                // Additional output widget
                FloatingWindow(showAdditionalOutputWindow, window, settingsManager, "AdditionalOutput")
                {
                    AdditionalOutputWindow(profileManager.currentMainViewModel.value, settingsManager)
                }

                ProfileDialog(
                    showProfileDialog, profileManager.gameWindows.value, settingsManager,
                    onAddWindow = { windowName -> profileManager.addProfile(windowName) }
                )
            }

            LaunchedEffect(Unit) { // launch only on first composition, when the program starts
                mapModel.initMaps(profileManager)
            }
        }
    }
}

@Composable
private fun SignUpToWindowEvents(state: WindowState, settings: SettingsManager) {
    LaunchedEffect(state) {
        snapshotFlow { state.size }
            .onEach{ onWindowStateUpdated(state, settings)}
            .launchIn(this)

        snapshotFlow { state.position }
            .filter { it.isSpecified }
            .onEach{ onWindowStateUpdated(state, settings)}
            .launchIn(this)

        snapshotFlow { state.placement }
            .onEach{ onWindowStateUpdated(state, settings)}
            .launchIn(this)
    }
}

private fun onWindowStateUpdated(state: WindowState, settings: SettingsManager) {
    settings.updateWindowState(state)
}