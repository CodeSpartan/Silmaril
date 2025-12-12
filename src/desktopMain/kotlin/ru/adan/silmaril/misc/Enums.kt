package ru.adan.silmaril.misc

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.icon.IconKey

// Desktop-specific Compose Color extension functions for RoomColor
fun RoomColor.toComposeColor(): Color {
    return when (this) {
        RoomColor.Default -> Color.LightGray
        RoomColor.Red -> Color.Red
        RoomColor.Yellow -> Color.Yellow
        RoomColor.Purple -> Color(0xffff00ff)
        RoomColor.Brown -> Color(0xffff8400)
        RoomColor.Green -> Color.Green
    }
}

fun RoomColor.toOptionColor(): Color {
    return when (this) {
        RoomColor.Default -> Color.LightGray
        RoomColor.Red -> Color(0xffe62929)
        RoomColor.Yellow -> Color(0xfffff42b)
        RoomColor.Purple -> Color(0xff9842ea)
        RoomColor.Brown -> Color(0xfffa8900)
        RoomColor.Green -> Color(0xff23d965)
    }
}



data class RoomIconOption(val roomIcon: RoomIcon, val icon: IconKey?)

val roomIconOptions =
    listOf(
        RoomIconOption(RoomIcon.None, null),
        RoomIconOption(RoomIcon.Quester, CustomIconKeys.Quester),
        RoomIconOption(RoomIcon.NoMagic, CustomIconKeys.NoMagic),
        RoomIconOption(RoomIcon.Boss, CustomIconKeys.Boss),
        RoomIconOption(RoomIcon.Danger, CustomIconKeys.Danger), // AllIconsKeys.Status.FailedInProgress
        RoomIconOption(RoomIcon.DeathTrap, CustomIconKeys.DeathTrap),
        RoomIconOption(RoomIcon.Item, CustomIconKeys.Item),
        RoomIconOption(RoomIcon.Trigger, CustomIconKeys.Trigger),
        RoomIconOption(RoomIcon.Misc, CustomIconKeys.Misc),
    )

fun getRoomIconOption(icon: RoomIcon?) : Int {
    if (icon == null) return 0
    val index = roomIconOptions.indexOfFirst { it.roomIcon == icon }
    if (index == -1) return roomIconOptions.lastIndex
    return index
}

// Desktop-specific icon mappings using Jewel IconKey