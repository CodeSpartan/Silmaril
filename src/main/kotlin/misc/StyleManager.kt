package misc

import androidx.compose.ui.graphics.Color

object StyleManager {

    val styles = mapOf(
        "Black" to BlackColorStyle(),
        "DarkRed" to DarkRedColorStyle(),
    )

    fun getStyle(s: String): ColorStyle {
        return styles[s]!!
    }
}

abstract class ColorStyle {
    abstract fun getAnsiColor(color: AnsiColor, bright: Boolean): Color
    abstract fun getUiColor(color: UiColor): Color
}

class BlackColorStyle : ColorStyle() {
    override fun getAnsiColor(color: AnsiColor, bright: Boolean): Color {
        return ansiColorToTextColor(color, bright)
    }

    override fun getUiColor(color: UiColor): Color {
        return when (color) {
            UiColor.MainWindowBackground -> Color.Black
            UiColor.AdditionalWindowBackground -> Color.Black
        }
    }
}

class DarkRedColorStyle : ColorStyle() {
    override fun getAnsiColor(color: AnsiColor, bright: Boolean): Color {
        return when (color) {
            AnsiColor.None -> Color(0xffbfb0ac)
            else -> ansiColorToTextColor(color, bright)
        }
    }

    override fun getUiColor(color: UiColor): Color {
        return when (color) {
            UiColor.MainWindowBackground -> Color(0xFF231917)
            UiColor.AdditionalWindowBackground -> Color(0xFF1A110F)
        }
    }
}