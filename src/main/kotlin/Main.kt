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
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.DialogWindow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeDialog
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

        if (showMapWindow.value) {
            DialogWindow(
                create = {
                    ComposeDialog(owner = window).apply {
                        size = Dimension(300, 200)
                        isFocusable = true
                        isUndecorated = true
                        setLocation(300, 300)

                        addWindowListener(object : WindowAdapter() {
                            override fun windowClosing(e: WindowEvent) {
                                showMapWindow.value = false
                                println("Dialog is closing")
                            }
                        })
                    }
                },
                dispose = ComposeDialog::dispose
            ) {
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

        if (showAdditionalOutputWindow.value) {
            DialogWindow(
                create = {
                    ComposeDialog(owner = window).apply {
                        size = Dimension(300, 200)
                        isFocusable = true
                        isUndecorated = true
                        setLocation(300, 500)

                        addWindowListener(object : WindowAdapter() {
                            override fun windowClosing(e: WindowEvent) {
                                showAdditionalOutputWindow.value = false
                                println("Dialog is closing")
                            }
                        })
                    }
                },
                dispose = ComposeDialog::dispose,
            ) {
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
    }

    mainViewModel.displaySystemMessage("Проверяю карты...")
    val mapsUpdated : Boolean = SettingsManager.updateMaps()
    mainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")

    mainViewModel.connect()
}

@Composable
private fun WindowScope.AppWindowTitleBar() = WindowDraggableArea {
    Box(Modifier.fillMaxWidth().height(15.dp).background(Color.DarkGray))
}