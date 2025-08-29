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
        logger.debug { "Added regex lambda trigger for pattern: $this" }
    }

    // The function that allows writing in DSL: "condition" grep "action"
    infix fun String.grep(textAction: String) {
        val newTrigger = Trigger.regCreate(this, textAction, 5, true)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug { "Added regex command trigger for pattern: $this" }
    }

    // The function that allows writing in DSL: "condition" act { match -> send("action") }
    infix fun String.act(action: (Map<Int, String>) -> Unit) {
        val newTrigger = Trigger.create(this, action)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug {"Added lambda trigger for pattern: $this"}
    }

    // The function that allows writing in DSL: "condition" act "action"
    infix fun String.act(textAction: String) {
        val newTrigger = Trigger.create(this, textAction, 5, true)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug {"Added simple trigger for pattern: $this"}
    }

    // DSL: "shorthand" alias { match -> send("full command")}
    infix fun String.alias(action: (Map<Int, String>) -> Unit) {
        val newAlias = Trigger.createAlias(this, action)
        this@MudScriptHost.addAlias(newAlias)
        logger.debug {"Added lambda alias for pattern: $this"}
    }

    // DSL: "shorthand" alias "full command"
    infix fun String.alias(textAction: String) {
        val newAlias = Trigger.createAlias(this, textAction, 5, true)
        this@MudScriptHost.addAlias(newAlias)
        logger.debug {"Added simple alias for pattern: $this"}
    }

    // The function that allows writing in DSL: "initial text" subReg { match -> echo("new text") }
    infix fun String.subReg(action: (Map<Int, String>) -> Unit) {
        val newSub = Trigger.subRegCreate(this, action)
        this@MudScriptHost.addSubstitute(newSub)
        logger.debug { "Added regex substitute for pattern: $this" }
    }

    // The function that allows writing in DSL: "initial text" subReg "new text"
    infix fun String.subReg(textAction: String) {
        val newSub = Trigger.subRegCreate(this, textAction, 5, true)
        this@MudScriptHost.addSubstitute(newSub)
        logger.debug { "Added regex substitute for pattern: $this" }
    }

    // The function that allows writing in DSL: "initial text" sub { match -> echo("new text") }
    infix fun String.sub(action: (Map<Int, String>) -> Unit) {
        val newSub = Trigger.subCreate(this, action)
        this@MudScriptHost.addSubstitute(newSub)
        logger.debug { "Added text substitute for pattern: $this" }
    }

    // The function that allows writing in DSL: "initial text" sub "result"
    infix fun String.sub(textAction: String) {
        val newSub = Trigger.subCreate(this, textAction, 5, true)
        this@MudScriptHost.addSubstitute(newSub)
        logger.debug { "Added text substitute for pattern: $this" }
    }
}

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

fun ScriptingEngine.window(windowName: String) {
    switchWindowCommand(windowName)
}

fun ScriptingEngine.isCurrentWindow(): Boolean {
    return getProfileManager().currentProfileName.value.equals(profileName, ignoreCase = true)
}

fun ScriptingEngine.out(message: String) {
    send("#output $message")
}

fun ScriptingEngine.getProfileName() = profileName

fun ScriptingEngine.getCurrentProfile() = getProfileManager().getCurrentProfile()

fun ScriptingEngine.getThisProfile() = getProfileManager().getProfileByName(profileName)

fun ScriptingEngine.formattedTime() = "<color=dark-grey><size=small>\$time </size></color>"