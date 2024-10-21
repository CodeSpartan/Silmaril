import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.res.painterResource
import model.MudConnection
import view.MainWindow
import viewmodel.MainViewModel

fun main() = application {
    val client = MudConnection("adan.ru", 4000)
    val mainViewModel = MainViewModel(client)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Silmaril",
        icon = painterResource("icon.png")
        ) {
        MainWindow(mainViewModel)
    }
    mainViewModel.displaySystemMessage("Проверяю карты...")
    val mapsUpdated : Boolean = SettingsManager.updateMaps()
    mainViewModel.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")
    mainViewModel.connect()

    // Simulate receiving messages periodically
//    LaunchedEffect(Unit) {
//        while (true) {
//            viewModel.receiveMessage()
//            kotlinx.coroutines.delay(500) // Optional delay to simulate message frequency
//        }
//    }
}
