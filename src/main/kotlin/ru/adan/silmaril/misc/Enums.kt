package ru.adan.silmaril.misc

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

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
enum class RoomColor {
    Default, Red, Yellow, Purple, Brown, Green
}

fun RoomColor.toComposeColor(): Color {
    return when (this) {
        RoomColor.Default -> Color.LightGray // Or any default color you prefer
        RoomColor.Red -> Color.Red
        RoomColor.Yellow -> Color.Yellow
        RoomColor.Purple -> Color(0xffff00ff) // A common hex for purple
        RoomColor.Brown -> Color(0xffff8400) // A common hex for brown
        RoomColor.Green -> Color.Green
    }
}

@Serializable
enum class RoomIcon {
    None,
    WeaponShop,
    FoodShop,
    MagicShop,
    LeatherShop,
    Bank,
    Route,
    Quester,
    Archer,
    Barbarian,
    Cleric,
    DarkKnight,
    Druid,
    Mage,
    Paladin,
    Pathfinder,
    Thief,
    Warrior,
    Question,
    Horse,
    Doska,
    Sklad,
    Post,
    MiscShop
}
