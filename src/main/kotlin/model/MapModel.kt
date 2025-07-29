package model

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import misc.decryptFile
import misc.getProgramDirectory
import xml_schemas.Room
import xml_schemas.Zone
import java.awt.Point
import java.io.File
import java.nio.file.Paths
import kotlin.math.absoluteValue

class MapModel(private val settings: SettingsManager) {
    private val zonesMap = HashMap<Int, Zone>() // Key: zoneId, Value: zone
    private val roomToZone = mutableMapOf<Int, Zone>()

    private val squashedZones : MutableSet<Int> = mutableSetOf()

    private val loadMapsScope = CoroutineScope(Dispatchers.IO)
    private var loadMapsJob: Job? = null

    fun cleanup() {
        loadMapsJob?.cancel()
    }

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

    // called from main() after new maps have been downloaded and unzipped (or didn't need an update)
    fun loadAllMaps(modelReady: MutableStateFlow<Boolean>) {
        if (loadMapsJob == null || loadMapsJob?.isActive == false) {
            val sourceDirPath = Paths.get(getProgramDirectory(), "maps", "MapGenerator", "MapResults").toString()
            loadMapsJob = loadMapsScope.launch {
                val xmlMapper = XmlMapper()

                // Get the list of all .xml files in the source directory
                val xmlFiles = File(sourceDirPath).listFiles { _, name ->
                    name.endsWith(".xml", ignoreCase = true)
                } ?: emptyArray()

                // Process each XML file
                for (xmlFile in xmlFiles) {
                    // Get the decrypted content from the file
                    val decryptedContent = decryptFile(xmlFile.absolutePath)

                    try {
                        val zone: Zone = xmlMapper.readValue(decryptedContent, Zone::class.java)
                        zonesMap[zone.id] = zone
                    } catch (e: JsonMappingException) {
                        println("Mapping error in ${xmlFile.name}: ${e.message}")
                    }
                }

                println("Zones loaded into memory: ${zonesMap.size}")

                zonesMap.forEach { zone ->
                    roomToZone.putAll(zone.value.roomsList.associate { it.id to zone.value })
                }

                modelReady.value = true
            }
        }
    }

    // Decrypt all maps for debugging purpose
    private fun decryptAllMaps() {
        val sourceDirPath = Paths.get(getProgramDirectory(), "maps", "MapGenerator", "MapResults").toString()
        val targetDirPath = Paths.get(getProgramDirectory(), "maps", "MapGenerator", "MapDecrypted").toString()
        File(targetDirPath).mkdirs()

        // Get the list of all .xml files in the source directory
        val xmlFiles = File(sourceDirPath).listFiles { _, name ->
            name.endsWith(".xml", ignoreCase = true)
        } ?: emptyArray()

        // Process each XML file
        for (xmlFile in xmlFiles) {
            // Get the decrypted content from the file
            val decryptedContent = decryptFile(xmlFile.absolutePath)

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

        // 1. find which level has the most rooms, so we'll start working with that level by default
        val roomsPerLevel : MutableMap<Int, Int> = mutableMapOf()
        for (room in rooms) {
            roomsPerLevel[room.value.z] = roomsPerLevel.getOrDefault(room.value.z, 0) + 1
        }
        val mainLevel : Int = roomsPerLevel.maxByOrNull { it.value }?.key ?: 0

        // 2. go over all rooms on the main level and place their coords into a map, so we can easily check if a coordinate is occupied
        val occupiedCoords: MutableSet<Point> = rooms.values
            .filter { it.z == mainLevel }
            .map { Point(it.x, it.y) }
            .toMutableSet()

        occupySpaceBetweenRooms(
            rooms.filter { (_, room) -> room.z == mainLevel }, // occupied rooms on the main floor
            occupiedCoords)

        val visitedIds: MutableMap<Int, Boolean> = rooms
            .mapValues { false }
            .toMutableMap()

        // prevent division by zero
        if (occupiedCoords.isEmpty())
            return

        // 3. find the center of gravity on the main level for later
        val centerOfGravity = Point(
            occupiedCoords.sumOf { it.x } / occupiedCoords.count(),
            occupiedCoords.sumOf { it.y } / occupiedCoords.count())

        // The squashing process is simple:
        // - Visit rooms. If they have connections that go upstairs or downstairs, isolate those chunks and try to fit them in on the main level
        // - If they don't fit, try to move them by 1 square from the center of gravity, and do it until they fit
        // - Once they fit, simply add them to the occupiedCoords and visitedCoords, and then they'll themselves get visited later when the main While loops again
        // - This will visit other levels one by one
        // - Some rooms on other levels may be unconnected. Treat them separately before exiting the loop for the last time
        while (visitedIds.values.any { !it }) {
            for (unvisitedId in visitedIds.filter { !it.value }) {
                val unvisitedRoom = rooms[unvisitedId.key]!!

                // if the room itself is on the non-main floor, start trying to move it to the main floor
                /*&& !(visitedIds.containsKey(unvisitedId.key) && visitedIds[unvisitedRoom.id]!!)*/
                if (unvisitedRoom.z != mainLevel) {
                    val neighbors : MutableMap<Int, Room> = mutableMapOf(unvisitedRoom.id to unvisitedRoom)
                    gatherChunkOfRooms(unvisitedRoom, rooms, neighbors)
                    trySquashRooms(neighbors, mainLevel, occupiedCoords, visitedIds, centerOfGravity)
                    occupySpaceBetweenRooms(
                        rooms.filter { (_, room) -> room.z == mainLevel }, // occupied rooms on the main floor
                        occupiedCoords)
                }
                else {
                    // if the room itself is on the main floor, try its up/down neighbors
                    for (exit in unvisitedRoom.exitsList) {
                        // if neighbor leads to another zone, skip it
                        val neighbor = rooms[exit.roomId] ?: continue
                        // if the neighbor is on another level, we need to treat it, otherwise skip it
                        if (neighbor.z != mainLevel) {
                            val neighbors : MutableMap<Int, Room> = mutableMapOf(neighbor.id to neighbor)
                            gatherChunkOfRooms(neighbor, rooms, neighbors)
                            trySquashRooms(neighbors, mainLevel, occupiedCoords, visitedIds, centerOfGravity)
                            occupySpaceBetweenRooms(
                                rooms.filter { (_, room) -> room.z == mainLevel }, // occupied rooms on the main floor
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
}