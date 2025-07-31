package ru.adan.silmaril.scripting

data class Trigger(
    val pattern: Regex,
    val action: (match: MatchResult) -> Unit,
    //val stopProcess: Boolean,
    //val hideMatchedText: Boolean,
)

// By making these extension functions on ScriptingEngine, they can only be called
// from within the script's context, where the engine is the 'this' receiver.

/**
 * DSL function to define a trigger.
 * @param pattern The regex pattern to match.
 * @param action The block of code to run on a match.
 */
fun ScriptingEngine.trigger(
    pattern: String,
    action: (match: MatchResult) -> Unit,
    //stopProcess: Boolean, // @TODO: can these be made part of the action code?
    //hideMatchedText: Boolean // @TODO: can these be made part of the action code?
) {
    val trigger = Trigger(pattern.toRegex(), action, /*stopProcess, hideMatchedText*/)
    this.addTrigger(trigger)
    println("[SYSTEM]: Registered trigger for pattern: $pattern")
}

/**
 * DSL function to send a command to the MUD.
 */
fun ScriptingEngine.send(command: String) {
    this.sendCommand(command)
}

/**
 * DSL function to print text to the client's local console (not sent to the MUD).
 */
fun ScriptingEngine.echo(message: String) {
    println("[ECHO]: $message")
}