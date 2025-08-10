package ru.adan.silmaril.scripting

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
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.model.SettingsManager

class ScriptingEngine(
    val profileName: String,
    val mainViewModel: MainViewModel,
    val isGroupActive: (String) -> Boolean,
    val settingsManager: SettingsManager,
    private val profileManager: ProfileManager
) {
    val logger = KotlinLogging.logger {}
    // @TODO: let triggers add/remove triggers. Currently that would throw an error, since they're matched against in the for loop.
    // CopyOnWrite is a thread-safe list
    private val triggers : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf()
    private var currentlyLoadingScript = ""

    fun addTrigger(trigger: Trigger) {
        val group = currentlyLoadingScript
        if (!triggers.containsKey(group)) {
            triggers[group] = CopyOnWriteArrayList<Trigger>()
        }
        triggers[group]!!.add(trigger)
    }

    fun sendCommand(command: String) {
        mainViewModel.treatUserInput(command)
    }

    fun sendAllCommand(command: String) {
        profileManager.gameWindows.value.values.forEach { profile -> profile.mainViewModel.treatUserInput(command) }
    }

    fun sendWindowCommand(window: String, command: String) {
        profileManager.gameWindows.value[window]?.mainViewModel?.treatUserInput(command)
    }

    fun getVarCommand(varName: String): Variable? {
        return profileManager.gameWindows.value[profileName]?.getVariable(varName)
    }

    fun setVarCommand(varName: String, varValue: Any) {
        profileManager.gameWindows.value[profileName]?.setVariable(varName, varValue)
    }

    fun echoToMainWindow(message: String, color: AnsiColor, isBright: Boolean) {
        mainViewModel.displayColoredMessage(message, color, isBright)
    }

    /**
     * Checks a line of text from the MUD against all active triggers.
     */
    fun processLine(line: String)  {
        for ((groupName, triggerList) in triggers) {
            if (isGroupActive(groupName)) {
                for (trigger in triggerList) {
                    val match = trigger.pattern.find(line)
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
    fun loadScript(scriptFile: File) : Int {
        //println("[HOST]: ScriptingEngine instance is loaded by: ${this.javaClass.classLoader}")

        logger.info {"[SYSTEM]: Loading and evaluating script ${scriptFile.name}..."}
        currentlyLoadingScript = scriptFile.name.replace(".mud.kts", "").uppercase()
        settingsManager.addGroup(currentlyLoadingScript)

        try {
            // 1. Use the low-level host for direct control.
            val host = BasicJvmScriptingHost()

            // 2. Define the compilation configuration.
            val compilationConfig = MudScriptDefinition

            // 3. Define the evaluation configuration.
            val evaluationConfig = ScriptEvaluationConfiguration {
                // Provide the actual 'this' instance for the script to use.
                implicitReceivers(this@ScriptingEngine)
            }

            // 4. Evaluate the script.
            // The host will use our compilation config (solving 'Unresolved reference')
            // and our evaluation config (providing the 'this' instance).
            val result = host.eval(scriptFile.toScriptSource(), compilationConfig, evaluationConfig)

            // 5. Process any resulting errors or warnings.
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
}