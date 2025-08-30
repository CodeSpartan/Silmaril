package ru.adan.silmaril.visual_styles

import androidx.compose.ui.graphics.Color
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.misc.ansiColorToTextColor

class ModernDarkRedColorStyle : ColorStyle() {
    override fun getAnsiColor(color: AnsiColor, bright: Boolean): Color {
        return when (color) {
            AnsiColor.None -> Color(0xffbfb0ac)
            else -> ansiColorToTextColor(color, bright)
        }
    }

    override fun getUiColor(color: UiColor): Color {
        return when (color) {
            UiColor.MainWindowBackground -> Color(0xFF231917)
            UiColor.MainWindowSelectionBackground -> Color(0x4ca69860)
            UiColor.AdditionalWindowBackground -> Color(0xFF1A110F)
            UiColor.InputField -> Color(0xFF3D3230)
            UiColor.InputFieldText -> Color(0xFFE7D6D1)
            UiColor.MapRoomStroke -> Color.White
            UiColor.MapRoomStrokeSecondary -> Color(0xff818181)
            UiColor.MapNeutralIcon -> Color(0xffcfcfcf)
            UiColor.MapWarningIcon -> Color(0xffffe0d3)
            UiColor.HoverBackground -> Color(0xFF242424)
            UiColor.HoverSeparator -> Color(0xff2b2b2b)
            UiColor.GroupSecondaryFontColor -> Color(0xff4f4f4f)
            UiColor.GroupPrimaryFontColor -> Color(0xff8f8f8f)
            UiColor.HpGood -> Color(0xff91e966)
            UiColor.HpMedium -> Color(0xffe9d866)
            UiColor.HpBad -> Color(0xffe94747)
            UiColor.HpExecrable -> Color(0xffc91c1c)
            UiColor.Stamina -> Color(0xffe7dfd5)
            UiColor.WaitTime -> Color(0xffe98447)
            UiColor.AttackedInAnotherRoom -> Color(0xff330000)
            else -> Color.White
        }
    }

    override fun getUiColorList(color: UiColor): List<Color> {
        return when (color) {
            UiColor.MapRoomUnvisited -> listOf(
                Color(0xFF222222), // Start color: Dark Blue
                Color(0xFF222222), // Start color: Dark Blue
                Color(0xFF191919), // Middle color: Light blue
                Color(0xFF222222), // End color: Dark Blue
            )
            UiColor.MapRoomVisited -> listOf(
                Color(0xFF222222), // Start color: Dark Blue
                Color(0xFF222222), // Start color: Dark Blue
                Color(0xFF191919), // Middle color: Light blue
                Color(0xFF222222), // End color: Dark Blue
            )
            else -> listOf(Color.White)
        }
    }

    override fun inputFieldCornerRoundness(): Float {
        return 32.0f
    }
}