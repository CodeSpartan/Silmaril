package ru.adan.silmaril.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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

    private val _gameWindows = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val gameWindows: StateFlow<Map<String, Profile>> = _gameWindows

    //@TODO: move knownGroupHPs to some other singleton class more appropriate for this, e.g. "GameState"
    private val _knownGroupHPs = MutableStateFlow(mapOf<String, Int>())
    val knownGroupHPs: StateFlow<Map<String, Int>> get() = _knownGroupHPs

    // called from GroupModel's coroutine
    suspend fun addKnownHp(name: String, maxHp: Int) {
        val updatedMap = _knownGroupHPs.value.toMutableMap().apply {
            this[name] = maxHp
        }
        _knownGroupHPs.emit(updatedMap)
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
}