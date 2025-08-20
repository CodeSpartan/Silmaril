package ru.adan.silmaril.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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

class ProfileManager(private val settingsManager: SettingsManager) : KoinComponent {
    var currentClient: MutableState<MudConnection>
    var currentMainViewModel: MutableState<MainViewModel>
    var currentGroupModel: MutableState<GroupModel>
    var currentProfileName: MutableState<String>
    var selectedTabIndex = mutableStateOf(0)

    val logger = KotlinLogging.logger {}

    private val _gameWindows = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val gameWindows: StateFlow<Map<String, Profile>> = _gameWindows

    //@TODO: move knownGroupHPs to some other singleton class more appropriate for this, e.g. "GameState"
    private val _knownGroupHPs = MutableStateFlow(mapOf<String, Int>())
    val knownGroupHPs: StateFlow<Map<String, Int>> get() = _knownGroupHPs

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

    fun addProfile(windowName: String) {
        val newProfile: Profile = get { parametersOf(windowName) }
        _gameWindows.value += (windowName to newProfile)
    }

    fun assignNewWindowsTemp(newMap: Map<String, Profile> ) {
        _gameWindows.value = newMap
    }

    init {
        settingsManager.settings.value.gameWindows.forEach {
            gameWindow -> addProfile(gameWindow)
        }
        currentClient = mutableStateOf(gameWindows.value.values.first().client)
        currentMainViewModel = mutableStateOf(gameWindows.value.values.first().mainViewModel)
        currentGroupModel = mutableStateOf(gameWindows.value.values.first().groupModel)
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

    fun switchWindow(windowName: String) : Boolean {
        val newIndex = gameWindows.value.values.indexOfFirst { it.profileName == windowName }
        if (newIndex == -1) return false
        selectedTabIndex.value = newIndex
        currentClient.value = gameWindows.value[windowName]!!.client
        currentMainViewModel.value = gameWindows.value[windowName]!!.mainViewModel
        currentProfileName.value = windowName.capitalized()
        return true
    }

    //@TODO: move this to a separate class
    // @Return: true = consume the event
    fun onHotkeyKey(onPreviewKeyEvent: KeyEvent) : Boolean {
        if (onPreviewKeyEvent.type == KeyEventType.KeyDown) { // Ensures we only react once per press
            if (onPreviewKeyEvent.isCtrlPressed && onPreviewKeyEvent.key == Key.S) {
                logger.info {"Ctrl + S was pressed!"}
                return true // Consume the event
            } else {
                return false // Do not consume the event
            }
        }
        return false
    }

    /**
    // A map to convert string representations to Key objects.
    // You can expand this map with any other keys you need.
    private val keyMap: Map<String, Key> = mapOf(
    "A" to Key.A, "B" to Key.B, "C" to Key.C, "D" to Key.D, "E" to Key.E,
    "F" to Key.F, "G" to Key.G, "H" to Key.H, "I" to Key.I, "J" to Key.J,
    "K" to Key.K, "L" to Key.L, "M" to Key.M, "N" to Key.N, "O" to Key.O,
    "P" to Key.P, "Q" to Key.Q, "R" to Key.R, "S" to Key.S, "T" to Key.T,
    "U" to Key.U, "V" to Key.V, "W" to Key.W, "X" to Key.X, "Y" to Key.Y,
    "Z" to Key.Z,
    "F1" to Key.F1, "F2" to Key.F2, "F3" to Key.F3, "F4" to Key.F4,
    "F5" to Key.F5, "F6" to Key.F6, "F7" to Key.F7, "F8" to Key.F8,
    "F9" to Key.F9, "F10" to Key.F10, "F11" to Key.F11, "F12" to Key.F12,
    "ENTER" to Key.Enter,
    "BACKSPACE" to Key.Backspace,
    "DELETE" to Key.Delete,
    "TAB" to Key.Tab
    // Add other keys as needed
    )

    /**
     * Parses a string like "Ctrl+Shift+S" into a Hotkey object.
    */
    fun parseHotkey(configString: String): Hotkey? {
    val parts = configString.uppercase().split('+').map { it.trim() }
    if (parts.isEmpty()) return null

    val keyName = parts.last()
    val key = keyMap[keyName] ?: return null // Return null if the key is not in our map

    val modifiers = parts.dropLast(1).toSet()

    return Hotkey(
    key = key,
    isCtrlPressed = "CTRL" in modifiers,
    isShiftPressed = "SHIFT" in modifiers,
    isAltPressed = "ALT" in modifiers
    )
    }

    /**
     * Checks if a KeyEvent matches a defined Hotkey.
    */
    fun KeyEvent.matches(hotkey: Hotkey): Boolean {
    return this.key == hotkey.key &&
    this.isCtrlPressed == hotkey.isCtrlPressed &&
    this.isShiftPressed == hotkey.isShiftPressed &&
    this.isAltPressed == hotkey.isAltPressed
    }
     */
}