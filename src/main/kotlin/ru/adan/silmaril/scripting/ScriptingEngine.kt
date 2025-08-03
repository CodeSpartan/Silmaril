package ru.adan.silmaril.scripting

import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class ScriptingEngine(
    // The engine needs a way to interact with the game client (e.g. send data to MUD), which MainViewModel will help with
    val mainViewModel: MainViewModel,
    val profileName: String,
) {
    // @TODO: let triggers add/remove triggers. Currently that would throw an error in the for loop, probably.
    // CopyOnWrite is a thread-safe list
    private val triggers = CopyOnWriteArrayList<Trigger>()
    var timesAskedPassword: Int = 0

    fun addTrigger(trigger: Trigger) {
        triggers.add(trigger)
    }

    fun sendCommand(command: String) {
        mainViewModel.sendMessage(command)
    }

    /**
     * Checks a line of text from the MUD against all active triggers.
     */
    fun processLine(line: String)  {
        for (trigger in triggers) {
            val match = trigger.pattern.find(line)
            if (match != null) {
                // Execute the trigger's action if it matches
                trigger.action.invoke(this, match)
            }
        }
    }

    /**
     * Loads and executes a user's .kts script file.
     * @return number of triggers loaded
     */
    fun loadScript(scriptFile: File) : Int {
        println("[HOST]: ScriptingEngine instance is loaded by: ${this.javaClass.classLoader}")
        println("[SYSTEM]: Loading and evaluating script ${scriptFile.name}...")

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
                    println("[SCRIPT ${report.severity}]: ${report.message} $location")
                }
            }

            val triggersLoaded = result.reports.filter { report -> report.severity < ScriptDiagnostic.Severity.ERROR }.size
            println("Triggers loaded: $triggersLoaded")
            return triggersLoaded

        } catch (e: Exception) {
            println("[SYSTEM ERROR]: An exception occurred while setting up the script engine.")
            e.printStackTrace()
            return 0
        }
    }
}