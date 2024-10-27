package viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import model.SettingsManager

class SettingsViewModel (private val settingsManager: SettingsManager) {
    val currentFontFamily: StateFlow<String> = settingsManager.font
    val currentFontSize: StateFlow<Int> = settingsManager.fontSize
    val currentColorStyle: StateFlow<String> = settingsManager.colorStyle

    // temp method to toggle font
    fun toggleFont() {
        if (currentFontFamily.value == "Fira")
            settingsManager.updateFont("Roboto" )
        else
            settingsManager.updateFont("Fira")
    }

    fun toggleColorStyle() {
        if (currentColorStyle.value == "Black")
            settingsManager.updateColorStyle("DarkRed")
        else
            settingsManager.updateColorStyle("Black")
    }
}