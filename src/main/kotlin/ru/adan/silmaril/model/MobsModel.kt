package ru.adan.silmaril.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.mud_messages.Creature
import ru.adan.silmaril.mud_messages.RoomMobs
import kotlin.getValue

class MobsModel(
    private val client: MudConnection,
    private val settingsManager: SettingsManager,
    private val onMobsReceived: (newRound: Boolean, List<Creature>) -> Unit,
) : KoinComponent {

    val logger = KotlinLogging.logger {}
    val profileManager: ProfileManager by inject()

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val roomMobs: StateFlow<RoomMobs> = client.lastMonstersMessage

    val mobs: StateFlow<List<Creature>> =
        roomMobs
            .map { it.mobs }
            .stateIn(
                scopeDefault,
                SharingStarted.Eagerly,
                roomMobs.value.mobs
            )

    private val mobsListener = roomMobs
        .onEach { creatures ->
            onMobsReceived(roomMobs.value.newRound, roomMobs.value.mobs)
        }
        .launchIn(scopeDefault)

    fun getMobs(): List<Creature> {
        return roomMobs.value.mobs
    }
    
    fun cleanup() {
        scopeDefault.cancel()
    }
}