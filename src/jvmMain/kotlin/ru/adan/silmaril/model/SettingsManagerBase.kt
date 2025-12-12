package ru.adan.silmaril.model

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import ru.adan.silmaril.misc.ProfileData
import ru.adan.silmaril.misc.InstantSerializer
import ru.adan.silmaril.misc.getProfileDirectory
import ru.adan.silmaril.misc.getProgramDirectory

/**
 * Platform-agnostic settings data.
 * Desktop adds window-specific settings on top of this.
 */
@Serializable
data class BaseSettings(
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
 * Base class for settings management with platform-agnostic functionality.
 * Desktop extends this with window-specific settings.
 */
abstract class SettingsManagerBase : SettingsProvider {
    protected val settingsFile: File = File(getProgramDirectory(), "settings.json")

    protected val _coreSettings = MutableStateFlow(CoreSettings())
    override val coreSettings: StateFlow<CoreSettings> = _coreSettings

    protected val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    protected val _profiles = MutableStateFlow(mutableListOf<String>())
    override val profiles: StateFlow<MutableList<String>> get() = _profiles

    protected val _groups = MutableStateFlow(mutableSetOf("SESSION"))
    override val groups: StateFlow<MutableSet<String>> get() = _groups

    protected val scope = CoroutineScope(Dispatchers.Default)

    protected fun loadProfiles() {
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

    override fun cleanup() {
        scope.cancel()
    }

    override fun createProfile(newProfileName: String): ProfileData {
        val profileFile = File(getProfileDirectory(), "${newProfileName}.json")
        val profileData = ProfileData()
        if (!profileFile.exists()) {
            val json = jsonFormat.encodeToString(ProfileData.serializer(), profileData)
            profileFile.writeText(json)
        }
        val currentList = profiles.value.toMutableList()
        currentList.add(newProfileName)
        _profiles.value = currentList
        return profileData
    }

    override fun loadProfile(profileName: String): ProfileData {
        val profileFile = File(getProfileDirectory(), "${profileName}.json")
        if (profileFile.exists() && profiles.value.contains(profileName)) {
            val json = profileFile.readText()
            val profileData: ProfileData = Json.decodeFromString(json)
            (profileData.enabledGroups + "SESSION").forEach { groupName -> addGroup(groupName) }
            return profileData.copy(enabledGroups = profileData.enabledGroups + "SESSION")
        } else {
            val profileData = createProfile(profileName)
            addGroup("SESSION")
            return profileData.copy(enabledGroups = profileData.enabledGroups + "SESSION")
        }
    }

    override fun deleteProfile(profile: String) {
        val profileFile = File(getProfileDirectory(), "${profile}.json")
        profileFile.delete()
        val currentList = profiles.value.toMutableList()
        currentList.remove(profile)
        _profiles.value = currentList
    }

    override fun saveProfile(profile: String, profileData: ProfileData) {
        val profileFile = File(getProfileDirectory(), "${profile}.json")
        val json = jsonFormat.encodeToString(ProfileData.serializer(), profileData)
        profileFile.writeText(json)
    }

    override fun addGroup(newGroupName: String) {
        val groupInCaps = newGroupName.uppercase()
        if (!_groups.value.contains(groupInCaps)) {
            _groups.update { currentList ->
                currentList.toMutableSet().apply {
                    add(groupInCaps)
                }
            }
        }
    }

    override fun doesGroupExist(groupName: String): Boolean {
        val groupInCaps = groupName.uppercase()
        return _groups.value.contains(groupInCaps)
    }

    // Methods to update CoreSettings - subclasses call these after updating their full settings
    protected fun updateCoreSettings(
        gameServer: String? = null,
        gamePort: Int? = null,
        autoReconnect: Boolean? = null,
        mapsUrl: String? = null,
        lastMapsUpdateDate: Instant? = null,
        font: String? = null,
        fontSize: Int? = null,
        colorStyle: String? = null,
        gameWindows: List<String>? = null,
        splitCommands: Boolean? = null
    ) {
        _coreSettings.update { current ->
            CoreSettings(
                gameServer = gameServer ?: current.gameServer,
                gamePort = gamePort ?: current.gamePort,
                autoReconnect = autoReconnect ?: current.autoReconnect,
                mapsUrl = mapsUrl ?: current.mapsUrl,
                lastMapsUpdateDate = lastMapsUpdateDate ?: current.lastMapsUpdateDate,
                font = font ?: current.font,
                fontSize = fontSize ?: current.fontSize,
                colorStyle = colorStyle ?: current.colorStyle,
                gameWindows = gameWindows ?: current.gameWindows,
                splitCommands = splitCommands ?: current.splitCommands
            )
        }
    }

    // Abstract method for subclasses to implement their own saving logic
    protected abstract fun saveSettings()

    // Common update methods that call saveSettings - open for desktop to override
    open fun updateFont(newFont: String) {
        updateCoreSettings(font = newFont)
        saveSettings()
    }

    open fun updateFontSize(newSize: Int) {
        updateCoreSettings(fontSize = newSize)
        saveSettings()
    }

    open fun updateColorStyle(newStyle: String) {
        updateCoreSettings(colorStyle = newStyle)
        saveSettings()
    }

    open fun updateSplitSetting(newValue: Boolean) {
        updateCoreSettings(splitCommands = newValue)
        saveSettings()
    }

    open fun addGameWindow(windowName: String) {
        val newWindows = (coreSettings.value.gameWindows + windowName)
        updateCoreSettings(gameWindows = newWindows)
        saveSettings()
    }

    open fun removeGameWindow(windowName: String) {
        val newWindows = coreSettings.value.gameWindows.filter { it != windowName }
        updateCoreSettings(gameWindows = newWindows)
        saveSettings()
    }

    open fun reorderGameWindows(newOrder: List<String>) {
        updateCoreSettings(gameWindows = newOrder)
        saveSettings()
    }

    open fun toggleAutoReconnect() {
        updateCoreSettings(autoReconnect = !coreSettings.value.autoReconnect)
        saveSettings()
    }

    override fun updateLastMapsUpdateDate(newValue: Instant) {
        updateCoreSettings(lastMapsUpdateDate = newValue)
        saveSettings()
    }
}
