package ru.adan.silmaril.viewmodel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import ru.adan.silmaril.mud_messages.Creature
import ru.adan.silmaril.mud_messages.CurrentRoomMessage

data class MapInfoSource (
    val profileName: String,
    val currentRoom: StateFlow<CurrentRoomMessage>,
)

data class ProfileCreatureSource(
    val profileName: String,
    val currentCreatures: StateFlow<List<Creature>>,
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

    private val _groupMatesInRoom = MutableStateFlow<List<ProfileCreatureSource>>(emptyList())
    val groupMatesInRoom: StateFlow<List<ProfileCreatureSource>> = _groupMatesInRoom

    private val _enemiesInRoom = MutableStateFlow<List<ProfileCreatureSource>>(emptyList())
    val enemiesInRoom: StateFlow<List<ProfileCreatureSource>> = _enemiesInRoom

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

    fun setSources(newSources: List<MapInfoSource>, groupSources: List<ProfileCreatureSource>, enemySources: List<ProfileCreatureSource>) {
        _profilesInRoom.value = newSources
        _groupMatesInRoom.value = groupSources
        _enemiesInRoom.value = enemySources
    }

    fun cleanup() {
        scopeDefault.cancel()
    }
}