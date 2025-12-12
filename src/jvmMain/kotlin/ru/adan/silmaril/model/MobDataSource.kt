package ru.adan.silmaril.model

import kotlinx.coroutines.flow.StateFlow
import ru.adan.silmaril.mud_messages.RoomMobs

/**
 * Interface for providing mob/monster-related data flows.
 * Implemented by MudConnection in desktopMain.
 */
interface MobDataSource {
    val lastMonstersMessage: StateFlow<RoomMobs>
}
