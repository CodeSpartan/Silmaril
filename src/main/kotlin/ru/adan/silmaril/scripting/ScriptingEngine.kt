package ru.adan.silmaril.scripting

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.misc.Hotkey
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.set
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.text.replace

interface ScriptingEngine {
    // Properties
    val profileName: String
    val mainViewModel: MainViewModel
    val logger: KLogger

    // Methods
    fun addTriggerToGroup(group: String, trigger: Trigger)
    fun addTrigger(trigger: Trigger)
    fun removeTriggerFromGroup(condition: String, action: String, priority: Int, group: String, isRegex: Boolean) : Boolean
    fun addAliasToGroup(group: String, alias: Trigger)
    fun addAlias(alias: Trigger)
    fun removeAliasFromGroup(condition: String, action: String, priority: Int, group: String) : Boolean
    fun addHotkeyToGroup(group: String, hotkey: Hotkey)
    fun removeHotkeyFromGroup(keyString: String, actionText: String, priority: Int, group: String) : Boolean
    fun sendCommand(command: String)
    fun sendAllCommand(command: String)
    fun sendWindowCommand(window: String, command: String)
    fun getVarCommand(varName: String): Variable?
    fun setVarCommand(varName: String, varValue: Any)
    fun unvarCommand(varName: String)
    fun echoCommand(message: String, color: AnsiColor, isBright: Boolean)
    fun sortTriggersByPriority()
    fun sortAliasesByPriority()
    fun sortHotkeysByPriority()
    fun processLine(line: String)
    fun processAlias(line: String) : Pair<Boolean, String?>
    fun processHotkey(keyEvent: KeyEvent) : Boolean
    fun loadScript(scriptFile: File) : Int
    fun getTriggers(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun getAliases(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun getHotkeys(): MutableMap<String, CopyOnWriteArrayList<Hotkey>>
    fun switchWindowCommand(window: String) : Boolean
    fun loreCommand(loreName: String)
    fun commentCommand(comment: String): Boolean
    fun getProfileManager(): ProfileManager
}

open class ScriptingEngineImpl(
    override val profileName: String,
    override val mainViewModel: MainViewModel,
    private val isGroupActive: (String) -> Boolean,
    private val settingsManager: SettingsManager,
    private val profileManager: ProfileManager,
    private val loreManager: LoreManager,
) : ScriptingEngine {
    companion object {
        val jvmHost by lazy { BasicJvmScriptingHost() }
    }

    override val logger = KotlinLogging.logger {}
    // @TODO: let triggers add/remove triggers. Currently that would throw an error, since they're matched against in the for loop.
    // CopyOnWrite is a thread-safe list
    private val triggers : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var triggersByPriority = listOf<Trigger>()

    private val aliases : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var aliasesByPriority = listOf<Trigger>()

    private val hotkeys : MutableMap<String, CopyOnWriteArrayList<Hotkey>> = mutableMapOf() // key is GroupName
    private var hotkeysByPriority = listOf<Hotkey>()

    private var currentlyLoadingScript = ""
    private val regexContainsPercentPatterns = Regex("""%\d+""")

    override fun addTriggerToGroup(group: String, trigger: Trigger) {
        if (!triggers.containsKey(group)) {
            triggers[group] = CopyOnWriteArrayList<Trigger>()
        }
        triggers[group]!!.add(trigger)
    }

    override fun addTrigger(trigger: Trigger) {
        val group = currentlyLoadingScript
        if (!triggers.containsKey(group)) {
            triggers[group] = CopyOnWriteArrayList<Trigger>()
        }
        triggers[group]!!.add(trigger)
    }

    override fun removeTriggerFromGroup(condition: String, action: String, priority: Int, group: String, isRegex: Boolean) : Boolean {
        return triggers[group]?.removeIf {
            !it.withDsl
            && (it.condition is RegexCondition) == isRegex
            && it.priority == priority
            && it.action.originalCommand == action
            && it.condition.originalPattern == condition
        } == true
    }

    override fun addAlias(alias: Trigger) {
        val group = currentlyLoadingScript
        if (!aliases.containsKey(group)) {
            aliases[group] = CopyOnWriteArrayList<Trigger>()
        }
        aliases[group]!!.add(alias)
    }

    override fun addAliasToGroup(group: String, alias: Trigger) {
        if (!aliases.containsKey(group)) {
            aliases[group] = CopyOnWriteArrayList<Trigger>()
        }
        aliases[group]!!.add(alias)
    }

    override fun removeAliasFromGroup(condition: String, action: String, priority: Int, group: String): Boolean {
        return aliases[group]?.removeIf {
            !it.withDsl
            && it.priority == priority
            && it.action.originalCommand == action
            && it.condition.originalPattern == condition
        } == true
    }

    override fun addHotkeyToGroup(group: String, hotkey: Hotkey) {
        if (!hotkeys.containsKey(group)) {
            hotkeys[group] = CopyOnWriteArrayList<Hotkey>()
        }
        hotkeys[group]!!.add(hotkey)
    }

    override fun removeHotkeyFromGroup(keyString: String, actionText: String, priority: Int, group: String) : Boolean {
        return hotkeys[group]?.removeIf {
            it.priority == priority
                && it.actionText == actionText
                && it.keyString == keyString
        } == true
    }

    override fun sendCommand(command: String) {
        mainViewModel.treatUserInput(command)
    }

    override fun sendAllCommand(command: String) {
        profileManager.gameWindows.value.values.forEach { profile -> profile.mainViewModel.treatUserInput(command) }
    }

    override fun sendWindowCommand(window: String, command: String) {
        profileManager.gameWindows.value[window]?.mainViewModel?.treatUserInput(command)
    }

    override fun switchWindowCommand(window: String) : Boolean =
        profileManager.switchWindow(window)

    override fun loreCommand(loreName: String) {
        loreManager.findLoreInFiles(loreName)
    }

    override fun commentCommand(comment: String): Boolean =
        loreManager.commentLastLore(comment)

    override fun getProfileManager(): ProfileManager {
        return profileManager
    }

    override fun getVarCommand(varName: String): Variable? =
        profileManager.gameWindows.value[profileName]?.getVariable(varName)

    override fun setVarCommand(varName: String, varValue: Any) {
        profileManager.gameWindows.value[profileName]?.setVariable(varName, varValue)
    }

    override fun unvarCommand(varName: String) {
        profileManager.gameWindows.value[profileName]?.removeVariable(varName)
    }

    override fun echoCommand(message: String, color: AnsiColor, isBright: Boolean) {
        mainViewModel.displayColoredMessage(message, color, isBright)
    }

    override fun sortTriggersByPriority() {
        triggersByPriority = triggers.filter { isGroupActive(it.key) }.values.flatten().sortedBy { it.priority }
    }

    override fun sortAliasesByPriority() {
        aliasesByPriority = aliases.filter { isGroupActive(it.key) }.values.flatten().sortedBy { it.priority }
    }

    override fun sortHotkeysByPriority() {
        hotkeysByPriority = hotkeys.filter { isGroupActive(it.key) }.values.flatten().sortedBy { it.priority }
    }

    /**
     * Checks a line of text from the MUD against all active triggers.
     */
    override fun processLine(line: String)  {
        for (trigger in triggersByPriority) {
            val match = trigger.condition.check(line)
            if (match != null) {
                // Execute the trigger's action if it matches
                trigger.action.lambda.invoke(this, match)
            }
        }
    }

    override fun processAlias(line: String): Pair<Boolean, String?> {
        for (alias in aliasesByPriority) {
            val match = alias.condition.check(line.trim())
            if (match != null) {
                if (alias.action.commandToSend != null) {
                    val returnStr = alias.action.commandToSend.invoke(this, match)

                    // When it's a normal alias (not DSL) without any matching patterns such as %0, %1, etc,
                    // then there's an automatically added (.+) at the end of the condition (see AliasCondition::parsePattern).
                    // But if nothing matches the (.+) in the action, then append " %0" to the action, so that
                    // a simple #al {e} {eat} will allow "e food" -> "eat food"
                    if (alias.action.originalCommand != null) {
                        if (!regexContainsPercentPatterns.containsMatchIn(alias.action.originalCommand))
                            if (1 in match.groupValues.indices)
                                return true to line.replace(match.groupValues[0], returnStr + " " + match.groupValues[1]).trim()
                    }
                    return true to returnStr
                } else {
                    // In DSL, don't return any string. The lambda is supposed to issue its own "sends".
                    alias.action.lambda.invoke(this, match)
                    return true to null
                }
            }
        }
        return false to null
    }

    override fun processHotkey(keyEvent: KeyEvent): Boolean {
        if (!Hotkey.isKeyValid(keyEvent)) return false

        val foundHotkeys = hotkeysByPriority.filter { it.keyboardKey == keyEvent.key
                && it.isAltPressed == keyEvent.isAltPressed
                && it.isCtrlPressed == keyEvent.isCtrlPressed
                && it.isShiftPressed == keyEvent.isShiftPressed
        }

        foundHotkeys.forEach { hotkey ->
            mainViewModel.treatUserInput(hotkey.actionText)
        }

        return foundHotkeys.isNotEmpty()
    }

    /**
     * Loads and executes a user's .kts script file.
     * @return number of triggers loaded
     */
    override fun loadScript(scriptFile: File) : Int {
        //println("[HOST]: Host Classloader: ${this.javaClass.classLoader}")
        //println("[HOST]: Host's ScriptingEngine Interface Classloader: ${ScriptingEngine::class.java.classLoader}")

        logger.info {"[SYSTEM]: Loading and evaluating script ${scriptFile.name}..."}
        currentlyLoadingScript = scriptFile.name.replace(".mud.kts", "").uppercase()
        settingsManager.addGroup(currentlyLoadingScript)

        try {
            val host = jvmHost
            val compilationConfig = MudScriptDefinition
            val evaluationConfig = ScriptEvaluationConfiguration {
                constructorArgs(this@ScriptingEngineImpl)
            }
            val result = host.eval(scriptFile.toScriptSource(), compilationConfig, evaluationConfig)

            // Process any resulting errors or warnings.
            result.reports.forEach { report ->
                if (report.severity >= ScriptDiagnostic.Severity.WARNING) {
                    val location = report.location?.let { "at line ${it.start.line}, col ${it.start.col}" } ?: ""
                    logger.warn { "[SCRIPT]: ${report.message} $location" }
                    echoCommand("[$scriptFile.mud.kts]: ${report.message} $location", AnsiColor.Red, true)
                }
            }

            val triggersLoaded = result.reports.filter { report -> report.severity < ScriptDiagnostic.Severity.ERROR }.size
            logger.info { "Triggers loaded: $triggersLoaded" }
            return triggersLoaded

        } catch (e: Exception) {
            logger.error(e) { "An exception occurred while setting up the script engine." }
            return 0
        }
    }

    override fun getTriggers() = triggers

    override fun getAliases() = aliases

    override fun getHotkeys() = hotkeys

    /** Private methods **/

}