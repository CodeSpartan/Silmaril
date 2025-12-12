package ru.adan.silmaril.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.ProfileData
import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.misc.currentTime
import ru.adan.silmaril.misc.evaluateMathExpressions
import ru.adan.silmaril.misc.toVariable
import ru.adan.silmaril.platform.createLogger
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.scripting.ScriptingEngineImpl
import ru.adan.silmaril.scripting.TransientScriptData
import ru.adan.silmaril.scripting.Trigger
import ru.adan.silmaril.viewmodel.MainViewModel
import ru.adan.silmaril.viewmodel.MapViewModel

/**
 * Android implementation of ProfileInterface.
 * Creates and manages all models for a single game profile.
 */
class AndroidProfile(
    override val profileName: String,
    private val settingsManager: AndroidSettingsManager,
    private val mapModel: MapModel,
    private val outputWindowModel: OutputWindowModel,
) : KoinComponent, ProfileInterface {

    private val logger = createLogger("AndroidProfile")

    // Inject dependencies
    private val loreManager: LoreManager by inject()
    private val settingsProvider: SettingsProvider by inject()

    // Create components directly to avoid Koin parameter casting issues with lambdas
    override val client: MudConnection by lazy {
        MudConnection(
            host = settingsManager.settings.value.gameServer,
            port = settingsManager.settings.value.gamePort,
            profileName = profileName,
            onMessageReceived = { msg: String -> scriptingEngine.processLine(msg) },
            settingsProvider = settingsProvider,
            loreManager = loreManager
        )
    }

    override val mainViewModel: MainViewModel by lazy {
        MainViewModel(
            client = client,
            onSystemMessage = { msg: String, level: Int -> onSystemMessage(msg, level) },
            onInsertVariables = ::onInsertVariables,
            onProcessAliases = { msg: String -> scriptingEngine.processAlias(msg) },
            onMessageReceived = { msg: String -> scriptingEngine.processLine(msg) },
            onRunSubstitutes = { colorfulMsg: ColorfulTextMessage -> scriptingEngine.processSubstitutes(colorfulMsg) },
            loreManager = loreManager,
            settingsProvider = settingsProvider
        )
    }

    override val groupModel: GroupModel by lazy {
        GroupModel(client = client)
    }

    override val mapViewModel: MapViewModel by lazy {
        MapViewModel(
            client = client,
            groupModel = groupModel,
            onDisplayTaggedString = { msg: String -> mainViewModel.displayTaggedText(msg, false) },
            onSendMessageToServer = { msg: String -> mainViewModel.treatUserInput(msg) },
            mapModel = mapModel,
            settingsProvider = settingsProvider
        )
    }

    override val mobsModel: MobsModel by lazy {
        MobsModel(
            client = client,
            onMobsReceived = { newRound: Boolean, mobs: List<Creature> ->
                scriptingEngine.processRound(newRound, groupModel.getGroupMates(), mobs)
            }
        )
    }

    // Android uses the base ScriptingEngineImpl (no .kts script loading)
    override val scriptingEngine: ScriptingEngine by lazy {
        ScriptingEngineImpl(
            profileName = profileName,
            mainViewModel = mainViewModel,
            isGroupActive = ::isGroupActive,
            scriptData = ru.adan.silmaril.scripting.TransientScriptData(),
            settingsProvider = settingsProvider,
            profileManager = settingsManager.let {
                // Get ProfileManager from Koin
                org.koin.java.KoinJavaComponent.get(ProfileManagerInterface::class.java)
            },
            loreManager = loreManager
        )
    }

    val textMacrosManager: TextMacrosManager by inject()

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _profileData = MutableStateFlow(settingsManager.loadProfile(profileName))
    val profileData: StateFlow<ProfileData> = _profileData

    private val _isReadyToConnect = MutableStateFlow(false)
    val isReadyToConnect: StateFlow<Boolean> = _isReadyToConnect

    // Regex to find $variables in user input
    private val insertVarRegex = """(\$[\p{L}\p{N}_]+)""".toRegex()

    init {
        if (!settingsManager.settings.value.gameWindows.contains(profileName)) {
            settingsManager.addGameWindow(profileName)
        }

        scopeDefault.launch {
            logger.info { "AndroidProfile ($profileName): Initialize" }
            // Load text macros (triggers, aliases, etc.)
            textMacrosManager.initExplicit(this@AndroidProfile)
            // Wait for maps to be ready with timeout (maps might fail to load on Android)
            try {
                kotlinx.coroutines.withTimeout(15_000) { // 15 second timeout
                    mapModel.areMapsReady.first { it }
                }
                logger.info { "AndroidProfile ($profileName): Maps loaded successfully" }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                logger.warn { "AndroidProfile ($profileName): Maps failed to load within timeout, continuing without maps" }
                mainViewModel.displaySystemMessage("Карты не загрузились за 15 секунд. Продолжаю без карт.")
            }
            groupModel.init()
            // Connect after triggers loaded
            mainViewModel.initAndConnect()
            _isReadyToConnect.value = true
            logger.info { "AndroidProfile ($profileName): Initialization complete, ready to connect" }
        }

        // Auto-save profile data on changes
        debounceSaveProfile()

        // Observe connection state and report to ProfileManager
        observeConnectionState()
    }

    /**
     * Observes the MudConnection.connectionState and reports changes to ProfileManager.
     * This allows the foreground service to update its notification.
     */
    private fun observeConnectionState() {
        client.connectionState
            .onEach { state ->
                // Get ProfileManager and report connection state
                val profileManager: AndroidProfileManager = org.koin.java.KoinJavaComponent.get(
                    AndroidProfileManager::class.java
                )
                profileManager.updateConnectionState(profileName, state)
            }
            .launchIn(scopeDefault)
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun debounceSaveProfile() {
        _profileData
            .drop(1)
            .debounce(500L)
            .onEach {
                settingsManager.saveProfile(profileName, it)
            }
            .launchIn(scopeDefault)
    }

    // ProfileInterface implementations
    override fun getVariable(varName: String): Variable? {
        return profileData.value.variables[varName]
    }

    override fun setVariable(varName: String, varValue: Any) {
        val newVariable = varValue.toVariable()
        _profileData.value = profileData.value.copy(
            variables = profileData.value.variables + (varName to newVariable)
        )
    }

    override fun removeVariable(varName: String) {
        _profileData.value = profileData.value.copy(
            variables = profileData.value.variables - varName
        )
    }

    override fun isGroupActive(groupName: String): Boolean {
        return profileData.value.enabledGroups.any { it.equals(groupName, ignoreCase = true) }
    }

    override fun addSingleTriggerToWindow(
        condition: String,
        action: String,
        groupName: String,
        priority: Int,
        isRegex: Boolean
    ) {
        val newTrigger = if (isRegex) {
            Trigger.regCreate(condition, action, priority, false)
        } else {
            Trigger.create(condition, action, priority, false)
        }
        scriptingEngine.addTriggerToGroup(groupName, newTrigger)
    }

    override fun addSingleAliasToWindow(
        shorthand: String,
        action: String,
        groupName: String,
        priority: Int
    ) {
        val newAlias = Trigger.createAlias(shorthand, action, priority, false)
        scriptingEngine.addAliasToGroup(groupName, newAlias)
    }

    override fun addSingleSubToWindow(
        shorthand: String,
        action: String,
        groupName: String,
        priority: Int,
        isRegex: Boolean
    ) {
        val newSub = if (isRegex) {
            Trigger.subRegCreate(shorthand, action, priority, false)
        } else {
            Trigger.subCreate(shorthand, action, priority, false)
        }
        scriptingEngine.addSubstituteToGroup(groupName, newSub)
    }

    override fun addSingleHotkeyToWindow(
        keyString: String,
        action: String,
        groupName: String,
        priority: Int
    ) {
        // No-op on Android: keyboard hotkeys are a desktop-only feature.
        // Android uses touch-based UI patterns instead.
    }

    // System command handler
    private fun onSystemMessage(messageUntrimmed: String, recursionLevel: Int = 0) {
        val message = messageUntrimmed.trim()
        when (message.substringBefore(" ")) {
            "#var" -> parseVarCommand(message)
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
            "#sub" -> parseTextSubstitute(message)
            "#subreg" -> parseTextSubstitute(message)
            "#unsub" -> parseRemoveSubstitute(message)
            "#unsubreg" -> parseRemoveSubstitute(message)
            "#subs" -> printAllSubstitutes(message)
            "#echo" -> parseEchoCommand(message)
            "#zap" -> client.forceDisconnect()
            "#conn" -> parseConnectCommand(message)
            "#version" -> printVersion()
            "#hot", "#hotkey", "#unhot", "#unhotkey", "#hotkeys" ->
                mainViewModel.displaySystemMessage("Горячие клавиши не поддерживаются на Android.")
            "#window", "#windowId", "#moveWindow", "#output", "#out", "#send", "#sendId", "#sendAll" ->
                mainViewModel.displaySystemMessage("Многооконность не поддерживается на Android.")
            "#path" -> pathfind(message)
            "#previewZone" -> previewZone(message)
            "#zones" -> printZonesForLevel(message)
            "#lore" -> parseLoreCommand(message)
            "#comment" -> parseCommentCommand(message)
            else -> mainViewModel.displaySystemMessage("Ошибка – неизвестное системное сообщение.")
        }
    }

    // Command parsers
    private fun parseVarCommand(message: String) {
        val varRegex = """\#var [{]?([\p{L}\p{N}_]+)[}]? \{(.+)\}""".toRegex()
        val varRegex2 = """\#var [{]?([\p{L}\p{N}_]+)[}]? (.+)""".toRegex()

        var varName = ""
        var varValue = ""
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
                val varRegex3 = """\#var [{]?([\p{L}\p{N}_]+)[}]?""".toRegex()
                val match3 = varRegex3.find(message)
                if (match3 != null) {
                    varName = match3.groupValues[1]
                } else {
                    mainViewModel.displayErrorMessage("Ошибка #var - не смог распарсить.")
                    return
                }
            }
        }

        if (updatePattern) {
            setVariable(varName, varValue)
        }

        val foundVar = profileData.value.variables[varName]
        if (foundVar != null) {
            val varType = when (foundVar) {
                is Variable.IntValue -> "целое число"
                is Variable.FloatValue -> "число с запятой"
                is Variable.StringValue -> "строка"
            }
            mainViewModel.displaySystemMessage("Переменная $varName = $foundVar ($varType).")
        } else {
            mainViewModel.displaySystemMessage("Переменная $varName не найдена.")
        }
    }

    private fun parseUnvarCommand(message: String) {
        val unvarRegex = """\#unvar [{]?([\p{L}\p{N}_]+)[}]?""".toRegex()
        val match = unvarRegex.find(message)
        if (match != null) {
            val varName = match.groupValues[1]
            if (profileData.value.variables.containsKey(varName)) {
                removeVariable(varName)
                mainViewModel.displaySystemMessage("Переменная $varName удалена.")
            } else {
                mainViewModel.displaySystemMessage("Переменной $varName не было.")
            }
        } else {
            mainViewModel.displayErrorMessage("Ошибка #unvar - не смог распарсить.")
        }
    }

    private fun printAllVarsCommand() {
        profileData.value.variables.forEach { (varName, variable) ->
            val varType = when (variable) {
                is Variable.IntValue -> "целое число"
                is Variable.FloatValue -> "число с запятой"
                is Variable.StringValue -> "строка"
            }
            mainViewModel.displaySystemMessage("Переменная $varName = $variable ($varType).")
        }
    }

    private fun parseGroupCommand(message: String) {
        val groupRegex = """\#group (enable|disable) [{]?([\p{L}\p{N}\-_]+)[}]?""".toRegex()
        val match = groupRegex.find(message)
        if (match != null) {
            val enable = match.groupValues[1]
            val groupName = match.groupValues[2].uppercase()
            if (enable == "enable") {
                _profileData.value = profileData.value.copy(
                    enabledGroups = profileData.value.enabledGroups + groupName
                )
                mainViewModel.displaySystemMessage("Группа $groupName включена.")
            } else {
                _profileData.value = profileData.value.copy(
                    enabledGroups = profileData.value.enabledGroups - groupName
                )
                mainViewModel.displaySystemMessage("Группа $groupName выключена.")
            }
            scriptingEngine.sortTriggersByPriority()
            scriptingEngine.sortAliasesByPriority()
            scriptingEngine.sortSubstitutesByPriority()
            scriptingEngine.sortHotkeysByPriority()
            scriptingEngine.sortRoundTriggersByPriority()
        } else {
            mainViewModel.displayErrorMessage("Ошибка #group - не смог распарсить.")
        }
    }

    private fun printAllGroupsCommand() {
        mainViewModel.displaySystemMessage("Включенные группы: ${profileData.value.enabledGroups.joinToString(", ")}")
    }

    private fun parseTextTrigger(message: String) {
        // Android ICU regex doesn't support named groups, use numbered groups instead
        // Groups: 1=isRegex, 2=condition, 3=action, 4=priority, 5=group
        val actRegexBig = """\#(grep|act) \{(.+?)\} \{(.+)\} \{(\d+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val actRegexMedium = """\#(grep|act) \{(.+?)\} \{(.+)\} \{(\d+)\}$""".toRegex()
        val actRegexMediumV2 = """\#(grep|act) \{(.+?)\} \{(.+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val actRegexSmall = """\#(grep|act) \{(.+?)\} \{(.+)\}$""".toRegex()

        val matchBig = actRegexBig.find(message)
        val matchMedium = actRegexMedium.find(message)
        val matchMediumV2 = actRegexMediumV2.find(message)
        val matchSmall = actRegexSmall.find(message)

        val isRegex: Boolean
        val condition: String
        val action: String
        val priority: Int
        val groupName: String

        when {
            matchBig != null -> {
                isRegex = matchBig.groupValues[1] == "grep"
                condition = matchBig.groupValues[2]
                action = matchBig.groupValues[3]
                priority = matchBig.groupValues[4].toIntOrNull() ?: 5
                groupName = matchBig.groupValues[5].uppercase()
            }
            matchMedium != null -> {
                isRegex = matchMedium.groupValues[1] == "grep"
                condition = matchMedium.groupValues[2]
                action = matchMedium.groupValues[3]
                priority = matchMedium.groupValues[4].toIntOrNull() ?: 5
                groupName = "SESSION"
            }
            matchMediumV2 != null -> {
                isRegex = matchMediumV2.groupValues[1] == "grep"
                condition = matchMediumV2.groupValues[2]
                action = matchMediumV2.groupValues[3]
                priority = 5
                groupName = matchMediumV2.groupValues[4].uppercase()
            }
            matchSmall != null -> {
                isRegex = matchSmall.groupValues[1] == "grep"
                condition = matchSmall.groupValues[2]
                action = matchSmall.groupValues[3]
                priority = 5
                groupName = "SESSION"
            }
            else -> {
                mainViewModel.displayErrorMessage("Ошибка - не смог распарсить. Синтаксис: #act {условие} {команда} {приоритет} {группа}.")
                return
            }
        }

        val newTrigger = if (isRegex) Trigger.regCreate(condition, action, priority, false)
                         else Trigger.create(condition, action, priority, false)

        scriptingEngine.addTriggerToGroup(groupName, newTrigger)
        if (isGroupActive(groupName)) scriptingEngine.sortTriggersByPriority()

        if (groupName != "SESSION") textMacrosManager.saveTextTrigger(condition, action, groupName, priority, isRegex)
        mainViewModel.displaySystemMessage("Триггер добавлен в группу {$groupName}.")

        if (!isGroupActive(groupName)) {
            mainViewModel.displayTaggedText("Группа {$groupName} <color=yellow>выключена</color>. Чтобы включить, наберите #group enable {$groupName}.")
        }
    }

    private fun parseRemoveTrigger(message: String) {
        val actRegexBig = """\#un(grep|act) \{(.+?)\} \{(.+)\} \{(\d+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val actRegexMedium = """\#un(grep|act) \{(.+?)\} \{(.+)\} \{(\d+)\}$""".toRegex()
        val actRegexMediumV2 = """\#un(grep|act) \{(.+?)\} \{(.+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val actRegexSmall = """\#un(grep|act) \{(.+?)\} \{(.+)\}$""".toRegex()

        val matchBig = actRegexBig.find(message)
        val matchMedium = actRegexMedium.find(message)
        val matchMediumV2 = actRegexMediumV2.find(message)
        val matchSmall = actRegexSmall.find(message)

        val isRegex: Boolean
        val condition: String
        val action: String
        val priority: Int
        val groupName: String

        when {
            matchBig != null -> {
                isRegex = matchBig.groupValues[1] == "grep"
                condition = matchBig.groupValues[2]
                action = matchBig.groupValues[3]
                priority = matchBig.groupValues[4].toIntOrNull() ?: 5
                groupName = matchBig.groupValues[5].uppercase()
            }
            matchMedium != null -> {
                isRegex = matchMedium.groupValues[1] == "grep"
                condition = matchMedium.groupValues[2]
                action = matchMedium.groupValues[3]
                priority = matchMedium.groupValues[4].toIntOrNull() ?: 5
                groupName = "SESSION"
            }
            matchMediumV2 != null -> {
                isRegex = matchMediumV2.groupValues[1] == "grep"
                condition = matchMediumV2.groupValues[2]
                action = matchMediumV2.groupValues[3]
                priority = 5
                groupName = matchMediumV2.groupValues[4].uppercase()
            }
            matchSmall != null -> {
                isRegex = matchSmall.groupValues[1] == "grep"
                condition = matchSmall.groupValues[2]
                action = matchSmall.groupValues[3]
                priority = 5
                groupName = "SESSION"
            }
            else -> {
                mainViewModel.displayErrorMessage("Ошибка - не смог распарсить. Синтаксис: #unact {условие} {команда} {приоритет} {группа}.")
                return
            }
        }

        val removed = scriptingEngine.removeTriggerFromGroup(condition, action, priority, groupName, isRegex)
        if (isGroupActive(groupName)) scriptingEngine.sortTriggersByPriority()

        if (groupName != "SESSION") textMacrosManager.deleteTextTrigger(condition, action, groupName, priority, isRegex)
        mainViewModel.displaySystemMessage(if (removed) "Триггер удален." else "Триггер не найден.")
    }

    private fun printAllTriggers(message: String) {
        for ((groupName, trigList) in scriptingEngine.getTriggers()) {
            if (trigList.isEmpty()) continue
            val color = if (isGroupActive(groupName)) "green" else "yellow"
            mainViewModel.displayTaggedText("Группа: <color=$color>$groupName</color>")
            for (trig in trigList.sortedBy { it.priority }) {
                val isRegex = trig.condition is ru.adan.silmaril.scripting.RegexCondition
                mainViewModel.displayTaggedText("${if (isRegex) "Regex" else "Trigger"}: {${trig.condition.originalPattern}} {${trig.action.originalCommand ?: "lambda"}} {${trig.priority}}")
            }
        }
    }

    private fun parseTextAlias(message: String) {
        val aliasRegexBig = """\#al(?:ias)? \{(.+?)\} \{(.+)\} \{(\d+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val aliasRegexMedium = """\#al(?:ias)? \{(.+?)\} \{(.+)\} \{(\d+)\}$""".toRegex()
        val aliasRegexMediumV2 = """\#al(?:ias)? \{(.+?)\} \{(.+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val aliasRegexSmall = """\#al(?:ias)? \{(.+?)\} \{(.+)\}$""".toRegex()

        val matchBig = aliasRegexBig.find(message)
        val matchMedium = aliasRegexMedium.find(message)
        val matchMediumV2 = aliasRegexMediumV2.find(message)
        val matchSmall = aliasRegexSmall.find(message)

        val shorthand: String
        val action: String
        val priority: Int
        val groupName: String

        when {
            matchBig != null -> {
                shorthand = matchBig.groupValues[1]
                action = matchBig.groupValues[2]
                priority = matchBig.groupValues[3].toIntOrNull() ?: 5
                groupName = matchBig.groupValues[4].uppercase()
            }
            matchMedium != null -> {
                shorthand = matchMedium.groupValues[1]
                action = matchMedium.groupValues[2]
                priority = matchMedium.groupValues[3].toIntOrNull() ?: 5
                groupName = "SESSION"
            }
            matchMediumV2 != null -> {
                shorthand = matchMediumV2.groupValues[1]
                action = matchMediumV2.groupValues[2]
                priority = 5
                groupName = matchMediumV2.groupValues[3].uppercase()
            }
            matchSmall != null -> {
                shorthand = matchSmall.groupValues[1]
                action = matchSmall.groupValues[2]
                priority = 5
                groupName = "SESSION"
            }
            else -> {
                mainViewModel.displayErrorMessage("Ошибка - не смог распарсить. Синтаксис: #alias {скоропись} {команда} {приоритет} {группа}.")
                return
            }
        }

        val newAlias = Trigger.createAlias(shorthand, action, priority, false)
        scriptingEngine.addAliasToGroup(groupName, newAlias)
        if (isGroupActive(groupName)) scriptingEngine.sortAliasesByPriority()

        if (groupName != "SESSION") textMacrosManager.saveTextAlias(shorthand, action, groupName, priority)
        mainViewModel.displaySystemMessage("Алиас добавлен в группу {$groupName}.")
    }

    private fun parseRemoveAlias(message: String) {
        val aliasRegexBig = """\#unal(?:ias)? \{(.+?)\} \{(.+)\} \{(\d+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val aliasRegexMedium = """\#unal(?:ias)? \{(.+?)\} \{(.+)\} \{(\d+)\}$""".toRegex()
        val aliasRegexMediumV2 = """\#unal(?:ias)? \{(.+?)\} \{(.+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val aliasRegexSmall = """\#unal(?:ias)? \{(.+?)\} \{(.+)\}$""".toRegex()

        val matchBig = aliasRegexBig.find(message)
        val matchMedium = aliasRegexMedium.find(message)
        val matchMediumV2 = aliasRegexMediumV2.find(message)
        val matchSmall = aliasRegexSmall.find(message)

        val shorthand: String
        val action: String
        val priority: Int
        val groupName: String

        when {
            matchBig != null -> {
                shorthand = matchBig.groupValues[1]
                action = matchBig.groupValues[2]
                priority = matchBig.groupValues[3].toIntOrNull() ?: 5
                groupName = matchBig.groupValues[4].uppercase()
            }
            matchMedium != null -> {
                shorthand = matchMedium.groupValues[1]
                action = matchMedium.groupValues[2]
                priority = matchMedium.groupValues[3].toIntOrNull() ?: 5
                groupName = "SESSION"
            }
            matchMediumV2 != null -> {
                shorthand = matchMediumV2.groupValues[1]
                action = matchMediumV2.groupValues[2]
                priority = 5
                groupName = matchMediumV2.groupValues[3].uppercase()
            }
            matchSmall != null -> {
                shorthand = matchSmall.groupValues[1]
                action = matchSmall.groupValues[2]
                priority = 5
                groupName = "SESSION"
            }
            else -> {
                mainViewModel.displayErrorMessage("Ошибка - не смог распарсить. Синтаксис: #unalias {скоропись} {команда} {приоритет} {группа}.")
                return
            }
        }

        val removed = scriptingEngine.removeAliasFromGroup(shorthand, action, priority, groupName)
        if (isGroupActive(groupName)) scriptingEngine.sortAliasesByPriority()

        if (groupName != "SESSION") textMacrosManager.deleteTextAlias(shorthand, action, groupName, priority)
        mainViewModel.displaySystemMessage(if (removed) "Алиас удален." else "Алиас не найден.")
    }

    private fun printAllAliases(message: String) {
        for ((groupName, aliasList) in scriptingEngine.getAliases()) {
            if (aliasList.isEmpty()) continue
            val color = if (isGroupActive(groupName)) "green" else "yellow"
            mainViewModel.displayTaggedText("Группа: <color=$color>$groupName</color>")
            for (alias in aliasList.sortedBy { it.priority }) {
                mainViewModel.displayTaggedText("Alias: {${alias.condition.originalPattern}} {${alias.action.originalCommand ?: "lambda"}} {${alias.priority}}")
            }
        }
    }

    private fun parseTextSubstitute(message: String) {
        // Android ICU regex doesn't support named groups, use numbered groups instead
        val subRegexBig = """\#sub(?:reg)? \{(.+?)\} \{(.+)\} \{(\d+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val subRegexMedium = """\#sub(?:reg)? \{(.+?)\} \{(.+)\} \{(\d+)\}$""".toRegex()
        val subRegexSmall = """\#sub(?:reg)? \{(.+?)\} \{(.+)\}$""".toRegex()

        val matchBig = subRegexBig.find(message)
        val matchMedium = subRegexMedium.find(message)
        val matchSmall = subRegexSmall.find(message)

        if (matchBig == null && matchMedium == null && matchSmall == null) {
            mainViewModel.displayErrorMessage("Ошибка - не смог распарсить. Синтаксис: #sub {условие} {замена} {приоритет} {группа}.")
            return
        }

        val isRegex = message.startsWith("#subreg")
        val condition: String
        val action: String
        val priority: Int
        val groupName: String

        when {
            matchBig != null -> {
                condition = matchBig.groupValues[1]
                action = matchBig.groupValues[2]
                priority = matchBig.groupValues[3].toIntOrNull() ?: 5
                groupName = matchBig.groupValues[4].uppercase()
            }
            matchMedium != null -> {
                condition = matchMedium.groupValues[1]
                action = matchMedium.groupValues[2]
                priority = matchMedium.groupValues[3].toIntOrNull() ?: 5
                groupName = "SESSION"
            }
            else -> {
                condition = matchSmall!!.groupValues[1]
                action = matchSmall.groupValues[2]
                priority = 5
                groupName = "SESSION"
            }
        }

        val newSub = if (isRegex) Trigger.subRegCreate(condition, action, priority, false)
                     else Trigger.subCreate(condition, action, priority, false)

        scriptingEngine.addSubstituteToGroup(groupName, newSub)
        if (isGroupActive(groupName)) scriptingEngine.sortSubstitutesByPriority()

        if (groupName != "SESSION") textMacrosManager.saveTextSub(condition, action, groupName, priority, isRegex)
        mainViewModel.displaySystemMessage("Подстановка добавлена в группу {$groupName}.")
    }

    private fun parseRemoveSubstitute(message: String) {
        // Android ICU regex doesn't support named groups, use numbered groups instead
        val subRegexBig = """\#unsub(?:reg)? \{(.+?)\} \{(.+)\} \{(\d+)\} \{([\p{L}\p{N}_]+)\}$""".toRegex()
        val subRegexMedium = """\#unsub(?:reg)? \{(.+?)\} \{(.+)\} \{(\d+)\}$""".toRegex()
        val subRegexSmall = """\#unsub(?:reg)? \{(.+?)\} \{(.+)\}$""".toRegex()

        val matchBig = subRegexBig.find(message)
        val matchMedium = subRegexMedium.find(message)
        val matchSmall = subRegexSmall.find(message)

        if (matchBig == null && matchMedium == null && matchSmall == null) {
            mainViewModel.displayErrorMessage("Ошибка - не смог распарсить. Синтаксис: #unsub {условие} {замена} {приоритет} {группа}.")
            return
        }

        val isRegex = message.startsWith("#unsubreg")
        val condition: String
        val action: String
        val priority: Int
        val groupName: String

        when {
            matchBig != null -> {
                condition = matchBig.groupValues[1]
                action = matchBig.groupValues[2]
                priority = matchBig.groupValues[3].toIntOrNull() ?: 5
                groupName = matchBig.groupValues[4].uppercase()
            }
            matchMedium != null -> {
                condition = matchMedium.groupValues[1]
                action = matchMedium.groupValues[2]
                priority = matchMedium.groupValues[3].toIntOrNull() ?: 5
                groupName = "SESSION"
            }
            else -> {
                condition = matchSmall!!.groupValues[1]
                action = matchSmall.groupValues[2]
                priority = 5
                groupName = "SESSION"
            }
        }

        val removed = scriptingEngine.removeSubstituteFromGroup(condition, action, priority, groupName, isRegex)
        if (isGroupActive(groupName)) scriptingEngine.sortSubstitutesByPriority()

        if (groupName != "SESSION") textMacrosManager.deleteTextSub(condition, action, groupName, priority, isRegex)
        mainViewModel.displaySystemMessage(if (removed) "Подстановка удалена." else "Подстановка не найдена.")
    }

    private fun printAllSubstitutes(message: String) {
        for ((groupName, subList) in scriptingEngine.getSubstitutes()) {
            if (subList.isEmpty()) continue
            val color = if (isGroupActive(groupName)) "green" else "yellow"
            mainViewModel.displayTaggedText("Группа: <color=$color>$groupName</color>")
            for (sub in subList.sortedBy { it.priority }) {
                val isRegex = sub.condition is ru.adan.silmaril.scripting.RegexCondition
                mainViewModel.displayTaggedText("${if (isRegex) "SubReg" else "Sub"}: {${sub.condition.originalPattern}} {${sub.action.originalCommand ?: "lambda"}} {${sub.priority}}")
            }
        }
    }

    private fun parseEchoCommand(message: String) {
        val echoText = message.removePrefix("#echo").trim()
        mainViewModel.displayTaggedText(echoText, false)
    }

    private fun parseConnectCommand(message: String) {
        if (client.isConnected) {
            mainViewModel.displaySystemMessage("Уже подключен.")
        } else {
            mainViewModel.initAndConnect()
        }
    }

    private fun printVersion() {
        mainViewModel.displaySystemMessage("Silmaril Android v0.1")
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
        // Try to match room ID first, then zone name
        val roomIdRegex = """\#path (\d+)""".toRegex()
        val zoneNameRegex = """\#path (.+)""".toRegex()

        val roomIdMatch = roomIdRegex.find(message)
        val zoneNameMatch = zoneNameRegex.find(message)

        if (roomIdMatch == null && zoneNameMatch == null) {
            mainViewModel.displayErrorMessage("Ошибка #path - не смог распарсить. Правильный синтаксис: #path номер комнаты или #path имя зоны")
            return
        }

        mapViewModel.resetPathfinding()

        var targetRoomId = -1
        var targetZoneId = -1
        if (roomIdMatch != null) {
            targetRoomId = roomIdMatch.groupValues[1].toInt()
        } else {
            val zoneName = zoneNameMatch!!.groupValues[1].trim()
            val foundZones = mapModel.findZonesByName(zoneName)
            if (foundZones.isEmpty()) {
                mainViewModel.displayTaggedText("Вы крутили карту и так и сяк, но не нашли такую локацию.", false)
                return
            } else if (foundZones.size > 1) {
                mainViewModel.displayTaggedText("Похожих локаций на карте несколько: ${foundZones.joinToString { it.name }}", false)
                return
            } else {
                val zone = foundZones.first()
                targetZoneId = zone.id
                targetRoomId = when (zone.id) {
                    372 -> 5413
                    65 -> 6501
                    47 -> 4791
                    else -> zone.roomsList.first().id
                }
            }
        }

        val currentRoomId = mapViewModel.getCurrentRoom().roomId

        scopeDefault.launch {
            val path = mapModel.findPath(currentRoomId, targetRoomId)
            val transitions = path.size - 1
            if (transitions > 0) {
                mainViewModel.displayTaggedText("Вы отыскали маршрут, путь займет $transitions переходов.", false)
                mapViewModel.setPathTarget(targetRoomId, targetZoneId, path)
            } else if (transitions == -1) {
                mainViewModel.displayTaggedText("Вы крутили карту и так и сяк, но не нашли путь.", false)
            } else {
                mainViewModel.displayTaggedText("Вы уже здесь!", false)
            }
        }
    }

    private fun previewZone(message: String) {
        // Try to match zone ID first, then zone name
        val zoneIdRegex = """\#previewZone (\d+)""".toRegex()
        val zoneNameRegex = """\#previewZone (.+)""".toRegex()

        val zoneIdMatch = zoneIdRegex.find(message)
        val zoneNameMatch = zoneNameRegex.find(message)

        if (zoneIdMatch == null && zoneNameMatch == null) {
            mainViewModel.displayErrorMessage("Ошибка #previewZone - не смог распарсить. Правильный синтаксис: #previewZone номер зоны или #previewZone имя зоны")
            return
        }

        var targetZoneId = -100
        if (zoneIdMatch != null) {
            targetZoneId = zoneIdMatch.groupValues[1].toInt()
        } else {
            val zoneName = zoneNameMatch!!.groupValues[1].trim()
            val foundZones = mapModel.findZonesByName(zoneName)
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

    private fun parseLoreCommand(message: String) {
        val loreRegex = """\#lore ([\p{L}\p{N}_",\-\s]+)$""".toRegex()
        val match = loreRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #lore - не смог распарсить. Правильный синтаксис: #lore имя предмета.")
            return
        }
        val loreName = match.groupValues[1].trimEnd()
        scriptingEngine.loreCommand(loreName)
    }

    private fun parseCommentCommand(message: String) {
        val commentRegex = """\#comment (.*)""".toRegex()
        val match = commentRegex.find(message)
        if (match == null) {
            mainViewModel.displayErrorMessage("Ошибка #comment - не смог распарсить. Правильный синтаксис: #comment ваш комментарий.")
            return
        }
        if (scriptingEngine.commentCommand(match.groupValues[1].trimEnd())) {
            mainViewModel.displayTaggedText("Вы сделали заметку.", false)
        } else {
            mainViewModel.displayTaggedText("Комната не найдена на карте.", false)
        }
    }

    private fun onInsertVariables(input: String): String {
        // First, evaluate $math() expressions (handles nested calls and variables inside)
        val afterMath = evaluateMathExpressions(input) { varName ->
            when (varName) {
                "time" -> currentTime()
                else -> getVariable(varName)?.toString()
            }
        }

        // Then replace remaining $variables
        return insertVarRegex.replace(afterMath) { matchResult ->
            when (matchResult.value) {
                "\$time" -> currentTime()
                else -> {
                    val varName = matchResult.groupValues[1].substring(1) // Remove $
                    getVariable(varName)?.toString() ?: matchResult.value
                }
            }
        }
    }

    fun cleanup() {
        logger.info { "AndroidProfile ($profileName): Cleanup started" }

        // Disconnect from server first
        client.forceDisconnect()

        // Cancel our scope
        scopeDefault.cancel()

        // Cleanup all models
        mainViewModel.cleanup()
        mapViewModel.cleanup()
        groupModel.cleanup()
        mobsModel.cleanup()
        scriptingEngine.cleanup()

        logger.info { "AndroidProfile ($profileName): Cleanup completed" }
    }

    fun onCloseWindow() {
        cleanup()
        settingsManager.removeGameWindow(profileName)
    }
}
