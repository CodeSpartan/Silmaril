package viewmodel

import kotlinx.coroutines.flow.StateFlow
import model.SettingsManager

class SettingsViewModel (private val settingsManager: SettingsManager) {
    val currentFontFamily: StateFlow<String> = settingsManager.font
    val currentFontSize: StateFlow<Int> = settingsManager.fontSize
    val currentColorStyleName: StateFlow<String> = settingsManager.colorStyle

    // temp method to toggle font
    fun toggleFont() {
        if (currentFontFamily.value == "FiraMono")
            settingsManager.updateFont("RobotoMono" )
        else
            settingsManager.updateFont("FiraMono")
    }

    fun toggleColorStyle() {
        settingsManager.updateColorStyle (when (currentColorStyleName.value) {
            "ClassicBlack" -> "ModernBlack"
            "ModernBlack" -> "ModernDarkRed"
            "ModernDarkRed" -> "ClassicBlack"
            else -> "ClassicBlack"
        })
    }
}