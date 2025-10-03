package ru.adan.silmaril.misc

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

enum class AnsiColor {
    Black,
    Red,
    Green,
    Yellow,
    Blue,
    Magenta,
    Cyan,
    White,
    None
}

enum class UiColor {
    MainWindowBackground,
    MainWindowSelectionBackground,
    AdditionalWindowBackground,
    InputField,
    InputFieldText,
    MapRoomUnvisited,
    MapRoomVisited,
    MapRoomStroke,
    MapRoomStrokeSecondary,
    MapNeutralIcon,
    MapWarningIcon,
    HoverBackground,
    HoverSeparator,
    GroupSecondaryFontColor,
    GroupPrimaryFontColor,
    HpGood,
    HpMedium,
    HpBad,
    HpExecrable,
    Stamina,
    WaitTime,
    AttackedInAnotherRoom,
    Link,
}

enum class TextSize {
    Small,
    Normal,
    Large,
}

@Serializable
enum class RoomColor (private val displayName: String) {
    Default("[нет]"),
    Red("Красный"),
    Yellow("Желтый"),
    Purple("Фиолетовый"),
    Brown("Коричневый"),
    Green("Зеленый");

    override fun toString(): String {
        return displayName
    }
}

fun RoomColor.toComposeColor(): Color {
    return when (this) {
        RoomColor.Default -> Color.LightGray // Or any default color you prefer
        RoomColor.Red -> Color.Red
        RoomColor.Yellow -> Color.Yellow
        RoomColor.Purple -> Color(0xffff00ff)
        RoomColor.Brown -> Color(0xffff8400)
        RoomColor.Green -> Color.Green
    }
}

fun RoomColor.toOptionColor(): Color {
    return when (this) {
        RoomColor.Default -> Color.LightGray // Or any default color you prefer
        RoomColor.Red -> Color(0xffe62929)
        RoomColor.Yellow -> Color(0xfffff42b)
        RoomColor.Purple -> Color(0xff9842ea)
        RoomColor.Brown -> Color(0xfffa8900)
        RoomColor.Green -> Color(0xff23d965)
    }
}

@Serializable
enum class RoomIcon(private val displayName: String) {
    None("[нет]"),
    WeaponShop("[нет]"),
    FoodShop("[нет]"),
    MagicShop("[нет]"),
    LeatherShop("[нет]"),
    Bank("[нет]"),
    Route("[нет]"),
    Quester("Квест"),
    Archer("[нет]"),
    Barbarian("[нет]"),
    Cleric("[нет]"),
    DarkKnight("[нет]"),
    Druid("[нет]"),
    Mage("[нет]"),
    Paladin("[нет]"),
    Pathfinder("[нет]"),
    Thief("[нет]"),
    Warrior("[нет]"),
    Question("[нет]"),
    Horse("[нет]"),
    Doska("[нет]"),
    Sklad("[нет]"),
    Post("[нет]"),
    MiscShop("[нет]"),
    // ^ these were values from AMC
    // the values below are values from Silmaril:
    NoMagic("!Магия"),
    DeathTrap("Смерть"),
    Item("Предмет"),
    Danger("Опасность"),
    Boss("Босс"),
    Trigger("Триггер"),
    Misc("Другое");

    override fun toString(): String {
        return displayName
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

val roomColorOptions =
    listOf(
        RoomColor.Default, RoomColor.Red, RoomColor.Yellow, RoomColor.Purple, RoomColor.Brown, RoomColor.Green
    )

fun getRoomColorOption(roomColor: RoomColor?) : Int {
    if (roomColor == null) return 0
    return roomColorOptions.indexOfFirst { it == roomColor }
}