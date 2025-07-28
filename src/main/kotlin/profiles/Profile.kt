package profiles

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import model.MudConnection
import model.SettingsManager
import viewmodel.MainViewModel

class Profile(val name: String, settings: SettingsManager, isMapWidgetReady: StateFlow<Boolean>) {
    val client = MudConnection(settings.gameServer, settings.gamePort)
    val mainViewModel = MainViewModel(client, settings)
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        if (!settings.gameWindows.value.contains(name)) {
            settings.addGameWindow(name)
        }
        // read settings from settings.options dir/user_profiles/profilename.profile
        scope.launch {
            // Wait until the map is ready
            // The 'first' operator suspends the coroutine until the StateFlow has a 'true'
            isMapWidgetReady.first { it }
            mainViewModel.connect()
        }
    }

    fun cleanup() {
        scope.cancel()
        mainViewModel.cleanup()
    }
}