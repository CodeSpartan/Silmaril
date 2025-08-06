package ru.adan.silmaril.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.viewmodel.MainViewModel

class ProfileManager(private val settingsManager: SettingsManager) : KoinComponent {
    var currentClient: MutableState<MudConnection>
    var currentMainViewModel: MutableState<MainViewModel>
    var currentProfileName: MutableState<String>

    private val _gameWindows = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val gameWindows: StateFlow<Map<String, Profile>> = _gameWindows

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
}