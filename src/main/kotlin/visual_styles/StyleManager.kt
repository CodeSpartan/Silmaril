package visual_styles

import androidx.compose.ui.graphics.Color
import misc.AnsiColor
import misc.UiColor

object StyleManager {

    private val styles = mapOf(
        "ClassicBlack" to ClassicBlackColorStyle(),
        "ModernBlack" to ModernBlackColorStyle(),
        "ModernDarkRed" to ModernDarkRedColorStyle(),
    )

    fun getStyle(s: String): ColorStyle {
        return styles[s]!!
    }
}

abstract class ColorStyle {
    abstract fun getAnsiColor(color: AnsiColor, bright: Boolean): Color
    abstract fun getUiColor(color: UiColor): Color
    abstract fun getUiColorList(color: UiColor): List<Color>
    abstract fun inputFieldCornerRoundness(): Float
}