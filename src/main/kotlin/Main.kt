import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import model.SettingsManager
import java.awt.Dimension
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.*
import model.FileLogger
import model.MapModel
import profiles.Profile
import view.*
import view.small_dialogs.*

fun main() = application {
    val settingsManager = remember { SettingsManager() }
    val settings by settingsManager.settings.collectAsState()

    val _areMapsReady = remember { MutableStateFlow(false) }
    val areMapsReady = _areMapsReady.asStateFlow()

    val mapModel = remember { MapModel(settingsManager) }

    var gameWindows: MutableMap<String, Profile> by remember {
        mutableStateOf(settings.gameWindows.associateWith { windowName -> Profile(windowName, settingsManager, areMapsReady) }.toMutableMap())
    }

    var currentClient by remember {mutableStateOf(gameWindows.values.first().client)}
    var currentMainViewModel by remember {mutableStateOf(gameWindows.values.first().mainViewModel)}

    val showMapWindow = remember { mutableStateOf(settingsManager.getFloatingWindowState("MapWindow").show) }
    val showAdditionalOutputWindow = remember { mutableStateOf(settingsManager.getFloatingWindowState("AdditionalOutput").show) }
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
            gameWindows.values.forEach {
                it.cleanup()
            }
            mapModel.cleanup()
            settingsManager.cleanup()
            exitApplication()
        },
        state = mainWindowState,
        title = "Silmaril",
        icon = painterResource("icon.png"),
        ) {
        MenuBar {
            Menu("Файл") {
                Item("Toggle Map") {
                    showMapWindow.value = !showMapWindow.value
                    settingsManager.updateFloatingWindowState("MapWindow", showMapWindow.value)
                }
                Item("Toggle Additional Output") {
                    showAdditionalOutputWindow.value = !showAdditionalOutputWindow.value
                    settingsManager.updateFloatingWindowState("AdditionalOutput", showAdditionalOutputWindow.value)
                }
                Item("Toggle Font") {
                    settingsManager.toggleFont()
                }
                Item("Toggle Color Style") {
                    settingsManager.toggleColorStyle()
                }
                Item("Exit") {
                    gameWindows.values.forEach {
                        it.cleanup()
                    }
                    mapModel.cleanup()
                    settingsManager.cleanup()
                    exitApplication()
                }
            }
            Menu("Вид") {
                Item("Добавить окно", onClick = { showProfileDialog.value = true })
            }
        }
        window.minimumSize = Dimension(800, 600)

        // watch for resize, move, fullscreen toggle and save into settings
        SignUpToWindowEvents(mainWindowState, settingsManager)

        // Stability is achieved through keys in TabbedView for each tab
        val tabs = gameWindows.values.map { profile ->
            Tab(
                // TabbedView will provide isFocused and thisTabId to Tabs when it composes them
                title = profile.name,
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
                selectedTabIndex = newIndex
                println("Switching to tab: $tabName")
                currentClient = gameWindows[tabName]!!.client
                currentMainViewModel = gameWindows[tabName]!!.mainViewModel
            }
        )

        // Map widget
        // FloatingWindow will provide real OwnerWindow down the line
        FloatingWindow(showMapWindow, window, settingsManager, "MapWindow")
        {
            HoverManagerProvider(window) {
                MapWindow(currentClient, mapModel, settingsManager)
            }
        }

        // Additional output widget
        FloatingWindow(showAdditionalOutputWindow, window, settingsManager,"AdditionalOutput")
        {
            AdditionalOutputWindow(currentMainViewModel, settingsManager)
        }

        ProfileDialog(showProfileDialog, gameWindows, settingsManager,
            onAddWindow = { windowName ->
                val newProfile = Profile(windowName, settingsManager, areMapsReady)
                gameWindows = (gameWindows + (windowName to newProfile)).toMutableMap()
            }
        )
    }

    // launch only on first composition, when the program starts
    LaunchedEffect(Unit) {
        // @TODO: each connection needs its own log
        FileLogger.initialize("Silmaril")
        currentMainViewModel.displaySystemMessage("Проверяю карты...")
        val mapsUpdated: Boolean = settingsManager.updateMaps()
        currentMainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")
        // all Profiles->MainViewModels wait for this bool to become true to connect to the server
        mapModel.loadAllMaps(_areMapsReady)
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

