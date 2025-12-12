package ru.adan.silmaril.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
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
import kotlinx.coroutines.flow.update
import ru.adan.silmaril.scripting.isBoundHotkeyEvent
import ru.adan.silmaril.scripting.processHotkey

data class GameWindowsState(
    val orderedKeys: List<String>,
    val byKey: Map<String, Profile>
)

class ProfileManager(
    private val unifiedMapsViewModel: UnifiedMapsViewModel,
    private val settingsManager: SettingsManager
) : KoinComponent, SystemMessageDisplay, LoreDisplayCallback, KnownHpTracker, ProfileManagerInterface {
    var currentClient: MutableState<MudConnection>
    var currentMapViewModel: MutableState<MapViewModel>
    var currentMainViewModel: MutableState<MainViewModel>
    var currentGroupModel: MutableState<GroupModel>
    var currentMobsModel: MutableState<MobsModel>
    var currentProfileName: MutableState<String>
    var selectedTabIndex = mutableStateOf(0)

    val logger = KotlinLogging.logger {}

    private val _gameWindows = MutableStateFlow(
        GameWindowsState(emptyList(), emptyMap())
    )
    val gameWindows: StateFlow<GameWindowsState> = _gameWindows

    //@TODO: move knownGroupHPs to some other singleton class more appropriate for this, e.g. "GameState"
    private val _knownGroupHPs = MutableStateFlow(mapOf<String, Int>())
    val knownGroupHPs: StateFlow<Map<String, Int>> get() = _knownGroupHPs

    //@TODO: implement mob hp prediction
    private val _knownMobsHPs = MutableStateFlow(mapOf<String, Int>())
    val knownMobsHPs: StateFlow<Map<String, Int>> get() = _knownMobsHPs

    fun moveGameWindow(windowKey: String, newIndex: Int) {
        val fromIndex = _gameWindows.value.orderedKeys.indexOf(windowKey)
        if (fromIndex == -1) {
            logger.debug { "moveGameWindow by name: didn't find the name "}
            return
        }
        moveGameWindow(fromIndex, newIndex)
        switchWindow(windowKey)
    }

    fun moveGameWindow(fromIndex: Int, newIndex: Int) {
        logger.debug { "moving window $fromIndex to $newIndex" }
        logger.debug { "order before: ${_gameWindows.value.orderedKeys.joinToString(",")}" }

        _gameWindows.update { state ->
            val size = state.orderedKeys.size
            if (fromIndex !in 0 until size) return@update state
            if (size <= 1) return@update state

            // Work on a mutable copy of the order
            val order = state.orderedKeys.toMutableList()
            val key = order.removeAt(fromIndex)

            // Compute target on the shortened list; allowing append at end (newIndex == original size)
            val insertAt = newIndex.coerceIn(0, order.size)
            order.add(insertAt, key)

            // If nothing effectively changed, bail out
            if (order == state.orderedKeys) return@update state

            // Rebuild map in the new order (preserves iteration order)
            val orderedMap = LinkedHashMap<String, Profile>(order.size).apply {
                for (k in order) {
                    state.byKey[k]?.let { put(k, it) }
                }
            }

            state.copy(orderedKeys = order, byKey = orderedMap)
        }

        updateUnifiedMapViewModel()
        settingsManager.reorderGameWindows(_gameWindows.value.orderedKeys)
        logger.debug { "new order: ${_gameWindows.value.orderedKeys.joinToString(",")}" }
    }

    override fun isProfileOpen(name: String) : Boolean {
        return gameWindows.value.byKey.keys.contains(name)
    }

    override fun getAllOpenedProfileNames() : Set<String> {
        return gameWindows.value.byKey.keys
    }

    //@TODO: move knownGroupHPs to some other singleton class more appropriate for this, e.g. "GameState"
    // called from GroupModel's coroutine
    override suspend fun addKnownHp(name: String, maxHp: Int) {
        if (_knownGroupHPs.value[name] != maxHp) {
            val updatedMap = _knownGroupHPs.value.toMutableMap().apply {
                this[name] = maxHp
            }
            _knownGroupHPs.emit(updatedMap)
        }
    }

    fun updateUnifiedMapViewModel() {
        val roomSources: List<MapInfoSource> =
            gameWindows.value.byKey.values.map { profile ->
                MapInfoSource(
                    profileName = profile.profileName,
                    currentRoom = profile.mapViewModel.currentRoom
                )
            }

        val groupSources: List<ProfileCreatureSource> =
            gameWindows.value.byKey.values.map { profile ->
                ProfileCreatureSource(
                    profileName = profile.profileName,
                    currentCreatures = profile.groupModel.groupMates
                )
            }

        val enemySources: List<ProfileCreatureSource> =
            gameWindows.value.byKey.values.map { profile ->
                ProfileCreatureSource(
                    profileName = profile.profileName,
                    currentCreatures = profile.mobsModel.mobs
                )
            }

        unifiedMapsViewModel.setSources(roomSources, groupSources, enemySources)
    }

    fun addProfile(windowName: String) {
        val newProfile: Profile = get { parametersOf(windowName) }
        _gameWindows.update { state ->
            val newByKey = state.byKey + (windowName to newProfile)
            val newOrder = state.orderedKeys + windowName
            state.copy(orderedKeys = newOrder, byKey = newByKey)
        }
        updateUnifiedMapViewModel()
    }

    fun removeWindow(windowName: String) {
        val snapshot = _gameWindows.value

        // don't close when there's only one tab left
        if (snapshot.orderedKeys.size <= 1) {
            currentMainViewModel.value.displayErrorMessage(
                "Нельзя закрыть единственное окно. Откройте другое, затем закройте это."
            )
            return
        }

        // Perform side effect outside of update
        snapshot.byKey[windowName]?.onCloseWindow()

        _gameWindows.update { state ->
            if (windowName !in state.byKey) return@update state

            val newByKey = state.byKey - windowName
            val newOrder = state.orderedKeys.filter { it != windowName }

            state.copy(orderedKeys = newOrder, byKey = newByKey)
        }

        updateUnifiedMapViewModel()
    }

    init {
        settingsManager.settings.value.gameWindows.forEach { gameWindow ->
            addProfile(gameWindow)
        }
        if (gameWindows.value.byKey.values.isEmpty()) {
            addProfile("Default")
        }
        currentClient = mutableStateOf(gameWindows.value.byKey.values.first().client)
        currentMainViewModel = mutableStateOf(gameWindows.value.byKey.values.first().mainViewModel)
        currentMapViewModel = mutableStateOf(gameWindows.value.byKey.values.first().mapViewModel)
        currentGroupModel = mutableStateOf(gameWindows.value.byKey.values.first().groupModel)
        currentMobsModel = mutableStateOf(gameWindows.value.byKey.values.first().mobsModel)
        currentProfileName = mutableStateOf(gameWindows.value.byKey.values.first().profileName.capitalized())
    }

    fun cleanup() {
        gameWindows.value.byKey.values.forEach {
            it.cleanup()
        }
    }

    // ProfileManagerInterface implementation
    override fun getCurrentProfileName(): String = currentProfileName.value
    override fun getCurrentMainViewModel(): MainViewModel = currentMainViewModel.value
    override fun getCurrentMapViewModel(): MapViewModel = currentMapViewModel.value
    override fun getCurrentGroupModel(): GroupModel = currentGroupModel.value
    override fun getCurrentMobsModel(): MobsModel = currentMobsModel.value
    override fun getCurrentClient(): MudConnection = currentClient.value

    override fun getProfileByName(name: String): ProfileInterface? {
        return gameWindows.value.byKey.entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value
    }

    override fun getAllOpenedProfiles(): Collection<ProfileInterface> {
        return gameWindows.value.byKey.values
    }

    override fun getWindowById(id: Int): ProfileInterface? {
        return gameWindows.value.byKey.values.elementAtOrNull(id - 1)
    }

    override fun displaySystemMessage(message: String) {
        currentMainViewModel.value.displaySystemMessage(message)
    }

    override fun displayTaggedText(text: String, brightWhiteAsDefault: Boolean) {
        currentMainViewModel.value.displayTaggedText(text, brightWhiteAsDefault)
    }

    override suspend fun processLoreLines(lines: List<String>) {
        currentClient.value.processLoreLines(lines)
    }

    override fun switchWindow(index: Int) : Boolean {
        if (index >= gameWindows.value.byKey.size) return false
        switchWindow(gameWindows.value.byKey.values.toList()[index].profileName)
        return true
    }

    override fun switchWindow(windowName: String): Boolean {
        val newIndex = gameWindows.value.byKey.values.indexOfFirst { it.profileName == windowName }
        if (newIndex == -1) return false
        selectedTabIndex.value = newIndex
        currentClient.value = gameWindows.value.byKey[windowName]!!.client
        currentMainViewModel.value = gameWindows.value.byKey[windowName]!!.mainViewModel
        currentMapViewModel.value = gameWindows.value.byKey[windowName]!!.mapViewModel
        currentProfileName.value = windowName.capitalized()
        currentGroupModel.value = gameWindows.value.byKey[windowName]!!.groupModel
        currentMobsModel.value = gameWindows.value.byKey[windowName]!!.mobsModel
        return true
    }

    override fun getCurrentProfile(): ProfileInterface? {
        return getCurrentProfileInternal()
    }

    /** Desktop-specific method to get the full Profile with scriptingEngine */
    fun getCurrentProfileInternal(): Profile? {
        return gameWindows.value.byKey.entries.firstOrNull { (key, _) ->
            key.equals(
                currentProfileName.value,
                ignoreCase = true
            )
        }?.value
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
            val handled = getCurrentProfileInternal()?.scriptingEngine?.processHotkey(onPreviewKeyEvent)
            if (handled == true) {
                suppressTextInput = true
            }
            return handled ?: false
        } else {
            val handled = getCurrentProfileInternal()?.scriptingEngine?.isBoundHotkeyEvent(onPreviewKeyEvent)
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
}