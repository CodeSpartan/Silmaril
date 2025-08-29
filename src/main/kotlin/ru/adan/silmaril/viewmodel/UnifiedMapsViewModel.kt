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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import ru.adan.silmaril.mud_messages.CurrentRoomMessage

data class MapInfoSource (
    val profileName: String,
    val currentRoom: StateFlow<CurrentRoomMessage>,
)

data class MapUpdate(
    val profileName: String,
    val inFight: Boolean,
    val monsters: Int,
)

class UnifiedMapsViewModel () : KoinComponent {
    private val logger = KotlinLogging.logger {}

    // Dynamic list of instances can change at runtime
    private val _profilesInRoom = MutableStateFlow<List<MapInfoSource>>(emptyList())
    val profilesInRoom: StateFlow<List<MapInfoSource>> = _profilesInRoom

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupMatesRooms: StateFlow<Map<Int, List<String>>> =
        profilesInRoom.flatMapLatest { members ->
            if (members.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(members.map { it.currentRoom }) { rooms: Array<CurrentRoomMessage> ->
                    rooms.mapIndexed { i, room ->
                        room.roomId to members[i].profileName
                    }.groupBy(
                        keySelector = { it.first },
                        valueTransform = { it.second }
                    )
                }
            }
        }.stateIn(
            scope = scopeDefault,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    fun setProfileSources(newSources: List<MapInfoSource>) {
        _profilesInRoom.value = newSources
    }

    fun cleanup() {
        scopeDefault.cancel()
    }
}