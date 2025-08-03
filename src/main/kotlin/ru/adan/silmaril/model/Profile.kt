package ru.adan.silmaril.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.adan.silmaril.misc.getTriggersDirectory
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File

class Profile(val name: String, private val settingsManager: SettingsManager, val areMapsReady: StateFlow<Boolean>) {
    val client = MudConnection(
        host = settingsManager.settings.value.gameServer,
        port = settingsManager.settings.value.gamePort,
        // Profile catches text messages from the MudConnection and sends them to the scripting engine
        onMessageReceived = { msg -> scriptingEngine.processLine(msg) }
    )
    val mainViewModel: MainViewModel = MainViewModel(client, settingsManager)
    val scriptingEngine: ScriptingEngine = ScriptingEngine(mainViewModel, name)
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        if (!settingsManager.settings.value.gameWindows.contains(name)) {
            settingsManager.addGameWindow(name)
        }

        // if profile doesn't exist (e.g. Default one), create it
        if (!settingsManager.profiles.value.contains(name)) {
            settingsManager.createProfile(name)
        } else {
            // @TODO: if it exists, load it
        }

        scope.launch {
            compileTriggers()
        }
    }

    suspend fun compileTriggers() {
        // load triggers
        mainViewModel.displaySystemMessage("Компилирую триггеры...")
        val triggersDir = File(getTriggersDirectory())
        var triggersLoaded = 0
        if (triggersDir.exists() && triggersDir.isDirectory) {
            triggersDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "kts") {
                    triggersLoaded += scriptingEngine.loadScript(file)
                }
            }
        }
        mainViewModel.displaySystemMessage("Триггеров скомпилировано: $triggersLoaded")

        // Wait until the map is ready
        // The 'first' operator suspends the coroutine until the StateFlow has a 'true'
        areMapsReady.first { it }
        mainViewModel.connect()
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