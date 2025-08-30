package ru.adan.silmaril.viewmodel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import ru.adan.silmaril.mud_messages.Creature
import ru.adan.silmaril.mud_messages.CurrentRoomMessage
import ru.adan.silmaril.mud_messages.Position

data class MapInfoSource (
    val profileName: String,
    val currentRoom: StateFlow<CurrentRoomMessage>,
)

data class ProfileCreatureSource(
    val profileName: String,
    val currentCreatures: StateFlow<List<Creature>>,
)

data class MapUpdate(
    val profileName: List<String>,
    val groupMatesInFight: Boolean,
    val roomId: Int,
    val monsters: Int,
    val groupMates: Int,
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
    val mapUpdatesForRooms: StateFlow<Map<Int, MapUpdate>> =
        combine(
            profilesInRoom,      // StateFlow<List<MapInfoSource>>
            groupMatesInRoom,    // StateFlow<List<ProfileCreatureSource>>
            enemiesInRoom        // StateFlow<List<ProfileCreatureSource>>
        ) { profiles, mates, enemies ->
            Triple(profiles, mates, enemies)
        }
            .flatMapLatest { (profiles, mates, enemies) ->
                if (profiles.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    // Lookup tables by profile
                    val matesByName = mates.associateBy { it.profileName }
                    val enemiesByName = enemies.associateBy { it.profileName }

                    // Per-profile stream -> a simple “partial” record
                    data class Partial(
                        val profileName: String,
                        val roomId: Int,
                        val inFight: Boolean,
                        val matesCount: Int,
                        val monstersCount: Int
                    )

                    val perProfile: List<Flow<Partial>> = profiles.map { p ->
                        val roomFlow = p.currentRoom
                        val matesFlow = matesByName[p.profileName]?.currentCreatures ?: flowOf(emptyList())
                        val enemiesFlow = enemiesByName[p.profileName]?.currentCreatures ?: flowOf(emptyList())

                        combine(roomFlow, matesFlow, enemiesFlow) { room, mateCreatures, enemyCreatures ->
                            Partial(
                                profileName = p.profileName,
                                roomId = room.roomId,
                                inFight = mateCreatures.any { it.inSameRoom && it.position == Position.Fighting },
                                matesCount = mateCreatures.filter { it.inSameRoom }.size,
                                monstersCount = enemyCreatures.size
                            )
                        }
                    }

                    // Combine all profiles -> aggregate by room
                    combine(perProfile) { arr -> arr.toList() }
                        .map { partials: List<Partial> ->
                            partials
                                .groupBy { it.roomId }
                                .mapValues { (_, list) ->
                                    // If you expect these to be identical per room, you can take the first;
                                    // otherwise, use OR/max to be robust across slightly out-of-sync sources.
                                    val names = list.map { it.profileName }.sorted()
                                    val inFight = list.any { it.inFight }
                                    val monsters = list.maxOfOrNull { it.monstersCount } ?: 0
                                    val mates = list.maxOfOrNull { it.matesCount } ?: 0

                                    MapUpdate(
                                        profileName = names,
                                        groupMatesInFight = inFight,
                                        roomId = list.first().roomId,
                                        monsters = monsters,
                                        groupMates = mates
                                    )
                                }
                        }
                }
            }
            .stateIn(
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