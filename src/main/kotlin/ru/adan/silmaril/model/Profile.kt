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
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import ru.adan.silmaril.misc.getOrNull
import ru.adan.silmaril.scripting.Trigger

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

    val scriptingEngine: ScriptingEngine  by lazy {
        get<ScriptingEngine > { parametersOf(profileName, mainViewModel, ::isGroupActive) }
    }

    // lazy injection
    val profileManager: ProfileManager by inject()

    val textTriggerManager: TextTriggerManager by inject()

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
            // only one profile will initialize triggerManager, others will not be able to
            // @TODO: move this to ProfileManager or somewhere that it can't be cancelled
            textTriggerManager.initExplicit(this@Profile)
            mapModel.areMapsReady.first { it }
            // connect after triggers are compiled and maps are ready
            mainViewModel.initAndConnect()
        }

        debounceSaveProfile()
    }

    suspend fun compileTriggers() {
        // load triggers
        mainViewModel.displaySystemMessage("Компилирую скрипты...")
        val triggersDir = File(getTriggersDirectory())
        var triggersLoaded = 0
        if (triggersDir.exists() && triggersDir.isDirectory) {
            triggersDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "kts") {
                    triggersLoaded += scriptingEngine.loadScript(file)
                }
            }
        }
        mainViewModel.displaySystemMessage("Триггеров в скриптах скомпилировано: $triggersLoaded")
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
            "#vars" -> printAllVarsCommand()
            "#group" -> parseGroupCommand(message)
            "#groups" -> printAllGroupsCommand()
            "#act" -> parseTextTrigger(message)
            "#grep" -> parseTextTrigger(message)
            "#unact" -> parseRemoveTrigger(message)
            "#zap" -> client.forceDisconnect()
            "#conn" -> parseConnectCommand(message)
            "#echo" -> parseEchoCommand(message)
            "#sendWindow" -> parseSendWindowCommand(message)
            "#sendAll" -> parseSendAllCommand(message)
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
            setVariable(varName, varValue)
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
                removeVariable(varName)
                mainViewModel.displaySystemMessage("Переменная $varName удалена.")
            } else {
                mainViewModel.displaySystemMessage("Переменной $varName не было.")
            }
        } else {
            mainViewModel.displayErrorMessage("Ошибка #unvar - не смог распарсить. Правильный синтаксис: #unvar {имя} (фигурные скобки опциональны).")
        }
    }

    fun printAllVarsCommand() {
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

    fun removeVariable(varName: String) {
        _profileData.update { currentProfile ->
            val newVariablesMap = currentProfile.variables - varName
            currentProfile.copy(variables = newVariablesMap)
        }
    }

    fun isGroupActive(groupName: String): Boolean {
        return profileData.value.enabledTriggerGroups.contains(groupName.uppercase())
    }

    fun parseGroupCommand(message: String) {
        // matches #group enable {test}
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
                mainViewModel.displaySystemMessage("Группа $groupName включена.${if (!settingsManager.doesGroupExist(groupName)) " Предупреждение: такой группы нет." else ""}")
            } else if (enable == "disable") {
                _profileData.update { currentProfile ->
                    val newGroups = currentProfile.enabledTriggerGroups - groupName
                    currentProfile.copy(enabledTriggerGroups = newGroups)
                }
                mainViewModel.displaySystemMessage("Группа $groupName выключена.")
            }
            scriptingEngine.sortTriggersByPriority()
        } else {
            val groupRegex2 = """\#group [{]?([\p{L}\p{N}_]+)[}]?$""".toRegex()
            val match2 = groupRegex2.find(message)
            if (match2 != null) {
                val groupName = match2.groupValues[1].uppercase()
                mainViewModel.displaySystemMessage("Группа $groupName ${if (isGroupActive(groupName)) "включена" else "выключена"}.")
            } else {
                mainViewModel.displayErrorMessage("Ошибка #group - не смог распарсить. Правильный синтаксис: #group enable или disable {имя группы}.")
            }
        }
    }

    // a command that displays all groups
    fun printAllGroupsCommand() {
        profileData.value.enabledTriggerGroups.forEach { groupName ->
            mainViewModel.displayTaggedText("Группа $groupName <color=green>включена</color>.")
        }
        val disabledGroups = settingsManager.groups.value.subtract(profileData.value.enabledTriggerGroups)
        if (disabledGroups.isNotEmpty()) {
            mainViewModel.displaySystemMessage("-----------")
        }
        disabledGroups.forEach { groupName ->
            mainViewModel.displayTaggedText("Группа $groupName <color=yellow>выключена</color>.")
        }
    }

    fun addSingleTriggerToAll(condition: String, action: String, groupName: String, priority: Int, isRegex: Boolean) {
        val newTrigger =
            if (isRegex) Trigger.regCreate(condition, action, priority)
            else Trigger.create(condition, action, priority)
        profileManager.gameWindows.value.values.forEach { profile -> profile.scriptingEngine.addTriggerToGroup(groupName, newTrigger) }
    }

    fun addSingleTriggerToWindow(condition: String, action: String, groupName: String, priority: Int, isRegex: Boolean) {
        val newTrigger =
            if (isRegex) Trigger.regCreate(condition, action, priority)
            else Trigger.create(condition, action, priority)
        scriptingEngine.addTriggerToGroup(groupName, newTrigger)
    }

    fun parseTextTrigger(message: String) {
        // matches #act {cond} {trigger} {priority} {group}, where condition isn't greedy, but trigger is greedy.
        // this allows the trigger to be multi-layered, e.g. an #act command inside the trigger
        // some '{' symbols had to be additionally escaped due to kotlin syntax

        // match against 4 patterns
        // full pattern: \#(re)?act {(.+?)} {(.+)} {(\d+)} {([\p{L}\p{N}_]+)}$ - this will match #act {cond} {trig} {5} {group}
        // if medium pattern: \#(re)?act {(.+?)} {(.+)} {(\d+)}$ - this will match #act {cond} {trig} {5}
        // if medium v2 pattern: \#(re)?act {(.+?)} {(.+)} {(\d+)}$ - this will match #act {cond} {trig} {group}
        // if small pattern: \#(re)?act {(.+?)} {(.+)}$ - this will match #act {cond} {trig}
        val actRegexBig = """\#(?<isRegex>grep|act) \{(?<condition>.+?)} \{(?<action>.+)} \{(?<priority>\d+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val actRegexMedium = """\#(?<isRegex>grep|act) \{(?<condition>.+?)} \{(?<action>.+)} \{(?<priority>\d+)}$""".toRegex()
        val actRegexMediumV2 = """\#(?<isRegex>grep|act) \{(?<condition>.+?)} \{(?<action>.+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val actRegexSmall = """\#(?<isRegex>grep|act) \{(?<condition>.+?)} \{(?<action>.+)}$""".toRegex()

        val match = actRegexBig.find(message)
            ?: actRegexMedium.find(message)
            ?: actRegexMediumV2.find(message)
            ?: actRegexSmall.find(message)
        if (match == null) {
            if (message.startsWith("#act"))
                mainViewModel.displayErrorMessage("Ошибка #act - не смог распарсить. Правильный синтаксис: #act {условие} {команда} {приоритет} {группа}.")
            else
                mainViewModel.displayErrorMessage("Ошибка #grep - не смог распарсить. Правильный синтаксис: #grep {регекс} {команда} {приоритет} {группа}.")
            return
        }
        val groups = match.groups

        val entireCommand = match.value
        val isRegex = groups["isRegex"]!!.value == "grep"
        val condition = groups["condition"]!!.value
        val action = groups["action"]!!.value
        val priority = groups.getOrNull("priority")?.value?.toIntOrNull() ?: 5
        val groupName = groups.getOrNull("group")?.value?.uppercase() ?: "SESSION"

        logger.debug { "Parsed trigger: $entireCommand" }
        logger.debug { "Is regex: $isRegex" }
        logger.debug { "Condition: $condition" }
        logger.debug { "Action: $action" }
        logger.debug { "Priority: $priority" }
        logger.debug { "Group: $groupName" }

        settingsManager.addGroup(groupName)

        val newTrigger =
            if (isRegex) Trigger.regCreate(condition, action, priority)
            else Trigger.create(condition, action, priority)

        // SESSION is the magic keyword. SESSION triggers only apply to current window, not to all windows.
        // They're not saved to any file, so they're transient.
        // The "SESSION" group always exists and is enabled at every launch of the program, even if it had been manually disabled before.
        if (groupName == "SESSION") {
            scriptingEngine.addTriggerToGroup(groupName, newTrigger)
            if (isGroupActive(groupName))
                scriptingEngine.sortTriggersByPriority()
        }
        else {
            profileManager.gameWindows.value.values.forEach { profile ->
                profile.scriptingEngine.addTriggerToGroup(groupName, newTrigger)
                if (profile.isGroupActive(groupName))
                    profile.scriptingEngine.sortTriggersByPriority()
            }
        }

        if (groupName != "SESSION")
            textTriggerManager.saveTextTrigger(condition, action, groupName, priority, isRegex)

        mainViewModel.displaySystemMessage("Триггер добавлен в группу {$groupName}.")

        if (!profileData.value.enabledTriggerGroups.contains(groupName)) {
            mainViewModel.displayTaggedText("Группа {$groupName} <color=yellow>выключена</color>. Чтобы включить, наберите #group enable {$groupName}.")
        }
    }

    fun parseRemoveTrigger(message: String) {
        for (trig in scriptingEngine.getTriggers().values.flatten()) {
            if (trig.action.textCommand != null)
                mainViewModel.displayTaggedText("Trigger: {${trig.condition.originalPattern}} {${trig.action.textCommand}}")
        }
    }

    fun parseConnectCommand(message: String) {
        val actRegex = """\#conn (.+?) (\d+)$""".toRegex()
        val match = actRegex.find(message)
        if (match != null) {
            val host = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull() ?: 4000
            mainViewModel.displaySystemMessage("Подключаюсь к $host:$port")
            client.host = host
            client.port = port
            client.forceDisconnect()
            if (!settingsManager.settings.value.autoReconnect)
                client.forceReconnect()
        } else {
            mainViewModel.displayErrorMessage("Ошибка #conn - не смог распарсить. Правильный синтаксис: #conn host port.")
        }
    }

    fun parseEchoCommand(message: String) {
        // matches #echo text
        val echoRegex = """\#echo (.+)""".toRegex()
        val match = echoRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #echo - не смог распарсить. Правильный синтаксис: #echo текст")
            return
        }
        val echoText = match.groupValues[1]
        mainViewModel.displayColoredMessage(echoText)
    }

    fun parseSendWindowCommand(message: String) {
        // matches #sendWindow {name} {command}
        val sendWindowRegex = """\#sendWindow \{(.+?)} \{(.+)}$""".toRegex()
        val match = sendWindowRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #sendWindow - не смог распарсить. Правильный синтаксис: #sendWindow {окно} {команда}.")
            return
        }
        val windowName = match.groupValues[1]
        val command = match.groupValues[2]
        scriptingEngine.sendWindowCommand(windowName, command)
    }

    fun parseSendAllCommand(message: String) {
        // matches #sendAll text
        val sendAllRegex = """\#sendAll (.+)""".toRegex()
        val match = sendAllRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #sendAll - не смог распарсить. Правильный синтаксис: #sendAll текст")
            return
        }
        val command = match.groupValues[1]
        scriptingEngine.sendAllCommand(command)
    }
}