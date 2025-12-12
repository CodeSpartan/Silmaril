package ru.adan.silmaril.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.TextMessageChunk
import ru.adan.silmaril.misc.getLoresDirectory
import ru.adan.silmaril.mud_messages.LoreMessage
import ru.adan.silmaril.platform.createLogger
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Paths
import kotlin.getValue

class LoreManager() : KoinComponent {
    val logger = createLogger("ru.adan.silmaril.model.LoreManager")
    val loreDisplayCallback: LoreDisplayCallback by inject()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastLoreMessage: LoreMessage? = null

    fun cleanup() {
        coroutineScope.cancel()
    }

    suspend fun findExactMatchOrAlmost(loreName: String) : Pair<Boolean, List<String>> = withContext(Dispatchers.IO) {
        val directory = File(getLoresDirectory())
        val sanitized = loreName.replace(" ", "_").replace("\"", "")
        val filter = FilenameFilter { _, name ->
            name.startsWith(sanitized, ignoreCase = true)
        }
        val files = directory.listFiles(filter)
        val fileExactMatch = files.find {
            it.name.equals(sanitized, ignoreCase = true)
        }
        if (files.size == 1 || fileExactMatch != null ) {
            val file = if (files.size == 1) files[0] else fileExactMatch
            true to (LoreMessage.fromXml(file!!.readText(Charsets.UTF_16LE))?.loreAsTaggedTexts() ?: emptyList())
        } else {
            false to emptyList()
        }
    }

    fun findLoreInFiles(loreName: String) {
        val sanitized = loreName.replace(" ", "_").replace("\"", "")
        coroutineScope.launch {
            val directory = File(getLoresDirectory())
            val filter = FilenameFilter { _, name ->
                name.contains(sanitized, ignoreCase = true)
            }
            val files = directory.listFiles(filter)

            if (files.size > 1) {
                withContext(Dispatchers.Main) {
                    loreDisplayCallback.displayTaggedText(
                        "Вы вспомнили похожие предметы: ${
                            files.take(30).joinToString { filename -> filename.name.replace("_", " ") }
                        }${if (files.size > 30) "..." else ""}",
                        false
                    )
                }
            }
            val fileExactMatch = files.find { it.name == sanitized }
            if (files.size == 1 || fileExactMatch != null ) {
                val file = if (files.size == 1) files[0] else fileExactMatch
                LoreMessage.fromXml(file!!.readText(Charsets.UTF_16LE))?.let {
                    withContext(Dispatchers.Main) {
                        lastLoreMessage = it
                        loreDisplayCallback.processLoreLines(it.loreAsTaggedTexts())
                    }
                }
            }
            if (files.size == 0 && fileExactMatch == null) {
                withContext(Dispatchers.Main) {
                    loreDisplayCallback.displayTaggedText(
                        "Вы никогда не видели такого предмета.",
                        false
                    )
                }
            }
        }
    }

    fun saveLoreIfNew(loreMessage: LoreMessage, forceOverwrite: Boolean = false) {
        lastLoreMessage = loreMessage
        coroutineScope.launch {
            val regex = Regex("""\s\(x\d+\)$""") // remove trailing stacks, e.g. (x3)
            loreMessage.name = loreMessage.name.replace(regex, "")
            val filename = loreMessage.name.replace(" ", "_").replace("\"", "")

            val directory = File(getLoresDirectory())
            val filter = FilenameFilter { _, name ->
                name.equals(filename, ignoreCase = true)
            }
            val files = directory.listFiles(filter)

            if (!forceOverwrite) {
                // if lore message isn't full and some file already exists (full or not full), we don't need to overwrite it
                if (!loreMessage.isFull && files.size > 0) {
                    return@launch
                }
                if (files.size > 0) {
                    val file = files[0]
                    LoreMessage.fromXml(file!!.readText(Charsets.UTF_16LE))?.let {
                        // if lore message is full, but existing file is also full, we don't need to overwrite it
                        if (it.isFull)
                            return@launch
                    }
                }
            }

            val file = Paths.get(getLoresDirectory(), filename )
            file.toFile().writeText(loreMessage.toXml(), Charsets.UTF_16LE)

            if (!forceOverwrite) {
                withContext(Dispatchers.Main) {
                    loreDisplayCallback.displayTaggedText(
                        "Вы запомнили предмет.",
                        false
                    )
                }
            }
        }
    }

    fun commentLastLore(comment: String): Boolean {
        if (lastLoreMessage == null) return false

        if (comment.isEmpty())
            lastLoreMessage?.comment = null
        else
            lastLoreMessage?.comment = comment
        saveLoreIfNew(lastLoreMessage!!, true)
        return true
    }

    fun insertLoreLinks(message: ColorfulTextMessage) : ColorfulTextMessage {
        val fullText = buildString { message.chunks.forEach { append(it.text) } }

        val marketRegex = """^\d+\s+\p{L}+\s+!?(.+?)(?: \(x\d+\))?(?:\.\.\.)?\s+(?:мало|средне|много)\s+\d+\s+\d+\s+(?:<|>)?\d+\p{L}+\s*\p{L}*.*""".toRegex()
        val marketRegex2 = """^Рынок: Лот #\d+: Новая вещь - '(.*?)(?: \(x\d+\))?',.*""".toRegex()
        val shopRegex = """^\s?\d+\. \[\s+\d*\s*\d+\] !?(.+?)(?= \((?:x\d+)\)\s*$|\s*$)""".toRegex()
        val auctionRegex = """^(?:Аукцион: )?Лот #\d: (?:Новая )?[вВ]ещь (?:- )?'(.*?)(?: \(x\d+\))?',?.*""".toRegex()
        val equipmentRegex = """^<[\p{L}\s\.\d]+>\s+([^\[.]+?)(?:\s(?:\[|\()[\p{L}\s]+(?:\]|\))){0,2}(?:\s\.\.\.[\p{L}\s]+)*(?:\s\(невидим\p{L}+\))?$""".toRegex()

        val match = marketRegex.find(fullText)
            ?: marketRegex2.find(fullText)
            ?: shopRegex.find(fullText)
            ?: auctionRegex.find(fullText)
            ?: equipmentRegex.find(fullText)
            ?: return message

        val g1 = match.groups[1] ?: return message

        val raw = g1.value
        val firstNonWs = raw.indexOfFirst { !it.isWhitespace() }
        if (firstNonWs == -1) return message
        val lastNonWs = raw.indexOfLast { !it.isWhitespace() }
        val trimStartInGroup = firstNonWs
        val trimEndExclusiveInGroup = lastNonWs + 1

        val itemName = raw.substring(trimStartInGroup, trimEndExclusiveInGroup)
        if (itemName == "пустая ячейка") return message

        // Adjusted indices of the non-space captured substring in the full text
        val adjStart = g1.range.first + trimStartInGroup
        val adjEndExclusive = g1.range.first + trimEndExclusiveInGroup
        if (adjStart >= adjEndExclusive) return message

        // Rebuild chunks by removing [adjStart, adjEndExclusive) across potentially many chunks, preserving styles
        val rebuilt = ArrayList<TextMessageChunk>(message.chunks.size)
        var cursor = 0 // global index into fullText

        for (chunk in message.chunks) {
            val chunkText = chunk.text
            val chunkStart = cursor
            val chunkEnd = cursor + chunkText.length

            // No overlap with removal range: keep chunk as-is
            if (chunkEnd <= adjStart || chunkStart >= adjEndExclusive) {
                if (chunkText.isNotEmpty()) {
                    rebuilt.add(
                        TextMessageChunk(
                            text = chunkText,
                            fg = chunk.fg,
                            bg = chunk.bg,
                            textSize = chunk.textSize
                        )
                    )
                }
            } else {
                // Overlap: keep left part before adjStart
                val leftLen = (adjStart - chunkStart).coerceIn(0, chunkText.length)
                if (leftLen > 0) {
                    rebuilt.add(
                        TextMessageChunk(
                            text = chunkText.substring(0, leftLen),
                            fg = chunk.fg,
                            bg = chunk.bg,
                            textSize = chunk.textSize
                        )
                    )
                }

                // Keep right part after adjEndExclusive
                val rightStartInChunk = (adjEndExclusive - chunkStart).coerceIn(0, chunkText.length)
                if (rightStartInChunk < chunkText.length) {
                    rebuilt.add(
                        TextMessageChunk(
                            text = chunkText.substring(rightStartInChunk),
                            fg = chunk.fg,
                            bg = chunk.bg,
                            textSize = chunk.textSize
                        )
                    )
                }
            }

            cursor = chunkEnd
        }

        return ColorfulTextMessage(
            chunks = rebuilt.toTypedArray(),
            loreItem = itemName
        )
    }

}
