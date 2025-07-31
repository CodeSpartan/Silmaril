package model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import misc.getProfileDirectory
import misc.getTriggersDirectory
import scripting.ScriptingEngine
import viewmodel.MainViewModel
import java.io.File

class Profile(val name: String, private val settingsManager: SettingsManager, areMapsReady: StateFlow<Boolean>) {
    val client = MudConnection(
        host = settingsManager.settings.value.gameServer,
        port = settingsManager.settings.value.gamePort,
        // Profile catches text messages from the MudConnection and sends them to the scripting engine
        onMessageReceived = { msg -> scriptingEngine.processLine(msg) }
    )
    val mainViewModel: MainViewModel = MainViewModel(client, settingsManager)
    val scriptingEngine: ScriptingEngine = ScriptingEngine(mainViewModel)
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
            // load triggers
            val triggersDir = File(getTriggersDirectory())
            if (triggersDir.exists() && triggersDir.isDirectory) {
                triggersDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == "kts") {
                        scriptingEngine.loadScript(file)
                    }
                }
            }
            mainViewModel.displaySystemMessage("Triggers are loaded")
            mainViewModel.connect()
        }
    }

    fun onCloseWindow() {
        settingsManager.removeGameWindow(name)
        cleanup()
    }

    fun cleanup() {
        scope.cancel()
        mainViewModel.cleanup()
    }
}