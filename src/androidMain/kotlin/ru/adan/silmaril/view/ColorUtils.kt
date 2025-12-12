package ru.adan.silmaril.view

import androidx.compose.ui.graphics.Color
import ru.adan.silmaril.misc.AnsiColor

/**
 * Converts ANSI color codes to Compose Color with brightness support.
 */
fun ansiToComposeColor(ansiColor: AnsiColor, isBright: Boolean): Color {
    return if (isBright) {
        when (ansiColor) {
            AnsiColor.Black -> Color(0xff616161)
            AnsiColor.Red -> Color(0xffff2b2b)
            AnsiColor.Green -> Color(0xff2bff79)
            AnsiColor.Yellow -> Color(0xfffff42b)
            AnsiColor.Blue -> Color(0xff3b82f6)
            AnsiColor.Magenta -> Color(0xffa855f7)
            AnsiColor.Cyan -> Color(0xff67e8f9)
            AnsiColor.White -> Color(0xfffafafa)
            AnsiColor.None -> Color(0xffcccccc)
        }
    } else {
        when (ansiColor) {
            AnsiColor.Black -> Color(0xff595959)
            AnsiColor.Red -> Color(0xff991b1b)
            AnsiColor.Green -> Color(0xff15803d)
            AnsiColor.Yellow -> Color(0xffc1944e)
            AnsiColor.Blue -> Color(0xff1d4ed8)
            AnsiColor.Magenta -> Color(0xff7e22ce)
            AnsiColor.Cyan -> Color(0xff067c99)
            AnsiColor.White -> Color(0xff808080)
            AnsiColor.None -> Color(0xffcccccc)
        }
    }
}
