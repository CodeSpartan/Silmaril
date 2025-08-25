package ru.adan.silmaril.viewmodel

import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.mud_messages.CurrentRoomMessage
import ru.adan.silmaril.mud_messages.GroupStatusMessage
import kotlin.getValue

class MapViewModel(
    private val client: MudConnection,
    private val settingsManager: SettingsManager
) : KoinComponent {
    val roomDataManager : RoomDataManager by inject()
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
    }

    fun cleanup() {
        viewModelScope.cancel()
    }
}