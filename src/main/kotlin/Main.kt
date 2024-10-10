import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import model.MudConnection
import view.MainWindow
import viewmodel.MainViewModel

fun main() = application {
    val client = MudConnection("adan.ru", 4000)
    val mainViewModel = MainViewModel(client)
    Window(onCloseRequest = ::exitApplication) {
        MainWindow(mainViewModel)
    }
    mainViewModel.connect()

    // Simulate receiving messages periodically
//    LaunchedEffect(Unit) {
//        while (true) {
//            viewModel.receiveMessage()
//            kotlinx.coroutines.delay(500) // Optional delay to simulate message frequency
//        }
//    }
}
