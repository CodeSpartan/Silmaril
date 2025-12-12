package ru.adan.silmaril.misc

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ColorfulTextMessage(
    val chunks: Array<TextMessageChunk>,
    val loreItem: String? = null
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
         * Color specifies foreground color, as well as background color (optional).
         * Size specifies small/normal/large.
         *
         * Color usage examples:
         * <color=yellow>, <color fg=yellow>, <color fg=yellow, bg=black>, <color fg=#FAFAFA>, <color fg=#FAFAFA, bg=#000000>
         * If named color is specified, it's assumed bright. You can also prefix any named color with "dark-", e.g. <color=dark-yellow>
         *
         * Examples with size:
         * - "Normal <color=bright-yellow>bright yellow <size=large>BIG</size> still yellow</color> back."
         * - "Mix <size=small>tiny <color=dark-green>tiny green</color> tiny</size> normal."
         */
        fun makeColoredChunksFromTaggedText(
            taggedText: String,
            brightWhiteAsDefault: Boolean,
            defaultFgAnsi: AnsiColor = AnsiColor.None,
        ): Array<TextMessageChunk> {
            val chunks = mutableListOf<TextMessageChunk>()

            val defaultFgAnsi =
                if (defaultFgAnsi != AnsiColor.None)
                    defaultFgAnsi
                else
                    if (brightWhiteAsDefault) AnsiColor.White
                    else AnsiColor.None
            val defaultFgBright = brightWhiteAsDefault
            val defaultBgAnsi = AnsiColor.None
            val defaultBgBright = false

            data class Style(
                val fg: ColorOrAnsi,
                val bg: ColorOrAnsi,
                val size: TextSize
            )

            var current = Style(
                fg = ColorOrAnsi.fromAnsi(defaultFgAnsi, defaultFgBright),
                bg = ColorOrAnsi.fromAnsi(defaultBgAnsi, defaultBgBright),
                size = TextSize.Normal
            )

            // Regex to recognize tags at the current position (case-insensitive)
            // Legacy: <color=...> where ... can be "bright-yellow", "yellow", or "#RRGGBB"/"#RRGGBBAA"
            val openColorLegacy = """<color=([^>]+)>""".toRegex(RegexOption.IGNORE_CASE)
            // Attributes: <color fg=... bg=...>
            val openColorAttrs  = """<color\s+([^>]+)>""".toRegex(RegexOption.IGNORE_CASE)
            val openSize        = """<size=(\w+)>""".toRegex(RegexOption.IGNORE_CASE)
            val closeTag        = """</(color|size)>""".toRegex(RegexOption.IGNORE_CASE)

            val stack = ArrayDeque<Pair<Style, String>>() // "color" | "size"

            val sb = StringBuilder()
            var i = 0

            fun flush() {
                if (sb.isNotEmpty()) {
                    chunks.add(
                        TextMessageChunk(
                            text = sb.toString(),
                            fg = current.fg,
                            bg = current.bg,
                            textSize = current.size
                        )
                    )
                    sb.clear()
                }
            }

            // Helper
            fun unquote(s: String): String {
                val t = s.trim()
                return if (t.length >= 2 && ((t.first() == '"' && t.last() == '"') || (t.first() == '\'' && t.last() == '\''))) {
                    t.substring(1, t.length - 1)
                } else t
            }

            // Parse #RRGGBB or #RRGGBBAA into Color
            fun parseHexToColorOrNull(v: String): Color? {
                if (!v.startsWith("#")) return null
                val hex = v.removePrefix("#")
                return when (hex.length) {
                    6 -> {
                        // #RRGGBB
                        val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                        val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                        val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                        Color(r, g, b)
                    }
                    8 -> {
                        // #RRGGBBAA
                        val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                        val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                        val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                        val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
                        Color(r, g, b, a)
                    }
                    else -> null
                }
            }

            // "bright-yellow" / "dark-green" / "yellow" -> (AnsiColor, isBright)
            fun parseAnsiToken(value: String, fallbackAnsi: AnsiColor, fallbackBright: Boolean): Pair<AnsiColor, Boolean> {
                val v = value.trim()
                val dash = v.indexOf('-')
                val (explicitBright: Boolean?, colorName: String) = if (dash > 0) {
                    val prefix = v.substring(0, dash)
                    val name = v.substring(dash + 1)
                    when {
                        prefix.equals("bright", ignoreCase = true) -> true to name
                        prefix.equals("dark", ignoreCase = true) -> false to name
                        else -> null to v
                    }
                } else {
                    null to v
                }
                val ansi = enumValueOfIgnoreCase(colorName, fallbackAnsi)
                val isBright = explicitBright ?: true // legacy default
                return ansi to isBright
            }

            // key=value map for <color ...>
            fun parseAttrMap(attrBlob: String): Map<String, String> {
                val kv = """(\w+)\s*=\s*("[^"]*"|'[^']*'|[^"\s>]+)""".toRegex(RegexOption.IGNORE_CASE)
                return kv.findAll(attrBlob).associate { m ->
                    val key = m.groups[1]!!.value.lowercase()
                    val raw = m.groups[2]!!.value
                    key to unquote(raw)
                }
            }

            // Given a value token (hex or ansi token), return ColorOrAnsi; fallback preserves current
            fun toColorOrAnsi(value: String, currentAnsi: AnsiColor, currentBright: Boolean): ColorOrAnsi {
                parseHexToColorOrNull(value)?.let { return ColorOrAnsi.fromColor(it) }
                val (ansi, bright) = parseAnsiToken(value, currentAnsi, currentBright)
                return ColorOrAnsi.fromAnsi(ansi, bright)
            }

            while (i < taggedText.length) {
                val c = taggedText[i]
                if (c == '<') {
                    val close = closeTag.find(taggedText, i)?.takeIf { it.range.first == i }
                    val openCL = openColorLegacy.find(taggedText, i)?.takeIf { it.range.first == i }
                    val openCA = openColorAttrs.find(taggedText, i)?.takeIf { it.range.first == i }
                    val openS  = openSize.find(taggedText, i)?.takeIf { it.range.first == i }

                    if (close != null || openCL != null || openCA != null || openS != null) {
                        flush()
                        when {
                            openCL != null -> {
                                // <color=...>  where ... can be ANSI token or hex
                                stack.addLast(current to "color")
                                val value = openCL.groups[1]!!.value.trim()
                                // Legacy rule: acts like fg=...
                                val newFg = toColorOrAnsi(value, currentAnsi = current.fg.ansi, currentBright = current.fg.isBright)
                                current = current.copy(fg = newFg)
                                i = openCL.range.last + 1
                                continue
                            }
                            openCA != null -> {
                                // <color fg=... bg=...>
                                stack.addLast(current to "color")
                                val attrs = parseAttrMap(openCA.groups[1]?.value ?: "")

                                attrs["fg"]?.let { v ->
                                    current = current.copy(
                                        fg = toColorOrAnsi(v, currentAnsi = current.fg.ansi, currentBright = current.fg.isBright)
                                    )
                                }
                                attrs["bg"]?.let { v ->
                                    current = current.copy(
                                        bg = toColorOrAnsi(v, currentAnsi = current.bg.ansi, currentBright = current.bg.isBright)
                                    )
                                }

                                i = openCA.range.last + 1
                                continue
                            }
                            openS != null -> {
                                // <size=...>
                                stack.addLast(current to "size")
                                val sizeName = openS.groups[1]?.value
                                val size = enumValueOfIgnoreCase(sizeName, TextSize.Normal)
                                current = current.copy(size = size)
                                i = openS.range.last + 1
                                continue
                            }
                            else -> {
                                // </color> or </size>
                                val tagType = close!!.groups[1]!!.value.lowercase()
                                if (stack.isNotEmpty() && stack.last().second == tagType) {
                                    val (prev, _) = stack.removeLast()
                                    current = prev
                                    i = close.range.last + 1
                                    continue
                                } else {
                                    // Mismatched closer: treat '<' literally
                                    sb.append('<')
                                    i += 1
                                    continue
                                }
                            }
                        }
                    }
                }

                sb.append(c)
                i += 1
            }

            flush()
            return chunks.toTypedArray()
        }
    }
}

data class ColorOrAnsi(
    val color: Color? = null,
    val ansi: AnsiColor = AnsiColor.None,
    val isBright: Boolean = false
) {
    companion object {
        fun fromAnsi(ansi: AnsiColor, isBright: Boolean) = ColorOrAnsi(color = null, ansi = ansi, isBright = isBright)
        fun fromColor(color: Color) = ColorOrAnsi(color = color)
    }

    val isLiteral: Boolean get() = color != null
}

@Immutable
data class TextMessageChunk(
    var text: String,
    var fg: ColorOrAnsi = ColorOrAnsi.fromAnsi(AnsiColor.None, false),
    var bg: ColorOrAnsi = ColorOrAnsi.fromAnsi(AnsiColor.None, false),
    var textSize: TextSize = TextSize.Normal,
) {
    // Legacy-friendly constructor: keeps old call sites working (including named args)
    constructor(
        text: String,
        fgColor: AnsiColor,
        bgColor: AnsiColor = AnsiColor.None,
        isBright: Boolean = false,
        textSize: TextSize = TextSize.Normal,
        isBgBright: Boolean = false,
    ) : this(
        text = text,
        fg = ColorOrAnsi.fromAnsi(fgColor, isBright),
        bg = ColorOrAnsi.fromAnsi(bgColor, isBgBright),
        textSize = textSize
    )
}
