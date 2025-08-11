package ru.adan.silmaril.scripting

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import ru.adan.silmaril.model.SettingsManager

abstract class MudScriptHost(engine: ScriptingEngine) : ScriptingEngine by engine {
    infix fun String.grep(action: ScriptingEngine.(match: MatchResult) -> Unit) {
        // The receiver `this` is the String pattern. Create a RegexCondition from it.
        val condition = RegexCondition(this.toRegex())

        // Create the Trigger object with the condition and the action lambda.
        val newTrigger = Trigger(condition, action)

        // Because this function is defined inside MudScriptHost, it has access to
        // the outer `this` (the ScriptingEngine instance) to add the trigger.
        // We qualify `this@MudScriptHost` to be explicit.
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug { "Added regex trigger: $this" }
    }

    infix fun String.act(action: ScriptingEngine.(match: MatchResult) -> Unit) {
        val condition = SimpleCondition(this)
        val newTrigger = Trigger(condition, action)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug { "Added simple trigger: $this" }
    }

//    infix fun String.act(textCommand: String) {
//        val condition = SimpleCondition(this)
//        val newTrigger = Trigger(condition) { sendCommand(textCommand) }
//        this@MudScriptHost.addTrigger(newTrigger)
//        logger.debug { "Added simple trigger: $this" }
//    }
    infix fun String.act(textCommand: String) {
        val condition = SimpleCondition(this)
        val newTrigger = Trigger(condition) { matchResult ->
            var commandToSend = textCommand
            // The first group (index 0) is the full match.
            // Captured groups start at index 1.
            // We replace %0 with the first captured group, %1 with the second, and so on.
            if (matchResult.groupValues.size > 1) {
                for (i in 1 until matchResult.groupValues.size) {
                    val groupValue = matchResult.groupValues[i]
                    commandToSend = commandToSend.replace("%${i - 1}", groupValue)
                }
            }
            sendCommand(commandToSend) // This would be your actual game command function
            //println("Executing command: $commandToSend") // For demonstration
        }
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug {"Added simple trigger for pattern: $this"}
    }
}

interface ScriptingEngine {
    // Properties
    val profileName: String
    val mainViewModel: MainViewModel
    val logger: KLogger

    // Methods
    fun addTrigger(trigger: Trigger)
    fun sendCommand(command: String)
    fun sendAllCommand(command: String)
    fun sendWindowCommand(window: String, command: String)
    fun getVarCommand(varName: String): Variable?
    fun setVarCommand(varName: String, varValue: Any)
    fun echoToMainWindow(message: String, color: AnsiColor, isBright: Boolean)
    fun processLine(line: String)
    fun loadScript(scriptFile: File) : Int
}

open class ScriptingEngineImpl(
    override val profileName: String,
    override val mainViewModel: MainViewModel,
    private val isGroupActive: (String) -> Boolean,
    private val settingsManager: SettingsManager,
    private val profileManager: ProfileManager
) : ScriptingEngine {
    override val logger = KotlinLogging.logger {}
    // @TODO: let triggers add/remove triggers. Currently that would throw an error, since they're matched against in the for loop.
    // CopyOnWrite is a thread-safe list
    private val triggers : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf()
    private var currentlyLoadingScript = ""

    override fun addTrigger(trigger: Trigger) {
        val group = currentlyLoadingScript
        if (!triggers.containsKey(group)) {
            triggers[group] = CopyOnWriteArrayList<Trigger>()
        }
        triggers[group]!!.add(trigger)
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

    override fun getVarCommand(varName: String): Variable? {
        return profileManager.gameWindows.value[profileName]?.getVariable(varName)
    }

    override fun setVarCommand(varName: String, varValue: Any) {
        profileManager.gameWindows.value[profileName]?.setVariable(varName, varValue)
    }

    override fun echoToMainWindow(message: String, color: AnsiColor, isBright: Boolean) {
        mainViewModel.displayColoredMessage(message, color, isBright)
    }

    /**
     * Checks a line of text from the MUD against all active triggers.
     */
    override fun processLine(line: String)  {
        for ((groupName, triggerList) in triggers) {
            if (isGroupActive(groupName)) {
                for (trigger in triggerList) {
                    val match = trigger.condition.check(line)
                    if (match != null) {
                        // Execute the trigger's action if it matches
                        trigger.action.invoke(this, match)
                    }
                }
            }
        }
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

    /** Private methods **/

}