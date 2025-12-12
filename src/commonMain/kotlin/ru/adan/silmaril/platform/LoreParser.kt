package ru.adan.silmaril.platform

import ru.adan.silmaril.model.Creature
import ru.adan.silmaril.network.CurrentRoomData
import ru.adan.silmaril.network.RoomMobsData

/**
 * Platform-specific LoreMessage parser.
 * Uses Jackson XML on JVM platforms (Android and Desktop).
 * Returns null if parsing fails.
 */
expect fun parseLoreXml(xmlData: String): List<String>?

/**
 * Platform-specific GroupStatusMessage parser.
 * Uses Jackson XML on JVM platforms (Android and Desktop).
 * Returns null if parsing fails.
 */
expect fun parseGroupStatusXml(xmlData: String): List<Creature>?

/**
 * Platform-specific RoomMonstersMessage parser.
 * Uses Jackson XML on JVM platforms (Android and Desktop).
 * Returns null if parsing fails.
 */
expect fun parseRoomMonstersXml(xmlData: String): RoomMobsData?

/**
 * Platform-specific CurrentRoomMessage parser.
 * Uses Jackson XML on JVM platforms (Android and Desktop).
 * Returns null if parsing fails.
 */
expect fun parseCurrentRoomXml(xmlData: String): CurrentRoomData?
