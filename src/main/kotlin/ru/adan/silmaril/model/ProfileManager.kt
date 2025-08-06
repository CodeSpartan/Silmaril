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
import ru.adan.silmaril.view.Tab
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

    fun tabSwitched(newIndex: Int, tabName: String) : Int {
        currentClient.value = gameWindows.value[tabName]!!.client
        currentMainViewModel.value = gameWindows.value[tabName]!!.mainViewModel
        currentProfileName.value = tabName.capitalized()
        return newIndex
    }

    fun tabClosed(tabs: List<Tab>, index: Int, tabName: String, selectedTabIndex: Int) : Int {
        var returnIndex = selectedTabIndex
        gameWindows.value[tabName]?.onCloseWindow()
        assignNewWindowsTemp(gameWindows.value.filterKeys { it != tabName }.toMap())
        // if we're closing the currently opened tab, switch to the first available one
        if (index == selectedTabIndex) {
            val firstValidProfile = gameWindows.value.values.first()
            currentClient.value = firstValidProfile.client
            currentMainViewModel.value = firstValidProfile.mainViewModel

            val firstAvailableTabIndex = tabs.indexOfFirst { it.title == firstValidProfile.profileName }
            returnIndex = if (firstAvailableTabIndex > index) firstAvailableTabIndex - 1 else firstAvailableTabIndex
            currentProfileName.value = firstValidProfile.profileName.capitalized()
        }
        // if we're closing a tab to the left of current, the current id will need to be adjusted to the left
        else {
            if (selectedTabIndex > index) {
                returnIndex--
            }
        }
        return returnIndex
    }
}