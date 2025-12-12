package ru.adan.silmaril.model

import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import ru.adan.silmaril.misc.FloatWindowSettings
import ru.adan.silmaril.misc.InstantSerializer
import ru.adan.silmaril.misc.WindowSettings
import ru.adan.silmaril.misc.module
import java.awt.Dimension
import java.awt.Point

// This class is saved as json in getProgramDirectory()/settings.ini
// Below are its default values when not specified in the .ini
@Serializable
data class Settings(
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
    val windowSettings: WindowSettings = WindowSettings(),
    val floatWindows: Map<String, FloatWindowSettings> = mapOf(
        "MapWindow" to FloatWindowSettings(
            show = true,
            windowPosition = Point(600, 300),
            windowSize = Dimension(400, 400),
        ),
        "AdditionalOutput" to FloatWindowSettings(
            show = true,
            windowPosition = Point(600, 700),
            windowSize = Dimension(400, 400),
        ),
        "GroupWindow" to FloatWindowSettings(
            show = true,
            windowPosition = Point(100, 200),
            windowSize = Dimension(400, 300),
        ),
        "MobsWindow" to FloatWindowSettings(
            show = true,
            windowPosition = Point(100, 700),
            windowSize = Dimension(400, 300),
        ),
    ),
    val splitCommands: Boolean = true,
)

class SettingsManager : SettingsManagerBase() {
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings

    private val _settingsFlow = MutableStateFlow(settings.value.windowSettings)
    private val _floatWindowsFlow = MutableStateFlow(settings.value.floatWindows)

    init {
        // Initialize core settings from loaded desktop settings
        syncCoreSettings()
        loadProfiles()
        debounceSaveSettings()
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

    private fun loadSettings(): Settings {
        if (settingsFile.exists()) {
            val json = settingsFile.readText()
            return Json { ignoreUnknownKeys = true }.decodeFromString(json)
        }
        return Settings()
    }

    override fun saveSettings() {
        val json = jsonFormat.encodeToString(Settings.serializer(), settings.value)
        settingsFile.writeText(json)
    }

    fun getFloatingWindowState(windowName: String): FloatWindowSettings {
        return settings.value.floatWindows[windowName] ?: FloatWindowSettings()
    }

    @OptIn(FlowPreview::class)
    private fun debounceSaveSettings(debouncePeriod: Long = 500L) {
        combine(_settingsFlow, _floatWindowsFlow) { value1, value2 -> Pair(value1, value2) }
            .debounce(debouncePeriod)
            .onEach { (value1, value2) ->
                _settings.update { currentState ->
                    currentState.copy(windowSettings = value1, floatWindows = value2)
                }
                saveSettings()
            }
            .launchIn(scope)
    }

    // Override base class methods to also update the full Settings object
    override fun updateFont(newFont: String) {
        _settings.update { it.copy(font = newFont) }
        super.updateFont(newFont)
    }

    override fun updateFontSize(newSize: Int) {
        _settings.update { it.copy(fontSize = newSize) }
        super.updateFontSize(newSize)
    }

    override fun updateColorStyle(newStyle: String) {
        _settings.update { it.copy(colorStyle = newStyle) }
        super.updateColorStyle(newStyle)
    }

    override fun updateSplitSetting(newValue: Boolean) {
        _settings.update { it.copy(splitCommands = newValue) }
        super.updateSplitSetting(newValue)
    }

    override fun addGameWindow(windowName: String) {
        _settings.update { it.copy(gameWindows = (settings.value.gameWindows + windowName).toMutableList()) }
        super.addGameWindow(windowName)
    }

    override fun removeGameWindow(windowName: String) {
        _settings.update { it.copy(gameWindows = settings.value.gameWindows.filter { it != windowName }.toMutableList()) }
        super.removeGameWindow(windowName)
    }

    override fun reorderGameWindows(newOrder: List<String>) {
        _settings.update { it.copy(gameWindows = newOrder.toMutableList()) }
        super.reorderGameWindows(newOrder)
    }

    override fun toggleAutoReconnect() {
        _settings.update { it.copy(autoReconnect = !it.autoReconnect) }
        super.toggleAutoReconnect()
    }

    override fun updateLastMapsUpdateDate(newValue: Instant) {
        _settings.update { it.copy(lastMapsUpdateDate = newValue) }
        super.updateLastMapsUpdateDate(newValue)
    }

    // Desktop-specific window methods
    fun updateWindowState(newWindowState: WindowState) {
        _settingsFlow.value = WindowSettings(
            windowPlacement = newWindowState.placement,
            windowSize = newWindowState.size,
            windowPosition = newWindowState.position,
        )
    }

    fun updateFloatingWindowState(windowName: String, show: Boolean) {
        val updatedWindows = _floatWindowsFlow.value.toMutableMap()
        updatedWindows[windowName] = FloatWindowSettings(
            show = show,
            windowPosition = updatedWindows[windowName]!!.windowPosition,
            windowSize = updatedWindows[windowName]!!.windowSize
        )
        _floatWindowsFlow.value = updatedWindows
    }

    fun updateFloatingWindow(windowName: String, location: Point, size: Dimension) {
        val updatedWindows = _floatWindowsFlow.value.toMutableMap()
        updatedWindows[windowName] = FloatWindowSettings(
            show = updatedWindows[windowName]!!.show,
            windowPosition = location,
            windowSize = size
        )
        _floatWindowsFlow.value = updatedWindows
    }
}
