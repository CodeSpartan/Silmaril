package ru.adan.silmaril.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.adan.silmaril.misc.InstantSerializer
import ru.adan.silmaril.platform.getDocumentsDirectory
import java.io.File
import java.time.Instant

/**
 * Android-specific settings data.
 * No window-specific settings needed on Android.
 */
@Serializable
data class AndroidSettings(
    val gameServer: String = "adan.ru",
    val gamePort: Int = 4000,
    val autoReconnect: Boolean = true,
    val mapsUrl: String = "http://adan.ru/files/Maps.zip",
    @Serializable(with = InstantSerializer::class)
    val lastMapsUpdateDate: Instant = Instant.EPOCH,
    val font: String = "RobotoMono",
    val fontSize: Int = 15,
    val colorStyle: String = "ModernBlack",
    val gameWindows: MutableList<String> = mutableListOf("Default"),
    val splitCommands: Boolean = true,
)

/**
 * Android implementation of SettingsManagerBase.
 * Handles settings persistence without desktop window management.
 */
class AndroidSettingsManager : SettingsManagerBase() {
    private val _settings = kotlinx.coroutines.flow.MutableStateFlow(loadSettings())
    val settings: kotlinx.coroutines.flow.StateFlow<AndroidSettings> = _settings

    init {
        syncCoreSettings()
        loadProfiles()
    }

    private fun syncCoreSettings() {
        val s = settings.value
        updateCoreSettings(
            gameServer = s.gameServer,
            gamePort = s.gamePort,
            autoReconnect = s.autoReconnect,
            mapsUrl = s.mapsUrl,
            lastMapsUpdateDate = s.lastMapsUpdateDate,
            font = s.font,
            fontSize = s.fontSize,
            colorStyle = s.colorStyle,
            gameWindows = s.gameWindows,
            splitCommands = s.splitCommands
        )
    }

    private fun loadSettings(): AndroidSettings {
        if (settingsFile.exists()) {
            val json = settingsFile.readText()
            return Json { ignoreUnknownKeys = true }.decodeFromString(json)
        }
        return AndroidSettings()
    }

    override fun saveSettings() {
        val json = jsonFormat.encodeToString(AndroidSettings.serializer(), settings.value)
        settingsFile.writeText(json)
    }

    // Override base class methods to also update the Android settings object
    override fun updateFont(newFont: String) {
        _settings.value = _settings.value.copy(font = newFont)
        super.updateFont(newFont)
    }

    override fun updateFontSize(newSize: Int) {
        _settings.value = _settings.value.copy(fontSize = newSize)
        super.updateFontSize(newSize)
    }

    override fun updateColorStyle(newStyle: String) {
        _settings.value = _settings.value.copy(colorStyle = newStyle)
        super.updateColorStyle(newStyle)
    }

    override fun updateSplitSetting(newValue: Boolean) {
        _settings.value = _settings.value.copy(splitCommands = newValue)
        super.updateSplitSetting(newValue)
    }

    override fun addGameWindow(windowName: String) {
        _settings.value = _settings.value.copy(
            gameWindows = (settings.value.gameWindows + windowName).toMutableList()
        )
        super.addGameWindow(windowName)
    }

    override fun removeGameWindow(windowName: String) {
        _settings.value = _settings.value.copy(
            gameWindows = settings.value.gameWindows.filter { it != windowName }.toMutableList()
        )
        super.removeGameWindow(windowName)
    }

    override fun reorderGameWindows(newOrder: List<String>) {
        _settings.value = _settings.value.copy(gameWindows = newOrder.toMutableList())
        super.reorderGameWindows(newOrder)
    }

    override fun toggleAutoReconnect() {
        _settings.value = _settings.value.copy(autoReconnect = !_settings.value.autoReconnect)
        super.toggleAutoReconnect()
    }

    override fun updateLastMapsUpdateDate(newValue: Instant) {
        _settings.value = _settings.value.copy(lastMapsUpdateDate = newValue)
        super.updateLastMapsUpdateDate(newValue)
    }
}
