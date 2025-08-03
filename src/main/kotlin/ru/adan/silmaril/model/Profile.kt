package ru.adan.silmaril.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.adan.silmaril.misc.ProfileData
import ru.adan.silmaril.misc.getTriggersDirectory
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File

class Profile(val profileName: String, private val settingsManager: SettingsManager, val areMapsReady: StateFlow<Boolean>) {
    val client = MudConnection(
        host = settingsManager.settings.value.gameServer,
        port = settingsManager.settings.value.gamePort,
        // Profile catches text messages from the MudConnection and sends them to the scripting engine
        onMessageReceived = { msg -> scriptingEngine.processLine(msg) }
    )
    val mainViewModel: MainViewModel = MainViewModel(client, settingsManager)
    val scriptingEngine: ScriptingEngine = ScriptingEngine(mainViewModel, profileName)
    private val scopeDefault: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var _profileData = MutableStateFlow(settingsManager.loadProfile(profileName))
    val profileData: StateFlow<ProfileData> = _profileData

    init {
        if (!settingsManager.settings.value.gameWindows.contains(profileName)) {
            settingsManager.addGameWindow(profileName)
        }

        scopeDefault.launch {
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
        settingsManager.removeGameWindow(profileName)
        cleanup()
    }

    fun cleanup() {
        scopeDefault.cancel()
        mainViewModel.cleanup()
    }
}