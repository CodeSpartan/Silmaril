package ru.adan.silmaril.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.platform.createLogger
import ru.adan.silmaril.viewmodel.MainViewModel
import ru.adan.silmaril.viewmodel.MapViewModel

/**
 * Android implementation of ProfileManagerInterface.
 * Manages multiple AndroidProfile instances for a multi-window MUD client.
 */
class AndroidProfileManager(
    val settingsManager: AndroidSettingsManager,
    private val mapModel: MapModel,
    private val outputWindowModel: OutputWindowModel,
) : KoinComponent, ProfileManagerInterface, SystemMessageDisplay, KnownHpTracker, LoreDisplayCallback {

    private val logger = createLogger("AndroidProfileManager")
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Current selected profile components
    private val _currentClient = MutableStateFlow<MudConnection?>(null)
    private val _currentMainViewModel = MutableStateFlow<MainViewModel?>(null)
    private val _currentMapViewModel = MutableStateFlow<MapViewModel?>(null)
    private val _currentGroupModel = MutableStateFlow<GroupModel?>(null)
    private val _currentMobsModel = MutableStateFlow<MobsModel?>(null)
    private val _currentProfileName = MutableStateFlow("")

    val currentClient: StateFlow<MudConnection?> = _currentClient
    val currentMainViewModel: StateFlow<MainViewModel?> = _currentMainViewModel
    val currentMapViewModel: StateFlow<MapViewModel?> = _currentMapViewModel
    val currentGroupModel: StateFlow<GroupModel?> = _currentGroupModel
    val currentMobsModel: StateFlow<MobsModel?> = _currentMobsModel
    val currentProfileName: StateFlow<String> = _currentProfileName

    // Wrapper to ensure StateFlow emits on reorder (Map.equals ignores order)
    data class ProfilesState(
        val profiles: Map<String, AndroidProfile>,
        val revision: Int = 0
    )

    // All open profiles
    // Note: We expose ProfilesState directly to avoid stateIn's equality checking
    // which would ignore the order change (Map.equals ignores order)
    private val _profilesState = MutableStateFlow(ProfilesState(emptyMap()))
    val profilesState: StateFlow<ProfilesState> = _profilesState

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex

    // Known HP tracking (for group display)
    private val _knownGroupHPs = MutableStateFlow<Map<String, Int>>(emptyMap())
    val knownGroupHPs: StateFlow<Map<String, Int>> = _knownGroupHPs

    // Connection state tracking (for foreground service notification)
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates

    init {
        // Load profiles from settings
        settingsManager.settings.value.gameWindows.forEach { windowName ->
            addProfile(windowName)
        }
        // Ensure at least one profile exists
        if (_profilesState.value.profiles.isEmpty()) {
            addProfile("Default")
        }

        // Set initial current values
        val firstProfile = _profilesState.value.profiles.values.firstOrNull()
        if (firstProfile != null) {
            _currentClient.value = firstProfile.client
            _currentMainViewModel.value = firstProfile.mainViewModel
            _currentMapViewModel.value = firstProfile.mapViewModel
            _currentGroupModel.value = firstProfile.groupModel
            _currentMobsModel.value = firstProfile.mobsModel
            _currentProfileName.value = firstProfile.profileName.capitalized()
        }
    }

    fun addProfile(windowName: String) {
        // Ensure profile exists in settings (creates if needed)
        if (!settingsManager.profiles.value.contains(windowName)) {
            settingsManager.createProfile(windowName)
        }

        val newProfile = AndroidProfile(
            profileName = windowName,
            settingsManager = settingsManager,
            mapModel = mapModel,
            outputWindowModel = outputWindowModel
        )
        _profilesState.update { current ->
            // Preserve LinkedHashMap type to maintain order
            val orderedMap = LinkedHashMap(current.profiles)
            orderedMap[windowName] = newProfile
            ProfilesState(orderedMap, current.revision + 1)
        }

        // Add to gameWindows list (persists which profiles are open)
        if (!settingsManager.coreSettings.value.gameWindows.contains(windowName)) {
            settingsManager.addGameWindow(windowName)
        }

        logger.info { "Added profile: $windowName" }
    }

    fun removeProfile(windowName: String) {
        val snapshot = _profilesState.value.profiles

        // Don't close when there's only one profile left
        if (snapshot.size <= 1) {
            _currentMainViewModel.value?.displayErrorMessage(
                "Нельзя закрыть единственное окно. Откройте другое, затем закройте это."
            )
            return
        }

        // Cleanup the profile (disconnects, cancels coroutines)
        snapshot[windowName]?.onCloseWindow()

        _profilesState.update { current ->
            // Preserve LinkedHashMap type to maintain order
            val orderedMap = LinkedHashMap(current.profiles)
            orderedMap.remove(windowName)
            ProfilesState(orderedMap, current.revision + 1)
        }

        // If we removed the current profile, switch to another
        if (_currentProfileName.value.equals(windowName, ignoreCase = true)) {
            val remainingProfiles = _profilesState.value.profiles.values.toList()
            if (remainingProfiles.isNotEmpty()) {
                switchWindow(remainingProfiles.first().profileName)
            }
        }

        logger.info { "Removed profile: $windowName" }
    }

    fun deleteProfile(windowName: String) {
        // First close it if it's open
        if (_profilesState.value.profiles.containsKey(windowName)) {
            removeProfile(windowName)
        }

        // Then permanently delete from disk
        settingsManager.deleteProfile(windowName)

        logger.info { "Deleted profile permanently: $windowName" }
    }

    fun reorderProfiles(newOrder: List<String>) {
        // Create the reordered map with incremented revision
        // The revision ensures StateFlow emits even though Map.equals() ignores order
        val currentState = _profilesState.value
        val orderedMap = LinkedHashMap<String, AndroidProfile>()
        newOrder.forEach { name ->
            currentState.profiles[name]?.let { profile ->
                orderedMap[name] = profile
            }
        }

        _profilesState.value = ProfilesState(orderedMap, currentState.revision + 1)

        // Update selectedTabIndex to match where the current profile moved to
        // This prevents the pager from switching to a different profile when order changes
        val currentProfile = _currentProfileName.value
        val newIndex = newOrder.indexOfFirst { it.equals(currentProfile, ignoreCase = true) }
        if (newIndex >= 0) {
            _selectedTabIndex.value = newIndex
        }

        // Persist to settings
        settingsManager.reorderGameWindows(newOrder)

        logger.info { "Reordered profiles: ${newOrder.joinToString()}, revision: ${currentState.revision + 1}, updated index to $newIndex" }
    }

    fun cleanup() {
        _profilesState.value.profiles.values.forEach { profile ->
            profile.cleanup()
        }
    }

    // ProfileManagerInterface implementation
    override fun getCurrentProfileName(): String = _currentProfileName.value

    override fun getCurrentMainViewModel(): MainViewModel {
        return _currentMainViewModel.value
            ?: throw IllegalStateException("No current MainViewModel")
    }

    override fun getCurrentMapViewModel(): MapViewModel {
        return _currentMapViewModel.value
            ?: throw IllegalStateException("No current MapViewModel")
    }

    override fun getCurrentGroupModel(): GroupModel {
        return _currentGroupModel.value
            ?: throw IllegalStateException("No current GroupModel")
    }

    override fun getCurrentMobsModel(): MobsModel {
        return _currentMobsModel.value
            ?: throw IllegalStateException("No current MobsModel")
    }

    override fun getCurrentClient(): MudConnection {
        return _currentClient.value
            ?: throw IllegalStateException("No current MudConnection")
    }

    override fun getProfileByName(name: String): ProfileInterface? {
        return _profilesState.value.profiles.entries.firstOrNull { (key, _) ->
            key.equals(name, ignoreCase = true)
        }?.value
    }

    override fun getAllOpenedProfiles(): Collection<ProfileInterface> {
        return _profilesState.value.profiles.values
    }

    override fun switchWindow(windowName: String): Boolean {
        val profile = _profilesState.value.profiles[windowName] ?: return false
        val newIndex = _profilesState.value.profiles.keys.toList().indexOf(windowName)

        _selectedTabIndex.value = newIndex
        _currentClient.value = profile.client
        _currentMainViewModel.value = profile.mainViewModel
        _currentMapViewModel.value = profile.mapViewModel
        _currentGroupModel.value = profile.groupModel
        _currentMobsModel.value = profile.mobsModel
        _currentProfileName.value = windowName.capitalized()

        logger.info { "Switched to profile: $windowName" }
        return true
    }

    override fun switchWindow(index: Int): Boolean {
        val profilesList = _profilesState.value.profiles.values.toList()
        if (index >= profilesList.size) return false
        return switchWindow(profilesList[index].profileName)
    }

    override fun getWindowById(id: Int): ProfileInterface? {
        // IDs are 1-indexed
        return _profilesState.value.profiles.values.elementAtOrNull(id - 1)
    }

    override fun getCurrentProfile(): ProfileInterface? {
        return _profilesState.value.profiles.entries.firstOrNull { (key, _) ->
            key.equals(_currentProfileName.value, ignoreCase = true)
        }?.value
    }

    override fun getAllOpenedProfileNames(): Set<String> {
        return _profilesState.value.profiles.keys
    }

    override fun isProfileOpen(name: String): Boolean {
        return _profilesState.value.profiles.keys.contains(name)
    }

    // HP tracking for group display (KnownHpTracker interface)
    override suspend fun addKnownHp(name: String, maxHp: Int) {
        if (_knownGroupHPs.value[name] != maxHp) {
            _knownGroupHPs.update { current ->
                current + (name to maxHp)
            }
        }
    }

    override fun displaySystemMessage(message: String) {
        _currentMainViewModel.value?.displaySystemMessage(message)
    }

    // LoreDisplayCallback implementation
    override fun displayTaggedText(text: String, brightWhiteAsDefault: Boolean) {
        _currentMainViewModel.value?.displayTaggedText(text, brightWhiteAsDefault)
    }

    override suspend fun processLoreLines(lines: List<String>) {
        lines.forEach { line ->
            _currentMainViewModel.value?.displayTaggedText(line, false)
        }
    }

    /**
     * Called by AndroidProfile to report connection state changes.
     * This allows MainActivity to update the foreground service notification.
     */
    fun updateConnectionState(profileName: String, state: ConnectionState) {
        _connectionStates.update { current ->
            current + (profileName to state)
        }
        logger.debug { "Connection state updated: $profileName -> $state" }
    }
}
