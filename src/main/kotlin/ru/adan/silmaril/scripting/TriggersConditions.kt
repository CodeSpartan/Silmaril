package ru.adan.silmaril.scripting

import java.util.regex.Pattern

data class Trigger(
    val condition: TriggerCondition,
    val action: ScriptingEngine.(match: MatchResult) -> Unit
)

// interface for RegexCondition and SimpleCondition
interface TriggerCondition {
    fun check(line: String): MatchResult?
}

// a condition of the "grep" verb in DSL
class RegexCondition(private val regex: Regex) : TriggerCondition {
    override fun check(line: String): MatchResult? = regex.find(line)
}

/**
 * A condition of the "act" verb in DSL.
* 1. It reads a ^ prefix and treats it like the ^ prefix in regex
* 2. It reads a $ suffix and trats it like the $ suffix in regex
* 3. It treats %0, %1, %2, etc as (.*) in regex, then allows the match to be referenced in the associated action using the same (%0, %1) names
* So for example, a condition "^%0 entered the room" would match "Mob entered the room",
* allowing the user to preconfigure the action "kill %0", which would effectively send "kill Mob" in this case.
 * The code that matches %0 in action to %0 in condition is found in MudScriptHost::String.act
*/
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