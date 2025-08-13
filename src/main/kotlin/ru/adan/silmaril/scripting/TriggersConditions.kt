package ru.adan.silmaril.scripting

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.text.toRegex

class Trigger(
    val condition: TriggerCondition,
    val action: ScriptingEngine.(match: MatchResult) -> Unit,
    val priority: Int
) {

    companion object {
        public fun create(textCondition: String, textAction: String, priority: Int) : Trigger {
            val condition = SimpleCondition(textCondition)
            val newTrigger = Trigger(condition, { matchResult ->
                var commandToSend = textAction

                // Get the list of captured values (group 0 is the full match, so we skip it)
                val capturedValues = matchResult.groupValues.drop(1)

                // Get the list of placeholder numbers in the order they appeared in the pattern
                val placeholderNumbers = condition.placeholderOrder

                // For each captured value, find its corresponding placeholder number and replace it
                placeholderNumbers.forEachIndexed { index, placeholderNum ->
                    if (index < capturedValues.size) {
                        val value = capturedValues[index]
                        commandToSend = commandToSend.replace("%$placeholderNum", value)
                    }
                }
                sendCommand(commandToSend)
            }, priority)
            return newTrigger
        }

        public fun create(textCondition: String, action: (Map<Int, String>) -> Unit) : Trigger {
            val condition = SimpleCondition(textCondition)
            val newTrigger = Trigger(condition, { matchResult ->
                // Get the placeholder numbers and the captured string values
                val placeholderNumbers = condition.placeholderOrder
                val capturedValues = matchResult.groupValues.drop(1)

                // Create the map by "zipping" the two lists together.
                // This elegantly maps each placeholder number to its corresponding captured value.
                val match = placeholderNumbers.zip(capturedValues).toMap()

                // Execute the user's lambda with the prepared map
                action(match)
            }, 5)
            return newTrigger
        }

        /**
         * Creates a trigger from a regular expression that executes a lambda.
         * The lambda receives a map where the key is the 1-based group index and the value is the captured string.
         */
        public fun regCreate(textCondition: String, action: (Map<Int, String>) -> Unit) : Trigger  {
            val condition = RegexCondition(textCondition)
            return Trigger(condition, { matchResult ->
                // Create the map for the user.
                // Key is the 1-based group index (1, 2, 3...).
                // Value is the captured string.
                val matchMap = matchResult.groupValues
                    .drop(1) // Drop the full match at index 0
                    .mapIndexed { index, value -> (index + 1) to value }
                    .toMap()

                action(matchMap)
            }, 5)
        }

        /**
         * Creates a trigger from a regular expression that executes a simple text command.
         * Placeholders %0, %1, %2, etc. in the `textAction` string will be replaced by the corresponding captured group.
         * %0 = first group, %1 = second group, and so on.
         */
        public fun regCreate(textCondition: String, textAction: String, priority: Int) : Trigger {
            val condition = RegexCondition(textCondition)
            return Trigger(condition, { matchResult ->
                var commandToSend = textAction
                val capturedGroups = matchResult.groupValues.drop(1) // Drop full match

                // Replace %0, %1, etc., with the captured group values
                capturedGroups.forEachIndexed { index, value ->
                    commandToSend = commandToSend.replace("%$index", value)
                }

                sendCommand(commandToSend)
            }, priority)
        }
    }
}

// interface for RegexCondition and SimpleCondition
interface TriggerCondition {
    fun check(line: String): MatchResult?
}

// a condition of the "grep" verb in DSL
class RegexCondition(pattern: String) : TriggerCondition {
    private val regex: Regex = try {
        pattern.toRegex()
    } catch (e: PatternSyntaxException) {
        println("ERROR: Invalid regex pattern provided to 'grep': '$pattern'. ${e.message}")
        "a^".toRegex() // Return a regex that will never match anything.
    }
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