package ru.adan.silmaril.scripting
import ru.adan.silmaril.misc.*

/**
 * All functions in this file are API that can be called from DSL scripts
 */

// Because act and grep are defined inside MudScriptHost, they have access to
// the outer `this` (the ScriptingEngine instance) to add the trigger.
// We qualify `this@MudScriptHost` to be explicit.
abstract class MudScriptHost(engine: ScriptingEngine) : ScriptingEngine by engine {

    // The function that allows writing in DSL: "condition" grep { match -> send("action") }
    infix fun String.grep(action: (Map<Int, String>) -> Unit) {
        val newTrigger = Trigger.regCreate(this, action)
        this@MudScriptHost.addTrigger(newTrigger)
        println("Added regex lambda trigger for pattern: $this")
    }

    // The function that allows writing in DSL: "condition" grep "action"
    infix fun String.grep(textAction: String) {
        val newTrigger = Trigger.regCreate(this, textAction, 5)
        this@MudScriptHost.addTrigger(newTrigger)
        println("Added regex command trigger for pattern: $this")
    }

    // The function that allows writing in DSL: "condition" act { match -> send("action") }
    infix fun String.act(action: (Map<Int, String>) -> Unit) {
        val newTrigger = Trigger.create(this, action)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug {"Added lambda trigger for pattern: $this"}
    }

    // The function that allows writing in DSL: "condition" act "action"
    infix fun String.act(textAction: String) {
        val newTrigger = Trigger.create(this, textAction, 5)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug {"Added simple trigger for pattern: $this"}
    }
}

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

fun ScriptingEngine.send(command: String) {
    sendCommand(command)
}

fun ScriptingEngine.echo(message: String, color: AnsiColor = AnsiColor.None, isBright: Boolean = false) {
    echoCommand(message, color, isBright)
}

fun ScriptingEngine.sendAll(command: String) {
    sendAllCommand(command)
}

fun ScriptingEngine.sendWindow(window: String, command: String) {
    sendWindowCommand(window, command)
}

fun ScriptingEngine.getVar(varName: String): Variable? {
    return getVarCommand(varName)
}

fun ScriptingEngine.setVar(varName: String, varValue: Any) {
    setVarCommand(varName, varValue)
}

fun ScriptingEngine.unVar(varName: String) {
    unvarCommand(varName)
}