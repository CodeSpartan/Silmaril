package ru.adan.silmaril.mud_messages

import ru.adan.silmaril.misc.AnsiColor

data class ColorfulTextMessage(
    val chunks: Array<TextMessageChunk>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColorfulTextMessage

        return chunks.contentEquals(other.chunks)
    }

    override fun hashCode(): Int {
        return chunks.contentHashCode()
    }
}

data class TextMessageChunk (
    var text : String,
    var fgColor : AnsiColor,
    var bgColor : AnsiColor = AnsiColor.Black,
    var isBright : Boolean = false
)