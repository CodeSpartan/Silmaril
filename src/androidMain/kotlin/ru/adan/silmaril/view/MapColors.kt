package ru.adan.silmaril.view

import androidx.compose.ui.graphics.Color
import ru.adan.silmaril.misc.RoomColor

/**
 * ModernBlack-style map colors for Android.
 * Extracted from ModernBlackColorStyle.kt for use in the Android map view.
 */
object MapColors {
    // Room colors
    val roomVisitedStart = Color(0xff525252)
    val roomVisitedEnd = Color(0xff4d4d4d)
    val roomUnvisited = Color(0xff333333)

    // Stroke colors
    val roomStroke = Color(0xffdadada)          // Current player's room border
    val roomStrokeSecondary = Color(0xff818181) // Groupmate's room border

    // Connection colors
    val connectionNormal = Color.Gray
    val connectionCurrent = Color.White
    val connectionPath = Color(0xff008700)      // Green for pathfinding route

    // Icon colors
    val neutralIcon = Color(0xffcfcfcf)
    val warningIcon = Color(0xffffe0d3)

    // Text colors
    val inputFieldText = Color(0xFFe8e8e8)

    // Background
    val mapBackground = Color(0xFF141414)       // Slightly darker than AdditionalWindowBackground

    // Custom room color tints (for RoomColor enum)
    fun getRoomColorTint(color: RoomColor): Color {
        return when (color) {
            RoomColor.Default -> Color.Transparent
            RoomColor.Red -> Color(0xffff4444)
            RoomColor.Yellow -> Color(0xffffff44)
            RoomColor.Purple -> Color(0xffaa44ff)
            RoomColor.Brown -> Color(0xff8b4513)
            RoomColor.Green -> Color(0xff44ff44)
        }
    }
}
