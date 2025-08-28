package ru.adan.silmaril.viewmodel

import androidx.compose.runtime.collectAsState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.mud_messages.CurrentRoomMessage
import ru.adan.silmaril.mud_messages.GroupStatusMessage
import kotlin.getValue

class MapViewModel(
    private val client: MudConnection,
    private val onDisplayTaggedString: (String) -> Unit,
    private val onSendMessageToServer: (String) -> Unit,
    private val mapModel: MapModel,
    private val settingsManager: SettingsManager
) : KoinComponent {
    val roomDataManager : RoomDataManager by inject()
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val logger = KotlinLogging.logger {}

    var targetRoomId: Int = -1
    var targetZoneId: Int = -1
    var pathToFollow: List<Int> = emptyList()
    private val _pathToHighlight = MutableStateFlow<Set<Int>>(emptySet())
    val pathToHighlight: StateFlow<Set<Int>> = _pathToHighlight.asStateFlow()

    val currentRoom: StateFlow<CurrentRoomMessage> =
        client.currentRoomMessages
            .distinctUntilChanged()
            .onEach { msg -> onNewRoom(msg) } // side-effect
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = CurrentRoomMessage.EMPTY
            )

    fun onNewRoom(roomMsg: CurrentRoomMessage) {
        roomDataManager.visitRoom(zoneId = roomMsg.zoneId, roomId = roomMsg.roomId)
        tryMoveAlongPath(roomMsg)
    }

    fun tryMoveAlongPath(roomMsg: CurrentRoomMessage) {
        if (targetRoomId == -1) return

        if (roomMsg.zoneId == targetZoneId || roomMsg.roomId == targetRoomId) {
            resetPathfinding()
            onDisplayTaggedString("Вы пришли.")
            return
        }

        val curIndex = pathToFollow.indexOf(roomMsg.roomId)
        if (curIndex == -1) return // we're off path
        val nextRoomId = pathToFollow[curIndex+1]
        val currentRoom = mapModel.roomById[roomMsg.roomId]
        if (currentRoom == null) return
        val exit = currentRoom.exitsList.firstOrNull { it.roomId == nextRoomId }
        if (exit == null) return
        onSendMessageToServer(exit.direction)
    }

    fun resetPathfinding() {
        targetRoomId = -1
        targetZoneId = -1
        pathToFollow = emptyList()
        _pathToHighlight.value = emptySet()
    }

    fun cleanup() {
        viewModelScope.cancel()
    }

    fun setPathTarget(inTargetRoomId: Int, inTargetZoneId: Int, inPath: List<Int>) {
        targetRoomId = inTargetRoomId
        targetZoneId = inTargetZoneId
        pathToFollow = inPath
        _pathToHighlight.value = pathToFollow.toSet()
        tryMoveAlongPath(currentRoom.value)
    }
}