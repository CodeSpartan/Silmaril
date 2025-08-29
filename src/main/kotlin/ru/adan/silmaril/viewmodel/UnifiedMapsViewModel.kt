package ru.adan.silmaril.viewmodel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.koin.core.component.KoinComponent
import ru.adan.silmaril.mud_messages.CurrentRoomMessage

data class MapInfoSource (
    val profileName: String,
    val currentRoom: StateFlow<CurrentRoomMessage>
)

data class MapInfoUpdate(
    val profileName: String,
    val message: CurrentRoomMessage
)

class UnifiedMapsViewModel () : KoinComponent {
    private val logger = KotlinLogging.logger {}

    // Dynamic list of instances can change at runtime
    private val _sources = MutableStateFlow<List<MapInfoSource>>(emptyList())
    val sources: StateFlow<List<MapInfoSource>> = _sources

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    val roomEvents: SharedFlow<MapInfoUpdate> =
        sources
            .flatMapLatest { list: List<MapInfoSource> ->
                if (list.isEmpty()) {
                    emptyFlow()
                } else {
                    list.asFlow().flatMapMerge { src: MapInfoSource ->
                        src.currentRoom.map { msg -> MapInfoUpdate(src.profileName, msg) }
                    }
                }
            }
            .shareIn(
                scope = scopeDefault,
                started = SharingStarted.Eagerly,
                replay = 0
            )

    fun setSources(newSources: List<MapInfoSource>) {
        _sources.value = newSources
    }

    fun cleanup() {
        scopeDefault.cancel()
    }
}