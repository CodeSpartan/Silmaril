package ru.adan.silmaril.scripting
import ru.adan.silmaril.misc.*


interface TriggerCondition {
    /**
     * Checks if the line of text meets this condition.
     * @return A `MatchResult` if the condition is met, `null` otherwise.
     */
    fun check(line: String): MatchResult?
}

// a condition of act.Regex()
class RegexCondition(private val regex: Regex) : TriggerCondition {
    override fun check(line: String): MatchResult? = regex.find(line)
}

// a condition of act.Simple()
class SimpleCondition(private val textToMatch: String) : TriggerCondition {
    private val regex: Regex = getRegex()

    private fun getRegex() : Regex {
        // if simple pattern is prefixed by ^, we prefix the regex with ^ and remove it from textToMatch
        // if simple pattern is postfixed by $, same thing
        // and then, escape the input to treat it as literal string
        var escapedText: String = textToMatch
        var startsWith = false
        if (escapedText.startsWith('^')) {
            escapedText = escapedText.removePrefix("^")
            startsWith = true
        }

        var endsWith = false
        if (escapedText.endsWith('$')) {
            escapedText = escapedText.removeSuffix("$")
            endsWith = true
        }

        escapedText = Regex.escape(escapedText)

        val start = if (startsWith) "^" else ""
        val end = if (endsWith) "$" else ""

        return "$start$escapedText$end".toRegex()
    }

    override fun check(line: String): MatchResult? = regex.find(line)
}

data class Trigger(
    val condition: TriggerCondition,
    val action: ScriptingEngine.(match: MatchResult) -> Unit
)

// By making these extension functions on ScriptingEngine, they can only be called
// from within the script's context, where the engine is the 'this' receiver.

/**
 * A factory class that provides the `on.Regex(...)` and `on.Simple(...)` syntax.
 * It holds a reference to the ScriptingEngine to add the triggers it builds.
 */
class TriggerBuilder(private val engine: ScriptingEngine) {

    /**
     * The new DSL function for creating Regex-based triggers.
     */
    fun Regex(
        patternString: String,
        action: ScriptingEngine.(match: MatchResult) -> Unit
    ) {
        val condition = RegexCondition(patternString.toRegex())
        val newTrigger = Trigger(condition, action)
        engine.addTrigger(newTrigger)
        println("[SYSTEM]: Registered Regex trigger for pattern: $patternString")
    }

    /**
     * The new DSL function for creating Simple-match triggers.
     */
    fun Simple(
        textToMatch: String,
        action: ScriptingEngine.(match: MatchResult) -> Unit
    ) {
        val condition = SimpleCondition(textToMatch)
        val newTrigger = Trigger(condition, action)
        engine.addTrigger(newTrigger)
        println("[SYSTEM]: Registered Simple trigger for text: '$textToMatch'")
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
    echoToMainWindow(message, color, isBright)
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