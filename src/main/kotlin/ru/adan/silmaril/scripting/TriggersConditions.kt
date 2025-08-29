package ru.adan.silmaril.scripting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.text.toRegex

class Trigger(
    val condition: TriggerCondition,
    val action: TriggerAction,
    val priority: Int,
    val withDsl: Boolean // true when created in DSL
) {

    companion object {
        public fun create(textCondition: String, textAction: String, priority: Int, withDsl: Boolean) : Trigger {
            val condition = SimpleCondition(textCondition)
            val newTrigger = Trigger(condition, TriggerAction(originalCommand = textAction, lambda = { matchResult ->
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
            }), priority, withDsl)
            return newTrigger
        }

        public fun create(textCondition: String, action: (Map<Int, String>) -> Unit) : Trigger {
            val condition = SimpleCondition(textCondition)
            val newTrigger = Trigger(condition, TriggerAction (lambda = { matchResult ->
                // Get the placeholder numbers and the captured string values
                val placeholderNumbers = condition.placeholderOrder
                val capturedValues = matchResult.groupValues.drop(1)

                // Create the map by "zipping" the two lists together.
                // This elegantly maps each placeholder number to its corresponding captured value.
                val match = placeholderNumbers.zip(capturedValues).toMap()

                // Execute the user's lambda with the prepared map
                action(match)
            }), 5, true)
            return newTrigger
        }

        /**
         * Creates a trigger from a regular expression that executes a lambda.
         * The lambda receives a map where the key is the 1-based group index and the value is the captured string.
         */
        public fun regCreate(textCondition: String, action: (Map<Int, String>) -> Unit) : Trigger  {
            val condition = RegexCondition(textCondition)
            return Trigger(condition, TriggerAction(lambda = { matchResult ->
                // Create the map for the user.
                // Key is the 1-based group index (1, 2, 3...).
                // Value is the captured string.
                val matchMap = matchResult.groupValues
                    .drop(0) // Don't drop the full match at index 0
                    .mapIndexed { index, value -> (index) to value }
                    .toMap()

                action(matchMap)
            }), 5, true)
        }

        /**
         * Creates a trigger from a regular expression that executes a simple text command.
         * Placeholders %0, %1, %2, etc. in the `textAction` string will be replaced by the corresponding captured group.
         * %0 = first group, %1 = second group, and so on.
         */
        public fun regCreate(textCondition: String, textAction: String, priority: Int, withDsl: Boolean) : Trigger {
            val condition = RegexCondition(textCondition)
            return Trigger(condition, TriggerAction(originalCommand = textAction, lambda ={ matchResult ->
                var commandToSend = textAction
                val capturedGroups = matchResult.groupValues.drop(0) // Don't drop full match

                // Replace %0, %1, etc., with the captured group values
                capturedGroups.forEachIndexed { index, value ->
                    commandToSend = commandToSend.replace("%$index", value)
                }

                sendCommand(commandToSend)
            }), priority, withDsl)
        }

        public fun createAlias(textCondition: String, action: (Map<Int, String>) -> Unit) : Trigger {
            val condition = AliasCondition(textCondition)
            return Trigger(condition, TriggerAction (originalCommand = null, lambda = { matchResult ->
                // Get the placeholder numbers and the captured string values
                val placeholderNumbers = condition.placeholderOrder
                val capturedValues = matchResult.groupValues.drop(1)

                // Create the map by "zipping" the two lists together.
                // This elegantly maps each placeholder number to its corresponding captured value.
                val match = placeholderNumbers.zip(capturedValues).toMap()

                // Execute the user's lambda with the prepared map
                action(match)
            }), 5, true)
        }

        public fun createAlias(textCondition: String, textAction: String, priority: Int, withDsl: Boolean) : Trigger {
            val condition = AliasCondition(textCondition)
            return Trigger(condition, TriggerAction(originalCommand = textAction, commandToSend = { matchResult ->
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

                commandToSend
            }, lambda = {}), priority, withDsl)
        }

        /**
         * Creates a substitute from a regular expression that executes a lambda.
         * The lambda receives a map where the key is the 1-based group index and the value is the captured string.
         * To return the full match, use %0
         */
        public fun subRegCreate(textCondition: String, action: (Map<Int, String>) -> Unit) : Trigger  {
            val condition = RegexCondition(textCondition)
            return Trigger(condition, TriggerAction(lambda = { matchResult ->
                // Create the map for the user.
                // Key is the 1-based group index (1, 2, 3...).
                // Value is the captured string.
                val matchMap = matchResult.groupValues
                    .drop(0) // Don't drop the full match at index 0
                    .mapIndexed { index, value -> (index) to value }
                    .toMap()

                action(matchMap)
            }), 5, true)
        }

        /**
         * Creates a trigger from a regular expression that executes a simple text command.
         * Regex groups like (.*) in the `textAction` string will be replaced by the corresponding captured group.
         * %0 = full match
         * %1 = first group, %2 = second group, and so on.
         */
        public fun subRegCreate(textCondition: String, textAction: String, priority: Int, withDsl: Boolean) : Trigger {
            val condition = RegexCondition(textCondition)
            return Trigger(condition, TriggerAction(originalCommand = textAction, commandToSend = { matchResult ->
                var commandToSend = textAction
                val capturedGroups = matchResult.groupValues.drop(0) // Don't drop full match

                // Replace %0, %1, etc., with the captured group values
                capturedGroups.forEachIndexed { index, value ->
                    commandToSend = commandToSend.replace("%$index", value)
                }

                commandToSend
            }, lambda = {}), priority, withDsl)
        }

        public fun subCreate(textCondition: String, action: (Map<Int, String>) -> Unit) : Trigger {
            val condition = SubstituteCondition(textCondition)
            val newTrigger = Trigger(condition, TriggerAction (lambda = { matchResult ->
                // Get the placeholder numbers and the captured string values
                val placeholderNumbers = condition.placeholderOrder
                val capturedValues = matchResult.groupValues.drop(0)

                // Create the map by "zipping" the two lists together.
                // This elegantly maps each placeholder number to its corresponding captured value.
                val match = placeholderNumbers.zip(capturedValues).toMap()

                // Execute the user's lambda with the prepared map
                action(match)
            }), 5, true)
            return newTrigger
        }

        /**
         * Creates a trigger from a simple text expression that executes a simple text command.
         * Pattern matches starting with %1 will be replaced by the corresponding captured group.
         * %0 = full match
         * %1 = first group, %2 = second group, and so on.
         */
        public fun subCreate(textCondition: String, textAction: String, priority: Int, withDsl: Boolean) : Trigger {
            val condition = SubstituteCondition(textCondition)
            val newTrigger = Trigger(condition, TriggerAction(originalCommand = textAction, commandToSend = { matchResult ->
                var commandToSend = textAction

                // Get the list of captured values (group 0 is the full match, don't skip it)
                val capturedValues = matchResult.groupValues.drop(0)

                // Get the list of placeholder numbers in the order they appeared in the pattern
                val placeholderNumbers = condition.placeholderOrder

                // For each captured value, find its corresponding placeholder number and replace it
                placeholderNumbers.forEachIndexed { index, placeholderNum ->
                    if (index < capturedValues.size) {
                        val value = capturedValues[index]
                        commandToSend = commandToSend.replace("%$placeholderNum", value)
                    }
                }

                commandToSend
            }, lambda = {}), priority, withDsl)
            return newTrigger
        }
    }
}

class TriggerAction(
    val lambda: ScriptingEngine.(match: MatchResult) -> Unit,
    val originalCommand: String? = null, // optional
    //@TODO: make an interface GenericAction and two classes TriggerAction and AliasAction, since they're quite different
    // for aliases and substitutes only:
    val commandToSend: (ScriptingEngine.(match: MatchResult) -> String)? = null,
)

// interface for RegexCondition and SimpleCondition
interface TriggerCondition {
    val originalPattern: String
    fun check(line: String): MatchResult?
}

// a condition of the "grep" verb in DSL
class RegexCondition(pattern: String) : TriggerCondition {
    override val originalPattern = pattern
    private val regex: Regex = try {
        pattern.toRegex()
    } catch (e: PatternSyntaxException) {
        val logger = KotlinLogging.logger {}
        logger.info { "ERROR: Invalid regex pattern provided to 'grep': '$pattern'. ${e.message}" }
        "a^".toRegex() // Return a regex that will never match anything.
    }
    override fun check(line: String): MatchResult? = regex.find(line)
}

/**
 * A condition of the "act" verb in DSL.
* 1. It reads a ^ prefix and treats it like the ^ prefix in regex
* 2. It reads a $ suffix and treats it like the $ suffix in regex
* 3. It treats %0, %1, %2, etc as (.*) in regex, then allows the match to be referenced in the associated action using the same (%0, %1) names
* So for example, a condition "^%0 entered the room" would match "Mob entered the room",
* allowing the user to preconfigure the action "kill %0", which would effectively send "kill Mob" in this case.
 * The code that matches %0 in action to %0 in condition is found in MudScriptHost::String.act
*/
class SimpleCondition(private val textToMatch: String) : TriggerCondition {
    override val originalPattern = textToMatch
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

class SubstituteCondition(private val textToMatch: String) : TriggerCondition {
    override val originalPattern = textToMatch
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

        val placeholderRegex = Regex("%[1-9]")
        val foundPlaceholders = mutableListOf<Int>(0) // inject %0 as a full match
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

class AliasCondition(private val textToMatch: String) : TriggerCondition {
    override val originalPattern = textToMatch
    val placeholderOrder: List<Int>
    private val regex: Regex

    init {
        val parsingResult = parsePattern()
        this.regex = parsingResult.first
        this.placeholderOrder = parsingResult.second
    }

    private fun parsePattern(): Pair<Regex, List<Int>> {
        var pattern = textToMatch

        val regexBuilder = StringBuilder()
        regexBuilder.append('^')

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

        // if there were no %0, %1, etc, then append one, so that a simple alias always ends with a %0
        // the pattern used here allows optional matching of any sequence, without matching the first space
        if (lastIndex == 0) {
            regexBuilder.append("""(?:\s+(.+))?""")
            foundPlaceholders.add(0)
        }

        regexBuilder.append("$")

        return Pair(regexBuilder.toString().toRegex(), foundPlaceholders)
    }

    override fun check(line: String): MatchResult? = regex.find(line)
}