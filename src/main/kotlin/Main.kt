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
import view.AdditionalOutputWindow
import view.MapWindow
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

fun main() = application {
    val client = MudConnection("adan.ru", 4000)
    val mainViewModel = MainViewModel(client)
    val mapViewModel = MapViewModel(client)

    val showMapWindow = remember { mutableStateOf(true) }
    val showAdditionalOutputWindow = remember { mutableStateOf(true) }

    // Main Window
    Window(
        onCloseRequest = {
            mainViewModel.cleanup()
            mapViewModel.cleanup()
            exitApplication()
        },
        title = "Silmaril",
        icon = painterResource("icon.png"),
        ) {
        MenuBar {
            Menu("File") {
                Item("Toggle Map") {
                    showMapWindow.value = !showMapWindow.value
                }
                Item("Toggle Additional Output") {
                    showAdditionalOutputWindow.value = !showAdditionalOutputWindow.value
                }
                Item("Exit") {
                    mainViewModel.cleanup()
                    mapViewModel.cleanup()
                    exitApplication()
                }
            }
        }

        MainWindow(mainViewModel)

        // Map widget
        FloatingWindow(showMapWindow, 600, 300, window)
        {
            MapWindow(mapViewModel)
        }

        // Additional output widget
        FloatingWindow(showAdditionalOutputWindow, 600, 500, window)
        {
            AdditionalOutputWindow(mainViewModel)
        }
    }

    mainViewModel.displaySystemMessage("Проверяю карты...")
    val mapsUpdated : Boolean = SettingsManager.updateMaps()
    mainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")
    mapViewModel.loadAllMaps()

    mainViewModel.connect()
}

@Composable
private fun WindowScope.AppWindowTitleBar() = WindowDraggableArea {
    Box(Modifier.fillMaxWidth().height(15.dp).background(Color.DarkGray))
}

@Composable
fun FloatingWindow(
    show: MutableState<Boolean>,
    positionX: Int,
    positionY: Int,
    owner: ComposeWindow, // owner is necessary for correct focus behavior
    content: @Composable () -> Unit // Custom content as a composable lambda
) {
    if (show.value) {
        DialogWindow(
            create = {
                ComposeDialog(owner = owner).apply { // Set the owner as the window
                    size = Dimension(300, 200)
                    isFocusable = true
                    isUndecorated = true
                    setLocation(positionX, positionY)

                    addWindowListener(object : WindowAdapter() {
                        override fun windowClosing(e: WindowEvent) {
                            show.value = false
                            println("Dialog is closing")
                        }
                    })
                }
            },
            dispose = ComposeDialog::dispose,
        ) {
            Column(Modifier.background(Color.Black)) {
                AppWindowTitleBar()
                content()
            }
        }
    }
}