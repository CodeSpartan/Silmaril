import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.res.painterResource
import model.MudConnection
import viewmodel.MainViewModel
import viewmodel.MapViewModel
import androidx.compose.ui.window.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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

fun main() = application {
    val settings = SettingsManager()

    val _isMapWidgetReady = MutableStateFlow(false)
    val isMapWidgetReady = _isMapWidgetReady.asStateFlow()

    val gameWindows: MutableMap<String, Profile> = mutableMapOf()
    // read settings, start profiles that are in settings.ini
    // if no profile exists, settings will provide a new one called "Default"
    settings.gameWindows.value.forEach { windowName ->
        gameWindows[windowName] = Profile(windowName, settings, isMapWidgetReady)
    }

    var currentClient by remember {mutableStateOf(gameWindows.values.first().client)}
    var currentMainViewModel by remember {mutableStateOf(gameWindows.values.first().mainViewModel)}

    val mapViewModel = MapViewModel(currentClient, settings)
    val settingsViewModel = SettingsViewModel(settings)
    FileLogger.initialize("Silmaril")

    val showMapWindow = remember { mutableStateOf(settings.getFloatingWindowState("MapWindow").show) }
    val showAdditionalOutputWindow = remember { mutableStateOf(settings.getFloatingWindowState("AdditionalOutput").show) }
    val state = rememberWindowState(
        placement = settings.windowPlacement,
        position = settings.windowPosition,
        size = settings.windowSize
    )

    var selectedTabIndex by remember { mutableStateOf(0) }

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
            Menu("File") {
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
            Menu("View") {
                Item("Add Window") {
                    // @TODO: display popup asking for name, and a list of existing ones
                }
            }
        }
        window.minimumSize = Dimension(800, 600)

        // watch for resize, move, fullscreen toggle and save into settings
        SignUpToWindowEvents(state, settings)

        val tabs : MutableList<Tab> = remember {mutableListOf()}
        gameWindows.forEach {
            tabs.add(
                // TabbedView will provide isFocused and thisTabId to Tabs when it composes them
                Tab(it.value.name) { isFocused, thisTabId ->
                    HoverManagerProvider(window) {
                        MainWindow(it.value.mainViewModel, settingsViewModel, window, isFocused, thisTabId)
                    }
                },
            )
        }
        TabbedView(tabs = tabs)

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
    }

    // @TODO: this message isn't displayed right now. Why? Because the View isn't initialized yet?
    currentMainViewModel.displaySystemMessage("Проверяю карты...")
    val mapsUpdated : Boolean = settings.updateMaps()
    currentMainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")
    mapViewModel.loadAllMaps()
    // all Profiles->MainViewModels wait for this bool to become true to connect to the server
    _isMapWidgetReady.value = true
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

