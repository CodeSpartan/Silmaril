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
         * Parses a string with nested <color=...>...</color> and <size=...>...</size> tags
         * into TextMessageChunk[].
         *
         * Examples:
         * - "Normal <color=bright-yellow>bright yellow <size=large>BIG</size> still yellow</color> back."
         * - "Mix <size=small>tiny <color=dark-green>tiny green</color> tiny</size> normal."
         */
        fun makeColoredChunksFromTaggedText(
            taggedText: String,
            brightWhiteAsDefault: Boolean
        ): Array<TextMessageChunk> {
            val chunks = mutableListOf<TextMessageChunk>()

            val defaultFg = if (brightWhiteAsDefault) AnsiColor.White else AnsiColor.None
            val defaultBright = brightWhiteAsDefault
            val defaultBg = AnsiColor.None

            data class Style(val fg: AnsiColor, val bright: Boolean, val size: TextSize)

            // Current (effective) style
            var current = Style(
                fg = defaultFg,
                bright = defaultBright,
                size = TextSize.Normal
            )

            // Regex to recognize tags at the current position (case-insensitive)
            val openColor = """<color=(?:(bright|dark)-)?(\w+)>""".toRegex(RegexOption.IGNORE_CASE)
            val openSize  = """<size=(\w+)>""".toRegex(RegexOption.IGNORE_CASE)
            val closeTag  = """</(color|size)>""".toRegex(RegexOption.IGNORE_CASE)

            // Stack holds previous style + which tag type opened it
            val stack = ArrayDeque<Pair<Style, String>>() // tag type: "color" | "size"

            val sb = StringBuilder()
            var i = 0

            fun flush() {
                if (sb.isNotEmpty()) {
                    chunks.add(
                        TextMessageChunk(
                            text = sb.toString(),
                            fgColor = current.fg,
                            bgColor = defaultBg,
                            isBright = current.bright,
                            textSize = current.size
                        )
                    )
                    sb.clear()
                }
            }

            while (i < taggedText.length) {
                val c = taggedText[i]
                if (c == '<') {
                    val close = closeTag.find(taggedText, i)?.takeIf { it.range.first == i }
                    val openC = openColor.find(taggedText, i)?.takeIf { it.range.first == i }
                    val openS = openSize.find(taggedText, i)?.takeIf { it.range.first == i }

                    if (close != null || openC != null || openS != null) {
                        // We are at a tag boundary: first flush accumulated text
                        flush()

                        when {
                            openC != null -> {
                                // Opening color tag
                                stack.addLast(current to "color")
                                val brightness = openC.groups[1]?.value
                                val colorName = openC.groups[2]?.value
                                val isBright = !"dark".equals(brightness, ignoreCase = true) // default bright if omitted
                                val color = enumValueOfIgnoreCase(colorName, defaultFg)
                                current = current.copy(fg = color, bright = isBright)
                                i = openC.range.last + 1
                                continue
                            }
                            openS != null -> {
                                // Opening size tag
                                stack.addLast(current to "size")
                                val sizeName = openS.groups[1]?.value
                                val size = enumValueOfIgnoreCase(sizeName, TextSize.Normal)
                                current = current.copy(size = size)
                                i = openS.range.last + 1
                                continue
                            }
                            else -> {
                                // Closing tag
                                val tagType = close!!.groups[1]!!.value.lowercase()
                                if (stack.isNotEmpty() && stack.last().second == tagType) {
                                    val (prev, _) = stack.removeLast()
                                    current = prev
                                    i = close.range.last + 1
                                    continue
                                } else {
                                    // Mismatched closer: treat '<' literally and move forward
                                    sb.append('<')
                                    i += 1
                                    continue
                                }
                            }
                        }
                    }
                }

                // Not a tag start: accumulate text
                sb.append(c)
                i += 1
            }

            // Flush any trailing text
            flush()

            return chunks.toTypedArray()
        }

        // Case-insensitive enum lookup with fallback default
        private inline fun <reified T : Enum<T>> enumValueOfIgnoreCase(name: String?, default: T): T {
            if (name == null) return default
            return enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
        }
    }
}

data class TextMessageChunk (
    var text : String,
    var fgColor : AnsiColor,
    var bgColor : AnsiColor = AnsiColor.None,
    var isBright : Boolean = false,
    var textSize : TextSize = TextSize.Normal,
)