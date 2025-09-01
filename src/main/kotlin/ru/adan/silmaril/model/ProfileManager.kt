package ru.adan.silmaril.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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
        gameWindows.value[windowName]?.onCloseWindow()
        _gameWindows.value = gameWindows.value.filterKeys { it != windowName }.toMap()
        updateUnifiedMapViewModel()
    }

    init {
        settingsManager.settings.value.gameWindows.forEach { gameWindow ->
            addProfile(gameWindow)
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

    fun switchWindow(index: Int) {
        switchWindow(gameWindows.value.values.toList()[index].profileName)
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

    //@TODO: move this to a separate class
    // Return true to consume the event
    fun onHotkeyKey(onPreviewKeyEvent: KeyEvent): Boolean {
        if (onPreviewKeyEvent.type == KeyEventType.KeyDown) {
            return getCurrentProfile()?.scriptingEngine?.processHotkey(onPreviewKeyEvent) ?: false
        }
        return false
    }

    fun getWindowById(id: Int): Profile? {
        return gameWindows.value.values.elementAtOrNull(id-1)
    }
}