package ru.adan.silmaril.model

/**
 * Interface for tracking known max HP values of group members.
 * Implemented by ProfileManager in desktopMain.
 */
interface KnownHpTracker {
    suspend fun addKnownHp(name: String, maxHp: Int)
}
