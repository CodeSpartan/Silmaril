package mud_messages

import AnsiColor

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
    var foregroundColor : AnsiColor,
    var backgroundColor : AnsiColor = AnsiColor.Black,
    var isBright : Boolean = false,
    var text : String
)