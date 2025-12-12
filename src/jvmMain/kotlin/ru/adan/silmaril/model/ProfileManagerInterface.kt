package ru.adan.silmaril.model

import ru.adan.silmaril.misc.Variable
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.viewmodel.MainViewModel
import ru.adan.silmaril.viewmodel.MapViewModel

/**
 * Platform-agnostic interface for ProfileManager.
 * This allows the scripting system to access profiles without depending on
 * the concrete ProfileManager class which has Compose UI dependencies.
 */
interface ProfileManagerInterface {
    /** Get current profile name */
    fun getCurrentProfileName(): String

    /** Get current MainViewModel */
    fun getCurrentMainViewModel(): MainViewModel

    /** Get current MapViewModel */
    fun getCurrentMapViewModel(): MapViewModel

    /** Get current GroupModel */
    fun getCurrentGroupModel(): GroupModel

    /** Get current MobsModel */
    fun getCurrentMobsModel(): MobsModel

    /** Get current MudConnection */
    fun getCurrentClient(): MudConnection

    fun getProfileByName(name: String): ProfileInterface?
    fun getAllOpenedProfiles(): Collection<ProfileInterface>
    fun switchWindow(windowName: String): Boolean
    fun switchWindow(index: Int): Boolean
    fun getWindowById(id: Int): ProfileInterface?
    fun getCurrentProfile(): ProfileInterface?
    fun getAllOpenedProfileNames() : Set<String>
    fun isProfileOpen(name: String) : Boolean
}

/**
 * Platform-agnostic interface for Profile.
 * Exposes the necessary properties and methods for the scripting system.
 */
interface ProfileInterface {
    val profileName: String
    val mainViewModel: MainViewModel
    val mapViewModel: MapViewModel
    val groupModel: GroupModel
    val mobsModel: MobsModel
    val client: MudConnection
    val scriptingEngine: ScriptingEngine

    fun getVariable(varName: String): Variable?
    fun setVariable(varName: String, varValue: Any)
    fun removeVariable(varName: String)
    fun isGroupActive(groupName: String): Boolean

    // Text macro loading methods
    fun addSingleTriggerToWindow(condition: String, action: String, groupName: String, priority: Int, isRegex: Boolean)
    fun addSingleAliasToWindow(shorthand: String, action: String, groupName: String, priority: Int)
    fun addSingleSubToWindow(shorthand: String, action: String, groupName: String, priority: Int, isRegex: Boolean)
    fun addSingleHotkeyToWindow(keyString: String, action: String, groupName: String, priority: Int)
}
