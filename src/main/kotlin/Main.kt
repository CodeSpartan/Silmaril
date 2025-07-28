import androidx.compose.ui.res.painterResource
import viewmodel.MapViewModel
import androidx.compose.ui.window.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import model.SettingsManager
import viewmodel.SettingsViewModel
import java.awt.Dimension
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.*
import model.FileLogger
import profiles.Profile
import view.*
import view.small_dialogs.*

fun main() = application {
    val settings = remember { SettingsManager() }

    val _areMapsReady = MutableStateFlow(false)
    val areMapsReady = _areMapsReady.asStateFlow()

    var gameWindows: MutableMap<String, Profile> by remember {
        mutableStateOf(settings.gameWindows.value.associateWith { windowName -> Profile(windowName, settings, areMapsReady) }.toMutableMap())
    }

    var currentClient by remember {mutableStateOf(gameWindows.values.first().client)}
    var currentMainViewModel by remember {mutableStateOf(gameWindows.values.first().mainViewModel)}

    val mapViewModel = remember(currentClient) {
        MapViewModel(currentClient, settings)
    }
    val settingsViewModel = remember { SettingsViewModel(settings) }

    val showMapWindow = remember { mutableStateOf(settings.getFloatingWindowState("MapWindow").show) }
    val showAdditionalOutputWindow = remember { mutableStateOf(settings.getFloatingWindowState("AdditionalOutput").show) }
    val state = rememberWindowState(
        placement = settings.windowPlacement,
        position = settings.windowPosition,
        size = settings.windowSize
    )

    var selectedTabIndex by remember { mutableStateOf(0) }
    var showProfileDialog = remember { mutableStateOf(false) }

    // Main Window
    Window(
        onCloseRequest = {
            gameWindows.values.forEach {
                it.cleanup()
            }
            mapViewModel.cleanup()
            settings.cleanup()
            exitApplication()
        },
        state = state,
        title = "Silmaril",
        icon = painterResource("icon.png"),
        ) {
        MenuBar {
            Menu("Файл") {
                Item("Toggle Map") {
                    showMapWindow.value = !showMapWindow.value
                    settings.updateFloatingWindowState("MapWindow", showMapWindow.value)
                }
                Item("Toggle Additional Output") {
                    showAdditionalOutputWindow.value = !showAdditionalOutputWindow.value
                    settings.updateFloatingWindowState("AdditionalOutput", showAdditionalOutputWindow.value)
                }
                Item("Toggle Font") {
                    settingsViewModel.toggleFont()
                }
                Item("Toggle Color Style") {
                    settingsViewModel.toggleColorStyle()
                }
                Item("Exit") {
                    gameWindows.values.forEach {
                        it.cleanup()
                    }
                    mapViewModel.cleanup()
                    settings.cleanup()
                    exitApplication()
                }
            }
            Menu("Вид") {
                Item("Добавить окно", onClick = { showProfileDialog.value = true })
            }
        }
        window.minimumSize = Dimension(800, 600)

        // watch for resize, move, fullscreen toggle and save into settings
        SignUpToWindowEvents(state, settings)

        // Stability is achieved through keys in TabbedView for each tab
        val tabs = gameWindows.values.map { profile ->
            Tab(
                // TabbedView will provide isFocused and thisTabId to Tabs when it composes them
                title = profile.name,
                content = { isFocused, thisTabId ->
                    HoverManagerProvider(window) {
                        MainWindow(profile.mainViewModel, settingsViewModel, window, isFocused, thisTabId)
                    }
                }
            )
        }
        TabbedView(
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { newIndex, tabName ->
                selectedTabIndex = newIndex
                println("Switching to tab: ${tabName}")
                currentClient = gameWindows[tabName]!!.client
                currentMainViewModel = gameWindows[tabName]!!.mainViewModel
            }
        )

        // Map widget
        // FloatingWindow will provide real OwnerWindow down the line
        FloatingWindow(showMapWindow, window, settings, "MapWindow")
        {
            HoverManagerProvider(window) {
                MapWindow(mapViewModel, settingsViewModel)
            }
        }

        // Additional output widget
        FloatingWindow(showAdditionalOutputWindow, window, settings,"AdditionalOutput")
        {
            AdditionalOutputWindow(currentMainViewModel, settingsViewModel)
        }

        ProfileDialog(showProfileDialog, gameWindows, settings,
            onAddWindow = { windowName ->
                val newProfile = Profile(windowName, settings, areMapsReady)
                gameWindows = (gameWindows + (windowName to newProfile)).toMutableMap()
            }
        )
    }

    // launch only on first composition, when the program starts
    LaunchedEffect(Unit) {
        // @TODO: each connection needs its own log
        FileLogger.initialize("Silmaril")
        currentMainViewModel.displaySystemMessage("Проверяю карты...")
        val mapsUpdated: Boolean = settings.updateMaps()
        currentMainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")
        // all Profiles->MainViewModels wait for this bool to become true to connect to the server
        mapViewModel.loadAllMaps(_areMapsReady)
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

