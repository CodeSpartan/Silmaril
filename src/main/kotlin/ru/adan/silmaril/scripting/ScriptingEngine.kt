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
    fun removeSubstituteFromGroup(condition: String, action: String, priority: Int, group: String, isRegex: Boolean) : Boolean
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

    override fun removeSubstituteFromGroup(
        condition: String,
        action: String,
        priority: Int,
        group: String,
        isRegex: Boolean
    ): Boolean {
        return substitutes[group]?.removeIf {
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
        mainViewModel.displayChunks(ColorfulTextMessage.makeColoredChunksFromTaggedText(message, isBright, color))
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
        // Keep list order as is (assumed already by priority), or sort if needed:
        // val triggers = substitutesByPriority.sortedByDescending { it.priority }
        val triggers = substitutesByPriority

        for (trigger in triggers) {
            val fullText = buildString { current.chunks.forEach { append(it.text) } }
            if (fullText.isEmpty()) return current

            // DSL trigger: execute and suppress the line entirely.
            if (trigger.action.commandToSend == null) {
                val m = trigger.condition.check(fullText)
                if (m != null) {
                    trigger.action.lambda.invoke(this, m)
                    return null
                }
                continue
            }

            // Substitution trigger: find all non-overlapping occurrences on the current text
            val occurrences = findAllOccurrences(fullText, trigger.condition)
            if (occurrences.isEmpty()) continue

            // Build a new chunk list by splicing in replacements for every occurrence (left-to-right).
            val originalChunks = current.chunks
            val indexed = indexChunks(originalChunks)
            val result = ArrayList<TextMessageChunk>(originalChunks.size + occurrences.size * 2)

            var cursor = 0
            for (occ in occurrences) {
                // Copy original text between cursor and the match start (preserving styles)
                if (occ.start > cursor) {
                    appendSlice(result, indexed, cursor, occ.start)
                }

                // Replacement chunks: default style from the start chunk of this match
                val startLoc = locateChunk(indexed, occ.start)
                val defaultBright = startLoc.ic.chunk.fg.isBright
                val defaultAnsi = startLoc.ic.chunk.fg.ansi

                val replacementText = trigger.action.commandToSend!!.invoke(this, occ.match)
                val replacementChunks: Array<TextMessageChunk> =
                    ColorfulTextMessage.makeColoredChunksFromTaggedText(
                        taggedText = replacementText,
                        brightWhiteAsDefault = defaultBright,
                        defaultFgAnsi = defaultAnsi
                    )
                result.addAll(replacementChunks)

                cursor = occ.end
            }

            // Tail after the last match
            val endOfLine = if (indexed.isEmpty()) 0 else indexed.last().end
            if (cursor < endOfLine) {
                appendSlice(result, indexed, cursor, endOfLine)
            }

            // Merge adjacent chunks with identical style to reduce fragmentation
            val merged = mergeAdjacentChunks(result)
            current = ColorfulTextMessage(merged.toTypedArray())

            // Respect stopProcess after applying this trigger
            if (trigger.stopProcess) return current
        }

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

    private data class Indexed(val chunk: TextMessageChunk, val start: Int, val end: Int) // end exclusive
    private data class Located(val index: Int, val ic: Indexed)
    private data class MatchAt(val start: Int, val end: Int, val match: MatchResult)

    // Build global indices for each chunk (end exclusive)
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

    // Find the chunk that contains global position pos (0-based, inclusive)
    private fun locateChunk(indexed: List<Indexed>, pos: Int): Located {
        // Linear scan is fine for small N; switch to binary search if needed.
        for (i in indexed.indices) {
            val ic = indexed[i]
            if (pos >= ic.start && pos < ic.end) return Located(i, ic)
        }
        // Fallback to last if pos is at/beyond the end
        val lastIdx = indexed.lastIndex
        return Located(lastIdx, indexed[lastIdx])
    }

    // Append a slice [from, to) from the original chunks into dst, preserving styles.
    private fun appendSlice(
        dst: MutableList<TextMessageChunk>,
        indexed: List<Indexed>,
        from: Int,
        to: Int
    ) {
        if (from >= to || indexed.isEmpty()) return

        val startL = locateChunk(indexed, from)
        val endL = locateChunk(indexed, to - 1)

        val startIC = startL.ic
        val endIC = endL.ic

        if (startL.index == endL.index) {
            val text = startIC.chunk.text.substring(from - startIC.start, to - startIC.start)
            if (text.isNotEmpty()) dst += startIC.chunk.copy(text = text)
            return
        }

        // First partial
        val firstText = startIC.chunk.text.substring(from - startIC.start)
        if (firstText.isNotEmpty()) dst += startIC.chunk.copy(text = firstText)

        // Middle whole chunks
        for (i in (startL.index + 1) until endL.index) {
            dst += indexed[i].chunk
        }

        // Last partial
        val lastText = endIC.chunk.text.substring(0, to - endIC.start)
        if (lastText.isNotEmpty()) dst += endIC.chunk.copy(text = lastText)
    }

    // Collect all non-overlapping matches of a TriggerCondition over the given line.
// Guards against zero-length matches to avoid infinite loops.
    private fun findAllOccurrences(line: String, condition: TriggerCondition): List<MatchAt> {
        val out = ArrayList<MatchAt>()
        var offset = 0
        while (offset <= line.length) {
            val segment = line.substring(offset)
            val m = condition.check(segment) ?: break
            val s = offset + m.range.first
            val e = offset + m.range.last + 1
            if (e < s) break
            out += MatchAt(s, e, m)
            // Prevent zero-length infinite loops
            offset = if (e == s) e + 1 else e
        }
        return out
    }

    // Merge adjacent chunks that share the same style (fg/bg/size)
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