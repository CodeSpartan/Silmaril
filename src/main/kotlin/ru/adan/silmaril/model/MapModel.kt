package ru.adan.silmaril.model

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import ru.adan.silmaril.misc.AesDecryptor
import ru.adan.silmaril.misc.getProgramDirectory
import ru.adan.silmaril.misc.unzipFile
import ru.adan.silmaril.xml_schemas.Room
import ru.adan.silmaril.xml_schemas.Zone
import java.awt.Point
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Paths
import java.time.Instant
import kotlin.collections.iterator
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.random.Random

class MapModel(private val settingsManager: SettingsManager, private val roomDataManager: RoomDataManager) {
    private val zonesMap = HashMap<Int, Zone>() // Key: zoneId, Value: zone
    private val roomToZone = mutableMapOf<Int, Zone>()

    val logger = KotlinLogging.logger {}

    private val squashedZones : MutableSet<Int> = mutableSetOf()

    val _areMapsReady = MutableStateFlow(false)
    val areMapsReady = _areMapsReady.asStateFlow()

    val mapModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun getZone(zoneId: Int): Zone? {
        return zonesMap[zoneId]
    }

    fun getZoneByRoomId(roomId: Int): Zone? {
        return roomToZone[roomId]
    }

    fun getRooms(zoneId: Int): Map<Int, Room> {
        val zone = getZone(zoneId)
        return zone?.roomsList?.associateBy { it.id } ?: emptyMap()
    }

    suspend fun initMaps(profileManager: ProfileManager) {
        mapModelScope.launch(SupervisorJob() + Dispatchers.IO) {
            logger.info { "Проверяю карты..." }
            profileManager.displaySystemMessage("Проверяю карты...")
            val mapsUpdated: Boolean = updateMapsUntilSuccess()
            profileManager.displaySystemMessage(if (mapsUpdated) "Карты обновлены!" else "Карты соответствуют последней версии.")
            profileManager.displaySystemMessage("Загружаю карты...")
            val msg = loadAllMaps()
            profileManager.displaySystemMessage(msg)
            roomDataManager.loadVisitedRoomsYaml()
            roomDataManager.loadAdditionalInfoYaml(zonesMap)
            profileManager.displaySystemMessage("Карты готовы.")
            _areMapsReady.value = true
        }
    }

    fun cleanup() {
        mapModelScope.cancel()
    }

    suspend fun updateMapsUntilSuccess(
        initialDelayMs: Long = 1_000,    // 1s
        maxDelayMs: Long = 60_000,       // 60s
        backoffMultiplier: Double = 2.0,
        jitterRatio: Double = 0.2        // ±20% jitter - to avoid hammering the server
    ): Boolean {
        var delayMs = initialDelayMs

        while (currentCoroutineContext().isActive) {
            try {
                return updateMapsOnce() // returns true if downloaded, false if 304 not modified
            } catch (e: CancellationException) {
                throw e // always propagate cancellation
            } catch (e: IOException) {
                // Transient network issue (e.g., ConnectException, timeouts, etc.) — retry
                logger.warn(e) { "updateMaps failed; retrying in ${delayMs}ms..." }
                val jitter = 1.0 + Random.nextDouble(-jitterRatio, jitterRatio)
                delay((delayMs * jitter).toLong().coerceAtLeast(250)) // avoid 0 or negative
                delayMs = min((delayMs * backoffMultiplier).toLong(), maxDelayMs)
            } catch (e: Throwable) {
                // Non-IO error – don’t retry; surface it
                logger.error(e) { "updateMaps failed with non-retriable error." }
                throw e
            }
        }

        // Reached if the coroutine was cancelled
        return false
    }

    private suspend fun updateMapsOnce(): Boolean = withContext(Dispatchers.IO) {
        val url: String = settingsManager.settings.value.mapsUrl
        val lastChecked: Instant = settingsManager.settings.value.lastMapsUpdateDate

        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            useCaches = false
            connectTimeout = 5_000
            readTimeout = 5_000
            ifModifiedSince = lastChecked.toEpochMilli()
        }

        try {
            connection.connect()

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    logger.info { "Maps have been modified since the given date. Downloading…" }

                    val destinationFile = File(Paths.get(getProgramDirectory(), "maps.zip").toString())
                    connection.inputStream.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    settingsManager.updateLastMapsUpdateDate(
                        Instant.ofEpochMilli(connection.lastModified)
                    )

                    // Clean old XMLs
                    val oldFilesDir = File(Paths.get(getProgramDirectory(), "maps", "MapGenerator", "MapResults").toString())
                    if (oldFilesDir.exists()) {
                        oldFilesDir.listFiles { f -> f.isFile && f.extension == "xml" }
                            ?.forEach { it.delete() }
                    }

                    // Unzip
                    val unzippedMapsDirectory = Paths.get(getProgramDirectory(), "maps").toString()
                    val mapsDir = File(unzippedMapsDirectory)
                    if (!mapsDir.exists()) mapsDir.mkdir()
                    unzipFile(destinationFile.absolutePath, unzippedMapsDirectory)
                    destinationFile.delete()

                    true
                }
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    logger.info { "Maps have not been modified since the given date." }
                    false
                }
                else -> {
                    logger.warn { "Maps received unexpected response code: $code" }
                    // Treat unexpected codes as non-retriable unless you want to retry 5xx:
                    if (code in 500..599) throw IOException("Server error $code")
                    false
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    // Called from a coroutine after new maps have been downloaded and unzipped (or didn't need an update)
    suspend fun loadAllMaps() : String {
        val sourceDirPath = Paths.get(getProgramDirectory(), "maps", "MapGenerator", "MapResults").toString()
        val xmlMapper = XmlMapper()

        // Get the list of all .xml files in the source directory
        val xmlFiles = File(sourceDirPath).listFiles { file, name ->
            name.endsWith(".xml", ignoreCase = true)
        } ?: emptyArray()

        // Process each XML file
        for (xmlFile in xmlFiles) {
            // Get the decrypted content from the file
            val decryptedContent = AesDecryptor.decryptFile(xmlFile.absolutePath)

            try {
                val zone: Zone = xmlMapper.readValue(decryptedContent, Zone::class.java)
                zonesMap[zone.id] = zone
            } catch (e: JsonMappingException) {
                logger.error(e) { "Mapping error in ${xmlFile.name}." }
            }
        }

        val mapsLoadedMsg = "Карт загружено: ${zonesMap.size}"
        logger.info { mapsLoadedMsg }

        zonesMap.forEach { zone ->
            roomToZone.putAll(zone.value.roomsList.associate { it.id to zone.value })
        }

        return mapsLoadedMsg
    }

    // Decrypt all maps for debugging purpose
    private fun decryptAllMaps() {
        val sourceDirPath = Paths.get(getProgramDirectory(), "maps", "MapGenerator", "MapResults").toString()
        val targetDirPath = Paths.get(getProgramDirectory(), "maps", "MapGenerator", "MapDecrypted").toString()
        File(targetDirPath).mkdirs()

        // Get the list of all .xml files in the source directory
        val xmlFiles = File(sourceDirPath).listFiles { file, name ->
            name.endsWith(".xml", ignoreCase = true)
        } ?: emptyArray()

        // Process each XML file
        for (xmlFile in xmlFiles) {
            // Get the decrypted content from the file
            val decryptedContent = AesDecryptor.decryptFile(xmlFile.absolutePath)

            // Specify the target file path
            val targetFilePath = "$targetDirPath\\${xmlFile.name}"

            // Write the decrypted content to the new file
            File(targetFilePath).writeText(decryptedContent)
        }
    }

    // Places all rooms on the same z-level
    fun squashRooms(rooms: Map<Int, Room>, zoneId: Int) {
        if (squashedZones.contains(zoneId))
            return

        // special dispensation: delete connections from these junk rooms in Osgiliath
        // @TODO: make a file for this, this shouldn't be done in the code
        if (zoneId == 109) {
            val junkRooms = listOf(10963, 10964, 10965, 10966, 10968, 10969, 10974, 10976)
            for (junkRoom in junkRooms) rooms[junkRoom]?.exitsList = emptyList()
        }

        for ((_, room) in rooms) {
            room.originalX = room.x
            room.originalY = room.y
            room.originalZ = room.z
        }

        // 1. find which level has the most rooms, so we'll start working with that level by default
        val roomsPerLevel : MutableMap<Int, Int> = mutableMapOf()
        for (room in rooms) {
            roomsPerLevel[room.value.z] = roomsPerLevel.getOrDefault(room.value.z, 0) + 1
        }
        val mainLevel : Int = roomsPerLevel.maxByOrNull { it.value }?.key ?: 0

        // 2. go over all rooms on the ru.adan.silmaril.main level and place their coords into a map, so we can easily check if a coordinate is occupied
        val occupiedCoords: MutableSet<Point> = rooms.values
            .filter { it.z == mainLevel }
            .map { Point(it.x, it.y) }
            .toMutableSet()

        occupySpaceBetweenRooms(
            rooms.filter { (_, room) -> room.z == mainLevel }, // occupied rooms on the ru.adan.silmaril.main floor
            occupiedCoords)

        val visitedIds: MutableMap<Int, Boolean> = rooms
            .mapValues { false }
            .toMutableMap()

        // prevent division by zero
        if (occupiedCoords.isEmpty())
            return

        // 3. find the center of gravity on the ru.adan.silmaril.main level for later
        val centerOfGravity = Point(
            occupiedCoords.sumOf { it.x } / occupiedCoords.count(),
            occupiedCoords.sumOf { it.y } / occupiedCoords.count())

        // The squashing process is simple:
        // - Visit rooms. If they have connections that go upstairs or downstairs, isolate those chunks and try to fit them in on the ru.adan.silmaril.main level
        // - If they don't fit, try to move them by 1 square from the center of gravity, and do it until they fit
        // - Once they fit, simply add them to the occupiedCoords and visitedCoords, and then they'll themselves get visited later when the ru.adan.silmaril.main While loops again
        // - This will visit other levels one by one
        // - Some rooms on other levels may be unconnected. Treat them separately before exiting the loop for the last time
        while (visitedIds.values.any { !it }) {
            for (unvisitedId in visitedIds.filter { !it.value }) {
                val unvisitedRoom = rooms[unvisitedId.key]!!

                // if the room itself is on the non-ru.adan.silmaril.main floor, start trying to move it to the ru.adan.silmaril.main floor
                /*&& !(visitedIds.containsKey(unvisitedId.key) && visitedIds[unvisitedRoom.id]!!)*/
                if (unvisitedRoom.z != mainLevel) {
                    val neighbors : MutableMap<Int, Room> = mutableMapOf(unvisitedRoom.id to unvisitedRoom)
                    gatherChunkOfRooms(unvisitedRoom, rooms, neighbors)
                    trySquashRooms(neighbors, mainLevel, occupiedCoords, visitedIds, centerOfGravity)
                    occupySpaceBetweenRooms(
                        rooms.filter { (_, room) -> room.z == mainLevel }, // occupied rooms on the ru.adan.silmaril.main floor
                        occupiedCoords)
                }
                else {
                    // if the room itself is on the ru.adan.silmaril.main floor, try its up/down neighbors
                    for (exit in unvisitedRoom.exitsList) {
                        // if neighbor leads to another zone, skip it
                        val neighbor = rooms[exit.roomId] ?: continue
                        // if the neighbor is on another level, we need to treat it, otherwise skip it
                        if (neighbor.z != mainLevel) {
                            val neighbors : MutableMap<Int, Room> = mutableMapOf(neighbor.id to neighbor)
                            gatherChunkOfRooms(neighbor, rooms, neighbors)
                            trySquashRooms(neighbors, mainLevel, occupiedCoords, visitedIds, centerOfGravity)
                            occupySpaceBetweenRooms(
                                rooms.filter { (_, room) -> room.z == mainLevel }, // occupied rooms on the ru.adan.silmaril.main floor
                                occupiedCoords)
                        }
                    }
                    visitedIds[unvisitedId.key] = true
                }
            }
            // if we've treated all connected rooms, put all the unconnected ones into coords to visit
            if (!visitedIds.values.any { !it }) {
                val missingRoomIds = rooms.keys.subtract(visitedIds.keys)
                val newEntries = missingRoomIds.associateWith { false }
                visitedIds.putAll(newEntries)
            }
        }
        squashedZones.add(zoneId)
    }

    // gathers all neighbors on the same level recursively
    private fun gatherChunkOfRooms(room: Room, allRooms: Map<Int, Room>, result: MutableMap<Int, Room>) {
        for (exit in room.exitsList) {
            // if the neighbor is in another zone, skip it
            val neighbor = allRooms[exit.roomId] ?: continue
            if (neighbor.z == room.z && !result.containsKey(neighbor.id)) {
                result[neighbor.id] = neighbor
                gatherChunkOfRooms(neighbor, allRooms, result)
            }
        }
    }

    private fun trySquashRooms(
        roomsToMove: Map<Int, Room>,
        mainLevel: Int,
        occupiedCoords: MutableSet<Point>,
        visitedCoords: MutableMap<Int, Boolean>,
        mainLevelCenterOfGravity: Point,
    ) {
        if (roomsToMove.isEmpty()) return
        val thisLevel = roomsToMove.entries.firstOrNull()!!.value.z
        val goDown = thisLevel < mainLevel

        // special dispensation for a chunk of rooms on the road
        // @TODO: make a file with manual corrections
        if (roomsToMove.containsKey(15463)) {
            val ranges = listOf(15463..15476, 15478..15482)
            for (range in ranges) {
                for (i in range) {
                    if (roomsToMove.containsKey(i)) {
                        roomsToMove[i]!!.x = roomsToMove[i]!!.x + 52
                        roomsToMove[i]!!.y = roomsToMove[i]!!.y - 12
                    }
                }
            }
        }

        val centerOfGravity = Point(
            roomsToMove.values.sumOf { it.x } / roomsToMove.count(),
            roomsToMove.values.sumOf { it.y } / roomsToMove.count())
        val goRight = centerOfGravity.x >= mainLevelCenterOfGravity.x
        var success = false
        var i = 0
        while (!success) {
            success = roomsToMove.all { room ->
                !occupiedCoords.contains(Point(room.value.x, room.value.y))
            }

            if (!success) {
                for (entry in roomsToMove) {
                    entry.value.y += if (goDown) +1 else -1
                }
            }
            i++

            // for every 5 steps down (or up), try to move it to the right (or left), away from the center of gravity
            if (!success && i % 5 == 0) {
                val offsetX = i / 5
                for (entry in roomsToMove) {
                    entry.value.y -= if (goDown) +i else -i
                    entry.value.x += if (goRight) +offsetX else -offsetX
                }
                success = roomsToMove.all { room ->
                    !occupiedCoords.contains(Point(room.value.x, room.value.y))
                }
                // if no success, move the rooms back
                if (!success) {
                    for (entry in roomsToMove) {
                        entry.value.y += if (goDown) +i else -i
                        entry.value.x += if (goRight) -offsetX else +offsetX
                    }
                }
            }
        }
        roomsToMove.forEach { it.value.z = mainLevel }
        val newOccupied = roomsToMove.values.map { Point(it.x, it.y) }.toMutableSet()
        occupiedCoords.addAll(newOccupied)
        val newVisited = roomsToMove.values.associate { it.id to false }
        visitedCoords.putAll(newVisited)
    }

    // adds more "occupied" points in the map by filling in empty space between existing rooms
    private fun occupySpaceBetweenRooms(rooms : Map<Int, Room>, occupiedCoords: MutableSet<Point>)
    {
        for (room in rooms.values) {
            for (exit in room.exitsList) {
                val neighbor = rooms[exit.roomId] ?: continue
                if (neighbor.z == room.z && Point(neighbor.x, neighbor.y).distance(Point(room.x, room.y)) > 1) {
                    if (room.x == neighbor.x) {
                        // build occupied points along the Y axis
                        val range = neighbor.y - room.y
                        val rangeSign = if (range >= 0) 1 else -1
                        for (i in 0 until range.absoluteValue) {
                            occupiedCoords.add(Point(room.x, room.y + i * rangeSign))
                        }
                    } else if (room.y == neighbor.y) {
                        // build occupied points along the X axis
                        val range = neighbor.x - room.x
                        val rangeSign = if (range >= 0) 1 else -1
                        for (i in 0 until range.absoluteValue) {
                            occupiedCoords.add(Point(room.x + i * rangeSign, room.y))
                        }
                    }
                }
            }
        }
    }

    fun getZonesForLevel(level: Int) : List<Zone> =
        zonesMap.values.filter { zone -> level >= zone.minLevel && level <= zone.maxLevel }
}