package ru.adan.silmaril.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import ru.adan.silmaril.misc.ProfileData
import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.misc.getTriggersDirectory
import ru.adan.silmaril.misc.toVariable
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

class Profile(
    val profileName: String,
    private val settingsManager: SettingsManager,
    private val mapModel: MapModel,
) : KoinComponent {
    val client: MudConnection by lazy {
        get {
            parametersOf(
                settingsManager.settings.value.gameServer,
                settingsManager.settings.value.gamePort,
                profileName,
                { msg: String -> scriptingEngine.processLine(msg) }
            )
        }
    }
    val mainViewModel: MainViewModel by lazy {
        get {
            parametersOf(
                client,
                ::onSystemMessage,
                ::onInsertVariables,
                { msg: String -> scriptingEngine.processLine(msg) }
            )
        }
    }

    val scriptingEngine: ScriptingEngine by lazy {
        get<ScriptingEngine> { parametersOf(profileName, mainViewModel, ::isGroupActive) }
    }

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _profileData = MutableStateFlow(settingsManager.loadProfile(profileName))
    val profileData: StateFlow<ProfileData> = _profileData

    // A regex to find any $variables in user's input, including cyrillic, in order to substitute with values
    val insertVarRegex = """(\$[\p{L}\p{N}_]+)""".toRegex()

    val logger = KotlinLogging.logger {}

    init {
        if (!settingsManager.settings.value.gameWindows.contains(profileName)) {
            settingsManager.addGameWindow(profileName)
        }

        scopeDefault.launch {
            compileTriggers()
            mapModel.areMapsReady.first { it }
            // connect after triggers are compiled and maps are ready
            mainViewModel.initAndConnect()
        }

        debounceSaveProfile()
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
            "#unvar" -> parseUnvarCommand(message)
            "#vars" -> printAllVars()
            "#group" -> parseGroupCommand(message)
            "#groups" -> printAllGroups()
            "#act" -> parseSimpleTrigger(message)
            "#zap" -> client.forceDisconnect()
            "#conn" -> parseConnect(message)
            else -> mainViewModel.displaySystemMessage("Ошибка – неизвестное системное сообщение.")
        }
    }

    fun onInsertVariables(message: String) : String {
        return message.replace(insertVarRegex) { profileData.value.variables[it.value.drop(1)]?.toString() ?: it.value }
    }

    fun parseVarCommand(message: String) {
        // tries to find pattern: #var {varName} {varValue}
        val varRegex = """\#var [{]?([\p{L}\p{N}_]+)[}]? \{(.+)\}""".toRegex()
        // tries to find pattern: #var varName varValue
        val varRegex2 = """\#var [{]?([\p{L}\p{N}_]+)[}]? (.+)""".toRegex()
        // in both cases, varName has to be a word, varValue can be any string with spaces

        var varName : String = ""
        var varValue : String = ""
        // if #var is followed only by name, updatePattern is false. It means we only want to display the var, no set it
        var updatePattern = false
        val match1 = varRegex.find(message)
        if (match1 != null) {
            varName = match1.groupValues[1]
            varValue = match1.groupValues[2]
            updatePattern = true
        } else {
            val match2 = varRegex2.find(message)
            if (match2 != null) {
                varName = match2.groupValues[1]
                varValue = match2.groupValues[2]
                updatePattern = true
            } else {
                // Parsing a #var command without a value - when this happens, just display it
                val varRegex3 = """\#var [{]?([\p{L}\p{N}_]+)[}]?""".toRegex()
                val match3 = varRegex3.find(message)
                if (match3 != null) {
                    varName = match3.groupValues[1]
                } else {
                    mainViewModel.displayErrorMessage("Ошибка #var - не смог распарсить.")
                    mainViewModel.displayErrorMessage("Правильный синтаксис: #var {имя} {значение} (фигурные скобки опциональны).")
                    mainViewModel.displayErrorMessage("Или #var {имя} для отображения значения. Для использования в командах: \$имя.")
                    return
                }
            }
        }
        // if updatePattern, update the value
        if (updatePattern) {
            _profileData.update { currentProfile ->
                val newVariablesMap = currentProfile.variables + (varName to varValue.toVariable())
                currentProfile.copy(variables = newVariablesMap)
            }
        }
        // now display the variable
        val foundVar = _profileData.value.variables[varName]
        if (foundVar != null) {
            val varType: String = when (foundVar) {
                is Variable.IntValue -> "целое число"
                is Variable.FloatValue -> "число с запятой"
                is Variable.StringValue -> "строка"
            }
            mainViewModel.displaySystemMessage("Переменная $varName = $foundVar ($varType).")
        } else {
            mainViewModel.displaySystemMessage("Переменная $varName не найдена.")
        }
    }

    fun parseUnvarCommand(message: String) {
        // matches #unvar test or #unvar {test}
        val unvarRegex = """\#unvar [{]?([\p{L}\p{N}_]+)[}]?""".toRegex()
        val match = unvarRegex.find(message)
        if (match != null) {
            val varName = match.groupValues[1]
            if (_profileData.value.variables.containsKey(varName)) {
                _profileData.update { currentProfile ->
                    val newVariablesMap = currentProfile.variables - varName
                    currentProfile.copy(variables = newVariablesMap)
                }
                mainViewModel.displaySystemMessage("Переменная $varName удалена.")
            } else {
                mainViewModel.displaySystemMessage("Переменной $varName не было.")
            }
        } else {
            mainViewModel.displayErrorMessage("Ошибка #unvar - не смог распарсить. Правильный синтаксис: #unvar {имя} (фигурные скобки опциональны).")
        }
    }

    fun printAllVars() {
        profileData.value.variables.forEach { (varName, variable) ->
            val varType: String = when (variable) {
                is Variable.IntValue -> "целое число"
                is Variable.FloatValue -> "число с запятой"
                is Variable.StringValue -> "строка"
            }
            mainViewModel.displaySystemMessage("Переменная $varName = $variable ($varType).")
        }
    }

    // This function can observe very rapid state changes, but will actually only saveSettings after a delay of 500 ms
    @OptIn(FlowPreview::class)
    private fun debounceSaveProfile(debouncePeriod: Long = 500L) {
        profileData
            .drop(1)
            .debounce(debouncePeriod)
            .onEach {
                settingsManager.saveProfile(profileName, it)
            }
            .launchIn(scopeDefault)
    }

    fun getVariable(varName: String): Variable? {
        return profileData.value.variables[varName]
    }

    fun setVariable(varName: String, varValue: Any) {
        _profileData.update { currentProfile ->
            val newVariablesMap = currentProfile.variables + (varName to varValue.toVariable())
            currentProfile.copy(variables = newVariablesMap)
        }
    }

    fun isGroupActive(groupName: String): Boolean {
        return profileData.value.enabledTriggerGroups.contains(groupName)
    }

    fun parseGroupCommand(message: String) {
        // matches #group {test} enable
        val groupRegex = """\#group (enable|disable) [{]?([\p{L}\p{N}_]+)[}]?""".toRegex()
        val match = groupRegex.find(message)
        if (match != null) {
            val enable = match.groupValues[1] // "enable" or "disable"
            val groupName = match.groupValues[2].uppercase()
            if (enable == "enable") {
                _profileData.update { currentProfile ->
                    val newGroups = currentProfile.enabledTriggerGroups + groupName
                    currentProfile.copy(enabledTriggerGroups = newGroups)
                }
                mainViewModel.displaySystemMessage("Группа $groupName включена.${if (!settingsManager.groups.value.contains(groupName)) " Предупреждение: такой группы нет." else ""}")
            } else if (enable == "disable") {
                _profileData.update { currentProfile ->
                    val newGroups = currentProfile.enabledTriggerGroups - groupName
                    currentProfile.copy(enabledTriggerGroups = newGroups)
                }
                mainViewModel.displaySystemMessage("Группа $groupName выключена.")
            }
        } else {
            val groupRegex2 = """\#group [{]?([\p{L}\p{N}_]+)[}]?$""".toRegex()
            val match2 = groupRegex2.find(message)
            if (match2 != null) {
                val groupName = match2.groupValues[1].uppercase()
                mainViewModel.displaySystemMessage("Группа $groupName ${if (profileData.value.enabledTriggerGroups.contains(groupName)) "включена" else "выключена"}.")
            } else {
                mainViewModel.displayErrorMessage("Ошибка #group - не смог распарсить. Правильный синтаксис: #group {имя} enable или disable.")
            }
        }
    }

    // a command that displays all groups
    fun printAllGroups() {
        profileData.value.enabledTriggerGroups.forEach { groupName ->
            mainViewModel.displaySystemMessage("Группа $groupName включена.")
        }
        val disabledGroups = settingsManager.groups.value.subtract(profileData.value.enabledTriggerGroups)
        if (disabledGroups.isNotEmpty()) {
            mainViewModel.displaySystemMessage("-----------")
        }
        disabledGroups.forEach { groupName ->
            mainViewModel.displaySystemMessage("Группа $groupName выключена.")
        }
    }

    fun parseSimpleTrigger(message: String) {
        // matches #act {cond} {trigger} @ {group}, where condition isn't greedy, but trigger is greedy.
        // this allows the trigger to be multi-layered, e.g. an #act command inside the trigger
        // some '{' symbols had to be additionally escaped due to kotlin syntax

        // match against 3 patterns
        // full pattern first: \#act {(.+?)} {(.+)} {(\d+)} {([\p{L}\p{N}_]+)}$ - this will match #act {cond} {trig} {5} {group}
        // if not, smaller pattern: \#act {(.+?)} {(.+)} {(\d+)}$ - this will match #act {cond} {trig} {5}
        // if not, even smaller pattern: \#act {(.+?)} {(.+)}$ - this will match #act {cond} {trig}
        val actRegexBig = """\#act \{(.+?)} \{(.+)} \{(\d+)} \{([\p{L}\p{N}_]+)}$""".toRegex()
        val actRegexMedium = """\#act \{(.+?)} \{(.+)} \{(\d+)}$""".toRegex()
        val actRegexSmall = """\#act \{(.+?)} \{(.+)}$""".toRegex()

        val match = actRegexBig.find(message) ?: actRegexMedium.find(message) ?: actRegexSmall.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #act - не смог распарсить. Правильный синтаксис: #act {условие} {команда} {приоритет} {группа}.")
            return
        }
        val entireCommand = match.groupValues[0]
        val condition = match.groupValues[1]
        val action = match.groupValues[2]
        val priority = if (match.groupValues.size >= 4) match.groupValues[3].toIntOrNull() ?: 5 else 5
        val groupName = if (match.groupValues.size >= 5) match.groupValues[4] else "DEFAULT"
        mainViewModel.displaySystemMessage("Trigger detected: $entireCommand")
        logger.info { "Condition: $condition" }
        logger.info { "Action: $action" }
        logger.info { "Priority: $priority" }
        logger.info { "Group: $groupName" }

    }

    fun parseConnect(message: String) {
        val actRegex = """\#conn (.+?) (\d+)$""".toRegex()
        val match = actRegex.find(message)
        if (match != null) {
            val host = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull() ?: 4000
            mainViewModel.displaySystemMessage("Connecting to: $host:$port")
            client.host = host
            client.port = port
            client.forceDisconnect()
            if (!settingsManager.settings.value.autoReconnect)
                client.connect()
        } else {
            mainViewModel.displayErrorMessage("Ошибка #conn - не смог распарсить. Правильный синтаксис: #conn host port.")
        }
    }
}