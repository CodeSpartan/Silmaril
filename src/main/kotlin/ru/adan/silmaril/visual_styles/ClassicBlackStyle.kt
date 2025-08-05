package ru.adan.silmaril.visual_styles

import androidx.compose.ui.graphics.Color
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.misc.ansiColorToTextColor

class ClassicBlackColorStyle : ColorStyle() {
    override fun getAnsiColor(color: AnsiColor, bright: Boolean): Color {
        return ansiColorToTextColor(color, bright)
    }

    override fun getUiColor(color: UiColor): Color {
        return when (color) {
            UiColor.MainWindowBackground -> Color.Black
            UiColor.MainWindowSelectionBackground -> Color(0x96818181)
            UiColor.AdditionalWindowBackground -> Color.Black
            UiColor.InputField -> Color(0xFF424242)
            UiColor.InputFieldText -> Color.White
            UiColor.MapRoomStroke -> Color.White
            UiColor.HoverBackground -> Color(0xFF242424)
            UiColor.HoverSeparator -> Color(0xff2b2b2b)
            else -> Color.White
        }
    }

    override fun getUiColorList(color: UiColor): List<Color> {
        return when (color) {
            UiColor.MapRoomUnvisited -> listOf(
                Color(0xFF5d5d5d), // Start color: Grey
                Color(0xFF5d5d5d), // Start color: Grey
                Color(0xff6d6d6d), // Middle color: Light Grey
                Color(0xFF5d5d5d), // End color: Grey
            )
            UiColor.MapRoomVisited -> listOf(
                Color(0xFF1e6c9d), // Start color: Dark Blue
                Color(0xFF1e6c9d), // Start color: Dark Blue
                Color(0xFF05979c), // Middle color: Light blue
                Color(0xFF1e6c9d), // End color: Dark Blue
            )
            else -> listOf(Color.White)
        }
    }

    override fun inputFieldCornerRoundness(): Float {
        return 0.0f
    }
}