package ru.adan.silmaril.scripting

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.model.LoreManager
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
    fun sendCommand(command: String)
    fun sendAllCommand(command: String)
    fun sendWindowCommand(window: String, command: String)
    fun getVarCommand(varName: String): Variable?
    fun setVarCommand(varName: String, varValue: Any)
    fun unvarCommand(varName: String)
    fun echoCommand(message: String, color: AnsiColor, isBright: Boolean)
    fun sortTriggersByPriority()
    fun sortAliasesByPriority()
    fun processLine(line: String)
    fun processAlias(line: String) : Pair<Boolean, String?>
    fun loadScript(scriptFile: File) : Int
    fun getTriggers(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun getAliases(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun switchWindowCommand(window: String) : Boolean
    fun loreCommand(loreName: String)
}

open class ScriptingEngineImpl(
    override val profileName: String,
    override val mainViewModel: MainViewModel,
    private val isGroupActive: (String) -> Boolean,
    private val settingsManager: SettingsManager,
    private val profileManager: ProfileManager,
    private val loreManager: LoreManager
) : ScriptingEngine {
    override val logger = KotlinLogging.logger {}
    // @TODO: let triggers add/remove triggers. Currently that would throw an error, since they're matched against in the for loop.
    // CopyOnWrite is a thread-safe list
    private val triggers : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var triggersByPriority = listOf<Trigger>()

    private val aliases : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var aliasesByPriority = listOf<Trigger>()

    private var currentlyLoadingScript = ""

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

    override fun sendCommand(command: String) {
        mainViewModel.treatUserInput(command)
    }

    override fun sendAllCommand(command: String) {
        profileManager.gameWindows.value.values.forEach { profile -> profile.mainViewModel.treatUserInput(command) }
    }

    override fun sendWindowCommand(window: String, command: String) {
        profileManager.gameWindows.value[window]?.mainViewModel?.treatUserInput(command)
    }

    override fun switchWindowCommand(window: String) : Boolean {
        return profileManager.switchWindow(window)
    }

    override fun loreCommand(loreName: String) {
        loreManager.findLoreInFiles(loreName.replace(" ", "_"))
    }

    override fun getVarCommand(varName: String): Variable? {
        return profileManager.gameWindows.value[profileName]?.getVariable(varName)
    }

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
            val match = alias.condition.check(line)
            if (match != null) {
                if (alias.action.commandToSend != null) {
                    val returnStr = alias.action.commandToSend.invoke(this, match)
                    return true to returnStr
                } else {
                    alias.action.lambda.invoke(this, match)
                    return true to null
                }
            }
        }
        return false to null
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
            val host = BasicJvmScriptingHost()
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

    override fun getTriggers(): MutableMap<String, CopyOnWriteArrayList<Trigger>> {
        return triggers
    }

    override fun getAliases(): MutableMap<String, CopyOnWriteArrayList<Trigger>> {
        return aliases
    }

    /** Private methods **/

}