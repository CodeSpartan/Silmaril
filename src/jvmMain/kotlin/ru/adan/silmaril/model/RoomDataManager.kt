package ru.adan.silmaril.model

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.misc.RoomColor
import ru.adan.silmaril.misc.RoomIcon
import ru.adan.silmaril.misc.getAdanMapDataDirectory
import ru.adan.silmaril.xml_schemas.ArrayOfAdditionalRoomParameters
import java.io.File
import kotlin.getValue
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import ru.adan.silmaril.misc.getSilmarilMapDataDirectory
import ru.adan.silmaril.xml_schemas.Zone
import java.util.concurrent.ConcurrentHashMap
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.misc.CyrillicFixer
import ru.adan.silmaril.platform.createLogger
import ru.adan.silmaril.xml_schemas.RoomExit
import ru.adan.silmaril.xml_schemas.ZoneType
import ru.adan.silmaril.xml_schemas.ZonesYaml

class RoomDataManager() : KoinComponent {
    val logger = createLogger("ru.adan.silmaril.model.RoomDataManager")
    val systemMessageDisplay: SystemMessageDisplay by inject()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory state
    // Because they can't be observed, we have a "kick" below to force map widget to redraw on changes
    val visitedRooms: ConcurrentHashMap<Int, MutableSet<Int>> = ConcurrentHashMap()
    val customColors: ConcurrentHashMap<Int, RoomColor> = ConcurrentHashMap()
    val roomComments: ConcurrentHashMap<Int, String> = ConcurrentHashMap()
    val roomIcons: ConcurrentHashMap<Int, RoomIcon> = ConcurrentHashMap()
    val roomTriggers: ConcurrentHashMap<Int, String> = ConcurrentHashMap()

    val kick = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    fun kickRedraw() { kick.tryEmit(Unit) }

    private val xml = XmlMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // loads room data from AMC
    fun importRoomDataFromAMC() {
        coroutineScope.launch {
            systemMessageDisplay.displaySystemMessage("Импортирую...")
            try {
                val directory = File(getAdanMapDataDirectory())
                if (!directory.exists() || !directory.isDirectory) {
                    logger.warn { "Map data directory not found: ${directory.absolutePath}" }
                    return@launch
                }

                val xmlFiles = directory.listFiles { _, name ->
                    name.endsWith(".xml", ignoreCase = true)
                } ?: emptyArray()

                logger.info { "Found ${xmlFiles.size} XML files in ${directory.absolutePath}" }

                var visitedRoomsAdded = 0
                var coloredRoomsAdded = 0
                var iconsOnRoomsAdded = 0
                var commentedRoomsAdded = 0

                for (xmlFile in xmlFiles) {
                    val zoneId = xmlFile.nameWithoutExtension.toIntOrNull()
                    if (zoneId == null) {
                        logger.warn { "Skipping file with non-integer name: ${xmlFile.name}" }
                        continue
                    }

                    runCatching {
                        val container: ArrayOfAdditionalRoomParameters = xml.readValue(xmlFile)

                        var addedVisited = 0
                        var addedColors = 0
                        var addedComments = 0
                        var addedIcons = 0

                        container.items.forEach { p ->
                            // Visited rooms per zone
                            if (p.hasBeenVisited) {
                                val set = visitedRooms.getOrPut(zoneId) { mutableSetOf() }
                                if (set.add(p.roomId)) addedVisited++
                            }

                            // Only store non-default color/icon to keep maps small
                            if (p.color != RoomColor.Default) {
                                customColors[p.roomId] = p.color
                                addedColors++
                            }

                            p.comments?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?.let {
                                    roomComments[p.roomId] = it
                                    addedComments++
                                }

                            if (p.icon != RoomIcon.None) {
                                roomIcons[p.roomId] = p.icon
                                addedIcons++
                            }
                        }

                        visitedRoomsAdded += addedVisited
                        coloredRoomsAdded += addedColors
                        iconsOnRoomsAdded += addedIcons
                        commentedRoomsAdded += addedComments

                        logger.info {
                            "Loaded zone $zoneId from ${xmlFile.name}: " +
                                    "visited +$addedVisited, colors +$addedColors, " +
                                    "comments +$addedComments, icons +$addedIcons"
                        }
                    }.onFailure { ex ->
                        systemMessageDisplay.displaySystemMessage("Не удалось распарсить зону ${xmlFile.name}")
                        logger.error(ex) { "Failed to parse ${xmlFile.name}" }
                    }
                }
                systemMessageDisplay.displaySystemMessage("Загружено посещенных комнат: $visitedRoomsAdded")
                systemMessageDisplay.displaySystemMessage("Загружено раскрашенных комнат: $coloredRoomsAdded")
                systemMessageDisplay.displaySystemMessage("Загружено комнат с иконками: $iconsOnRoomsAdded")
                systemMessageDisplay.displaySystemMessage("Загружено комментариев к комнатам: $commentedRoomsAdded")

                scheduleDebouncedSave()
            } catch (t: Throwable) {
                systemMessageDisplay.displaySystemMessage("Что-то пошло не так, сообщите о баге: ${t.message}")
                logger.error(t) { "Unexpected error while loading room data" }
            }
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
    }

    // ---- Runtime-updated mutators (all schedule a debounced save) ----

    // Mark a room as visited during gameplay.
    fun visitRoom(zoneId: Int, roomId: Int) {
        val set = visitedRooms.computeIfAbsent(zoneId) { ConcurrentHashMap.newKeySet<Int>() }
        if (set.add(roomId)) {
            scheduleDebouncedSave()
        }
    }

    // Set or clear a custom color. Pass RoomColor.Default or null to clear.
    fun setRoomColor(roomId: Int, color: RoomColor) {
        val shouldRemove = color == RoomColor.Default
        val changed = if (shouldRemove) {
            customColors.remove(roomId) != null
        } else {
            customColors[roomId] != color
        }
        if (!shouldRemove && changed) {
            customColors[roomId] = color
        }
        if (changed) {
            kickRedraw()
            scheduleDebouncedSave()
        }
    }

    // Set or clear a comment. Empty or blank string clears.
    fun setRoomComment(roomId: Int, comment: String?) {
        val normalized = comment?.trim().orEmpty()
        val shouldRemove = normalized.isEmpty()
        val changed = if (shouldRemove) {
            roomComments.remove(roomId) != null
        } else {
            roomComments[roomId] != normalized
        }
        if (!shouldRemove && changed) {
            roomComments[roomId] = normalized
        }
        if (changed) {
            kickRedraw()
            scheduleDebouncedSave()
        }
    }

    // Set or clear an icon. Pass RoomIcon.None or null to clear.
    fun setRoomIcon(roomId: Int, icon: RoomIcon?) {
        val shouldRemove = (icon == null || icon == RoomIcon.None)
        val changed = if (shouldRemove) {
            roomIcons.remove(roomId) != null
        } else {
            roomIcons[roomId] != icon
        }
        if (!shouldRemove && changed) {
            roomIcons[roomId] = icon
        }
        if (changed) {
            kickRedraw()
            scheduleDebouncedSave()
        }
    }

    // Set or clear a comment. Empty or blank string clears.
    fun setRoomTrigger(roomId: Int, trigger: String?) {
        val normalized = trigger?.trim().orEmpty()
        val shouldRemove = normalized.isEmpty()
        val changed = if (shouldRemove) {
            roomTriggers.remove(roomId) != null
        } else {
            roomTriggers[roomId] != normalized
        }
        if (!shouldRemove && changed) {
            roomTriggers[roomId] = normalized
        }
        if (changed) scheduleDebouncedSave()
    }

    // ---- YAML persistence (KAML) ----

    // Files (in getSilmarilMapDataDirectory())
    private val visitedRoomsFileName = "visitedRooms.yaml"
    private val customColorsFileName = "customColors.yaml"
    private val roomCommentsFileName = "roomComments.yaml"
    private val roomIconsFileName = "roomIcons.yaml"
    private val roomTriggersFileName = "roomTriggers.yaml"

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            strictMode = false // tolerate unknowns if files are edited manually
        )
    )

    // Serializers for our map types
    private val visitedRoomsSer =
        MapSerializer(Int.serializer(), SetSerializer(Int.serializer()))
    private val customColorsSer =
        MapSerializer(Int.serializer(), RoomColor.serializer())
    private val roomCommentsSer =
        MapSerializer(Int.serializer(), String.serializer())
    private val roomIconsSer =
        MapSerializer(Int.serializer(), RoomIcon.serializer())
    private val roomTriggersSer =
        MapSerializer(Int.serializer(), String.serializer())

    // Debounce state
    private var saveJob: Job? = null
    private val saveDebounceMs = 5000L

    private fun scheduleDebouncedSave() {
        saveJob?.cancel()
        saveJob = coroutineScope.launch {
            delay(saveDebounceMs)
            runCatching { saveAllYamlInternal() }
                .onFailure { logger.error(it) { "Failed to save YAML" } }
        }
    }

    // Call this at startup
    // It merges the YAML data into the in-memory maps.
    suspend fun loadVisitedRoomsYaml() = withContext(Dispatchers.IO) {
        systemMessageDisplay.displaySystemMessage("Загружаю посещенные клетки...")

        val dir = File(getSilmarilMapDataDirectory())

        var addedZones = 0
        var addedRooms = 0

        // visitedRooms
        loadYamlFile(dir, visitedRoomsFileName) { text ->
            val parsed = yaml.decodeFromString(visitedRoomsSer, text)
            for ((zoneId, rooms) in parsed) {
                val set = visitedRooms.getOrPut(zoneId) { mutableSetOf() }
                val before = set.size
                set.addAll(rooms)
                if (set.size > before) {
                    addedRooms += (set.size - before)
                }
                addedZones++
            }
            logger.info { "Loaded visitedRooms: zones=$addedZones, newRooms=$addedRooms" }
        }

        // customColors
        loadYamlFile(dir, customColorsFileName) { text ->
            val parsed = yaml.decodeFromString(customColorsSer, text)
            customColors.putAll(parsed)
            logger.info { "Loaded customColors: ${parsed.size} entries" }
        }

        // roomComments
        loadYamlFile(dir, roomCommentsFileName) { text ->
            val parsed = yaml.decodeFromString(roomCommentsSer, text)
            roomComments.putAll(parsed.filterValues { it.isNotBlank() })
            logger.info { "Loaded roomComments: ${parsed.size} entries" }
        }

        // roomIcons
        loadYamlFile(dir, roomIconsFileName) { text ->
            val parsed = yaml.decodeFromString(roomIconsSer, text)
            roomIcons.putAll(parsed)
            logger.info { "Loaded roomIcons: ${parsed.size} entries" }
        }

        // roomTriggers
        loadYamlFile(dir, roomTriggersFileName) { text ->
            val parsed = yaml.decodeFromString(roomTriggersSer, text)
            roomTriggers.putAll(parsed.filterValues { it.isNotBlank() })
            logger.info { "Loaded roomTriggers: ${parsed.size} entries" }
        }

        systemMessageDisplay.displaySystemMessage("Посещенные зоны: $addedZones, клетки: $addedRooms, комменты: ${roomComments.keys.size}, триггеры: ${roomTriggers.keys.size}")
    }

    // Load whether zones are solo or group oriented, plus what levels they're intended for
    // Merges into existing in-memory data
    suspend fun loadAdditionalInfoYaml(zonesMap: HashMap<Int, Zone>) = withContext(Dispatchers.IO) {
        //zonesMap
        val bytes = Res.readBytes("files/zones_info.yaml")
        val yaml = bytes.decodeToString()
        val zonesInfo = Yaml.default.decodeFromString(ZonesYaml.serializer(), yaml)
        zonesInfo.zones.forEach { zoneInfo ->
            val zoneInMemory = zonesMap[zoneInfo.id]
            if (zoneInMemory != null) {
                if (zoneInMemory.minLevel == 0 && zoneInMemory.maxLevel == 0) {
                    zoneInMemory.minLevel = zoneInfo.levelRange.first
                    zoneInMemory.maxLevel = zoneInfo.levelRange.last
                    logger.debug { "Adjusting levels of zone ${zoneInMemory.name} (${zoneInMemory.id}). Correct levels: ${zoneInfo.levelRange}"}
                }
                zoneInMemory.solo = zoneInfo.type == ZoneType.SOLO
            }
        }
    }

    fun fixTyposInZones(zonesMap: HashMap<Int, Zone>) {
        zonesMap.values.forEach { zone ->
            // fix accidental latin letters in cyrillic names
            if (CyrillicFixer.containsLatinInCyrillicContext(zone.name)) {
                val fixed = CyrillicFixer.fixLatinHomoglyphsInRussian(zone.name)
                if (fixed != zone.name) {
                    logger.debug { "Fixing typo in zone [${zone.id}] \"$fixed\"" }
                    zone.name = fixed
                }
            }
            if (zone.name == "Деревенский колодец")
                zone.name = "Деревенский Колодец-НГ"
            if (zone.name == "Деревенский Колодец")
                zone.name = "Деревенский Колодец-МХ"
        }
    }

    fun fixZonesMisc(zonesMap: HashMap<Int, Zone>) {
        // connect two rooms that get connected by the quest, otherwise any pathfinding will go through Huorns and RIP the party
        val fangorn = zonesMap[400]
        if (fangorn != null) {
            fangorn.roomsList.find { it.id == 40027 }?.exitsList?.add(RoomExit("North", 40020))
            fangorn.roomsList.find { it.id == 40020 }?.exitsList?.add(RoomExit("South", 40027))
        }

        val shelobLair = zonesMap[370]
        if (shelobLair != null) {
            shelobLair.roomsList.find { it.id == 37023 }?.exitsList?.removeIf { exit -> exit.roomId == 37024 }
            shelobLair.roomsList.find { it.id == 37023 }?.exitsList?.removeIf { exit -> exit.roomId == 37032 }
            shelobLair.roomsList.find { it.id == 37024 }?.exitsList?.removeIf { exit -> exit.roomId == 37023 }
        }

        // remove connections to death traps in deadly swamps
        val swamps = zonesMap[653]
        if (swamps != null) {
            swamps.roomsList.filter { it.id >= 65358 && it.id <= 65361 }.forEach { dt ->
                dt.exitsList.clear()
            }
            for (room in swamps.roomsList) {
                room.exitsList.removeIf { exit -> exit.roomId >= 65358 && exit.roomId <= 65361 }
            }
        }
    }

    private fun saveAllYamlInternal() {
        val dir = File(getSilmarilMapDataDirectory()).apply { mkdirs() }

        val visitedStr = yaml.encodeToString(
            visitedRoomsSer,
            visitedRooms.mapValues { (_, v) -> v.toSet() } // defensive copy
        )
        val colorsStr = yaml.encodeToString(customColorsSer, customColors.toMap())
        val commentsStr = yaml.encodeToString(roomCommentsSer, roomComments.toMap())
        val iconsStr = yaml.encodeToString(roomIconsSer, roomIcons.toMap())
        val triggersStr = yaml.encodeToString(roomTriggersSer, roomTriggers.toMap())

        val savedVisited = writeIfChangedAtomic(File(dir, visitedRoomsFileName), visitedStr)
        val savedColors = writeIfChangedAtomic(File(dir, customColorsFileName), colorsStr)
        val savedComments = writeIfChangedAtomic(File(dir, roomCommentsFileName), commentsStr)
        val savedIcons = writeIfChangedAtomic(File(dir, roomIconsFileName), iconsStr)
        val savedTriggers = writeIfChangedAtomic(File(dir, roomTriggersFileName), triggersStr)

        logger.info {
            "Saved YAML to ${dir.absolutePath} (visited=$savedVisited, colors=$savedColors, comments=$savedComments, icons=$savedIcons, triggers=$savedTriggers)"
        }
    }

    private fun loadYamlFile(dir: File, name: String, block: (String) -> Unit) {
        val file = File(dir, name)
        if (!file.exists()) return
        runCatching {
            val text = file.readText()
            if (text.isNotBlank()) block(text)
        }.onFailure {
            logger.error(it) { "Failed to read/parse YAML file: ${file.absolutePath}" }
        }
    }

    private fun writeIfChangedAtomic(target: File, content: String): Boolean {
        // Skip write if content identical
        if (target.exists()) {
            try {
                val existing = target.readText()
                if (existing == content) return false
            } catch (t: Throwable) {
                // If we can't read for comparison, proceed to rewrite
                logger.warn { "Couldn't read ${target.absolutePath} to compare; will rewrite: ${t.message}" }
            }
        }

        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeText(content)

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return true
    }

    fun isRoomVisited(zoneId: Int, roomId: Int): Boolean =
        when (zoneId) {
            // zones without fog of war:
            20 -> true // минас-моргул
            22 -> true // мордорский лагерь
            25 -> true // эсгарот
            26 -> true // мон-харнен
            27 -> true // норгорд
            30 -> true // минас-тирит
            else -> visitedRooms[zoneId]?.contains(roomId) == true
        }

    fun getRoomComment(roomId: Int): String? =
        roomComments[roomId]

    fun getRoomCustomIcon(roomId: Int): RoomIcon? =
        roomIcons[roomId]

    fun getRoomCustomColor(roomId: Int): RoomColor? =
        customColors[roomId]

    fun getRoomTrigger(roomId: Int): String? =
        roomTriggers[roomId]

    fun hasComment(roomId: Int): Boolean = roomComments.containsKey(roomId)

    fun hasColor(roomId: Int): Boolean = customColors.containsKey(roomId) && customColors[roomId] != RoomColor.Default
}
