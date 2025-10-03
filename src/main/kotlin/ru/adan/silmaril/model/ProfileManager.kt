package ru.adan.silmaril.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.viewmodel.MainViewModel
import ru.adan.silmaril.viewmodel.MapInfoSource
import ru.adan.silmaril.viewmodel.MapViewModel
import ru.adan.silmaril.viewmodel.ProfileCreatureSource
import ru.adan.silmaril.viewmodel.UnifiedMapsViewModel
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.nativeKeyLocation
import sun.awt.resources.awt

class ProfileManager(
    private val unifiedMapsViewModel: UnifiedMapsViewModel,
    private val settingsManager: SettingsManager
) : KoinComponent {
    var currentClient: MutableState<MudConnection>
    var currentMapViewModel: MutableState<MapViewModel>
    var currentMainViewModel: MutableState<MainViewModel>
    var currentGroupModel: MutableState<GroupModel>
    var currentMobsModel: MutableState<MobsModel>
    var currentProfileName: MutableState<String>
    var selectedTabIndex = mutableStateOf(0)

    val logger = KotlinLogging.logger {}

    private val _gameWindows = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val gameWindows: StateFlow<Map<String, Profile>> = _gameWindows

    //@TODO: move knownGroupHPs to some other singleton class more appropriate for this, e.g. "GameState"
    private val _knownGroupHPs = MutableStateFlow(mapOf<String, Int>())
    val knownGroupHPs: StateFlow<Map<String, Int>> get() = _knownGroupHPs

    //@TODO: implement mob hp prediction
    private val _knownMobsHPs = MutableStateFlow(mapOf<String, Int>())
    val knownMobsHPs: StateFlow<Map<String, Int>> get() = _knownMobsHPs


    //@TODO: move knownGroupHPs to some other singleton class more appropriate for this, e.g. "GameState"
    // called from GroupModel's coroutine
    suspend fun addKnownHp(name: String, maxHp: Int) {
        if (_knownGroupHPs.value[name] != maxHp) {
            val updatedMap = _knownGroupHPs.value.toMutableMap().apply {
                this[name] = maxHp
            }
            _knownGroupHPs.emit(updatedMap)
        }
    }

    fun updateUnifiedMapViewModel() {
        val roomSources: List<MapInfoSource> =
            gameWindows.value.values.map { profile ->
                MapInfoSource(
                    profileName = profile.profileName,
                    currentRoom = profile.mapViewModel.currentRoom
                )
            }

        val groupSources: List<ProfileCreatureSource> =
            gameWindows.value.values.map { profile ->
                ProfileCreatureSource(
                    profileName = profile.profileName,
                    currentCreatures = profile.groupModel.groupMates
                )
            }

        val enemySources: List<ProfileCreatureSource> =
            gameWindows.value.values.map { profile ->
                ProfileCreatureSource(
                    profileName = profile.profileName,
                    currentCreatures = profile.mobsModel.mobs
                )
            }

        unifiedMapsViewModel.setSources(roomSources, groupSources, enemySources)
    }

    fun addProfile(windowName: String) {
        val newProfile: Profile = get { parametersOf(windowName) }
        _gameWindows.value += (windowName to newProfile)
        updateUnifiedMapViewModel()
    }

    fun removeWindow(windowName: String) {
        // don't close when there's only one tab left
        if (gameWindows.value.values.size <= 1) {
            currentMainViewModel.value.displayErrorMessage("Нельзя закрыть единственное окно. Откройте другое, затем закройте это.")
            return
        }
        gameWindows.value[windowName]?.onCloseWindow()
        _gameWindows.value = gameWindows.value.filterKeys { it != windowName }.toMap()
        updateUnifiedMapViewModel()
    }

    init {
        settingsManager.settings.value.gameWindows.forEach { gameWindow ->
            addProfile(gameWindow)
        }
        if (gameWindows.value.values.isEmpty()) {
            addProfile("Default")
        }
        currentClient = mutableStateOf(gameWindows.value.values.first().client)
        currentMainViewModel = mutableStateOf(gameWindows.value.values.first().mainViewModel)
        currentMapViewModel = mutableStateOf(gameWindows.value.values.first().mapViewModel)
        currentGroupModel = mutableStateOf(gameWindows.value.values.first().groupModel)
        currentMobsModel = mutableStateOf(gameWindows.value.values.first().mobsModel)
        currentProfileName = mutableStateOf(gameWindows.value.values.first().profileName.capitalized())
    }

    fun cleanup() {
        gameWindows.value.values.forEach {
            it.cleanup()
        }
    }

    fun displaySystemMessage(msg: String) {
        currentMainViewModel.value.displaySystemMessage(msg)
    }

    fun switchWindow(index: Int) : Boolean {
        if (index >= gameWindows.value.size) return false
        switchWindow(gameWindows.value.values.toList()[index].profileName)
        return true
    }

    fun switchWindow(windowName: String): Boolean {
        val newIndex = gameWindows.value.values.indexOfFirst { it.profileName == windowName }
        if (newIndex == -1) return false
        selectedTabIndex.value = newIndex
        currentClient.value = gameWindows.value[windowName]!!.client
        currentMainViewModel.value = gameWindows.value[windowName]!!.mainViewModel
        currentMapViewModel.value = gameWindows.value[windowName]!!.mapViewModel
        currentProfileName.value = windowName.capitalized()
        currentGroupModel.value = gameWindows.value[windowName]!!.groupModel
        currentMobsModel.value = gameWindows.value[windowName]!!.mobsModel
        return true
    }

    fun getCurrentProfile(): Profile? {
        return gameWindows.value.entries.firstOrNull { (key, value) ->
            key.equals(
                currentProfileName.value,
                ignoreCase = true
            )
        }?.value
    }

    fun getProfileByName(name: String): Profile? {
        return gameWindows.value.entries.firstOrNull { (key, value) -> key.equals(name, ignoreCase = true) }?.value
    }

    // AWT sends 3 keycodes: Down, TYPED, Up.
    // So when we've caught our hotkey, we need to handle Typed and Up that will follow, otherwise the input field will type the letter
    var suppressTextInput = false

    //@TODO: move this to a separate class
    // Return true to consume the event
    fun onHotkeyKey(onPreviewKeyEvent: KeyEvent): Boolean {
        if (onPreviewKeyEvent.type == KeyEventType.KeyDown && onPreviewKeyEvent.key == Key.C && onPreviewKeyEvent.isAltPressed) {
            if (currentClient.value.connectionState.value != ConnectionState.CONNECTED && currentClient.value.connectionState.value != ConnectionState.CONNECTING)
                currentClient.value.connect()
            return true
        }

        if (onPreviewKeyEvent.type == KeyEventType.KeyDown && onPreviewKeyEvent.key == Key.Z && onPreviewKeyEvent.isAltPressed) {
            if (currentClient.value.connectionState.value == ConnectionState.CONNECTED || currentClient.value.connectionState.value == ConnectionState.CONNECTING)
                currentClient.value.forceDisconnect()
            return true
        }

        // Explanation: AWT sends Down, Typed and Up events, but in Compose we only have Down and Up
        // Compose receives the Typed event as Up & Key.Unknown
        // Compose receives no useful information about this event, no keycode, so we just suppress it if it follows a bound hotkey
        // If we don't suppress it, the key gets typed into the input field
        if (onPreviewKeyEvent.type == KeyEventType.KeyDown) {
            val handled = getCurrentProfile()?.scriptingEngine?.processHotkey(onPreviewKeyEvent)
            if (handled == true) {
                suppressTextInput = true
            }
            return handled ?: false
        } else {
            val handled = getCurrentProfile()?.scriptingEngine?.isBoundHotkeyEvent(onPreviewKeyEvent)
            if (handled == true) {
                suppressTextInput = false
                return true
            }
            // this is how Typed is sent
            if (onPreviewKeyEvent.key == Key.Unknown) {
                return suppressTextInput
            }
            return false
        }
    }

    fun getWindowById(id: Int): Profile? {
        return gameWindows.value.values.elementAtOrNull(id-1)
    }
}