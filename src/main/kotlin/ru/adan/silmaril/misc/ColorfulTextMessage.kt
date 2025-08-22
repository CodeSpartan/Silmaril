package ru.adan.silmaril.misc

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

    companion object {
        /**
         * Parses a string with color tags and passes the resulting chunks to displayChunks.
         * Example: "This is <color=bright-yellow>important</color>!"
         */
        fun makeColoredChunksFromTaggedText(taggedText: String, brightWhiteAsDefault: Boolean) : Array<TextMessageChunk> {
            val chunks = mutableListOf<TextMessageChunk>()
            // Regex to find color tags and capture brightness, color, and text
            val regex = """<color=(?:(bright|dark)-)?(\w+)>(.+?)<\/color>""".toRegex()
            var lastIndex = 0

            regex.findAll(taggedText).forEach { matchResult ->
                // 1. Add the plain text before the current tag
                val beforeText = taggedText.substring(lastIndex, matchResult.range.first)
                if (beforeText.isNotEmpty()) {
                    // Using default values
                    chunks.add(TextMessageChunk(
                        beforeText,
                        if (brightWhiteAsDefault) AnsiColor.White else AnsiColor.None,
                        AnsiColor.None,
                        brightWhiteAsDefault))
                }

                // 2. Extract captured groups from the matched tag
                val brightnessSpecifier = matchResult.groups[1]?.value
                val colorName = matchResult.groups[2]?.value
                val content = matchResult.groups[3]?.value ?: ""

                // 3. Determine brightness. It's bright unless "dark" is specified.
                val isBright = !"dark".equals(brightnessSpecifier, ignoreCase = true)

                // 4. Find the AnsiColor, defaulting to White if the name is invalid
                val color = enumValueOfIgnoreCase(colorName, if (brightWhiteAsDefault) AnsiColor.White else AnsiColor.None)

                // 5. Add the colored chunk
                chunks.add(TextMessageChunk(content, color, AnsiColor.None, isBright))

                // 6. Update our position in the string
                lastIndex = matchResult.range.last + 1
            }

            // 7. Add any remaining plain text after the last tag
            val remainingText = taggedText.substring(lastIndex)
            if (remainingText.isNotEmpty()) {
                chunks.add(TextMessageChunk(remainingText,
                    if (brightWhiteAsDefault) AnsiColor.White else AnsiColor.None,
                    AnsiColor.None,
                    brightWhiteAsDefault))
            }

            // 8. Call the original function with the assembled chunks
            return chunks.toTypedArray()
        }
    }
}

data class TextMessageChunk (
    var text : String,
    var fgColor : AnsiColor,
    var bgColor : AnsiColor = AnsiColor.Black,
    var isBright : Boolean = false
)