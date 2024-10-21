import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.res.painterResource
import model.MudConnection
import view.MainWindow
import viewmodel.MainViewModel
import viewmodel.MapViewModel
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val client = MudConnection("adan.ru", 4000)
    val mainViewModel = MainViewModel(client)
    val mapViewModel = MapViewModel(client)

    val showSecondaryWindow = remember { mutableStateOf(true) }

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
                    showSecondaryWindow.value = !showSecondaryWindow.value
                }
                Item("Exit") {
                    mainViewModel.cleanup()
                    mapViewModel.cleanup()
                    exitApplication()
                }
            }
        }
        MainWindow(mainViewModel)

        if (showSecondaryWindow.value) {
            SecondaryWindow { showSecondaryWindow.value = false } // becomes false if it gets closed
        }
    }

    mainViewModel.displaySystemMessage("Проверяю карты...")
    val mapsUpdated : Boolean = SettingsManager.updateMaps()
    mainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")

    mainViewModel.connect()
}

@Composable
fun SecondaryWindow(onClose: () -> Unit) {
    Window(
        title = "Silmaril Map",
        icon = painterResource("icon.png"),
        onCloseRequest = onClose,
        undecorated = true,
        state = rememberWindowState(width = 300.dp, height = 400.dp),
        resizable = false,
        alwaysOnTop = true
    ) {
        // Define UI within the secondary window
        Column(Modifier.background(Color(0xFFEEEEEE))) {
            AppWindowTitleBar()
            Row {
                Text("label 1", Modifier.size(100.dp, 100.dp).padding(10.dp).background(Color.White))
                Text("label 2", Modifier.size(150.dp, 200.dp).padding(5.dp).background(Color.White))
                Text("label 3", Modifier.size(200.dp, 300.dp).padding(25.dp).background(Color.White))
            }
        }
    }
}


@Composable
private fun WindowScope.AppWindowTitleBar() = WindowDraggableArea {
    Box(Modifier.fillMaxWidth().height(48.dp).background(Color.DarkGray))
}