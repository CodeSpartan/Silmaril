package ru.adan.silmaril.misc

import kotlinx.serialization.Serializable

@Serializable
enum class RoomColor(private val displayName: String) {
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

val roomColorOptions = listOf(
    RoomColor.Default, RoomColor.Red, RoomColor.Yellow, RoomColor.Purple, RoomColor.Brown, RoomColor.Green
)

fun getRoomColorOption(roomColor: RoomColor?): Int {
    if (roomColor == null) return 0
    return roomColorOptions.indexOfFirst { it == roomColor }
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
