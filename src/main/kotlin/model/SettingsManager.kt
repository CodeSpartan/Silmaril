package model

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable
import misc.*

// This class is saved as json in getProgramDirectory()/settings.ini
@Serializable
data class Settings(
    var gameServer: String = "adan.ru",
    var gamePort: Int = 4000,
    var mapsUrl: String = "http://adan.ru/files/Maps.zip",
    @Serializable(with = InstantSerializer::class)
    var lastMapsUpdateDate: Instant = Instant.EPOCH,
    var font: String = "Fira",
    var fontSize: Int = 15,
    var colorStyle: String = "Black",
    var windowSettings: WindowSettings = WindowSettings(),
)

class SettingsManager {
    private val settingsFile: File = File(getProgramDirectory(), "settings.json")
    private var settings: Settings = Settings()
    private val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val _font = MutableStateFlow(settings.font)
    val font: StateFlow<String> get() = _font

    private val _fontSize = MutableStateFlow(settings.fontSize)
    val fontSize: StateFlow<Int> get() = _fontSize

    private val _colorStyle = MutableStateFlow(settings.colorStyle)
    val colorStyle: StateFlow<String> get() = _colorStyle

    val windowPlacement: WindowPlacement get() = settings.windowSettings.windowPlacement
    val windowPosition: WindowPosition get() = settings.windowSettings.windowPosition
    val windowSize: DpSize get() = settings.windowSettings.windowSize
    private val settingsFlow = MutableStateFlow(settings.windowSettings)

    val gameServer: String get() = settings.gameServer
    val gamePort: Int get() = settings.gamePort

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        loadSettings()
        debounceSaveSettings()
    }

    private fun getOperatingSystem(): String {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> "Windows"
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> "Linux"
            osName.contains("mac") -> "MacOS"
            else -> "Unknown"
        }
    }

    fun getProgramDirectory(): String {
        val userHome = System.getProperty("user.home")
        val os = getOperatingSystem()
        val programPath: String = when {
            // for Windows, we're in user's /Documents/Silmaril
            // for MacOS, we're in ~/Documents/Silmaril
            // for Linux, we're in ~/.Silmaril
            os == "Windows" || os == "MacOS" -> Paths.get(userHome, "Documents", "Silmaril").toString()
            os == "Linux" -> Paths.get(userHome, ".Silmaril").toString()
            else -> Paths.get(userHome, "Silmaril").toString()
        }

        // Ensure the program directory exists
        val programDir = File(programPath)
        if (!programDir.exists()) {
            programDir.mkdirs() // create all non-existent parent directories
        }

        return programPath
    }

    private fun loadSettings() {
        if (settingsFile.exists()) {
            val json = settingsFile.readText()
            settings = Json.decodeFromString(json)
            _font.value = settings.font
            _fontSize.value = settings.fontSize
            _colorStyle.value = settings.colorStyle
        }
    }

    private fun saveSettings() {
        val json = jsonFormat.encodeToString(settings)
        settingsFile.writeText(json)
    }

    // This function can observe very rapid state changes, but will actually only saveSettings after a delay of 500 ms
    @OptIn(FlowPreview::class)
    private fun debounceSaveSettings(debouncePeriod: Long = 500L) {
        settingsFlow
            .debounce(debouncePeriod)
            .onEach {
                settings.windowSettings = it
                saveSettings()
            }
            .launchIn(scope) // Ensure to launch in a defined CoroutineScope
    }

    // Returns true if update happened
    fun updateMaps() : Boolean {
        val url: String = settings.mapsUrl
        val lastChecked: Instant = settings.lastMapsUpdateDate
        val urlConnection: HttpURLConnection = URI(url).toURL().openConnection() as HttpURLConnection

        // Format the Instant to the HTTP Date format (RFC 1123)
        val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
        val dateInUtc = lastChecked.atZone(ZoneId.of("UTC"))
        val formattedDate = formatter.format(dateInUtc)

        // Set the HTTP header for If-Modified-Since
        urlConnection.setRequestProperty("If-Modified-Since", formattedDate)

        try {
            urlConnection.connect()

            when (val responseCode = urlConnection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    println("Maps have been modified since the given date.")

                    val destinationFile = File(Paths.get(getProgramDirectory(), "maps.zip").toString())

                    // Download the file
                    urlConnection.inputStream.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    settings.lastMapsUpdateDate = Instant.ofEpochMilli(urlConnection.lastModified)
                    saveSettings()

                    // delete old .xml files (in case some area ceases to exist, so we don't keep loading it into memory)
                    val oldFilesDir = File(Paths.get(getProgramDirectory(), "maps", "MapGenerator", "MapResults").toString())
                    if (oldFilesDir.exists()) {
                        val xmlFiles = oldFilesDir.listFiles { file -> file.isFile && file.extension == "xml" }
                        xmlFiles?.forEach { file -> file.delete()  }
                    }

                    val unzippedMapsDirectory : String = Paths.get(getProgramDirectory(), "maps").toString()
                    val mapsDir = File(unzippedMapsDirectory)
                    if (!mapsDir.exists()) {
                        mapsDir.mkdir()
                    }
                    unzipFile(Paths.get(getProgramDirectory(), "maps.zip").toString(), unzippedMapsDirectory)
                    destinationFile.delete()

                    return true
                }
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    println("Maps have not been modified since the given date.")
                    return false
                }
                else -> {
                    println("Maps received unexpected response code: $responseCode")
                    return false
                }
            }
        } finally {
            urlConnection.disconnect()
        }
    }

    fun updateFont(newFont : String) {
        settings.font = newFont
        _font.value = newFont
        saveSettings()
    }

    fun updateColorStyle(newColorStyle : String) {
        settings.colorStyle = newColorStyle
        _colorStyle.value = newColorStyle
        saveSettings()
    }

    fun updateWindowState(newWindowState: WindowState) {
        settingsFlow.value = WindowSettings(
            windowPlacement = newWindowState.placement,
            windowSize = newWindowState.size,
            windowPosition = newWindowState.position,
        )
        // no need to call saveSettings(), it's handled in debounceSaveSettings
    }
}

fun unzipFile(zipFilePath: String, destDirectory: String) {
    val destDir = File(destDirectory)
    if (!destDir.exists()) destDir.mkdirs()

    ZipInputStream(FileInputStream(zipFilePath)).use { zipStream ->
        var entry = zipStream.nextEntry
        while (entry != null) {
            val entryPath = destDir.toPath().resolve(entry.name)
            if (entry.isDirectory) {
                Files.createDirectories(entryPath)
            } else {
                Files.createDirectories(entryPath.parent)
                FileOutputStream(entryPath.toFile()).use { outputStream ->
                    zipStream.copyTo(outputStream)
                }
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.closeEntry()
    }
}