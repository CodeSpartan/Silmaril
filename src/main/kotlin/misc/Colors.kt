package misc

import androidx.compose.ui.graphics.Color

fun ansiColorToTextColor(ansiColor: AnsiColor, isBright: Boolean): Color {
    return if (isBright) {
        when (ansiColor) {
            AnsiColor.Black -> Color(96, 96, 96)
            AnsiColor.Red -> Color(255, 0, 0)
            AnsiColor.Green -> Color(0, 255, 0)
            AnsiColor.Yellow -> Color(255, 255, 0)
            AnsiColor.Blue -> Color(0, 0, 255)
            AnsiColor.Magenta -> Color(255, 0, 255)
            AnsiColor.Cyan -> Color(0, 255, 255)
            AnsiColor.White -> Color(255, 255, 255)
            AnsiColor.None -> Color(192, 192, 192)
        }
    } else {
        when (ansiColor) {
            AnsiColor.Black -> Color(192, 192, 192)
            AnsiColor.Red -> Color(128, 0, 0)
            AnsiColor.Green -> Color(0, 128, 0)
            AnsiColor.Yellow -> Color(128, 128, 0)
            AnsiColor.Blue -> Color(0, 0, 128)
            AnsiColor.Magenta -> Color(128, 0, 128)
            AnsiColor.Cyan -> Color(0, 128, 128)
            AnsiColor.White -> Color(128, 128, 128)
            AnsiColor.None -> Color(192, 192, 192)
        }
    }
}