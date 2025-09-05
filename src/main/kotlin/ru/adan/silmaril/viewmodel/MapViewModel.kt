package ru.adan.silmaril.viewmodel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.model.GroupModel
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.mud_messages.CurrentRoomMessage
import ru.adan.silmaril.mud_messages.Position
import kotlin.getValue

class MapViewModel(
    private val client: MudConnection,
    private val groupModel: GroupModel,
    private val onDisplayTaggedString: (String) -> Unit,
    private val onSendMessageToServer: (String) -> Unit,
    private val mapModel: MapModel,
    private val settingsManager: SettingsManager
) : KoinComponent {
    val roomDataManager : RoomDataManager by inject()
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val logger = KotlinLogging.logger {}

    /** Pathfinding related */
    var targetRoomId: Int = -1
    var targetZoneId: Int = -1
    var pathToFollow: List<Int> = emptyList()
    private val _pathToHighlight = MutableStateFlow<Set<Int>>(emptySet())
    val pathToHighlight: StateFlow<Set<Int>> = _pathToHighlight.asStateFlow()
    private var cachedGroupMatesInSameRoom = 0 // this var holds real values only during path walking
    private var ignoreGroupMates = false

    /** Zone-preview related */
    var lastValidRoomBeforePreview = CurrentRoomMessage.EMPTY
    var previewZoneMsg = CurrentRoomMessage.EMPTY

    // don't take currentRoom and assume it's always a source of truth
    // if we're in preview mode, it's not a source of truth, so use getCurrentRoom() instead
    val currentRoom: StateFlow<CurrentRoomMessage> =
        client.currentRoomMessages
            // hijack messages to insert custom zone, which we want to preview through #previewZone
            .transform {
                if (previewZoneMsg.zoneId == -100) {
                    emit(it)
                }
                else {
                    // if the character has moved, turn off preview
                    if (it.roomId != lastValidRoomBeforePreview.roomId) {
                        stopPreview()
                        emit(it)
                    } else {
                        emit(previewZoneMsg)
                    }
                }
            }
            .distinctUntilChanged()
            .onEach {
                msg -> onNewRoom(msg)
                if (targetRoomId != -1) {
                    cachedGroupMatesInSameRoom = groupModel.getGroupMates().filter { it.isPlayerCharacter && it.inSameRoom }.size
                }
            } // side-effect
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = CurrentRoomMessage.EMPTY
            )

    fun onNewRoom(roomMsg: CurrentRoomMessage) {
        roomDataManager.visitRoom(zoneId = roomMsg.zoneId, roomId = roomMsg.roomId)
        tryMoveAlongPath(roomMsg)
    }

    fun canMoveForward() : Boolean {
        // we can forego checking party members if we're not a leader, but we should check our own stamina
        if (groupModel.isLeader() == false) {
            onDisplayTaggedString("Вы устали.")
            return groupModel.getMyStamina()?.let { it > 5 } ?: false
        }

        if (ignoreGroupMates) return true

        groupModel.getGroupMates().forEach { groupMate ->
            if (groupMate.isPlayerCharacter && groupMate.movesPercent <= 7) {
                onDisplayTaggedString("Ваша группа устала.")
                return false
            }
            if (groupMate.isPlayerCharacter && groupMate.inSameRoom && groupMate.position != Position.Standing && groupMate.position != Position.Riding) {
                onDisplayTaggedString("Согруппник расселся и не готов идти.")
                return false
            }
        }

        val newGroupMatesInSameRoom = groupModel.getGroupMates().filter { it.isPlayerCharacter && it.inSameRoom }.size
        if (newGroupMatesInSameRoom < cachedGroupMatesInSameRoom) {
            onDisplayTaggedString("Согруппник отстал от вас.")
            return false
        }

        return true
    }

    fun tryMoveAlongPath(roomMsg: CurrentRoomMessage) {
        if (targetRoomId == -1) return

        if (roomMsg.zoneId == targetZoneId || roomMsg.roomId == targetRoomId) {
            resetPathfinding()
            onDisplayTaggedString("Вы пришли.")
            return
        }

        if (!canMoveForward()) {
            return
        }

        val curIndex = pathToFollow.lastIndexOf(roomMsg.roomId)
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
        ignoreGroupMates = false
    }

    fun cleanup() {
        viewModelScope.cancel()
    }

    fun setPathTarget(inTargetRoomId: Int, inTargetZoneId: Int, inPath: List<Int>, ignoreGroup: Boolean = false) {
        targetRoomId = inTargetRoomId
        targetZoneId = inTargetZoneId
        pathToFollow = inPath
        ignoreGroupMates = ignoreGroup
        _pathToHighlight.value = pathToFollow.toSet()
        val curRoomId = getCurrentRoom()
        cachedGroupMatesInSameRoom = groupModel.getGroupMates().filter { it.inSameRoom }.size
        tryMoveAlongPath(curRoomId)
    }

    fun getCurrentRoom() : CurrentRoomMessage {
        return if (previewZoneMsg.zoneId == -100)
            currentRoom.value
        else
            lastValidRoomBeforePreview
    }

    fun previewZone(zoneId: Int) {
        val desiredZone = mapModel.getZone(zoneId)
        if (desiredZone == null) {
            stopPreview()
            return
        }
        val desiredRoomId = desiredZone.roomsList.first().id
        if (previewZoneMsg.zoneId == -100)
            lastValidRoomBeforePreview = currentRoom.value
        previewZoneMsg = CurrentRoomMessage(roomId = desiredRoomId, zoneId = desiredZone.id)
    }

    fun stopPreview() {
        previewZoneMsg = CurrentRoomMessage.EMPTY
    }
}