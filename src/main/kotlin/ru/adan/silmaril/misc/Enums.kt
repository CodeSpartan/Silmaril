package ru.adan.silmaril.misc

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
