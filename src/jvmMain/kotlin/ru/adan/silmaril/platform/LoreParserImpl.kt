package ru.adan.silmaril.platform

import ru.adan.silmaril.model.Creature
import ru.adan.silmaril.mud_messages.CurrentRoomMessage
import ru.adan.silmaril.mud_messages.GroupStatusMessage
import ru.adan.silmaril.mud_messages.LoreMessage
import ru.adan.silmaril.mud_messages.RoomMonstersMessage
import ru.adan.silmaril.network.CurrentRoomData
import ru.adan.silmaril.network.RoomMobsData

/**
 * Shared JVM implementation of LoreMessage parser using Jackson XML.
 * Both Android and Desktop actuals delegate to this function.
 */
fun parseLoreXmlImpl(xmlData: String): List<String>? {
    val loreMessage = LoreMessage.fromXml(xmlData)
    return loreMessage?.loreAsTaggedTexts()
}

/**
 * Shared JVM implementation of GroupStatusMessage parser using Jackson XML.
 */
fun parseGroupStatusXmlImpl(xmlData: String): List<Creature>? {
    val message = GroupStatusMessage.fromXml(xmlData)
    return message?.allCreatures
}

/**
 * Shared JVM implementation of RoomMonstersMessage parser using Jackson XML.
 */
fun parseRoomMonstersXmlImpl(xmlData: String): RoomMobsData? {
    val message = RoomMonstersMessage.fromXml(xmlData)
    return message?.let {
        RoomMobsData(it.isRound, it.allCreatures)
    }
}

/**
 * Shared JVM implementation of CurrentRoomMessage parser using Jackson XML.
 */
fun parseCurrentRoomXmlImpl(xmlData: String): CurrentRoomData? {
    val message = CurrentRoomMessage.fromXml(xmlData)
    return message?.let {
        CurrentRoomData(it.roomId, it.zoneId)
    }
}
