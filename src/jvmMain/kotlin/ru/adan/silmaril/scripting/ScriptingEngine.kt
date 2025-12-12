package ru.adan.silmaril.scripting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.misc.HotkeyData
import ru.adan.silmaril.misc.HotkeyEvent
import ru.adan.silmaril.misc.HotkeyKeyMap
import ru.adan.silmaril.misc.TextMessageChunk
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.model.ProfileManagerInterface
import ru.adan.silmaril.model.SettingsProvider
import ru.adan.silmaril.model.Creature
import ru.adan.silmaril.platform.Logger
import ru.adan.silmaril.platform.createLogger
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class TransientScriptData(
    var freeAvailable: Boolean = true,
    var standUpJob: Job? = null,
    val fightStatusMultiDelegate: MutableList<(inFight: Boolean) -> Unit> = mutableListOf(),
    var isInFightMode: Boolean = false,
    var currentEnemy: String = "",
    val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
)

interface ScriptingEngine {
    // Properties
    val profileName: String
    val mainViewModel: MainViewModel
    val logger: Logger
    val scriptData: TransientScriptData

    // Methods
    fun addTriggerToGroup(group: String, trigger: Trigger)
    fun addTrigger(trigger: Trigger)
    fun addRoundTrigger(roundTrigger: RoundTrigger)
    fun removeTriggerFromGroup(condition: String, action: String, priority: Int, group: String, isRegex: Boolean) : Boolean
    fun removeSubstituteFromGroup(condition: String, action: String, priority: Int, group: String, isRegex: Boolean) : Boolean
    fun addAliasToGroup(group: String, alias: Trigger)
    fun addAlias(alias: Trigger)
    fun addSubstituteToGroup(group: String, sub: Trigger)
    fun addSubstitute(sub: Trigger)
    fun removeAliasFromGroup(condition: String, action: String, priority: Int, group: String) : Boolean
    fun addHotkeyToGroup(group: String, hotkeyData: HotkeyData)
    fun removeHotkeyFromGroup(keyString: String, actionText: String, priority: Int, group: String) : Boolean
    fun sendCommand(command: String)
    fun sendAllCommand(command: String, recursionLevel: Int = 0)
    fun sendWindowCommand(window: String, command: String, recursionLevel: Int = 0)
    fun getVarCommand(varName: String): Variable?
    fun setVarCommand(varName: String, varValue: Any)
    fun unvarCommand(varName: String)
    fun echoCommand(message: String, color: AnsiColor, isBright: Boolean)
    fun sortTriggersByPriority()
    fun sortAliasesByPriority()
    fun sortHotkeysByPriority()
    fun sortSubstitutesByPriority()
    fun sortRoundTriggersByPriority()
    fun processLine(line: String)
    fun processRound(newRound: Boolean, groupMates: List<Creature>, mobs: List<Creature>)
    fun processAlias(line: String) : Pair<Boolean, String?>
    fun processHotkey(hotkeyEvent: HotkeyEvent) : Boolean
    fun isBoundHotkeyEvent(hotkeyEvent: HotkeyEvent) : Boolean
    fun processSubstitutes(msg: ColorfulTextMessage) : ColorfulTextMessage?
    fun loadScript(scriptFile: File) : Int
    fun getTriggers(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun getAliases(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun getHotkeys(): MutableMap<String, CopyOnWriteArrayList<HotkeyData>>
    fun getSubstitutes(): MutableMap<String, CopyOnWriteArrayList<Trigger>>
    fun switchWindowCommand(window: String) : Boolean
    fun loreCommand(loreName: String)
    fun commentCommand(comment: String): Boolean
    fun getProfileManager(): ProfileManagerInterface
    fun cleanup()
}

open class ScriptingEngineImpl(
    override val profileName: String,
    override val mainViewModel: MainViewModel,
    private val isGroupActive: (String) -> Boolean,
    override val scriptData: TransientScriptData,
    protected val settingsProvider: SettingsProvider,
    private val profileManager: ProfileManagerInterface,
    private val loreManager: LoreManager,
) : ScriptingEngine {
    override val logger = createLogger("ru.adan.silmaril.scripting.ScriptingEngine")
    // @TODO: let triggers add/remove triggers. Currently that would throw an error, since they're matched against in the for loop.
    // CopyOnWrite is a thread-safe list
    private val triggers : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var triggersByPriority = listOf<Trigger>()

    private val roundTriggers : MutableMap<String, CopyOnWriteArrayList<RoundTrigger>> = mutableMapOf() // key is GroupName
    private var roundTriggersByPriority = listOf<RoundTrigger>()

    private val aliases : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var aliasesByPriority = listOf<Trigger>()

    private val substitutes : MutableMap<String, CopyOnWriteArrayList<Trigger>> = mutableMapOf() // key is GroupName
    private var substitutesByPriority = listOf<Trigger>()

    private val hotkeys : MutableMap<String, CopyOnWriteArrayList<HotkeyData>> = mutableMapOf() // key is GroupName
    private var hotkeysByPriority = listOf<HotkeyData>()

    protected var currentlyLoadingScript = ""
    private val regexContainsPercentPatterns = Regex("""%\d+""")

    override fun addTriggerToGroup(group: String, trigger: Trigger) {
        settingsProvider.addGroup(group)
        if (!triggers.containsKey(group)) {
            triggers[group] = CopyOnWriteArrayList<Trigger>()
        }
        triggers[group]!!.add(trigger)
    }

    override fun addTrigger(trigger: Trigger) {
        addTriggerToGroup(currentlyLoadingScript, trigger)
    }

    override fun addRoundTrigger(roundTrigger: RoundTrigger) {
        val group = currentlyLoadingScript
        settingsProvider.addGroup(group)
        if (!roundTriggers.containsKey(group)) {
            roundTriggers[group] = CopyOnWriteArrayList<RoundTrigger>()
        }
        roundTriggers[group]!!.add(roundTrigger)
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
        addAliasToGroup(currentlyLoadingScript, alias)
    }

    override fun addSubstituteToGroup(group: String, sub: Trigger) {
        settingsProvider.addGroup(group)
        if (!substitutes.containsKey(group)) {
            substitutes[group] = CopyOnWriteArrayList<Trigger>()
        }
        substitutes[group]!!.add(sub)
    }

    override fun addSubstitute(sub: Trigger) {
        addSubstituteToGroup(currentlyLoadingScript, sub)
    }

    override fun addAliasToGroup(group: String, alias: Trigger) {
        settingsProvider.addGroup(group)
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

    override fun addHotkeyToGroup(group: String, hotkeyData: HotkeyData) {
        settingsProvider.addGroup(group)
        if (!hotkeys.containsKey(group)) {
            hotkeys[group] = CopyOnWriteArrayList<HotkeyData>()
        }
        hotkeys[group]!!.add(hotkeyData)
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

    override fun sendAllCommand(command: String, recursionLevel: Int) {
        profileManager.getAllOpenedProfiles().forEach { profile -> profile.mainViewModel.treatUserInput(command, true, recursionLevel) }
    }

    override fun sendWindowCommand(window: String, command: String, recursionLevel: Int) {
        val desiredWindowName = if (window == "ГМ") "ГМ" else if (getThisProfile()?.profileName?.contains("_локал", true) == true && !window.contains("_локал", true)) "${window}_локал" else window
        profileManager.getProfileByName(desiredWindowName)?.mainViewModel?.treatUserInput(command, true, recursionLevel)
    }

    override fun switchWindowCommand(window: String) : Boolean =
        profileManager.switchWindow(window)

    override fun loreCommand(loreName: String) {
        loreManager.findLoreInFiles(loreName)
    }

    override fun commentCommand(comment: String): Boolean =
        loreManager.commentLastLore(comment)

    override fun getProfileManager(): ProfileManagerInterface {
        return profileManager
    }

    override fun getVarCommand(varName: String): Variable? =
        profileManager.getProfileByName(profileName)?.getVariable(varName)

    override fun cleanup() {
        cleanupCoroutine()
    }

    override fun setVarCommand(varName: String, varValue: Any) {
        profileManager.getProfileByName(profileName)?.setVariable(varName, varValue)
    }

    override fun unvarCommand(varName: String) {
        profileManager.getProfileByName(profileName)?.removeVariable(varName)
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

    override fun sortRoundTriggersByPriority() {
        roundTriggersByPriority = roundTriggers.filter { isGroupActive(it.key) }.values.flatten().sortedBy { it.priority }
    }

    /**
     * Checks a line of text from the MUD against all active triggers.
     */
    override fun processLine(line: String)  {
        for (trigger in triggersByPriority) {
            val match = trigger.condition.check(line)
            if (match != null) {
                // Execute the trigger's action if it matches
                try {
                    trigger.action.lambda.invoke(this, match)
                } catch (e: Exception) {
                    echo("Ошибка DSL триггера.", color = AnsiColor.Red, true)
                    echo("Профиль: $profileName, триггер: ${trigger.condition.originalPattern}", color = AnsiColor.Red, true)
                    echoDslException(e.stackTraceToString(), color = AnsiColor.Red, true)

                    logger.warn { "Ошибка DSL триггера." }
                    logger.warn { "Профиль: $profileName, триггер: ${trigger.condition.originalPattern}" }
                    logger.warn { e.stackTraceToString() }
                }
            }
        }
    }

    override fun processRound(newRound: Boolean, groupMates: List<Creature>, mobs: List<Creature>) {
        for (roundTrigger in roundTriggersByPriority) {
            if (roundTrigger.isNewRound == newRound) {
                try {
                    // process old rounds immediately
                    if (!newRound) {
                        roundTrigger.action.lambda.invoke(this, groupMates, mobs)
                    }
                    // process new rounds with a ~18ms delay, so that we've already received the status line, knowing who our target is
                    else {
                        scriptData.backgroundScope.launch {
                            delay(3)
                            roundTrigger.action.lambda.invoke(this@ScriptingEngineImpl, groupMates, mobs)
                        }
                    }
                } catch (e: Exception) {
                    echo("Ошибка Round триггера.", color = AnsiColor.Red, true)
                    echo("Профиль $profileName, newRound: $newRound", color = AnsiColor.Red, true)
                    echoDslException(e.stackTraceToString(), color = AnsiColor.Red, true)

                    logger.warn { "Ошибка Round триггера." }
                    logger.warn { "Профиль $profileName, newRound: $newRound" }
                    logger.warn { e.stackTraceToString() }
                }
            }
        }
    }

    override fun processAlias(line: String): Pair<Boolean, String?> {
        for (alias in aliasesByPriority) {
            val match = alias.condition.check(line.trim())
            if (match != null) {
                // If non-DSL alias
                if (alias.action.commandToSend != null) {
                    var returnStr = ""
                    try {
                        returnStr = alias.action.commandToSend.invoke(this, match)
                    } catch (e: Exception) {
                        echo("Ошибка алиаса.", color = AnsiColor.Red, true)
                        echo("Профиль: $profileName, алиас: ${alias.condition.originalPattern}", color = AnsiColor.Red, true)
                        echoDslException(e.stackTraceToString(), color = AnsiColor.Red, true)

                        logger.warn { "Ошибка алиаса." }
                        logger.warn { "Профиль: $profileName, алиас: ${alias.condition.originalPattern}" }
                        logger.warn { e.stackTraceToString() }
                    }


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
                    try {
                        alias.action.lambda.invoke(this, match)
                    } catch (e: Exception) {
                        echo("Ошибка алиаса.", color = AnsiColor.Red, true)
                        echo("Профиль: $profileName, алиас: ${alias.condition.originalPattern}", color = AnsiColor.Red, true)
                        echoDslException(e.stackTraceToString(), color = AnsiColor.Red, true)

                        logger.warn { "Ошибка алиаса." }
                        logger.warn { "Профиль: $profileName, алиас: ${alias.condition.originalPattern}" }
                        logger.warn { e.stackTraceToString() }
                    }
                    return true to null
                }
            }
        }
        return false to null
    }

    override fun processHotkey(hotkeyEvent: HotkeyEvent): Boolean {
        if (!HotkeyKeyMap.isKeyCodeValid(hotkeyEvent.keyCode)) return false

        val foundHotkeys = hotkeysByPriority.filter { it.keyCode == hotkeyEvent.keyCode
                && it.isAltPressed == hotkeyEvent.isAltPressed
                && it.isCtrlPressed == hotkeyEvent.isCtrlPressed
                && it.isShiftPressed == hotkeyEvent.isShiftPressed
        }

        foundHotkeys.forEach { hotkey ->
            mainViewModel.treatUserInput(hotkey.actionText)
        }

        return foundHotkeys.isNotEmpty()
    }

    override fun isBoundHotkeyEvent(hotkeyEvent: HotkeyEvent): Boolean {
        if (!HotkeyKeyMap.isKeyCodeValid(hotkeyEvent.keyCode)) return false

        val foundHotkeys = hotkeysByPriority.filter { it.keyCode == hotkeyEvent.keyCode
                && it.isAltPressed == hotkeyEvent.isAltPressed
                && it.isCtrlPressed == hotkeyEvent.isCtrlPressed
                && it.isShiftPressed == hotkeyEvent.isShiftPressed
        }

        return foundHotkeys.isNotEmpty()
    }

    override fun processSubstitutes(msg: ColorfulTextMessage): ColorfulTextMessage? {
        if (substitutesByPriority.isEmpty()) return msg

        var current = msg
        // Keep list order as is (assumed already by priority), or sort if needed:
        // val triggers = substitutesByPriority.sortedByDescending { it.priority }
        val substitutes = substitutesByPriority

        for (substitute in substitutes) {
            val fullText = buildString { current.chunks.forEach { append(it.text) } }
            if (fullText.isEmpty()) return current

            // DSL substitute with a lambda: execute and suppress the line entirely.
            if (substitute.action.commandToSend == null) {
                val m = substitute.condition.check(fullText)
                if (m != null) {
                    try {
                        substitute.action.lambda.invoke(this, m)
                    } catch (e: Exception) {
                        echo("Ошибка замены.", color = AnsiColor.Red, true)
                        echo("Профиль: $profileName, замена: ${substitute.condition.originalPattern}", color = AnsiColor.Red, true)
                        echoDslException(e.stackTraceToString(), color = AnsiColor.Red, true)

                        logger.warn { "Ошибка замены." }
                        logger.warn { "Профиль: $profileName, замена: ${substitute.condition.originalPattern}" }
                        logger.warn { e.stackTraceToString() }
                    }
                    return null
                }
                continue
            }

            // Substitution trigger: find all non-overlapping occurrences on the current text
            val occurrences = findAllOccurrences(fullText, substitute.condition)
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

                var replacementText = " "
                try {
                    replacementText = substitute.action.commandToSend.invoke(this, occ.match)
                } catch (e: Exception) {
                    echo("Ошибка замены.", color = AnsiColor.Red, true)
                    echo("Профиль: $profileName, замена: ${substitute.condition.originalPattern}", color = AnsiColor.Red, true)
                    echoDslException(e.stackTraceToString(), color = AnsiColor.Red, true)

                    logger.warn { "Ошибка замены." }
                    logger.warn { "Профиль: $profileName, замена: ${substitute.condition.originalPattern}" }
                    logger.warn { e.stackTraceToString() }
                }
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
            if (substitute.stopProcess) return current
        }

        return current
    }


    /**
     * Loads and executes a user's .kts script file.
     * This is a stub implementation - override in desktop-specific subclass for actual script loading.
     * @return number of triggers loaded
     */
    override open fun loadScript(scriptFile: File) : Int {
        currentlyLoadingScript = scriptFile.name.replace(".mud.kts", "").uppercase()
        settingsProvider.addGroup(currentlyLoadingScript)
        // Stub implementation - actual script loading is done in desktop-specific subclass
        return 0
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
