package ru.adan.silmaril.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.misc.getAliasesDirectory
import ru.adan.silmaril.misc.getTriggersDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.getValue

@Serializable
data class SimpleTriggerData(
    val condition: String,
    val action: String,
    val priority: Int,
    val isRegex: Boolean
)

@Serializable
data class SimpleAliasData(
    val shorthand: String,
    val action: String,
    val priority: Int,
)

@OptIn(FlowPreview::class)
class TextTriggerManager() : KoinComponent {

    @OptIn(ExperimentalAtomicApi::class)
    var startedToInitialize = AtomicBoolean(false)
    @OptIn(ExperimentalAtomicApi::class)
    var hasInitialized = AtomicBoolean(false)
    val logger = KotlinLogging.logger {}
    val profileManager: ProfileManager by inject()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _textTriggersByGroup = MutableStateFlow<Map<String, List<SimpleTriggerData>>>(emptyMap())
    val textTriggersByGroup: StateFlow<Map<String, List<SimpleTriggerData>>> = _textTriggersByGroup.asStateFlow()

    private val _textAliasesByGroup = MutableStateFlow<Map<String, List<SimpleAliasData>>>(emptyMap())
    val textAliasesByGroup: StateFlow<Map<String, List<SimpleAliasData>>> = _textAliasesByGroup.asStateFlow()

    @OptIn(ExperimentalAtomicApi::class)
    public suspend fun initExplicit(callerProfile: Profile) {
        if (hasInitialized.load()) {
            textTriggersByGroup.value.forEach { (groupName, triggers) ->
                triggers.forEach { trig ->
                    callerProfile.addSingleTriggerToWindow(trig.condition, trig.action, groupName, trig.priority, trig.isRegex)
                }
            }
            val totalNumberOfTriggers = textTriggersByGroup.value.values.sumOf { it.size }
            callerProfile.scriptingEngine.sortTriggersByPriority()
            callerProfile.mainViewModel.displaySystemMessage("Простых триггеров загружено: $totalNumberOfTriggers")

            textAliasesByGroup.value.forEach { (groupName, aliases) ->
                aliases.forEach { alias ->
                    callerProfile.addSingleAliasToWindow(alias.shorthand, alias.action, groupName, alias.priority)
                }
            }
            val totalNumberOfAliases = textAliasesByGroup.value.values.sumOf { it.size }
            callerProfile.scriptingEngine.sortAliasesByPriority()
            callerProfile.mainViewModel.displaySystemMessage("Простых алиасов загружено: $totalNumberOfAliases")
            return
        }

        // It tries to change 'false' to 'true'.
        // If it fails (because it was already 'true'), it returns false, and we exit.
        if (!startedToInitialize.compareAndSet(expectedValue = false, newValue = true)) {
            logger.debug { "TextTriggerManager: Initialization already completed or in progress." }
            return
        }

        coroutineScope.launch {
            val initialTriggerData = loadTextTriggers()
            val initialAliasData = loadTextAliases()

            // triggers
            initialTriggerData.forEach { (groupName, triggers) ->
                triggers.forEach { trig ->
                    profileManager.gameWindows.value.values.firstOrNull()?.addSingleTriggerToAll(trig.condition, trig.action, groupName, trig.priority, trig.isRegex)
                }
            }
            profileManager.gameWindows.value.values.forEach { profile -> profile.scriptingEngine.sortTriggersByPriority() }
            _textTriggersByGroup.value = initialTriggerData
            val totalNumberOfTriggers = textTriggersByGroup.value.values.sumOf { it.size }
            profileManager.currentMainViewModel.value.displaySystemMessage("Простых триггеров загружено: $totalNumberOfTriggers")

            // aliases
            initialAliasData.forEach { (groupName, aliases) ->
                aliases.forEach { alias ->
                    profileManager.gameWindows.value.values.firstOrNull()?.addSingleAliasToAll(alias.shorthand, alias.action, groupName, alias.priority)
                }
            }
            profileManager.gameWindows.value.values.forEach { profile -> profile.scriptingEngine.sortAliasesByPriority() }
            _textAliasesByGroup.value = initialAliasData
            val totalNumberOfAliases = textAliasesByGroup.value.values.sumOf { it.size }
            profileManager.currentMainViewModel.value.displaySystemMessage("Простых алиасов загружено: $totalNumberOfAliases")


            hasInitialized.store(true)

            launch {
                _textTriggersByGroup
                    .drop(1)
                    .debounce(500L) // Still debounce to batch rapid changes.
                    .onEach {
                        logger.debug { "TextTriggerManager: change detected, saving text triggers..." }
                        saveTextTriggers()
                    }
                    .collect()
            }

            launch {
                _textAliasesByGroup
                    .drop(1)
                    .debounce(500L) // Still debounce to batch rapid changes.
                    .onEach {
                        logger.debug { "TextAliasManager: change detected, saving text aliases..." }
                        saveTextAliases()
                    }
                    .collect()
            }
        }
    }

    private suspend fun loadTextTriggers() : Map<String, List<SimpleTriggerData>> {
        return withContext(Dispatchers.IO) {
            logger.debug { "Loading text triggers from disk..." }
            val triggersDir = File(getTriggersDirectory())

            val triggerFiles =
                triggersDir.listFiles { file, name -> name.endsWith(".yaml", ignoreCase = true) }
                    ?: emptyArray()

            if (triggerFiles.isEmpty()) return@withContext emptyMap()

            triggerFiles.associate { triggerFile ->
                val groupName = triggerFile.nameWithoutExtension
                val yaml = triggerFile.readText()
                val triggers = Yaml.default.decodeFromString<List<SimpleTriggerData>>(string = yaml)
                groupName to triggers
            }
        }
    }

    fun saveTextTriggers() {
        logger.debug { "Saving text triggers to disk..." }
        try {
            for ((groupName, triggerData) in textTriggersByGroup.value) {
                val file = Paths.get(getTriggersDirectory(), "$groupName.yaml")
                if (triggerData.isEmpty()) {
                    Files.deleteIfExists(file)
                } else {
                    val yaml = Yaml.default.encodeToString<List<SimpleTriggerData>>(value = triggerData)
                    file.toFile().writeText(yaml)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save text triggers" }
        }
    }

    fun saveTextTrigger(condition: String, action: String, groupName: String, priority: Int, isRegex: Boolean) {
        val newTriggerData = SimpleTriggerData(condition, action, priority, isRegex)
        _textTriggersByGroup.update { currentMap ->
            val mutableMap = currentMap.toMutableMap()
            val groupTriggers = mutableMap.getOrPut(groupName) { emptyList() }.toMutableList()
            groupTriggers.add(newTriggerData)
            mutableMap[groupName] = groupTriggers
            mutableMap
        }
    }

    fun deleteTextTrigger(condition: String, action: String, groupName: String, priority: Int, isRegex: Boolean) {
        _textTriggersByGroup.update { currentMap ->
            val mutableMap = currentMap.toMutableMap()
            // Find the list of triggers for the given group. If it doesn't exist, do nothing.
            val currentTriggers = mutableMap[groupName] ?: return@update currentMap
            val updatedTriggers = currentTriggers.filterNot {
                it.condition == condition && it.action == action && it.priority == priority && it.isRegex == isRegex }
            if (updatedTriggers.isEmpty()) {
                mutableMap[groupName] = emptyList()
            } else {
                mutableMap[groupName] = updatedTriggers
            }
            mutableMap
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
    }

    private suspend fun loadTextAliases() : Map<String, List<SimpleAliasData>> {
        return withContext(Dispatchers.IO) {
            logger.debug { "Loading text aliases from disk..." }
            val aliasesDir = File(getAliasesDirectory())

            val aliasFiles =
                aliasesDir.listFiles { file, name -> name.endsWith(".yaml", ignoreCase = true) }
                    ?: emptyArray()

            if (aliasFiles.isEmpty()) return@withContext emptyMap()

            aliasFiles.associate { aliasFile ->
                val groupName = aliasFile.nameWithoutExtension
                val yaml = aliasFile.readText()
                val aliases = Yaml.default.decodeFromString<List<SimpleAliasData>>(string = yaml)
                groupName to aliases
            }
        }
    }

    fun saveTextAliases() {
        logger.debug { "Saving text aliases to disk..." }
        try {
            for ((groupName, aliasDataList) in textAliasesByGroup.value) {
                val file = Paths.get(getAliasesDirectory(), "$groupName.yaml")
                if (aliasDataList.isEmpty()) {
                    Files.deleteIfExists(file)
                } else {
                    val yaml = Yaml.default.encodeToString<List<SimpleAliasData>>(value = aliasDataList)
                    file.toFile().writeText(yaml)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save text triggers" }
        }
    }

    fun saveTextAlias(shorthand: String, action: String, groupName: String, priority: Int) {
        val newAliasData = SimpleAliasData(shorthand, action, priority)
        _textAliasesByGroup.update { currentMap ->
            val mutableMap = currentMap.toMutableMap()
            val groupAliases = mutableMap.getOrPut(groupName) { emptyList() }.toMutableList()
            groupAliases.add(newAliasData)
            mutableMap[groupName] = groupAliases
            mutableMap
        }
    }

    fun deleteTextAlias(shorthand: String, action: String, groupName: String, priority: Int) {
        _textAliasesByGroup.update { currentMap ->
            val mutableMap = currentMap.toMutableMap()
            // Find the list of triggers for the given group. If it doesn't exist, do nothing.
            val currentAliases = mutableMap[groupName] ?: return@update currentMap
            val updatedAliases = currentAliases.filterNot {
                it.shorthand == shorthand && it.action == action && it.priority == priority }
            if (updatedAliases.isEmpty()) {
                mutableMap[groupName] = emptyList()
            } else {
                mutableMap[groupName] = updatedAliases
            }
            mutableMap
        }
    }
}