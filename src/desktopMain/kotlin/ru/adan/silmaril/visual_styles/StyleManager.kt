package ru.adan.silmaril.visual_styles

import androidx.compose.ui.graphics.Color
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.UiColor

object StyleManager {

    val styles = mapOf(
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
    abstract fun borderAroundFloatWidgets(): Boolean
    abstract fun displayWhiteTintedMapIcons(): Boolean
}