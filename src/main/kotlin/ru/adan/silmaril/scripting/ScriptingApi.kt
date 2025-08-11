package ru.adan.silmaril.scripting
import ru.adan.silmaril.misc.*

///**
// * DSL function to define an alias.
// * @param pattern The regex pattern to match.
// * @param action The block of code to run on a match.
// */
//fun ScriptingEngine.alias(
//    pattern: String,
//    action: ScriptingEngine.(match: MatchResult) -> Unit
//) {
//    val alias = Alias(pattern.toRegex(), action)
//    this.addAlias(alias)
//    println("[SYSTEM]: Registered alias for pattern: $pattern")
//}

/**
 * DSL function to send a command to the MUD.
 */
fun ScriptingEngine.send(command: String) {
    this.sendCommand(command)
}

/**
 * DSL function to print text to the client's local console (not sent to the MUD).
 */
fun ScriptingEngine.echo(message: String, color: AnsiColor = AnsiColor.None, isBright: Boolean = false) {
    echoCommand(message, color, isBright)
}

/**
 * DSL function to send a command to all windows.
 */
fun ScriptingEngine.sendAll(command: String) {
    this.sendAllCommand(command)
}

/**
 * DSL function to send a command to a specific window.
 */
fun ScriptingEngine.sendWindow(window: String, command: String) {
    this.sendWindowCommand(window, command)
}

fun ScriptingEngine.getVar(varName: String): Variable? {
    return this.getVarCommand(varName)
}

fun ScriptingEngine.setVar(varName: String, varValue: Any) {
    this.setVarCommand(varName, varValue)
}

fun ScriptingEngine.unVar(varName: String) {
    this.unvarCommand(varName)
}