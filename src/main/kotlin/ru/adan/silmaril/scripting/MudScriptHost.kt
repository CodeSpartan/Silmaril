package ru.adan.silmaril.scripting

// Because act and grep are defined inside MudScriptHost, they have access to
// the outer `this` (the ScriptingEngine instance) to add the trigger.
// We qualify `this@MudScriptHost` to be explicit.
abstract class MudScriptHost(engine: ScriptingEngine) : ScriptingEngine by engine {

    // The function that allows writing in DSL: "condition" grep { send("action") }
    infix fun String.grep(action: ScriptingEngine.(match: MatchResult) -> Unit) {
        val newTrigger = Trigger.create(this, action)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug { "Added regex trigger: $this" }
    }

    // The function that allows writing in DSL: "condition" act { match -> send("action") }
    infix fun String.act(action: (Map<Int, String>) -> Unit) {
        val newTrigger = Trigger.create(this, action)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug {"Added lambda trigger for pattern: $this"}
    }

    // The function that allows writing in DSL: "condition" act "action"
    infix fun String.act(textCommand: String) {
        val newTrigger = Trigger.create(this, textCommand)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug {"Added simple trigger for pattern: $this"}
    }
}