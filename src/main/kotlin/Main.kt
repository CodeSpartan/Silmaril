import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.res.painterResource
import model.MudConnection
import view.MainWindow
import viewmodel.MainViewModel
import viewmodel.MapViewModel
import androidx.compose.ui.window.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import model.SettingsManager
import view.AdditionalOutputWindow
import view.MapWindow
import viewmodel.SettingsViewModel
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import model.FileLogger
import view.HoverManagerProvider
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

fun main() = application {
    val settings = SettingsManager()
    val client = MudConnection(settings.gameServer, settings.gamePort)
    val mainViewModel = MainViewModel(client, settings)
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

    // Main Window
    Window(
        onCloseRequest = {
            mainViewModel.cleanup()
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
                    mapViewModel.cleanup()
                    exitApplication()
                }
            }
        }
        window.minimumSize = Dimension(800, 600)

        // watch for resize, move, fullscreen toggle and save into settings
        SignUpToWindowEvents(state, settings)

        HoverManagerProvider (window) {
            MainWindow(mainViewModel, settingsViewModel, window)
        }

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

    mainViewModel.displaySystemMessage("Проверяю карты...")
    val mapsUpdated : Boolean = settings.updateMaps()
    mainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")
    mapViewModel.loadAllMaps()

    mainViewModel.connect()
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

@Composable
private fun WindowScope.AppWindowTitleBar() = WindowDraggableArea {
    Box(Modifier.fillMaxWidth().height(15.dp).background(Color.DarkGray))
}

@Composable
fun FloatingWindow(
    show: MutableState<Boolean>,
    owner: ComposeWindow, // owner is necessary for correct focus behavior
    settings: SettingsManager,
    windowName: String,
    content: @Composable () -> Unit
) {
    if (show.value) {
        DialogWindow(
            create = {
                ComposeDialog(owner = owner).apply { // Set the owner as the window
                    val windowInitialState = settings.getFloatingWindowState(windowName)
                    size = Dimension(windowInitialState.windowSize.width, windowInitialState.windowSize.height)
                    isFocusable = true
                    isUndecorated = true
                    location = windowInitialState.windowPosition

                    // Listen to "x" button being pressed
                    addWindowListener(object : WindowAdapter() {
                        override fun windowClosing(e: WindowEvent) {
                            show.value = false
                            settings.updateFloatingWindowState(windowName, false)
                        }
                    })

                    // Listen to position/size changes
                    addComponentListener(object : ComponentAdapter() {
                        override fun componentMoved(e: ComponentEvent) {
                            settings.updateFloatingWindow(windowName, location, size)
                        }

                        override fun componentResized(e: ComponentEvent) {
                            settings.updateFloatingWindow(windowName, location, size)
                        }
                    })
                }
            },
            dispose = ComposeDialog::dispose,
        ) {
            // Provides the real OwnerWindow down the hierarchy
            CompositionLocalProvider(OwnerWindow provides window) {
                Column(Modifier.background(Color.Black)) {
                    AppWindowTitleBar()
                    content()
                }
            }
        }
    }
}

// This will provide any composable with a reference to its immediate containing window.
val OwnerWindow = staticCompositionLocalOf<Window> {
    error("No Window provided. Wrap your content in a provider.")
}