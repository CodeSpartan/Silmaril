package profiles

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import model.MudConnection
import model.SettingsManager
import viewmodel.MainViewModel

class Profile(val name: String, settingsManager: SettingsManager, areMapsReady: StateFlow<Boolean>) {
    val client = MudConnection(settingsManager.settings.value.gameServer, settingsManager.settings.value.gamePort)
    val mainViewModel = MainViewModel(client, settingsManager)
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        if (!settingsManager.settings.value.gameWindows.contains(name)) {
            settingsManager.addGameWindow(name)
        }

        // if profile doesn't exist (e.g. Default one), create it
        if (!settingsManager.profiles.value.contains(name)) {
            settingsManager.createProfile(name)
        } else {
            // if it exists, load it
        }


        // read settings from settings.options dir/user_profiles/profilename.profile
        scope.launch {
            // Wait until the map is ready
            // The 'first' operator suspends the coroutine until the StateFlow has a 'true'
            areMapsReady.first { it }
            mainViewModel.connect()
        }
    }

    fun cleanup() {
        scope.cancel()
        mainViewModel.cleanup()
    }
}