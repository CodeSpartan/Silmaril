package ru.adan.silmaril.model

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
import ru.adan.silmaril.mud_messages.RoomMobs
import ru.adan.silmaril.platform.createLogger

class MobsModel(
    private val client: MobDataSource,
    private val onMobsReceived: (newRound: Boolean, List<Creature>) -> Unit,
) : KoinComponent {

    val logger = createLogger("ru.adan.silmaril.model.MobsModel")

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
