package ru.adan.silmaril.misc

/**
 * ANSI terminal color codes
 */
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

/**
 * UI element color identifiers
 */
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

/**
 * Text size options
 */
enum class TextSize {
    Small,
    Normal,
    Large,
}

/**
 * Character/creature position states
 */
enum class Position {
    Dying,
    Sleeping,
    Resting,
    Sitting,
    Fighting,
    Standing,
    Riding;

    companion object {
        fun fromString(value: String): Position = Position.valueOf(value)
    }
}

/**
 * Helper function to get enum value case-insensitively
 */
inline fun <reified E : Enum<E>> enumValueOfIgnoreCase(name: String?, fallback: E): E {
    if (name == null) return fallback
    return enumValues<E>().find { it.name.equals(name, ignoreCase = true) } ?: fallback
}
