package ru.adan.silmaril.scripting

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.misc.Hotkey
import ru.adan.silmaril.misc.TextMessageChunk
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.set
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.text.replace

interface ScriptingEngine {
    // Properties
    val profileName: String
    val mainViewModel: MainViewModel
    val logger: KLogger

    // Methods
    fun addTriggerToGroup(group: String, trigger: Trigger)
    fun addTrigger(trigger: Trigger)
    fun removeTriggerFromGroup(condition: String, action: String, priority: Int, group: String, isRegex: Boolean) : Boolean
    fun addAliasToGroup(group: String, alias: Trigger)
    fun addAlias(alias: Trigger)
    fun addSubstituteToGroup(group: String, sub: Trigger)
    fun addSubstitute(sub: Trigger)
    fun removeAliasFromGroup(condition: String, action: String, priority: Int, group: String) : Boolean
    fun addHotkeyToGroup(group: String, hotkey: Hotkey)
    fun removeHotkeyFromGroup(keyString: String, actionText: String, priority: Int, group: String) : Boolean
    fun sendCommand(command: String)
    fun sendAllCommand(command: String)
    fun sendWindowCommand(window: String, command: String)
    fun getVarCommand(varName: String): Variable?
    fun setVarCommand(varName: String, varValue: Any)
    fun unvarCommand(varName: String)
    fun echoCommand(message: String, color: AnsiColor, isBright: Boolean)
    fun sortTriggersByPriority()
    fun sortAliasesByPriority()
    fun sortHotkeysByPriority()
    fun sortSubstitutesByPriority()
    fun processLine(line: String)
    fun processAlias(line: String) : Pair<Boolean, String?>
    fun processHotkey(keyEvent: KeyEvent) : Boolean
    fun processSubstitutes(msg: ColorfulTextMessage) : ColorfulTextMessage?
    fun loadScript(scriptFile: File) : Int
    fun getTriggers(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun getAliases(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun getHotkeys(): MutableMap<String, CopyOnWriteArrayList<Hotkey>>
    fun getSubstitutes(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun switchWindowCommand(window: String) : Boolean
    fun loreCommand(loreName: String)
    fun commentCommand(comment: String): Boolean
    fun getProfileManager(): ProfileManager
}

open class ScriptingEngineImpl(
    override val profileName: String,
    override val mainViewModel: MainViewModel,
    private val isGroupActive: (String) -> Boolean,
    private val settingsManager: SettingsManager,
    private val profileManager: ProfileManager,
    private val loreManager: LoreManager,
) : ScriptingEngine {
    companion object {
        val jvmHost by lazy { BasicJvmScriptingHost() }
    }

    override val logger = KotlinLogging.logger {}
    // @TODO: let triggers add/remove triggers. Currently that would throw an error, since they're matched against in the for loop.
    // CopyOnWrite is a thread-safe list
    private val triggers : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var triggersByPriority = listOf<Trigger>()

    private val aliases : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var aliasesByPriority = listOf<Trigger>()

    private val substitutes : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var substitutesByPriority = listOf<Trigger>()

    private val hotkeys : MutableMap<String, CopyOnWriteArrayList<Hotkey>> = mutableMapOf() // key is GroupName
    private var hotkeysByPriority = listOf<Hotkey>()

    private var currentlyLoadingScript = ""
    private val regexContainsPercentPatterns = Regex("""%\d+""")

    override fun addTriggerToGroup(group: String, trigger: Trigger) {
        if (!triggers.containsKey(group)) {
            triggers[group] = CopyOnWriteArrayList<Trigger>()
        }
        triggers[group]!!.add(trigger)
    }

    override fun addTrigger(trigger: Trigger) {
        val group = currentlyLoadingScript
        if (!triggers.containsKey(group)) {
            triggers[group] = CopyOnWriteArrayList<Trigger>()
        }
        triggers[group]!!.add(trigger)
    }

    override fun removeTriggerFromGroup(condition: String, action: String, priority: Int, group: String, isRegex: Boolean) : Boolean {
        return triggers[group]?.removeIf {
            !it.withDsl
            && (it.condition is RegexCondition) == isRegex
            && it.priority == priority
            && it.action.originalCommand == action
            && it.condition.originalPattern == condition
        } == true
    }

    override fun addAlias(alias: Trigger) {
        val group = currentlyLoadingScript
        if (!aliases.containsKey(group)) {
            aliases[group] = CopyOnWriteArrayList<Trigger>()
        }
        aliases[group]!!.add(alias)
    }

    override fun addSubstituteToGroup(group: String, sub: Trigger) {
        if (!substitutes.containsKey(group)) {
            substitutes[group] = CopyOnWriteArrayList<Trigger>()
        }
        substitutes[group]!!.add(sub)
    }

    override fun addSubstitute(sub: Trigger) {
        val group = currentlyLoadingScript
        if (!substitutes.containsKey(group)) {
            substitutes[group] = CopyOnWriteArrayList<Trigger>()
        }
        substitutes[group]!!.add(sub)
    }

    override fun addAliasToGroup(group: String, alias: Trigger) {
        if (!aliases.containsKey(group)) {
            aliases[group] = CopyOnWriteArrayList<Trigger>()
        }
        aliases[group]!!.add(alias)
    }

    override fun removeAliasFromGroup(condition: String, action: String, priority: Int, group: String): Boolean {
        return aliases[group]?.removeIf {
            !it.withDsl
            && it.priority == priority
            && it.action.originalCommand == action
            && it.condition.originalPattern == condition
        } == true
    }

    override fun addHotkeyToGroup(group: String, hotkey: Hotkey) {
        if (!hotkeys.containsKey(group)) {
            hotkeys[group] = CopyOnWriteArrayList<Hotkey>()
        }
        hotkeys[group]!!.add(hotkey)
    }

    override fun removeHotkeyFromGroup(keyString: String, actionText: String, priority: Int, group: String) : Boolean {
        return hotkeys[group]?.removeIf {
            it.priority == priority
                && it.actionText == actionText
                && it.keyString == keyString
        } == true
    }

    override fun sendCommand(command: String) {
        mainViewModel.treatUserInput(command)
    }

    override fun sendAllCommand(command: String) {
        profileManager.gameWindows.value.values.forEach { profile -> profile.mainViewModel.treatUserInput(command) }
    }

    override fun sendWindowCommand(window: String, command: String) {
        profileManager.gameWindows.value[window]?.mainViewModel?.treatUserInput(command)
    }

    override fun switchWindowCommand(window: String) : Boolean =
        profileManager.switchWindow(window)

    override fun loreCommand(loreName: String) {
        loreManager.findLoreInFiles(loreName)
    }

    override fun commentCommand(comment: String): Boolean =
        loreManager.commentLastLore(comment)

    override fun getProfileManager(): ProfileManager {
        return profileManager
    }

    override fun getVarCommand(varName: String): Variable? =
        profileManager.gameWindows.value[profileName]?.getVariable(varName)

    override fun setVarCommand(varName: String, varValue: Any) {
        profileManager.gameWindows.value[profileName]?.setVariable(varName, varValue)
    }

    override fun unvarCommand(varName: String) {
        profileManager.gameWindows.value[profileName]?.removeVariable(varName)
    }

    override fun echoCommand(message: String, color: AnsiColor, isBright: Boolean) {
        mainViewModel.displayColoredMessage(message, color, isBright)
    }

    override fun sortTriggersByPriority() {
        triggersByPriority = triggers.filter { isGroupActive(it.key) }.values.flatten().sortedBy { it.priority }
    }

    override fun sortAliasesByPriority() {
        aliasesByPriority = aliases.filter { isGroupActive(it.key) }.values.flatten().sortedBy { it.priority }
    }

    override fun sortHotkeysByPriority() {
        hotkeysByPriority = hotkeys.filter { isGroupActive(it.key) }.values.flatten().sortedBy { it.priority }
    }

    override fun sortSubstitutesByPriority() {
        substitutesByPriority = substitutes.filter { isGroupActive(it.key) }.values.flatten().sortedBy { it.priority }
    }

    /**
     * Checks a line of text from the MUD against all active triggers.
     */
    override fun processLine(line: String)  {
        for (trigger in triggersByPriority) {
            val match = trigger.condition.check(line)
            if (match != null) {
                // Execute the trigger's action if it matches
                trigger.action.lambda.invoke(this, match)
            }
        }
    }

    override fun processAlias(line: String): Pair<Boolean, String?> {
        for (alias in aliasesByPriority) {
            val match = alias.condition.check(line.trim())
            if (match != null) {
                if (alias.action.commandToSend != null) {
                    val returnStr = alias.action.commandToSend.invoke(this, match)

                    // When it's a normal alias (not DSL) without any matching patterns such as %0, %1, etc,
                    // then there's an automatically added (.+) at the end of the condition (see AliasCondition::parsePattern).
                    // But if nothing matches the (.+) in the action, then append " %0" to the action, so that
                    // a simple #al {e} {eat} will allow "e food" -> "eat food"
                    if (alias.action.originalCommand != null) {
                        if (!regexContainsPercentPatterns.containsMatchIn(alias.action.originalCommand))
                            if (1 in match.groupValues.indices)
                                return true to line.replace(match.groupValues[0], returnStr + " " + match.groupValues[1]).trim()
                    }
                    return true to returnStr
                } else {
                    // In DSL, don't return any string. The lambda is supposed to issue its own "sends".
                    alias.action.lambda.invoke(this, match)
                    return true to null
                }
            }
        }
        return false to null
    }

    override fun processHotkey(keyEvent: KeyEvent): Boolean {
        if (!Hotkey.isKeyValid(keyEvent)) return false

        val foundHotkeys = hotkeysByPriority.filter { it.keyboardKey == keyEvent.key
                && it.isAltPressed == keyEvent.isAltPressed
                && it.isCtrlPressed == keyEvent.isCtrlPressed
                && it.isShiftPressed == keyEvent.isShiftPressed
        }

        foundHotkeys.forEach { hotkey ->
            mainViewModel.treatUserInput(hotkey.actionText)
        }

        return foundHotkeys.isNotEmpty()
    }

    override fun processSubstitutes(msg: ColorfulTextMessage): ColorfulTextMessage? {
        if (substitutesByPriority.isEmpty()) return msg

        var current = msg
        val maxPasses = 1 // safety to avoid infinite loops

        repeat(maxPasses) {
            val fullText = buildString { current.chunks.forEach { append(it.text) } }
            if (fullText.isEmpty()) return current

            var matchedAny = false
            var replacedThisPass = false

            for (trigger in substitutesByPriority) {
                val match = trigger.condition.check(fullText) ?: continue
                matchedAny = true

                // DSL trigger: execute and suppress the entire line
                if (trigger.action.commandToSend == null) {
                    trigger.action.lambda.invoke(this, match)
                    return null
                }

                // Substitution trigger: replace the matched span across chunks
                val replacementText = trigger.action.commandToSend.invoke(this, match)
                val startIdx = match.range.first
                val endExclusive = match.range.last + 1

                val indexed = indexChunks(current.chunks)
                val startPos = locateChunk(indexed, startIdx)
                val endPos = if (endExclusive > 0) locateChunk(indexed, endExclusive - 1) else startPos

                val newChunks = mutableListOf<TextMessageChunk>()

                // 1) Chunks fully before the match
                for (i in 0 until startPos.index) {
                    newChunks += indexed[i].chunk
                }

                // 2) Left remainder of the start chunk (before the match)
                val startIC = startPos.ic
                if (startIdx > startIC.start) {
                    val leftText = startIC.chunk.text.substring(0, startIdx - startIC.start)
                    if (leftText.isNotEmpty()) {
                        newChunks += startIC.chunk.copy(text = leftText)
                    }
                }

                // 3) Replacement chunks: defaults from the start chunk's FG style
                val defaultBright = startIC.chunk.fg.isBright
                val defaultAnsi = startIC.chunk.fg.ansi

                val replacementChunks: Array<TextMessageChunk> =
                    ColorfulTextMessage.makeColoredChunksFromTaggedText(
                        taggedText = replacementText,
                        brightWhiteAsDefault = defaultBright,
                        defaultFgAnsi = defaultAnsi
                    )
                newChunks.addAll(replacementChunks)

                // 4) Right remainder of the end chunk (after the match)
                val endIC = endPos.ic
                if (endExclusive < endIC.end) {
                    val rightText = endIC.chunk.text.substring(endExclusive - endIC.start)
                    if (rightText.isNotEmpty()) {
                        newChunks += endIC.chunk.copy(text = rightText)
                    }
                }

                // 5) Chunks fully after the match
                for (i in (endPos.index + 1) until indexed.size) {
                    newChunks += indexed[i].chunk
                }

                val merged = mergeAdjacentChunks(newChunks)
                current = ColorfulTextMessage(merged.toTypedArray())

                replacedThisPass = true
                break // only the first matching trigger per pass
            }

            if (!matchedAny) {
                // No triggers matched anywhere in the line
                return current
            }

            if (replacedThisPass) {
                // Continue to next pass from the top with updated chunks
                return@repeat
            }
        }

        // Max passes reached; return what we have (prevents infinite loops)
        return current
    }

    /**
     * Loads and executes a user's .kts script file.
     * @return number of triggers loaded
     */
    override fun loadScript(scriptFile: File) : Int {
        //println("[HOST]: Host Classloader: ${this.javaClass.classLoader}")
        //println("[HOST]: Host's ScriptingEngine Interface Classloader: ${ScriptingEngine::class.java.classLoader}")

        logger.info {"[SYSTEM]: Loading and evaluating script ${scriptFile.name}..."}
        currentlyLoadingScript = scriptFile.name.replace(".mud.kts", "").uppercase()
        settingsManager.addGroup(currentlyLoadingScript)

        try {
            val host = jvmHost
            val compilationConfig = MudScriptDefinition
            val evaluationConfig = ScriptEvaluationConfiguration {
                constructorArgs(this@ScriptingEngineImpl)
            }
            val result = host.eval(scriptFile.toScriptSource(), compilationConfig, evaluationConfig)

            // Process any resulting errors or warnings.
            result.reports.forEach { report ->
                if (report.severity >= ScriptDiagnostic.Severity.WARNING) {
                    val location = report.location?.let { "at line ${it.start.line}, col ${it.start.col}" } ?: ""
                    logger.warn { "[SCRIPT]: ${report.message} $location" }
                    echoCommand("[$scriptFile.mud.kts]: ${report.message} $location", AnsiColor.Red, true)
                }
            }

            val triggersLoaded = result.reports.filter { report -> report.severity < ScriptDiagnostic.Severity.ERROR }.size
            logger.info { "Triggers loaded: $triggersLoaded" }
            return triggersLoaded

        } catch (e: Exception) {
            logger.error(e) { "An exception occurred while setting up the script engine." }
            return 0
        }
    }

    override fun getTriggers() = triggers

    override fun getAliases() = aliases

    override fun getHotkeys() = hotkeys

    override fun getSubstitutes() = substitutes

    /** Private methods **/

    // ————— Substitution Helpers —————

    private data class Indexed(val chunk: TextMessageChunk, val start: Int, val end: Int)
    private data class Located(val index: Int, val ic: Indexed)

    // Build global indices for chunks (end is exclusive)
    private fun indexChunks(chunks: Array<TextMessageChunk>): List<Indexed> {
        val list = ArrayList<Indexed>(chunks.size)
        var pos = 0
        for (c in chunks) {
            val s = pos
            val e = s + c.text.length
            list += Indexed(c, s, e)
            pos = e
        }
        return list
    }

    // Find the chunk containing global position `pos` (0-based, inclusive).
    private fun locateChunk(indexed: List<Indexed>, pos: Int): Located {
        // For many chunks, consider binary search
        for (i in indexed.indices) {
            val ic = indexed[i]
            if (pos >= ic.start && pos < ic.end) return Located(i, ic)
        }
        // Fallback: return last if pos is at/beyond the end
        val lastIdx = indexed.lastIndex
        return Located(lastIdx, indexed[lastIdx])
    }

    // Merge adjacent chunks that share the same style
    private fun mergeAdjacentChunks(chunks: List<TextMessageChunk>): List<TextMessageChunk> {
        if (chunks.isEmpty()) return chunks
        val out = ArrayList<TextMessageChunk>(chunks.size)
        var cur = chunks[0]
        for (i in 1 until chunks.size) {
            val nxt = chunks[i]
            if (cur.fg == nxt.fg && cur.bg == nxt.bg && cur.textSize == nxt.textSize) {
                cur = cur.copy(text = cur.text + nxt.text)
            } else {
                out += cur
                cur = nxt
            }
        }
        out += cur
        return out
    }

}