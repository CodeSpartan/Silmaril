package ru.adan.silmaril.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.model.GroupModel
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.SettingsProvider
import ru.adan.silmaril.mud_messages.CurrentRoomMessage
import ru.adan.silmaril.misc.Position
import ru.adan.silmaril.platform.createLogger
import kotlin.math.absoluteValue

class MapViewModel(
    private val client: MudConnection,
    private val groupModel: GroupModel,
    private val onDisplayTaggedString: (String) -> Unit,
    private val onSendMessageToServer: (String) -> Unit,
    private val mapModel: MapModel,
    private val settingsProvider: SettingsProvider,
) : KoinComponent {
    val roomDataManager : RoomDataManager by inject()
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val logger = createLogger("ru.adan.silmaril.viewmodel.MapViewModel")

    /** Used for displaying "you entered this zone", "you left that zone" */
    var lastZone: Int = -100

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

    /** Room reading state - must be initialized before currentRoom flow starts */
    enum class ReadingRoomMessage {
        None,
        DetectedRoomName,
        ReadInProgress,
    }
    var readingRoom : ReadingRoomMessage = ReadingRoomMessage.None
    var roomName : String? = null
    var roomDescription : String? = null
    val nonRoomGreyTexts = setOf("Вы быстро убежали.")
    // Must be initialized before currentRoom flow, which calls onNewRoom() eagerly
    val ignoreRoomsForTreasure: Set<Int> = setOf(10145, 17900, 6512, 15120, 15130, 27305)

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

    private val colorfulJob = client.colorfulTextMessages
        .onEach { msg -> onNewColorfulText(msg) }
        .launchIn(viewModelScope)

    fun onNewColorfulText(msg: ColorfulTextMessage) {
        //logger.info { "New colorful message! Chunks: ${msg.chunks.size}" }
        //logger.info { "Text: ${msg.chunks[0].text}" }
        if (msg.chunks[0].text == "Здесь темно...") {
            roomName = null
            roomDescription = null
            return
        }
        when (readingRoom) {
            ReadingRoomMessage.None -> {
                if (msg.chunks[0].fg.ansi == AnsiColor.Cyan && msg.chunks[0].fg.isBright) {
                    //logger.info { "Detected room name" }
                    roomName = msg.chunks[0].text
                    readingRoom = ReadingRoomMessage.DetectedRoomName
                }
            }
            ReadingRoomMessage.DetectedRoomName -> {
                if (msg.chunks[0].fg.ansi != AnsiColor.None || msg.chunks[0].fg.isBright) {
                    //logger.info { "Text isn't room description" }
                    readingRoom = ReadingRoomMessage.None
                    return
                }
                if (msg.chunks[0].text.startsWith("   ")) {
                    //logger.info { "Text IS room description!" }
                    readingRoom = ReadingRoomMessage.ReadInProgress
                    roomDescription = "${msg.chunks[0].text}\n"
                } else {
                    //logger.info { "Text doesn't start with 3 spaces: ${msg.chunks[0].text}" }
                    readingRoom = ReadingRoomMessage.None
                }
            }
            ReadingRoomMessage.ReadInProgress -> {
                if (nonRoomGreyTexts.contains(msg.chunks[0].text)) return
                if (msg.chunks[0].text.startsWith("Вы видите совсем свежий след")) return
                if (msg.chunks[0].fg.ansi == AnsiColor.None && !msg.chunks[0].fg.isBright) {
                    //logger.info { "Adding text to room description" }
                    roomDescription += "${msg.chunks[0].text}\n"
                } else {
                    //logger.info { "Room description is over" }
                    readingRoom = ReadingRoomMessage.None
                }
            }
        }
    }

    fun onNewRoom(roomMsg: CurrentRoomMessage) {
        // Skip if view model is being cleaned up
        if (!viewModelScope.isActive) return

        // treasure detection
        if (!ignoreRoomsForTreasure.contains(roomMsg.roomId)
            && roomDescription != null
            && roomName != null
            && mapModel.roomById[roomMsg.roomId]?.description != null
            && roomDescription != mapModel.roomById[roomMsg.roomId]?.description
            && roomName == mapModel.roomById[roomMsg.roomId]?.name
            ) {
            if ( (roomDescription?.length ?: 0) - (mapModel.roomById[roomMsg.roomId]?.description?.length ?: 0).absoluteValue > 1 ) {
                logger.debug { "Text room name: ${roomName}, file room name: ${mapModel.roomById[roomMsg.roomId]?.name}" }
                logger.debug { "Room description from text (${roomDescription?.length}): $roomDescription" }
                logger.debug { "Room description from file (${mapModel.roomById[roomMsg.roomId]?.description?.length}): ${mapModel.roomById[roomMsg.roomId]?.description}" }
                onDisplayTaggedString("<color fg=cyan bg=dark-blue>Описание комнат не соответствует. Комната ${mapModel.roomById[roomMsg.roomId]?.name} (${roomMsg.roomId}).</color>")
            }
        }

        roomDataManager.visitRoom(zoneId = roomMsg.zoneId, roomId = roomMsg.roomId)

        if (roomMsg.zoneId != lastZone) {
            onDisplayTaggedString("Вы покинули зону: ${if (lastZone == -100) "Оффлайн" else mapModel.getZone(lastZone)?.name}")
            onDisplayTaggedString("Вы вошли в зону: ${if (roomMsg.zoneId == -100) "Оффлайн" else mapModel.getZone(roomMsg.zoneId)?.name}")
            lastZone = roomMsg.zoneId
        }

        // invoke room trigger if we're not in preview mode
        if (previewZoneMsg.zoneId == -100) {
            val roomTrigger = roomDataManager.getRoomTrigger(roomMsg.roomId)
            if (roomTrigger != null) {
                onSendMessageToServer(roomTrigger)
            }
        }
        tryMoveAlongPath(roomMsg)
    }

    fun canMoveForward() : Boolean {
        // we can forego checking party members if we're not a leader, but we should check our own stamina
        if (groupModel.isLeader() == false) {
            if (groupModel.getMyStamina()?.let { it > 5 } != true) {
                onDisplayTaggedString("Вы устали.")
                return false
            }
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

    fun pushForward() {
        tryMoveAlongPath(currentRoom.value)
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
