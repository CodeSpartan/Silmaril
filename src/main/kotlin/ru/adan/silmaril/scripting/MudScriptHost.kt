package ru.adan.silmaril.scripting

abstract class MudScriptHost(engine: ScriptingEngine) : ScriptingEngine by engine {

    // The function that allows writing in DSL: "condition" grep { send("action") }
    infix fun String.grep(action: ScriptingEngine.(match: MatchResult) -> Unit) {
        // The receiver `this` is the String pattern. Create a RegexCondition from it.
        val condition = RegexCondition(this.toRegex())

        // Create the Trigger object with the condition and the action lambda.
        val newTrigger = Trigger(condition, action)

        // Because this function is defined inside MudScriptHost, it has access to
        // the outer `this` (the ScriptingEngine instance) to add the trigger.
        // We qualify `this@MudScriptHost` to be explicit.
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug { "Added regex trigger: $this" }
    }

    // The function that allows writing in DSL: "condition" act { match -> send("action") }
    infix fun String.act(action: (Map<Int, String>) -> Unit) {
        val condition = SimpleCondition(this)
        val newTrigger = Trigger(condition) { matchResult ->
            // Get the placeholder numbers and the captured string values
            val placeholderNumbers = condition.placeholderOrder
            val capturedValues = matchResult.groupValues.drop(1)

            // Create the map by "zipping" the two lists together.
            // This elegantly maps each placeholder number to its corresponding captured value.
            val match = placeholderNumbers.zip(capturedValues).toMap()

            // Execute the user's lambda with the prepared map
            action(match)
        }
        addTrigger(newTrigger)
        logger.debug {"Added lambda trigger for pattern: $this"}
    }

    // The function that allows writing in DSL: "condition" act "action"
    infix fun String.act(textCommand: String) {
        val condition = SimpleCondition(this)
        val newTrigger = Trigger(condition) { matchResult ->
            var commandToSend = textCommand

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
        }
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug {"Added simple trigger for pattern: $this"}
    }
}