package ru.adan.silmaril.scripting
import ru.adan.silmaril.misc.*
import java.util.regex.Pattern


// interface for RegexCondition and SimpleCondition
interface TriggerCondition {
    fun check(line: String): MatchResult?
}

// a condition of "grep" verb in DSL
class RegexCondition(private val regex: Regex) : TriggerCondition {
    override fun check(line: String): MatchResult? = regex.find(line)
}

// a condition of "act" verb in DSL
class SimpleCondition(private val textToMatch: String) : TriggerCondition {
    val placeholderOrder: List<Int>
    private val regex: Regex

    init {
        val parsingResult = parsePattern()
        this.regex = parsingResult.first
        this.placeholderOrder = parsingResult.second
    }

    private fun parsePattern(): Pair<Regex, List<Int>> {
        var pattern = textToMatch
        val startsWith = pattern.startsWith('^')
        if (startsWith) {
            pattern = pattern.removePrefix("^")
        }

        val endsWith = pattern.endsWith('$')
        if (endsWith) {
            pattern = pattern.removeSuffix("$")
        }

        val regexBuilder = StringBuilder()
        if (startsWith) {
            regexBuilder.append('^')
        }

        val placeholderRegex = Regex("%[0-9]")
        val foundPlaceholders = mutableListOf<Int>()
        var lastIndex = 0

        placeholderRegex.findAll(pattern).forEach { match ->
            // Append the literal text before the placeholder, correctly escaped
            regexBuilder.append(Pattern.quote(pattern.substring(lastIndex, match.range.first)))
            // Append the regex capturing group
            regexBuilder.append("(.*)")

            // Extract the number from the placeholder (e.g., '1' from "%1") and store it
            val placeholderNumber = match.value.removePrefix("%").toInt()
            foundPlaceholders.add(placeholderNumber)

            lastIndex = match.range.last + 1
        }

        // Append any remaining literal text after the last placeholder
        regexBuilder.append(Pattern.quote(pattern.substring(lastIndex)))

        if (endsWith) {
            regexBuilder.append('$')
        }

        return Pair(regexBuilder.toString().toRegex(), foundPlaceholders)
    }

    override fun check(line: String): MatchResult? = regex.find(line)
}

data class Trigger(
    val condition: TriggerCondition,
    val action: ScriptingEngine.(match: MatchResult) -> Unit
)

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