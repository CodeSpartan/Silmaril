package scripting

import viewmodel.MainViewModel
import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class ScriptingEngine(
    // The engine needs a way to interact with the game client (e.g. send data to MUD), which MainViewModel will help with
    val mainViewModel: MainViewModel
) {
    // @TODO: let triggers add/remove triggers. Currently that would throw an error in the for loop, probably.
    private val triggers = mutableListOf<Trigger>()

    fun addTrigger(trigger: Trigger) {
        triggers.add(trigger)
    }

    fun sendCommand(command: String) {
        mainViewModel.sendMessage(command)
    }

    /**
     * Checks a line of text from the MUD against all active triggers.
     */
    fun processLine(line: String) : Boolean {
        var hideProcessedLine = false
        for (trigger in triggers) {
            val match = trigger.pattern.find(line)
            if (match != null) {
                // Execute the trigger's action if it matches
                trigger.action(match)
                //if (trigger.hideMatchedText) hideProcessedLine = true
                //if (trigger.stopProcess) return hideProcessedLine
            }
        }
        return hideProcessedLine
    }

    /**
     * Loads and executes a user's .kts script file.
     */
    fun loadScript(scriptFile: File) {
        println("[SYSTEM]: Loading script from ${scriptFile.name}...")
        val host = BasicJvmScriptingHost()
        val compilationConfig = ScriptCompilationConfiguration {
            // Here's the magic: we provide the ScriptingEngine instance (this)
            // as an "implicit receiver" to the script. This makes all of the
            // engine's public methods and our DSL extension functions available
            // directly within the script.
            implicitReceivers(ScriptingEngine::class)

            defaultImports(
                "scripting.*", // Imports trigger, echo, send
                "kotlin.text.MatchResult"     // Imports the type for the 'match' parameter in triggers
            )
        }
        val evaluationConfig = ScriptEvaluationConfiguration {
            // Pass the actual instance of the engine to be used as the receiver.
            implicitReceivers(this)
        }

        val result = host.eval(scriptFile.toScriptSource(), compilationConfig, evaluationConfig)

        // Error handling for script loading
        result.reports.forEach { report ->
            if (report.severity > ScriptDiagnostic.Severity.INFO) {
                println("[SCRIPT ERROR]: ${report.message} at ${report.location}")
            }
        }
    }
}