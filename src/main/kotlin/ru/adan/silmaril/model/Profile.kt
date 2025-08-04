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
import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.misc.getTriggersDirectory
import ru.adan.silmaril.misc.toVariable
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
    val mainViewModel: MainViewModel = MainViewModel(client, settingsManager, ::onSystemMessage)
    val scriptingEngine: ScriptingEngine = ScriptingEngine(mainViewModel, profileName)
    private val scopeDefault: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _profileData = MutableStateFlow(settingsManager.loadProfile(profileName))
    val profileData: StateFlow<ProfileData> = _profileData

    init {
        if (!settingsManager.settings.value.gameWindows.contains(profileName)) {
            settingsManager.addGameWindow(profileName)
        }

        scopeDefault.launch {
            compileTriggers()
            // Wait until the map is ready
            // The 'first' operator suspends the coroutine until the StateFlow has a 'true'
            areMapsReady.first { it }
            mainViewModel.connect()
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
    }

    fun onCloseWindow() {
        settingsManager.removeGameWindow(profileName)
        cleanup()
    }

    fun cleanup() {
        scopeDefault.cancel()
        mainViewModel.cleanup()
    }

    fun onSystemMessage(message: String) {
        // get first word of the message
        when (message.trim().substringBefore(" ")) {
            "#var"-> parseVarCommand(message)
            else -> mainViewModel.displaySystemMessage("Ошибка – неизвестное системное сообщение.")
        }
    }

    fun parseVarCommand(message: String) {
        // tries to find pattern: #var {varName} {varValue}
        val varRegex = """\#var \{(.+)\} \{(.+)\}""".toRegex()
        val varRegex2 = """\#var (.+) (.+)""".toRegex()
        var varName : String = ""
        var varValue : String = ""
        val match1 = varRegex.find(message)
        if (match1 != null) {
            varName = match1.groupValues[1]
            varValue = match1.groupValues[2]
        } else {
            val match2 = varRegex2.find(message)
            if (match2 != null) {
                varName = match2.groupValues[1]
                varValue = match2.groupValues[2]
            } else {
                mainViewModel.displayErrorMessage("Ошибка #var - не смог распарсить. Правильный синтаксис: #var {имя} {значение} (фигурные скобки опциональны).")
                return
            }
        }

        _profileData.value.variables[varName] = varValue.toVariable()
        var varType: String = ""
        when (_profileData.value.variables[varName]) {
            is Variable.IntValue -> varType = "целое число"
            is Variable.FloatValue -> varType = "число с запятой"
            is Variable.StringValue -> varType = "строка"
            null -> varType = "ошибка"
        }
        mainViewModel.displaySystemMessage("Переменная $varName = $varValue ($varType)")
    }
}