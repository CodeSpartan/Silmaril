package viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import model.SettingsManager

class SettingsViewModel (private val settingsManager: SettingsManager) {
    val currentFontFamily: StateFlow<String> = settingsManager.font

    private val _currentFontSize = MutableStateFlow(15)
    val currentFontSize: StateFlow<Int> = _currentFontSize

    // temp method to toggle font
    fun toggleFont() {
        if (currentFontFamily.value == "Fira")
            settingsManager.updateFont("Roboto" )
        else
            settingsManager.updateFont("Fira")
    }
}