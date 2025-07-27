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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import model.FileLogger
import view.*

fun main() = application {
    val settings = SettingsManager()

    val client = MudConnection(settings.gameServer, settings.gamePort)
    val mainViewModel = MainViewModel(client, settings)

    val client2 = MudConnection(settings.gameServer, settings.gamePort)
    val mainViewModel2 = MainViewModel(client2, settings)

    val mapViewModel = MapViewModel(client, settings)
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
            mainViewModel.cleanup()
            mainViewModel2.cleanup()
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
                    mainViewModel.cleanup()
                    mainViewModel2.cleanup()
                    mapViewModel.cleanup()
                    settings.cleanup()
                    exitApplication()
                }
            }
        }
        window.minimumSize = Dimension(800, 600)

        // watch for resize, move, fullscreen toggle and save into settings
        SignUpToWindowEvents(state, settings)

        val tabs = remember {
            listOf(
                Tab("Tab 1") { isFocused, thisTabId ->
                    HoverManagerProvider(window) {
                        MainWindow(mainViewModel, settingsViewModel, window, isFocused, thisTabId)
                    }
                },
                Tab("Tab 2") { isFocused, thisTabId ->
                    HoverManagerProvider(window) {
                        MainWindow(mainViewModel2, settingsViewModel, window, isFocused, thisTabId)
                    }
                }
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
            AdditionalOutputWindow(mainViewModel, settingsViewModel)
        }
    }

    // @TODO: this message isn't displayed right now. Why? Because the View isn't initialized yet?
    mainViewModel.displaySystemMessage("Проверяю карты...")
    val mapsUpdated : Boolean = settings.updateMaps()
    mainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")
    mapViewModel.loadAllMaps()

    mainViewModel.connect()
    mainViewModel2.connect()
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

