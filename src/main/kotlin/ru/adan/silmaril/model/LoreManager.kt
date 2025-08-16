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

    fun cleanup() {
        coroutineScope.cancel()
    }

    // loreName contains _ instead of spaces
    fun findLoreInFiles(loreName: String) {
        coroutineScope.launch {
            val directory = File(getLoresDirectory())
            val filter = FilenameFilter { _, name ->
                name.contains(loreName, ignoreCase = true)
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
            val fileExactMatch = files.find { it.name == loreName }
            if (files.size == 1 || fileExactMatch != null ) {
                val file = if (files.size == 1) files[0] else fileExactMatch
                LoreMessage.fromXml(file!!.readText(Charsets.UTF_16LE))?.let {
                    withContext(Dispatchers.Main) {
                        profileManager.currentClient.value.processLoreLines(it.loreAsTaggedTexts())
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    profileManager.currentMainViewModel.value.displayTaggedText(
                        "Вы никогда не видели такого предмета.",
                        false
                    )
                }
            }
        }
    }

    fun saveLoreIfNew(loreMessage: LoreMessage) {
        coroutineScope.launch {
            val filename = loreMessage.name.replace(" ", "_")

            val directory = File(getLoresDirectory())
            val filter = FilenameFilter { _, name ->
                name.equals(filename, ignoreCase = true)
            }
            val files = directory.listFiles(filter)

            if (files.size > 0) {
                return@launch
            }

            val file = Paths.get(getLoresDirectory(), filename )
            file.toFile().writeText(loreMessage.toXml(), Charsets.UTF_16LE)

            withContext(Dispatchers.Main) {
                profileManager.currentMainViewModel.value.displayTaggedText(
                    "Вы запомнили предмет.",
                    false
                )
            }
        }
    }
}