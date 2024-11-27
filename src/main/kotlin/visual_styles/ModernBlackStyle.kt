package visual_styles

import androidx.compose.ui.graphics.Color
import misc.AnsiColor
import misc.UiColor
import misc.ansiColorToTextColor

class ModernBlackColorStyle : ColorStyle() {
    override fun getAnsiColor(color: AnsiColor, bright: Boolean): Color {
        if (bright) {
            return when (color) {
                AnsiColor.Black -> Color(0xff616161) // grey ray in the "prismatic rays" spell + OOC channel
                AnsiColor.Red -> Color(0xffff2b2b)
                AnsiColor.Green -> Color(0xff2bff79)
                AnsiColor.Yellow -> Color(0xfffff42b)
                AnsiColor.Blue -> Color(0xff3b82f6) // blue ray in the "prismatic rays" spell
                AnsiColor.Magenta -> Color(0xffa855f7) // where in game to test this?
                AnsiColor.Cyan -> Color(0xff67e8f9)
                AnsiColor.White -> Color(0xfffafafa)
                AnsiColor.None -> Color(0xffcccccc)
            }
        } else {
            return when (color) {
                AnsiColor.Black -> Color(0xff595959) // where in game to test this?
                AnsiColor.Red -> Color(0xff991b1b)
                AnsiColor.Green -> Color(0xff15803d)
                AnsiColor.Yellow -> Color(0xffc1944e)
                AnsiColor.Blue -> Color(0xff1d4ed8) // where in game to test this?
                AnsiColor.Magenta -> Color(0xff7e22ce) // where in game to test this?
                AnsiColor.Cyan -> Color(0xff067c99)
                AnsiColor.White -> Color(0xff808080) // where in the game to test this?
                AnsiColor.None -> Color(0xffcccccc)
                //else -> ansiColorToTextColor(color, bright)
            }
        }
    }

    override fun getUiColor(color: UiColor): Color {
        return when (color) {
            UiColor.MainWindowBackground -> Color(0xff141414) // brightness 8%
            UiColor.AdditionalWindowBackground -> Color(0xFF1a1a1a)
            UiColor.InputField -> Color(0xFF3d3d3d)
            UiColor.InputFieldText -> Color(0xFFe8e8e8)
        }
    }

    override fun inputFieldCornerRoundness(): Float {
        return 32.0f
    }
}