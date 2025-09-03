package ru.adan.silmaril.model

import androidx.compose.runtime.mutableStateOf
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
import ru.adan.silmaril.misc.toVariable
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.Hotkey
import ru.adan.silmaril.misc.currentTime
import ru.adan.silmaril.misc.getCorrectTransitionWord
import ru.adan.silmaril.misc.getDslScriptsDirectory
import ru.adan.silmaril.misc.getOrNull
import ru.adan.silmaril.scripting.RegexCondition
import ru.adan.silmaril.scripting.Trigger
import ru.adan.silmaril.scripting.sendId
import ru.adan.silmaril.viewmodel.MapViewModel

class Profile(
    val profileName: String,
    private val settingsManager: SettingsManager,
    private val mapModel: MapModel,
    private val outputWindowModel: OutputWindowModel,
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
                { msg: String -> scriptingEngine.processAlias(msg) },
                { msg: String -> scriptingEngine.processLine(msg) },
                { colorfulMsg: ColorfulTextMessage -> scriptingEngine.processSubstitutes(colorfulMsg)}
            )
        }
    }

    val groupModel: GroupModel by lazy {
        get {
            parametersOf(
                client
            )
        }
    }

    val mapViewModel : MapViewModel by lazy {
        get {
            parametersOf(
                client,
                groupModel,
                { msg: String -> mainViewModel.displayTaggedText(msg, false) },
                { msg: String -> mainViewModel.treatUserInput(msg) },
            )
        }
    }

    val mobsModel: MobsModel by lazy {
        get {
            parametersOf(
                client
            )
        }
    }

    val scriptingEngine: ScriptingEngine  by lazy {
        get<ScriptingEngine > { parametersOf(profileName, mainViewModel, ::isGroupActive) }
    }

    // lazy injection
    val profileManager: ProfileManager by inject()
    val textMacrosManager: TextMacrosManager by inject()

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _profileData = MutableStateFlow(settingsManager.loadProfile(profileName))
    val profileData: StateFlow<ProfileData> = _profileData

    // A regex to find any $variables in user's input, including cyrillic, in order to substitute with values
    val insertVarRegex = """(\$[\p{L}\p{N}_]+)""".toRegex()

    val logger = KotlinLogging.logger {}

    var isReadyToConnect = mutableStateOf(false)

    init {
        if (!settingsManager.settings.value.gameWindows.contains(profileName)) {
            settingsManager.addGameWindow(profileName)
        }

        scopeDefault.launch {
            compileTriggers()
            // only one profile will initialize triggerManager, others will not be able to
            // @TODO: move this to ProfileManager or somewhere that it can't be cancelled
            textMacrosManager.initExplicit(this@Profile)
            mapModel.areMapsReady.first { it }
            groupModel.init()
            // connect after triggers are compiled and maps are ready
            mainViewModel.initAndConnect()
            isReadyToConnect.value = true
        }

        debounceSaveProfile()
    }

    suspend fun compileTriggers() {
        // load triggers
        mainViewModel.displaySystemMessage("Компилирую скрипты...")
        val dslDir = File(getDslScriptsDirectory())
        var triggersLoaded = 0
        if (dslDir.exists() && dslDir.isDirectory) {
            dslDir.listFiles()?.forEach { file ->
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
        mapViewModel.cleanup()
        groupModel.cleanup()
        mobsModel.cleanup()
    }

    fun onSystemMessage(messageUntrimmed: String) {
        val message = messageUntrimmed.trim()
        // get first word of the message
        when (messageUntrimmed.trim().substringBefore(" ")) {
            "#var"-> parseVarCommand(message)
            "#unvar" -> parseUnvarCommand(message)
            "#vars" -> printAllVarsCommand()
            "#group" -> parseGroupCommand(message)
            "#groups" -> printAllGroupsCommand()
            "#act" -> parseTextTrigger(message)
            "#grep" -> parseTextTrigger(message)
            "#unact" -> parseRemoveTrigger(message)
            "#ungrep" -> parseRemoveTrigger(message)
            "#triggers" -> printAllTriggers(message)
            "#al" -> parseTextAlias(message)
            "#alias" -> parseTextAlias(message)
            "#unal" -> parseRemoveAlias(message)
            "#unalias" -> parseRemoveAlias(message)
            "#aliases" -> printAllAliases(message)
            "#hot" -> parseAddHotkey(message)
            "#hotkey" -> parseAddHotkey(message)
            "#unhot" -> parseRemoveHotkey(message)
            "#unhotkey" -> parseRemoveHotkey(message)
            "#hotkeys" -> printAllHotkeys(message)
            "#zap" -> client.forceDisconnect()
            "#conn" -> parseConnectCommand(message)
            "#echo" -> parseEchoCommand(message)
            "#send" -> parseSendWindowCommand(message)
            "#sendId" -> parseSendIdCommand(message)
            "#sendAll" -> parseSendAllCommand(message)
            "#window" -> parseWindowCommand(message)
            "#output" -> parseOutputCommand(message)
            "#out" -> parseOutputCommand(message)
            "#lore" -> parseLoreCommand(message)
            "#comment" -> parseCommentCommand(message)
            "#zones" -> printZonesForLevel(message)
            "#path" -> pathfind(message)
            "#previewZone" -> previewZone(message)
            "#sub" -> parseTextSubstitute(message)
            "#subreg" -> parseTextSubstitute(message)
            "#unsub" -> parseRemoveSubstitute(message)
            "#unsubreg" -> parseRemoveSubstitute(message)
            "#subs" -> printAllSubstitutes(message)
            else -> mainViewModel.displaySystemMessage("Ошибка – неизвестное системное сообщение.")
        }
    }

    fun onInsertVariables(message: String) : String {
        return message.replace(insertVarRegex) { matchResult ->
            when (matchResult.value) {
                "\$time" -> currentTime()
                else -> profileData.value.variables[matchResult.value.drop(1)]?.toString() ?: matchResult.value
            }
        }
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
        return profileData.value.enabledGroups.contains(groupName.uppercase())
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
                    val newGroups = currentProfile.enabledGroups + groupName
                    currentProfile.copy(enabledGroups = newGroups)
                }
                mainViewModel.displaySystemMessage("Группа $groupName включена.${if (!settingsManager.doesGroupExist(groupName)) " Предупреждение: такой группы нет." else ""}")
            } else if (enable == "disable") {
                _profileData.update { currentProfile ->
                    val newGroups = currentProfile.enabledGroups - groupName
                    currentProfile.copy(enabledGroups = newGroups)
                }
                mainViewModel.displaySystemMessage("Группа $groupName выключена.")
            }
            scriptingEngine.sortTriggersByPriority()
            scriptingEngine.sortAliasesByPriority()
            scriptingEngine.sortSubstitutesByPriority()
            scriptingEngine.sortHotkeysByPriority()
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
        profileData.value.enabledGroups.forEach { groupName ->
            mainViewModel.displayTaggedText("Группа $groupName <color=green>включена</color>.")
        }
        val disabledGroups = settingsManager.groups.value.subtract(profileData.value.enabledGroups)
        if (disabledGroups.isNotEmpty()) {
            mainViewModel.displaySystemMessage("-----------")
        }
        disabledGroups.forEach { groupName ->
            mainViewModel.displayTaggedText("Группа $groupName <color=yellow>выключена</color>.")
        }
    }

    fun addSingleTriggerToAll(condition: String, action: String, groupName: String, priority: Int, isRegex: Boolean) {
        val newTrigger =
            if (isRegex) Trigger.regCreate(condition, action, priority, false)
            else Trigger.create(condition, action, priority, false)
        profileManager.gameWindows.value.values.forEach { profile -> profile.scriptingEngine.addTriggerToGroup(groupName, newTrigger) }
    }

    fun addSingleAliasToAll(shorthand: String, action: String, groupName: String, priority: Int) {
        val newAlias = Trigger.createAlias(shorthand, action, priority, false)
        profileManager.gameWindows.value.values.forEach { profile -> profile.scriptingEngine.addAliasToGroup(groupName, newAlias) }
    }

    fun addSingleSubToAll(condition: String, action: String, groupName: String, priority: Int, isRegex: Boolean) {
        val newSub =
            if (isRegex) Trigger.subRegCreate(condition, action, priority, false)
            else Trigger.subCreate(condition, action, priority, false)
        profileManager.gameWindows.value.values.forEach { profile -> profile.scriptingEngine.addSubstituteToGroup(groupName, newSub) }
    }

    fun addSingleHotkeyToAll(hotkey: String, action: String, groupName: String, priority: Int) {
        val newHotkey = Hotkey.create(hotkey, action, priority)
        if (newHotkey != null)
            profileManager.gameWindows.value.values.forEach { profile -> profile.scriptingEngine.addHotkeyToGroup(groupName, newHotkey) }
    }

    fun addSingleTriggerToWindow(condition: String, action: String, groupName: String, priority: Int, isRegex: Boolean) {
        val newTrigger =
            if (isRegex) Trigger.regCreate(condition, action, priority, false)
            else Trigger.create(condition, action, priority, false)
        scriptingEngine.addTriggerToGroup(groupName, newTrigger)
    }

    fun addSingleAliasToWindow(shorthand: String, action: String, groupName: String, priority: Int) {
        val newAlias = Trigger.createAlias(shorthand, action, priority, false)
        scriptingEngine.addAliasToGroup(groupName, newAlias)
    }

    fun addSingleSubToWindow(shorthand: String, action: String, groupName: String, priority: Int, isRegex: Boolean) {
        val newSub =
            if (isRegex) Trigger.subRegCreate(shorthand, action, priority, false)
            else Trigger.subCreate(shorthand, action, priority, false)
        scriptingEngine.addSubstituteToGroup(groupName, newSub)
    }

    fun addSingleHotkeyToWindow(keyString: String, action: String, groupName: String, priority: Int) {
        val newHotkey = Hotkey.create(keyString, action, priority)
        if (newHotkey != null)
            scriptingEngine.addHotkeyToGroup(groupName, newHotkey)
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

        val newTrigger =
            if (isRegex) Trigger.regCreate(condition, action, priority, false)
            else Trigger.create(condition, action, priority, false)

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
            textMacrosManager.saveTextTrigger(condition, action, groupName, priority, isRegex)

        mainViewModel.displaySystemMessage("Триггер добавлен в группу {$groupName}.")

        if (!profileData.value.enabledGroups.contains(groupName)) {
            mainViewModel.displayTaggedText("Группа {$groupName} <color=yellow>выключена</color>. Чтобы включить, наберите #group enable {$groupName}.")
        }
    }

    fun parseRemoveTrigger(message: String) {
        val actRegexBig = """\#un(?<isRegex>grep|act) \{(?<condition>.+?)} \{(?<action>.+)} \{(?<priority>\d+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val actRegexMedium = """\#un(?<isRegex>grep|act) \{(?<condition>.+?)} \{(?<action>.+)} \{(?<priority>\d+)}$""".toRegex()
        val actRegexMediumV2 = """\#un(?<isRegex>grep|act) \{(?<condition>.+?)} \{(?<action>.+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val actRegexSmall = """\#un(?<isRegex>grep|act) \{(?<condition>.+?)} \{(?<action>.+)}$""".toRegex()

        val match = actRegexBig.find(message)
            ?: actRegexMedium.find(message)
            ?: actRegexMediumV2.find(message)
            ?: actRegexSmall.find(message)
        if (match == null) {
            if (message.startsWith("#unact"))
                mainViewModel.displayErrorMessage("Ошибка #unact - не смог распарсить. Правильный синтаксис: #unact {условие} {команда} {приоритет} {группа}.")
            else
                mainViewModel.displayErrorMessage("Ошибка #grep - не смог распарсить. Правильный синтаксис: #ungrep {регекс} {команда} {приоритет} {группа}.")
            return
        }
        val groups = match.groups

        val entireCommand = match.value
        val isRegex = groups["isRegex"]!!.value == "grep"
        val condition = groups["condition"]!!.value
        val action = groups["action"]!!.value
        val priority = groups.getOrNull("priority")?.value?.toIntOrNull() ?: 5
        val groupName = groups.getOrNull("group")?.value?.uppercase() ?: "SESSION"

        var removedTrigger = false
        if (groupName == "SESSION") {
            removedTrigger = scriptingEngine.removeTriggerFromGroup(condition, action, priority, groupName, isRegex)
            if (isGroupActive(groupName))
                scriptingEngine.sortTriggersByPriority()
        } else {
            profileManager.gameWindows.value.values.forEach { profile ->
                removedTrigger = profile.scriptingEngine.removeTriggerFromGroup(condition, action, priority, groupName, isRegex)
                if (profile.isGroupActive(groupName))
                    profile.scriptingEngine.sortTriggersByPriority()
            }
        }

        if (groupName != "SESSION")
            textMacrosManager.deleteTextTrigger(condition, action, groupName, priority, isRegex)

        if (removedTrigger)
            mainViewModel.displaySystemMessage("Триггер успешно удален.")
        else
            mainViewModel.displaySystemMessage("Триггер не найден.")
    }

    fun parseRemoveAlias(message: String) {
        val aliasRegexBig = """\#unal(?:ias)? \{(?<shorthand>.+?)} \{(?<action>.+)} \{(?<priority>\d+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val aliasRegexMedium = """\#unal(?:ias)? \{(?<shorthand>.+?)} \{(?<action>.+)} \{(?<priority>\d+)}$""".toRegex()
        val aliasRegexMediumV2 = """\#unal(?:ias)? \{(?<shorthand>.+?)} \{(?<action>.+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val aliasRegexSmall = """\#unal(?:ias)? \{(?<shorthand>.+?)} \{(?<action>.+)}$""".toRegex()

        val match = aliasRegexBig.find(message)
            ?: aliasRegexMedium.find(message)
            ?: aliasRegexMediumV2.find(message)
            ?: aliasRegexSmall.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #unalias - не смог распарсить. Правильный синтаксис: #unalias {скоропись} {полная команда} {приоритет} {группа}.")
            return
        }
        val groups = match.groups

        val entireCommand = match.value
        val shorthand = groups["shorthand"]!!.value
        val action = groups["action"]!!.value
        val priority = groups.getOrNull("priority")?.value?.toIntOrNull() ?: 5
        val groupName = groups.getOrNull("group")?.value?.uppercase() ?: "SESSION"

        logger.debug { "Parsed trigger: $entireCommand" }
        logger.debug { "Shorthand: $shorthand" }
        logger.debug { "Action: $action" }
        logger.debug { "Priority: $priority" }
        logger.debug { "Group: $groupName" }

        var removedAlias = false
        if (groupName == "SESSION") {
            removedAlias = scriptingEngine.removeAliasFromGroup(shorthand, action, priority, groupName)
            if (isGroupActive(groupName))
                scriptingEngine.sortAliasesByPriority()
        } else {
            profileManager.gameWindows.value.values.forEach { profile ->
                removedAlias = profile.scriptingEngine.removeAliasFromGroup(shorthand, action, priority, groupName)
                if (profile.isGroupActive(groupName))
                    profile.scriptingEngine.sortAliasesByPriority()
            }
        }

        if (groupName != "SESSION")
            textMacrosManager.deleteTextAlias(shorthand, action, groupName, priority)

        if (removedAlias)
            mainViewModel.displaySystemMessage("Алиас успешно удален.")
        else
            mainViewModel.displaySystemMessage("Алиас не найден.")
    }

    fun printAllTriggers(message: String) {
        val triggersRegex = """\#triggers\s*[{]?([\p{L}\p{N}_]+)?[}]?""".toRegex()
        val match = triggersRegex.find(message)
        var printAllGroups = true
        var groupToPrint = ""
        if (match != null) {
            if (match.groupValues.size > 1) {
                groupToPrint = match.groupValues[1]
                printAllGroups = false
            }
        } else {
            mainViewModel.displayErrorMessage("Ошибка #triggers - не смог распарсить. Правильный синтаксис: #triggers {имя группы} (опционально).")
            return
        }

        for ((groupName, trigList) in scriptingEngine.getTriggers()) {
            if (trigList.isEmpty()) continue
            if (!printAllGroups && groupToPrint.isNotEmpty() && !groupName.equals(groupToPrint, true)) continue
            if (isGroupActive(groupName))
                mainViewModel.displayTaggedText("Группа: <color=green>$groupName</color>")
            else
                mainViewModel.displayTaggedText("Группа: <color=yellow>$groupName</color>")

            for (trig in trigList.filter { it.withDsl }.sortedBy { it.priority }) {
                val isRegex = trig.condition is RegexCondition
                mainViewModel.displayTaggedText("<color=magenta>DSL</color> ${if (isRegex) "Regex" else "Trigger"}: {${trig.condition.originalPattern}} {${trig.action.originalCommand ?: "-> lambda"}} {${trig.priority}} {$groupName}")
            }

            for (trig in trigList.filter { !it.withDsl }.sortedBy { it.priority }) {
                val isRegex = trig.condition is RegexCondition
                mainViewModel.displayTaggedText("${if (isRegex) "Regex" else "Trigger"}: {${trig.condition.originalPattern}} {${trig.action.originalCommand}} {${trig.priority}} {$groupName}")
            }
        }
    }

    fun printAllAliases(message: String) {
        val aliasesRegex = """\#aliases\s*[{]?([\p{L}\p{N}_]+)?[}]?""".toRegex()
        val match = aliasesRegex.find(message)
        var printAllGroups = true
        var groupToPrint = ""
        if (match != null) {
            if (match.groupValues.size > 1) {
                groupToPrint = match.groupValues[1]
                printAllGroups = false
            }
        } else {
            mainViewModel.displayErrorMessage("Ошибка #aliases - не смог распарсить. Правильный синтаксис: #aliases {имя группы} (опционально).")
            return
        }

        for ((groupName, aliasList) in scriptingEngine.getAliases()) {
            if (aliasList.isEmpty()) continue
            if (!printAllGroups && groupToPrint.isNotEmpty() && !groupName.equals(groupToPrint, true)) continue
            if (isGroupActive(groupName))
                mainViewModel.displayTaggedText("Группа: <color=green>$groupName</color>")
            else
                mainViewModel.displayTaggedText("Группа: <color=yellow>$groupName</color>")

            for (alias in aliasList.filter { it.withDsl }.sortedBy { it.priority }) {
                mainViewModel.displayTaggedText("<color=magenta>DSL</color> Alias: {${alias.condition.originalPattern}} {${alias.action.originalCommand ?: "-> lambda"}} {${alias.priority}} {$groupName}")
            }

            for (trig in aliasList.filter { !it.withDsl }.sortedBy { it.priority }) {
                mainViewModel.displayTaggedText("Alias: {${trig.condition.originalPattern}} {${trig.action.originalCommand}} {${trig.priority}} {$groupName}")
            }
        }
    }

    fun parseTextAlias(message: String) {
        val aliasRegexBig = """\#al(?:ias)? \{(?<shorthand>.+?)} \{(?<action>.+)} \{(?<priority>\d+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val aliasRegexMedium = """\#al(?:ias)? \{(?<shorthand>.+?)} \{(?<action>.+)} \{(?<priority>\d+)}$""".toRegex()
        val aliasRegexMediumV2 = """\#al(?:ias)? \{(?<shorthand>.+?)} \{(?<action>.+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val aliasRegexSmall = """\#al(?:ias)? \{(?<shorthand>.+?)} \{(?<action>.+)}$""".toRegex()

        val match = aliasRegexBig.find(message)
            ?: aliasRegexMedium.find(message)
            ?: aliasRegexMediumV2.find(message)
            ?: aliasRegexSmall.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #alias - не смог распарсить. Правильный синтаксис: #alias {скоропись} {полная команда} {приоритет} {группа}.")
            return
        }
        val groups = match.groups

        val entireCommand = match.value
        val shorthand = groups["shorthand"]!!.value
        val action = groups["action"]!!.value
        val priority = groups.getOrNull("priority")?.value?.toIntOrNull() ?: 5
        val groupName = groups.getOrNull("group")?.value?.uppercase() ?: "SESSION"

        logger.debug { "Parsed trigger: $entireCommand" }
        logger.debug { "Shorthand: $shorthand" }
        logger.debug { "Action: $action" }
        logger.debug { "Priority: $priority" }
        logger.debug { "Group: $groupName" }

        val newAlias = Trigger.createAlias(shorthand, action, priority, false)

        // SESSION is the magic keyword. SESSION triggers only apply to current window, not to all windows.
        // They're not saved to any file, so they're transient.
        // The "SESSION" group always exists and is enabled at every launch of the program, even if it had been manually disabled before.
        if (groupName == "SESSION") {
            scriptingEngine.addAliasToGroup(groupName, newAlias)
            if (isGroupActive(groupName))
                scriptingEngine.sortAliasesByPriority()
        }
        else {
            profileManager.gameWindows.value.values.forEach { profile ->
                profile.scriptingEngine.addAliasToGroup(groupName, newAlias)
                if (profile.isGroupActive(groupName))
                    profile.scriptingEngine.sortAliasesByPriority()
            }
        }

        if (groupName != "SESSION")
            textMacrosManager.saveTextAlias(shorthand, action, groupName, priority)

        mainViewModel.displaySystemMessage("Алиас добавлен в группу {$groupName}.")

        if (!profileData.value.enabledGroups.contains(groupName)) {
            mainViewModel.displayTaggedText("Группа {$groupName} <color=yellow>выключена</color>. Чтобы включить, наберите #group enable {$groupName}.")
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
        mainViewModel.displayTaggedText(match.groupValues[1], false)
    }

    fun parseSendWindowCommand(message: String) {
        // matches #send {name} {command}
        val sendWindowRegex = """\#send \{(.+?)} \{(.+)}$""".toRegex()
        val match = sendWindowRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #send - не смог распарсить. Правильный синтаксис: #send {окно} {команда}.")
            return
        }
        val windowName = match.groupValues[1]
        val command = match.groupValues[2]
        scriptingEngine.sendWindowCommand(windowName, command)
    }

    fun parseSendIdCommand(message: String) {
        // matches #sendId {name} {command}
        val sendWindowRegex = """\#sendId \{(\d+)} \{(.+)}$""".toRegex()
        val match = sendWindowRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #sendId - не смог распарсить. Правильный синтаксис: #sendId {номер окна} {команда}.")
            return
        }
        val windowId = match.groupValues[1].toInt()
        val command = match.groupValues[2]
        scriptingEngine.sendId(windowId, command)
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

    fun parseWindowCommand(message: String) {
        // matches #window {name}
        val switchWindowRegex = """\#window [{]?([\p{L}\p{N}_]+)[}]?$""".toRegex()
        val match = switchWindowRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #window - не смог распарсить. Правильный синтаксис: #window {окно}.")
            return
        }
        val windowName = match.groupValues[1]
        if (!scriptingEngine.switchWindowCommand(windowName)) {
            mainViewModel.displayErrorMessage("Ошибка #window - окно не найдено.")
        }
    }

    fun parseLoreCommand(message: String) {
        val loreRegex = """\#lore ([\p{L}\p{N}_",\-\s]+)$""".toRegex()
        val match = loreRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #lore - не смог распарсить. Правильный синтаксис: #lore имя предмета.")
            return
        }
        val loreName = match.groupValues[1].trimEnd()
        scriptingEngine.loreCommand(loreName)
    }

    fun parseOutputCommand(message: String) {
        val outputRegex = """\#out(?:put)? (.+)""".toRegex()
        val match = outputRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #output - не смог распарсить. Правильный синтаксис: #output сообщение")
            return
        }
        outputWindowModel.displayTaggedText(match.groupValues[1], false)
    }

    fun parseCommentCommand(message: String) {
        val commentRegex = """\#comment (.*)""".toRegex()
        val match = commentRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #comment - не смог распарсить. Правильный синтаксис: #comment ваш комментарий.")
            return
        }
        if (scriptingEngine.commentCommand(match.groupValues[1].trimEnd())) {
            mainViewModel.displayTaggedText("Вы сделали заметку.", false)
        } else {
            mainViewModel.displayErrorMessage("Ошибка #comment - команда оставляет комментарий на последнем отображенном лоре, а вы еще не отображали лоры.")
        }
    }

    private fun parseAddHotkey(message: String) {
        val hotkeyRegexBig = """\#hot(?:key)? \{(?<hotkey>.+?)} \{(?<action>.+)} \{(?<priority>\d+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val hotkeyRegexMedium = """\#hot(?:key)? \{(?<hotkey>.+?)} \{(?<action>.+)} \{(?<priority>\d+)}$""".toRegex()
        val hotkeyRegexMediumV2 = """\#hot(?:key)? \{(?<hotkey>.+?)} \{(?<action>.+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val hotkeyRegexSmall = """\#hot(?:key)? \{(?<hotkey>.+?)} \{(?<action>.+)}$""".toRegex()

        val match = hotkeyRegexBig.find(message)
            ?: hotkeyRegexMedium.find(message)
            ?: hotkeyRegexMediumV2.find(message)
            ?: hotkeyRegexSmall.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #hotkey - не смог распарсить. Правильный синтаксис: #hotkey {комбинация клавиш} {команда} {приоритет} {группа}.")
            return
        }
        val groups = match.groups

        val entireCommand = match.value
        val hotkeyString = groups["hotkey"]!!.value
        val action = groups["action"]!!.value
        val priority = groups.getOrNull("priority")?.value?.toIntOrNull() ?: 5
        val groupName = groups.getOrNull("group")?.value?.uppercase() ?: "SESSION"

        logger.debug { "Parsed trigger: $entireCommand" }
        logger.debug { "Hotkey: $hotkeyString" }
        logger.debug { "Action: $action" }
        logger.debug { "Priority: $priority" }
        logger.debug { "Group: $groupName" }

        val newHotkey = Hotkey.create(hotkeyString, action, priority)

        if (newHotkey == null) {
            mainViewModel.displayErrorMessage("Ошибка #hotkey - такой клавиши не найдено, см. документацию.")
            return
        }

        // SESSION is the magic keyword. SESSION triggers only apply to current window, not to all windows.
        // They're not saved to any file, so they're transient.
        // The "SESSION" group always exists and is enabled at every launch of the program, even if it had been manually disabled before.
        if (groupName == "SESSION") {
            scriptingEngine.addHotkeyToGroup(groupName, newHotkey)
            if (isGroupActive(groupName))
                scriptingEngine.sortHotkeysByPriority()
        }
        else {
            profileManager.gameWindows.value.values.forEach { profile ->
                profile.scriptingEngine.addHotkeyToGroup(groupName, newHotkey)
                if (profile.isGroupActive(groupName))
                    profile.scriptingEngine.sortHotkeysByPriority()
            }
        }

        if (groupName != "SESSION")
            textMacrosManager.saveHotkey(hotkeyString, action, groupName, priority)

        mainViewModel.displaySystemMessage("Хоткей добавлен в группу {$groupName}.")

        if (!profileData.value.enabledGroups.contains(groupName)) {
            mainViewModel.displayTaggedText("Группа {$groupName} <color=yellow>выключена</color>. Чтобы включить, наберите #group enable {$groupName}.")
        }
    }

    fun parseRemoveHotkey(message: String) {
        val hotkeyRegexBig = """\#unhot(?:key)? \{(?<hotkey>.+?)} \{(?<action>.+)} \{(?<priority>\d+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val hotkeyRegexMedium = """\#unhot(?:key)? \{(?<hotkey>.+?)} \{(?<action>.+)} \{(?<priority>\d+)}$""".toRegex()
        val hotkeyRegexMediumV2 = """\#unhot(?:key)? \{(?<hotkey>.+?)} \{(?<action>.+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val hotkeyRegexSmall = """\#unhot(?:key)? \{(?<hotkey>.+?)} \{(?<action>.+)}$""".toRegex()

        val match = hotkeyRegexBig.find(message)
            ?: hotkeyRegexMedium.find(message)
            ?: hotkeyRegexMediumV2.find(message)
            ?: hotkeyRegexSmall.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #unhotkey - не смог распарсить. Правильный синтаксис: #unhotkey {комбинация клавиш} {команда} {приоритет} {группа}.")
            return
        }
        val groups = match.groups

        val entireCommand = match.value
        val hotkeyString = groups["hotkey"]!!.value
        val action = groups["action"]!!.value
        val priority = groups.getOrNull("priority")?.value?.toIntOrNull() ?: 5
        val groupName = groups.getOrNull("group")?.value?.uppercase() ?: "SESSION"

        logger.debug { "Parsed trigger: $entireCommand" }
        logger.debug { "Shorthand: $hotkeyString" }
        logger.debug { "Action: $action" }
        logger.debug { "Priority: $priority" }
        logger.debug { "Group: $groupName" }

        var removedHotkey = false
        if (groupName == "SESSION") {
            removedHotkey = scriptingEngine.removeHotkeyFromGroup(hotkeyString, action, priority, groupName)
            if (isGroupActive(groupName))
                scriptingEngine.sortHotkeysByPriority()
        } else {
            profileManager.gameWindows.value.values.forEach { profile ->
                removedHotkey = profile.scriptingEngine.removeHotkeyFromGroup(hotkeyString, action, priority, groupName)
                if (profile.isGroupActive(groupName))
                    profile.scriptingEngine.sortHotkeysByPriority()
            }
        }

        if (groupName != "SESSION")
            textMacrosManager.deleteHotkey(hotkeyString, action, groupName, priority)

        if (removedHotkey)
            mainViewModel.displaySystemMessage("Хоткей успешно удален.")
        else
            mainViewModel.displaySystemMessage("Хоткей не найден.")
    }

    fun printAllHotkeys(message: String) {
        val aliasesRegex = """\#hotkeys\s*[{]?([\p{L}\p{N}_]+)?[}]?""".toRegex()
        val match = aliasesRegex.find(message)
        var printAllGroups = true
        var groupToPrint = ""
        if (match != null) {
            if (match.groupValues.size > 1) {
                groupToPrint = match.groupValues[1]
                printAllGroups = false
            }
        } else {
            mainViewModel.displayErrorMessage("Ошибка #hotkeys - не смог распарсить. Правильный синтаксис: #hotkeys {имя группы} (опционально).")
            return
        }

        for ((groupName, hotkeyList) in scriptingEngine.getHotkeys()) {
            if (hotkeyList.isEmpty()) continue
            if (!printAllGroups && groupToPrint.isNotEmpty() && !groupName.equals(groupToPrint, true)) continue
            if (isGroupActive(groupName))
                mainViewModel.displayTaggedText("Группа: <color=green>$groupName</color>")
            else
                mainViewModel.displayTaggedText("Группа: <color=yellow>$groupName</color>")

            for (hotkey in hotkeyList.sortedBy { it.priority }) {
                mainViewModel.displayTaggedText("Hotkey: {${hotkey.keyString}} {${hotkey.actionText}} {${hotkey.priority}} {$groupName}")
            }
        }
    }

    private fun printZonesForLevel(message: String) {
        val zonesRegex = """\#zones (\d+)""".toRegex()
        val match = zonesRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #zones - не смог распарсить. Правильный синтаксис: #zones уровень.")
            return
        }
        val level = match.groupValues[1].toInt()
        val zones = mapModel.getZonesForLevel(level)
        if (zones.isNotEmpty())
            mainViewModel.displayTaggedText("<color=green>Номер Название                       Уровни</color>")
        mainViewModel.displayTaggedText("-----------------------------------------------------------------------", false)
        zones.forEach { zone ->
            mainViewModel.displayTaggedText(String.format(
                "%-6s%-30s%5s",
                zone.id.toString(),
                zone.name,
                "${zone.minLevel}-${zone.maxLevel}${if (zone.solo != null) ", ${if (zone.solo == true) "соло" else "группы"}" else ""}"
            ), false)
        }
        mainViewModel.displayTaggedText("Всего подходящих зон: ${zones.size}", false)
    }

    private fun pathfind(message: String) {
        val zonesRegex = """\#path (?:(?<roomId>\d+)|(?<zoneName>.*))""".toRegex()
        val match = zonesRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #path - не смог распарсить. Правильный синтаксис: #path номер комнаты или #path имя зоны")
            return
        }
        val groups = match.groups

        mapViewModel.resetPathfinding()

        var targetRoomId = -1
        var targetZoneId = -1
        if (groups["roomId"] != null) {
            targetRoomId = groups["roomId"]!!.value.toInt()
        } else {
            val foundZones = mapModel.findZonesByName(groups["zoneName"]!!.value.trim())
            if (foundZones.isEmpty()) {
                mainViewModel.displayTaggedText("Вы крутили карту и так и сяк, но не нашли такую локацию.", false)
                return
            } else if (foundZones.size > 1) {
                mainViewModel.displayTaggedText("Похожих локаций на карте несколько: ${foundZones.joinToString { it.name }}", false)
                return
            } else {
                targetZoneId = foundZones.first().id
                targetRoomId = foundZones.first().roomsList.first().id
            }
        }

        val currentRoomId = mapViewModel.getCurrentRoom().roomId

        scopeDefault.launch {
            val path = mapModel.findPath(currentRoomId, targetRoomId)
            val transitions = path.size - 1
            if (transitions > 0) {
                val transitionWord = getCorrectTransitionWord(transitions)
                mainViewModel.displayTaggedText("Вы отыскали маршрут, путь займет $transitions $transitionWord.", false)
                // set targetRoomId, targetZoneId and path in MapViewModel here
                // set path to highlight in MapViewModel here too
                mapViewModel.setPathTarget(targetRoomId, targetZoneId, path)
            } else if (transitions == -1) {
                mainViewModel.displayTaggedText("Вы крутили карту и так и сяк, но не нашли путь.", false)
            } else { // if transitions == 0
                mainViewModel.displayTaggedText("Вы смотрите на карту, потом на местность; снова на карту, опять на местность. Ах, да вы же уже и так здесь!", false)
            }
        }
    }

    private fun previewZone(message: String) {
        val previewRegex = """\#previewZone (?:(?<zoneId>\d+)|(?<zoneName>.*))""".toRegex()
        val match = previewRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #previewZone - не смог распарсить. Правильный синтаксис: #path номер комнаты или #previewZone имя зоны")
            return
        }
        val groups = match.groups

        var targetZoneId = -100
        if (groups["zoneId"] != null) {
            targetZoneId = groups["zoneId"]!!.value.toInt()
        } else {
            val foundZones = mapModel.findZonesByName(groups["zoneName"]!!.value.trim())
            if (foundZones.isEmpty()) {
                mainViewModel.displayTaggedText("Вы крутили карту и так и сяк, но не нашли такую локацию.", false)
                return
            } else if (foundZones.size > 1) {
                mainViewModel.displayTaggedText("Похожих локаций на карте несколько: ${foundZones.joinToString { it.name }}", false)
                return
            } else {
                targetZoneId = foundZones.first().id
            }
        }

        mapViewModel.previewZone(targetZoneId)
    }

    private fun parseTextSubstitute(message: String) {
        val subRegexBig = """\#sub(?<isRegex>reg)? \{(?<condition>.+?)} \{(?<action>.+)} \{(?<priority>\d+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val subRegexMedium = """\#sub(?<isRegex>reg)? \{(?<condition>.+?)} \{(?<action>.+)} \{(?<priority>\d+)}$""".toRegex()
        val subRegexMediumV2 = """\#sub(?<isRegex>reg)? \{(?<condition>.+?)} \{(?<action>.+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val subRegexSmall = """\#sub(?<isRegex>reg)? \{(?<condition>.+?)} \{(?<action>.+)}$""".toRegex()

        val match = subRegexBig.find(message)
            ?: subRegexMedium.find(message)
            ?: subRegexMediumV2.find(message)
            ?: subRegexSmall.find(message)
        if (match == null) {
            if (message.startsWith("#subreg"))
                mainViewModel.displayErrorMessage("Ошибка #subreg - не смог распарсить. Правильный синтаксис: #subreg {регекс} {желаемая строка} {приоритет} {группа}.")
            else
                mainViewModel.displayErrorMessage("Ошибка #sub - не смог распарсить. Правильный синтаксис: #sub {изначальная строка} {желаемая строка} {приоритет} {группа}.")
            return
        }
        val groups = match.groups

        val entireCommand = match.value
        val isRegex = groups["isRegex"] != null
        val condition = groups["condition"]!!.value
        val action = groups["action"]!!.value
        val priority = groups.getOrNull("priority")?.value?.toIntOrNull() ?: 5
        val groupName = groups.getOrNull("group")?.value?.uppercase() ?: "SESSION"

        logger.debug { "Parsed substitute: $entireCommand" }
        logger.debug { "Is regex: $isRegex" }
        logger.debug { "Condition: $condition" }
        logger.debug { "Action: $action" }
        logger.debug { "Priority: $priority" }
        logger.debug { "Group: $groupName" }

        val newSub =
            if (isRegex) Trigger.subRegCreate(condition, action, priority, false)
            else Trigger.subCreate(condition, action, priority, false)

        // SESSION is the magic keyword. SESSION triggers only apply to current window, not to all windows.
        // They're not saved to any file, so they're transient.
        // The "SESSION" group always exists and is enabled at every launch of the program, even if it had been manually disabled before.
        if (groupName == "SESSION") {
            scriptingEngine.addSubstituteToGroup(groupName, newSub)
            if (isGroupActive(groupName))
                scriptingEngine.sortSubstitutesByPriority()
        }
        else {
            profileManager.gameWindows.value.values.forEach { profile ->
                profile.scriptingEngine.addSubstituteToGroup(groupName, newSub)
                if (profile.isGroupActive(groupName))
                    profile.scriptingEngine.sortSubstitutesByPriority()
            }
        }

        if (groupName != "SESSION")
            textMacrosManager.saveTextSub(condition, action, groupName, priority, isRegex)

        mainViewModel.displaySystemMessage("Замена добавлена в группу {$groupName}.")

        if (!profileData.value.enabledGroups.contains(groupName)) {
            mainViewModel.displayTaggedText("Группа {$groupName} <color=yellow>выключена</color>. Чтобы включить, наберите #group enable {$groupName}.")
        }
    }

    private fun parseRemoveSubstitute(message: String) {
        val subRegexBig = """\#unsub(?<isRegex>reg)? \{(?<condition>.+?)} \{(?<action>.+)} \{(?<priority>\d+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val subRegexMedium = """\#unsub(?<isRegex>reg)? \{(?<condition>.+?)} \{(?<action>.+)} \{(?<priority>\d+)}$""".toRegex()
        val subRegexMediumV2 = """\#unsub(?<isRegex>reg)? \{(?<condition>.+?)} \{(?<action>.+)} \{(?<group>[\p{L}\p{N}_]+)}$""".toRegex()
        val subRegexSmall = """\#unsub(?<isRegex>reg)? \{(?<condition>.+?)} \{(?<action>.+)}$""".toRegex()

        val match = subRegexBig.find(message)
            ?: subRegexMedium.find(message)
            ?: subRegexMediumV2.find(message)
            ?: subRegexSmall.find(message)
        if (match == null) {
            if (message.startsWith("#unsubreg"))
                mainViewModel.displayErrorMessage("Ошибка #unsubreg - не смог распарсить. Правильный синтаксис: #unsubreg {регекс} {желаемая строка} {приоритет} {группа}.")
            else
                mainViewModel.displayErrorMessage("Ошибка #unsub - не смог распарсить. Правильный синтаксис: #unsub {изначальная строка} {желаемая строка} {приоритет} {группа}.")
            return
        }
        val groups = match.groups

        val entireCommand = match.value
        val isRegex = groups["isRegex"] != null
        val condition = groups["condition"]!!.value
        val action = groups["action"]!!.value
        val priority = groups.getOrNull("priority")?.value?.toIntOrNull() ?: 5
        val groupName = groups.getOrNull("group")?.value?.uppercase() ?: "SESSION"

        var removedSub = false
        if (groupName == "SESSION") {
            removedSub = scriptingEngine.removeSubstituteFromGroup(condition, action, priority, groupName, isRegex)
            if (isGroupActive(groupName))
                scriptingEngine.sortSubstitutesByPriority()
        } else {
            profileManager.gameWindows.value.values.forEach { profile ->
                removedSub = profile.scriptingEngine.removeSubstituteFromGroup(condition, action, priority, groupName, isRegex)
                if (profile.isGroupActive(groupName))
                    profile.scriptingEngine.sortSubstitutesByPriority()
            }
        }

        if (groupName != "SESSION")
            textMacrosManager.deleteTextSub(condition, action, groupName, priority, isRegex)

        if (removedSub)
            mainViewModel.displaySystemMessage("Замена успешно удалена.")
        else
            mainViewModel.displaySystemMessage("Замена не найдена.")
    }

    private fun printAllSubstitutes(message: String) {
        val subsRegex = """\#subs\s*[{]?([\p{L}\p{N}_]+)?[}]?""".toRegex()
        val match = subsRegex.find(message)
        var printAllGroups = true
        var groupToPrint = ""
        if (match != null) {
            if (match.groupValues.size > 1) {
                groupToPrint = match.groupValues[1]
                printAllGroups = false
            }
        } else {
            mainViewModel.displayErrorMessage("Ошибка #subs - не смог распарсить. Правильный синтаксис: #subs {имя группы} (опционально).")
            return
        }

        for ((groupName, subsList) in scriptingEngine.getSubstitutes()) {
            if (subsList.isEmpty()) continue
            if (!printAllGroups && groupToPrint.isNotEmpty() && !groupName.equals(groupToPrint, true)) continue
            if (isGroupActive(groupName))
                mainViewModel.displayTaggedText("Группа: <color=green>$groupName</color>")
            else
                mainViewModel.displayTaggedText("Группа: <color=yellow>$groupName</color>")

            for (sub in subsList.filter { it.withDsl }.sortedBy { it.priority }) {
                val isRegex = sub.condition is RegexCondition
                mainViewModel.displayTaggedText("<color=magenta>DSL</color> ${if (isRegex) "Regex-Sub" else "Sub"}: {${sub.condition.originalPattern}} {${sub.action.originalCommand ?: "-> lambda"}} {${sub.priority}} {$groupName}")
            }

            for (sub in subsList.filter { !it.withDsl }.sortedBy { it.priority }) {
                val isRegex = sub.condition is RegexCondition
                mainViewModel.displayTaggedText("${if (isRegex) "Regex-Sub" else "Sub"}: {${sub.condition.originalPattern}} {${sub.action.originalCommand}} {${sub.priority}} {$groupName}")
            }
        }
    }
}