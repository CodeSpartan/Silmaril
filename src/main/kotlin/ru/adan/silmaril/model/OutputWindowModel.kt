package ru.adan.silmaril.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.TextMessageChunk
import kotlin.collections.plus

class OutputWindowModel {

    private val logger = KotlinLogging.logger {}
    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _colorfulTextMessages = MutableSharedFlow<ColorfulTextMessage>()
    val colorfulTextMessages = _colorfulTextMessages.asSharedFlow()

    fun cleanup() {
        scopeDefault.cancel()
    }

    fun displayTaggedText(taggedText: String, brightWhiteAsDefault: Boolean = true) {
        scopeDefault.launch {
            val chunks = ColorfulTextMessage.makeColoredChunksFromTaggedText(taggedText, brightWhiteAsDefault)
            _colorfulTextMessages.emit(ColorfulTextMessage(chunks))
        }
    }
}