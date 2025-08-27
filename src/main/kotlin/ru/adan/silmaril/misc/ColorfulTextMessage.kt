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
            val defaultBgBright = false

            data class Style(
                val fg: AnsiColor,
                val bright: Boolean,
                val bg: AnsiColor,
                val bgBright: Boolean,
                val size: TextSize
            )

            // Current (effective) style
            var current = Style(
                fg = defaultFg,
                bright = defaultBright,
                bg = defaultBg,
                bgBright = defaultBgBright,
                size = TextSize.Normal
            )

            // Regex to recognize tags at the current position (case-insensitive)
            val openColorLegacy = """<color=(?:(bright|dark)-)?(\w+)>""".toRegex(RegexOption.IGNORE_CASE)
            val openColorAttrs  = """<color\s+([^>]+)>""".toRegex(RegexOption.IGNORE_CASE) // e.g. <color fg=yellow bg=navy>
            val openSize        = """<size=(\w+)>""".toRegex(RegexOption.IGNORE_CASE)
            val closeTag        = """</(color|size)>""".toRegex(RegexOption.IGNORE_CASE)

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
                            bgColor = current.bg,
                            isBright = current.bright,
                            textSize = current.size,
                            isBgBright = current.bgBright
                        )
                    )
                    sb.clear()
                }
            }

            // Attribute parsing helpers
            fun unquote(s: String): String {
                val t = s.trim()
                if (t.length >= 2 && ((t.first() == '"' && t.last() == '"') || (t.first() == '\'' && t.last() == '\''))) {
                    return t.substring(1, t.length - 1)
                }
                return t
            }

            // Parses "bright-yellow" / "dark-green" / "yellow" into (AnsiColor, isBright)
            fun parseColorToken(value: String, fallbackColor: AnsiColor, fallbackBright: Boolean): Pair<AnsiColor, Boolean> {
                val v = value.trim()
                val dash = v.indexOf('-')
                val hasPrefix = dash > 0
                val (explicitBright: Boolean?, colorName: String) = if (hasPrefix) {
                    val prefix = v.substring(0, dash)
                    val name = v.substring(dash + 1)
                    when {
                        prefix.equals("bright", ignoreCase = true) -> true to name
                        prefix.equals("dark", ignoreCase = true) -> false to name
                        else -> null to v // unknown prefix; treat whole as color name
                    }
                } else {
                    null to v
                }

                val color = enumValueOfIgnoreCase(colorName, fallbackColor)
                // Legacy behavior: default to bright=true when brightness not specified
                val isBright = explicitBright ?: true

                return color to isBright
            }

            // Parse key=value pairs inside <color ...>
            fun parseAttrMap(attrBlob: String): Map<String, String> {
                // Matches: key=value, value can be quoted or bare
                val kv = """(\w+)\s*=\s*("[^"]*"|'[^']*'|[^"\s>]+)""".toRegex(RegexOption.IGNORE_CASE)
                return kv.findAll(attrBlob).associate { m ->
                    val key = m.groups[1]!!.value.lowercase()
                    val raw = m.groups[2]!!.value
                    key to unquote(raw)
                }
            }

            while (i < taggedText.length) {
                val c = taggedText[i]
                if (c == '<') {
                    val close = closeTag.find(taggedText, i)?.takeIf { it.range.first == i }
                    val openCL = openColorLegacy.find(taggedText, i)?.takeIf { it.range.first == i }
                    val openCA = openColorAttrs.find(taggedText, i)?.takeIf { it.range.first == i }
                    val openS  = openSize.find(taggedText, i)?.takeIf { it.range.first == i }

                    if (close != null || openCL != null || openCA != null || openS != null) {
                        // We are at a tag boundary: first flush accumulated text
                        flush()

                        when {
                            openCL != null -> {
                                // Opening legacy color tag: <color=bright-yellow> or <color=yellow>
                                stack.addLast(current to "color")
                                val brightness = openCL.groups[1]?.value // "bright" | "dark" | null
                                val colorName = openCL.groups[2]?.value ?: ""
                                val (fgColor, isBright) = parseColorToken(
                                    if (brightness != null) "$brightness-$colorName" else colorName,
                                    fallbackColor = current.fg,
                                    fallbackBright = current.bright
                                )
                                current = current.copy(fg = fgColor, bright = isBright)
                                i = openCL.range.last + 1
                                continue
                            }
                            openCA != null -> {
                                // Opening color tag with attributes: <color fg=yellow bg=navy>
                                stack.addLast(current to "color")
                                val attrBlob = openCA.groups[1]?.value ?: ""
                                val attrs = parseAttrMap(attrBlob)

                                // Update fg if present
                                attrs["fg"]?.let { v ->
                                    val (fgColor, isBright) = parseColorToken(v, fallbackColor = current.fg, fallbackBright = current.bright)
                                    current = current.copy(fg = fgColor, bright = isBright)
                                }
                                // Update bg if present
                                attrs["bg"]?.let { v ->
                                    val (bgColor, isBright) = parseColorToken(v, fallbackColor = current.bg, fallbackBright = current.bgBright)
                                    current = current.copy(bg = bgColor, bgBright = isBright)
                                }

                                i = openCA.range.last + 1
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

        // Case-insensitive enum lookup with fallback
        inline fun <reified T : Enum<T>> enumValueOfIgnoreCase(name: String?, default: T): T {
            if (name.isNullOrBlank()) return default
            val n = name.trim()
            return enumValues<T>().firstOrNull { it.name.equals(n, ignoreCase = true) } ?: default
        }
    }
}

data class TextMessageChunk (
    var text : String,
    var fgColor : AnsiColor,
    var bgColor : AnsiColor = AnsiColor.None,
    var isBright : Boolean = false,
    var textSize : TextSize = TextSize.Normal,
    var isBgBright : Boolean = false,
)