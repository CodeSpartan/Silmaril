package visual_styles

import androidx.compose.ui.graphics.Color
import misc.AnsiColor
import misc.UiColor
import misc.ansiColorToTextColor

class ClassicBlackColorStyle : ColorStyle() {
    override fun getAnsiColor(color: AnsiColor, bright: Boolean): Color {
        return ansiColorToTextColor(color, bright)
    }

    override fun getUiColor(color: UiColor): Color {
        return when (color) {
            UiColor.MainWindowBackground -> Color.Black
            UiColor.AdditionalWindowBackground -> Color.Black
            UiColor.InputField -> Color(0xFF424242)
            UiColor.InputFieldText -> Color.White
        }
    }

    override fun inputFieldCornerRoundness(): Float {
        return 0.0f
    }
}