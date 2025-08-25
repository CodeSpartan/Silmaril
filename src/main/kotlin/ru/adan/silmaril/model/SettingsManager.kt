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
import ru.adan.silmaril.misc.ProfileData
import ru.adan.silmaril.misc.WindowSettings
import ru.adan.silmaril.misc.getProfileDirectory
import ru.adan.silmaril.misc.getProgramDirectory
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
    val gameWindows: MutableList<String> = mutableListOf("Default"), // value is the window name and the profile name
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
    // Whether commands separated by a semicolon (e.g. "say hello;w") are echoed on separate lines or a single line
    val splitCommands: Boolean = true,
)

class SettingsManager {
    private val settingsFile: File = File(getProgramDirectory(), "settings.json")
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings // Expose the immutable StateFlow

    private val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
        serializersModule = module
    }

    private val _settingsFlow = MutableStateFlow(settings.value.windowSettings)
    private val _floatWindowsFlow = MutableStateFlow(settings.value.floatWindows)
    fun getFloatingWindowState(windowName: String) : FloatWindowSettings {
        return settings.value.floatWindows[windowName] ?: FloatWindowSettings()
    }

    private val _profiles = MutableStateFlow(mutableListOf<String>())
    val profiles: StateFlow<MutableList<String>> get() = _profiles

    // Groups aren't a real entity. The variable is populated from all triggers/aliases/etc that belong to groups.
    // If a new trigger/alias is created, we simply update the list
    private val _groups = MutableStateFlow(mutableSetOf("SESSION"))
    val groups: StateFlow<MutableSet<String>> get() = _groups

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        loadProfiles()
        debounceSaveSettings()
    }

    private fun loadSettings() : Settings {
        if (settingsFile.exists()) {
            val json = settingsFile.readText()
            val firstSettings : Settings = Json.decodeFromString(json)
            return firstSettings
        }
        else
            return Settings()
    }

    private fun loadProfiles() {
        val profileDir = File(getProfileDirectory())
        val jsonFiles = mutableListOf<String>()
        if (profileDir.exists() && profileDir.isDirectory) {
            profileDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "json") {
                    jsonFiles.add(file.nameWithoutExtension)
                }
            }
        }
        _profiles.value = jsonFiles
    }

    private fun saveSettings() {
        val json = jsonFormat.encodeToString(settings.value)
        settingsFile.writeText(json)
    }

    fun cleanup() {
        scope.cancel()
    }

    // This function can observe very rapid state changes, but will actually only saveSettings after a delay of 500 ms
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
            .launchIn(scope) // Ensure to launch in a defined CoroutineScope
    }

    fun updateFont(newFont: String) {
        _settings.update { currentState ->
            currentState.copy(font = newFont)
        }
        saveSettings()
    }

    fun updateFontSize(newSize: Int) {
        _settings.update { currentState ->
            currentState.copy(fontSize = newSize)
        }
        saveSettings()
    }

    fun updateColorStyle(newStyle: String) {
        _settings.update { currentState ->
            currentState.copy(colorStyle = newStyle)
        }
        saveSettings()
    }

    fun updateWindowState(newWindowState: WindowState) {
        _settingsFlow.value = WindowSettings(
            windowPlacement = newWindowState.placement,
            windowSize = newWindowState.size,
            windowPosition = newWindowState.position,
        )
        // no need to call saveSettings(), it's handled in debounceSaveSettings
    }

    fun updateFloatingWindowState(windowName: String, show: Boolean) {
        // this is the only way I found to trigger an emit, by changing an instance of a value and a reference to the map
        val updatedWindows = _floatWindowsFlow.value.toMutableMap()
        updatedWindows[windowName] = FloatWindowSettings(
            show = show,
            windowPosition = updatedWindows[windowName]!!.windowPosition,
            windowSize = updatedWindows[windowName]!!.windowSize
        )
        _floatWindowsFlow.value = updatedWindows
    }

    fun updateFloatingWindow(windowName: String, location: Point, size: Dimension) {
        // this is the only way I found to trigger an emit, by changing an instance of a value and a reference to the map
        val updatedWindows = _floatWindowsFlow.value.toMutableMap()
        updatedWindows[windowName] = FloatWindowSettings(
            show = updatedWindows[windowName]!!.show,
            windowPosition = location,
            windowSize = size
        )
        _floatWindowsFlow.value = updatedWindows
    }

    fun updateSplitSetting(newValue : Boolean) {
        _settings.update { currentState ->
            currentState.copy(splitCommands = newValue)
        }
        saveSettings()
    }

    fun addGameWindow(windowName : String) {
        _settings.update { currentState ->
            currentState.copy(gameWindows = (settings.value.gameWindows + windowName).toMutableList())
        }
        saveSettings()
    }

    fun removeGameWindow(windowName : String) {
        _settings.update { currentState ->
            currentState.copy(gameWindows = settings.value.gameWindows.filter { it != windowName }.toMutableList())
        }
        saveSettings()
    }

    fun createProfile(newProfileName: String) : ProfileData {
        val profileFile = File(getProfileDirectory(), "${newProfileName}.json")
        val profileData = ProfileData()
        if (!profileFile.exists()) {
            val json = jsonFormat.encodeToString(profileData)
            profileFile.writeText(json)
        }
        // add to list of profiles
        val currentList = profiles.value.toMutableList()
        currentList.add(newProfileName)
        _profiles.value = currentList
        return profileData
    }

    fun loadProfile(profileName: String) : ProfileData {
        val profileFile = File(getProfileDirectory(), "${profileName}.json")
        if (profileFile.exists() && profiles.value.contains(profileName))
        {
            val json = profileFile.readText()
            val profileData : ProfileData = Json.decodeFromString(json)
            (profileData.enabledGroups + "SESSION").forEach { groupName -> addGroup(groupName) }
            return profileData.copy(enabledGroups = profileData.enabledGroups + "SESSION")
        } else {
            val profileData = createProfile(profileName)
            addGroup("SESSION")
            return profileData.copy(enabledGroups = profileData.enabledGroups + "SESSION")
        }
    }

    fun deleteProfile(profile: String) {
        val profileFile = File(getProfileDirectory(), "${profile}.json")
        profileFile.delete()
        val currentList = profiles.value.toMutableList()
        currentList.remove(profile)
        _profiles.value = currentList
    }

    fun saveProfile(profile: String, profileData: ProfileData) {
        val profileFile = File(getProfileDirectory(), "${profile}.json")
        val json = jsonFormat.encodeToString(profileData)
        profileFile.writeText(json)
    }

    fun addGroup(newGroupName: String) {
        val groupInCaps = newGroupName.uppercase()
        if (!_groups.value.contains(groupInCaps)) {
            _groups.update { currentList ->
                // Create a new mutable list based on the old one, add the new item, and return it.
                // This new list becomes the new value of the StateFlow.
                currentList.toMutableSet().apply {
                    add(groupInCaps)
                }
            }
        }
    }

    fun doesGroupExist(groupName: String): Boolean {
        val groupInCaps = groupName.uppercase()
        return _groups.value.contains(groupInCaps)
    }

    fun toggleAutoReconnect() {
        _settings.update { currentState ->
            currentState.copy(autoReconnect = !currentState.autoReconnect)
        }
        saveSettings()
    }

    fun updateLastMapsUpdateDate(newValue: Instant) {
        _settings.update { currentState ->
            currentState.copy(lastMapsUpdateDate = newValue)
        }
        saveSettings()
    }
}