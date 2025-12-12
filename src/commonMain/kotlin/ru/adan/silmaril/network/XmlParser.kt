package ru.adan.silmaril.network

import ru.adan.silmaril.model.Creature

/**
 * Data class for room mobs information
 */
data class RoomMobsData(
    val isRound: Boolean = false,
    val mobs: List<Creature> = emptyList()
) {
    companion object {
        val EMPTY = RoomMobsData(false, emptyList())
    }
}

/**
 * Data class for current room information
 */
data class CurrentRoomData(
    val roomId: Int = -1,
    val zoneId: Int = -1
) {
    companion object {
        val EMPTY = CurrentRoomData(-1, -1)
    }
}