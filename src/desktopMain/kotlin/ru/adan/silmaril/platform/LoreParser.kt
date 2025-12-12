package ru.adan.silmaril.platform

import ru.adan.silmaril.model.Creature
import ru.adan.silmaril.network.CurrentRoomData
import ru.adan.silmaril.network.RoomMobsData

/**
 * Desktop implementation - delegates to shared JVM implementation.
 */
actual fun parseLoreXml(xmlData: String): List<String>? = parseLoreXmlImpl(xmlData)

actual fun parseGroupStatusXml(xmlData: String): List<Creature>? = parseGroupStatusXmlImpl(xmlData)

actual fun parseRoomMonstersXml(xmlData: String): RoomMobsData? = parseRoomMonstersXmlImpl(xmlData)

actual fun parseCurrentRoomXml(xmlData: String): CurrentRoomData? = parseCurrentRoomXmlImpl(xmlData)
