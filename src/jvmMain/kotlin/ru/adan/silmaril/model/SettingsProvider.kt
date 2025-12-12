package ru.adan.silmaril.model

import kotlinx.coroutines.flow.StateFlow
import ru.adan.silmaril.misc.ProfileData
import java.time.Instant

/**
 * Core settings data that's platform-agnostic.
 * Desktop-specific window settings are kept separate.
 */
data class CoreSettings(
    val gameServer: String = "adan.ru",
    val gamePort: Int = 4000,
    val autoReconnect: Boolean = true,
    val mapsUrl: String = "http://adan.ru/files/Maps.zip",
    val lastMapsUpdateDate: Instant = Instant.EPOCH,
    val font: String = "RobotoMono",
    val fontSize: Int = 15,
    val colorStyle: String = "ModernBlack",
    val gameWindows: List<String> = listOf("Default"),
    val splitCommands: Boolean = true,
)

/**
 * Interface for accessing settings from platform-agnostic code.
 * Implemented by SettingsManager in desktopMain.
 */
interface SettingsProvider {
    val coreSettings: StateFlow<CoreSettings>
    val profiles: StateFlow<MutableList<String>>
    val groups: StateFlow<MutableSet<String>>

    fun addGroup(newGroupName: String)
    fun doesGroupExist(groupName: String): Boolean
    fun loadProfile(profileName: String): ProfileData
    fun saveProfile(profile: String, profileData: ProfileData)
    fun createProfile(newProfileName: String): ProfileData
    fun deleteProfile(profile: String)
    fun updateLastMapsUpdateDate(newValue: Instant)
    fun cleanup()
}
