package visual_styles

import androidx.compose.ui.graphics.Color
import misc.AnsiColor
import misc.UiColor
import misc.ansiColorToTextColor

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
            UiColor.AdditionalWindowBackground -> Color(0xFF1A110F)
            UiColor.InputField -> Color(0xFF3D3230)
            UiColor.InputFieldText -> Color(0xFFE7D6D1)
            UiColor.MapRoomStroke -> Color.White
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