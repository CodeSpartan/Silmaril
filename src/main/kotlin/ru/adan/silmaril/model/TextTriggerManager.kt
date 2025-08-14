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

    @OptIn(ExperimentalAtomicApi::class)
    public suspend fun initExplicit(callerProfile: Profile) {
        if (hasInitialized.load()) {
            textTriggersByGroup.value.forEach { (groupName, triggers) ->
                triggers.forEach { trig ->
                    callerProfile.addSingleTriggerToWindow(trig.condition, trig.action, groupName, trig.priority, trig.isRegex)
                }
            }
            val totalNumberOfTriggers = textTriggersByGroup.value.values.sumOf { it.size }
            callerProfile.mainViewModel.displaySystemMessage("Простых триггеров загружено: $totalNumberOfTriggers")
            return
        }

        // It tries to change 'false' to 'true'.
        // If it fails (because it was already 'true'), it returns false, and we exit.
        if (!startedToInitialize.compareAndSet(expectedValue = false, newValue = true)) {
            logger.info { "TextTriggerManager: Initialization already completed or in progress." }
            return
        }

        coroutineScope.launch {
            val initialData = loadTextTriggers()

            initialData.forEach { (groupName, triggers) ->
                triggers.forEach { trig ->
                    profileManager.gameWindows.value.values.firstOrNull()?.addSingleTriggerToAll(trig.condition, trig.action, groupName, trig.priority, trig.isRegex)
                }
            }

            profileManager.gameWindows.value.values.forEach { profile -> profile.scriptingEngine.sortTriggersByPriority() }

            _textTriggersByGroup.value = initialData

            val totalNumberOfTriggers = textTriggersByGroup.value.values.sumOf { it.size }
            profileManager.currentMainViewModel.value.displaySystemMessage("Простых триггеров загружено: $totalNumberOfTriggers")

            hasInitialized.store(true)

            _textTriggersByGroup
                .drop(1)
                .debounce(500L) // Still debounce to batch rapid changes.
                .onEach {
                    logger.info { "TextTriggerManager: change detected, saving text triggers..." }
                    saveTextTriggers()
                }
                .collect()
        }
    }

    private suspend fun loadTextTriggers() : Map<String, List<SimpleTriggerData>> {
        return withContext(Dispatchers.IO) {
            logger.info { "Loading text triggers from disk..." }
            val triggersDir = File(getTriggersDirectory())
            if (!triggersDir.exists()) {
                logger.info { "Triggers directory does not exist, starting with empty set." }
                return@withContext emptyMap()
            }

            val triggerFiles =
                triggersDir.listFiles { file, name -> name.endsWith(".yaml", ignoreCase = true) }
                    ?: emptyArray()

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
}