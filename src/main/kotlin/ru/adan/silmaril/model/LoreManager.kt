package ru.adan.silmaril.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.misc.getLoresDirectory
import ru.adan.silmaril.mud_messages.LoreMessage
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Paths
import kotlin.getValue

class LoreManager() : KoinComponent {
    val logger = KotlinLogging.logger {}
    val profileManager: ProfileManager by inject()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastLoreMessage: LoreMessage? = null

    fun cleanup() {
        coroutineScope.cancel()
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
                    profileManager.currentMainViewModel.value.displayTaggedText(
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
                        profileManager.currentClient.value.processLoreLines(it.loreAsTaggedTexts())
                    }
                }
            }
            if (files.size == 0 && fileExactMatch == null) {
                withContext(Dispatchers.Main) {
                    profileManager.currentMainViewModel.value.displayTaggedText(
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
                    profileManager.currentMainViewModel.value.displayTaggedText(
                        "Вы запомнили предмет.",
                        false
                    )
                }
            }
        }
    }

    fun commentLastLore(comment: String): Boolean {
        if (lastLoreMessage == null) return false

        lastLoreMessage?.comment = comment
        saveLoreIfNew(lastLoreMessage!!, true)
        return true
    }
}