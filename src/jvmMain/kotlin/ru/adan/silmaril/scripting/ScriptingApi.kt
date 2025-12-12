package ru.adan.silmaril.scripting
import kotlinx.coroutines.*
import ru.adan.silmaril.misc.*
import ru.adan.silmaril.model.Creature
import ru.adan.silmaril.model.ProfileManagerInterface

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
        logger.debug { "Added lambda trigger for pattern: $this" }
    }

    // The function that allows writing in DSL: "condition" act "action"
    infix fun String.act(textAction: String) {
        val newTrigger = Trigger.create(this, textAction, 5, true)
        this@MudScriptHost.addTrigger(newTrigger)
        logger.debug { "Added simple trigger for pattern: $this" }
    }

    // DSL: "shorthand" alias { match -> send("full command")}
    infix fun String.alias(action: (Map<Int, String>) -> Unit) {
        val newAlias = Trigger.createAlias(this, action)
        this@MudScriptHost.addAlias(newAlias)
        logger.debug { "Added lambda alias for pattern: $this" }
    }

    // DSL: "shorthand" alias "full command"
    infix fun String.alias(textAction: String) {
        val newAlias = Trigger.createAlias(this, textAction, 5, true)
        this@MudScriptHost.addAlias(newAlias)
        logger.debug { "Added simple alias for pattern: $this" }
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

    infix fun Int.onNewRound(roundInfo: (groupMates: List<Creature>, mobs: List<Creature>) -> Unit) {
        logger.debug { "onNewRound: not implemented" }
    }

    infix fun Int.onOldRound(roundInfo: (groupMates: List<Creature>, mobs: List<Creature>) -> Unit) {
        logger.debug { "onOldRound: not implemented" }
    }
}

fun ScriptingEngine.send(command: String) {
    sendCommand(command)
}

fun ScriptingEngine.echo(message: String, color: AnsiColor = AnsiColor.None, isBright: Boolean = false) {
    echoCommand(message, color, isBright)
}

fun ScriptingEngine.echoDslException(message: String?, color: AnsiColor = AnsiColor.None, isBright: Boolean = false) {
    if (message != null) {
        val lines = message.lines()
        val lastIndex = lines.indexOfLast { it.contains(".mud.kts") }

        // Take all lines up to and including that index
        // If no such line is found, take all lines
        val result = if (lastIndex != -1) lines.take(lastIndex + 1) else lines

        result.forEach { echo(it, color, isBright) }
    }
}

fun ScriptingEngine.echoCurrentWindow(message: String, color: AnsiColor = AnsiColor.None, isBright: Boolean = false) {
    getProfileManager().getCurrentMainViewModel().displayChunks(ColorfulTextMessage.makeColoredChunksFromTaggedText(message, isBright, color))
}

fun ScriptingEngine.sendAll(command: String) {
    sendAllCommand(command)
}

fun ScriptingEngine.send(window: String, command: String) {
    sendWindowCommand(window, command)
}

/** WindowId: starts with 1 */
fun ScriptingEngine.sendId(windowId: Int, command: String, recursionLevel: Int = 0) {
    getProfileManager().getWindowById(windowId)?.mainViewModel?.treatUserInput(command, true, recursionLevel)
}

fun ScriptingEngine.getVar(varName: String): Variable? {
    return getVarCommand(varName)
}

fun ScriptingEngine.setVar(varName: String, varValue: Any) {
    setVarCommand(varName, varValue)
}

fun ScriptingEngine.windowId(windowId: Int) : Boolean {
    return getProfileManager().switchWindow(windowId - 1)
}

fun ScriptingEngine.unVar(varName: String) {
    unvarCommand(varName)
}

fun ScriptingEngine.window(windowName: String) {
    switchWindowCommand(windowName)
}

fun ScriptingEngine.isCurrentWindow(): Boolean {
    return getProfileManager().getCurrentProfileName().equals(profileName, ignoreCase = true)
}

fun ScriptingEngine.isCurrentWindowConnected(): Boolean {
    return getProfileManager().getCurrentClient().isConnected
}

fun ScriptingEngine.isThisTheFirstConnectedWindow(): Boolean {
    val thisProfile = getProfileManager().getProfileByName(profileName)
    val allProfiles = getProfileManager().getAllOpenedProfiles()
    val firstConnectedProfile = allProfiles.firstOrNull { profile -> profile.client.isConnected }
    return firstConnectedProfile == thisProfile
}

fun ScriptingEngine.isInSameRoomAsCurrentProfile(): Boolean {
    val currentProfileRoomId = getProfileManager().getCurrentMapViewModel().currentRoom.value.roomId
    val thisProfileRoomId = getProfileManager().getProfileByName(profileName)?.mapViewModel?.currentRoom?.value?.roomId ?: -1
    return currentProfileRoomId == thisProfileRoomId
}

fun ScriptingEngine.isThisTheFirstWindowInThisRoom(): Boolean {
    val thisProfile = getProfileManager().getProfileByName(profileName)
    val thisProfileRoomId = thisProfile?.mapViewModel?.currentRoom?.value?.roomId ?: -1
    val allProfiles = getProfileManager().getAllOpenedProfiles()
    val firstProfileInThisRoom = allProfiles.firstOrNull { profile -> profile.mapViewModel.currentRoom.value.roomId == thisProfileRoomId }
    return firstProfileInThisRoom == thisProfile
}

fun ScriptingEngine.isInSameGroupAsCurrentProfile(): Boolean {
    val currentProfileCharName = getProfileManager().getCurrentGroupModel().getMyName()
    val groupMates = getGroupLatest()
    return groupMates?.firstOrNull { it.name == currentProfileCharName } != null
}

fun ScriptingEngine.isThisTheFirstWindowInThisGroup(): Boolean {
    // all group mates
    // set<all opened profile names>
    // iterate over group mates and find the first that the set contains
    // this is the first
    val groupMates = getGroupLatest()
    val allProfilesWithNames = getProfileManager().getAllOpenedProfiles().filter { it.client.isConnected && it.groupModel.getMyName() != "" }
        .map { it.groupModel.getMyName() }.toSet()
    return groupMates?.firstOrNull { groupMate -> allProfilesWithNames.contains(groupMate.name) } == getMeLatest()
}

fun ScriptingEngine.out(message: String) {
    send("#output $message")
}

fun ScriptingEngine.cleanupCoroutine() {
    scriptData.backgroundScope.cancel()
}

fun ScriptingEngine.getProfileName() = profileName

fun ScriptingEngine.getCurrentProfile() = getProfileManager().getCurrentProfile()

fun ScriptingEngine.getThisProfile() = getProfileManager().getProfileByName(profileName)

fun ScriptingEngine.formattedTime() = "<color=dark-grey><size=small>\$time </size></color>"

fun ScriptingEngine.isGroupEnabled(name: String) : Boolean {
    return getThisProfile()?.isGroupActive(name) == true
}

fun ScriptingEngine.getGroupLatest(): List<Creature>? {
    return getThisProfile()?.groupModel?.groupMates?.value
}

fun ScriptingEngine.getMobsLatest(): List<Creature>? {
    return getThisProfile()?.mobsModel?.mobs?.value
}

fun ScriptingEngine.getMeLatest(): Creature ? {
    val myName = getVar("charname")
    if (myName == null) {
        echo("Variable 'charname' is not defined!")
        return null
    }
    return getThisProfile()?.groupModel?.groupMates?.value?.firstOrNull { it.name == myName.toString() }
}

fun ScriptingEngine.getPartyLatest(): List<Creature>? {
    val myName = getVar("charname")
    if (myName == null) {
        echo("Variable 'charname' is not defined!")
        return null
    }
    return getThisProfile()?.groupModel?.groupMates?.value
}