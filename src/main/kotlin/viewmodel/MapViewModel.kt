package viewmodel

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import mud_messages.CurrentRoomMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import misc.decryptFile
import model.MudConnection
import xml_schemas.*
import java.io.File
import java.nio.file.Paths


class MapViewModel(private val client: MudConnection) {
    private val zonesMap = HashMap<Int, Zone>() // Key: zoneId, Value: zone

    private val _currentRoomMessages = MutableSharedFlow<CurrentRoomMessage>(replay = 1)
    val currentRoomMessages : MutableSharedFlow<CurrentRoomMessage> get() = _currentRoomMessages

    private val managerJob = Job()
    private val managerScope = CoroutineScope(Dispatchers.Main + managerJob)

    private val loadMapsScope = CoroutineScope(Dispatchers.IO)
    private var loadMapsJob: Job? = null

    init {
        collectRoomMessages()
    }

    private fun collectRoomMessages() {
        managerScope.launch {
            client.currentRoomMessages.collect { message ->
                // println("New message received: $message")
                emitMessage(message)
            }
        }
    }

    // Method to emit a new message
    private fun emitMessage(message: CurrentRoomMessage) {
        managerScope.launch {
            _currentRoomMessages.emit(message)
        }
    }

    fun cleanup() {
        managerJob.cancel()
        loadMapsJob?.cancel()
    }

    // called from main() after new maps have been downloaded and unzipped (or didn't need an update)
    fun loadAllMaps() {
        if (loadMapsJob == null || loadMapsJob?.isActive == false) {
            val sourceDirPath = Paths.get(SettingsManager.getProgramDirectory(), "maps", "MapGenerator", "MapResults").toString()
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
            }
        }
    }

    // Decrypt all maps for debugging purpose
    private fun decryptAllMaps() {
        val sourceDirPath = Paths.get(SettingsManager.getProgramDirectory(), "maps", "MapGenerator", "MapResults").toString()
        val targetDirPath = Paths.get(SettingsManager.getProgramDirectory(), "maps", "MapGenerator", "MapDecrypted").toString()
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
}