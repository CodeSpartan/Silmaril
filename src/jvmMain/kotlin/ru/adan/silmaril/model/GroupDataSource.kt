package ru.adan.silmaril.model

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for providing group-related data flows.
 * Implemented by MudConnection in desktopMain.
 */
interface GroupDataSource {
    val lastGroupMessage: StateFlow<List<Creature>>
    val unformattedTextMessages: SharedFlow<String>
}
