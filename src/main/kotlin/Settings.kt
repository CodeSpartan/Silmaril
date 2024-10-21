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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Serializable
data class Settings(
    var mapsUrl: String = "http://adan.ru/files/Maps.zip",
    @Serializable(with = InstantSerializer::class)
    var lastMapsUpdateDate: Instant = Instant.EPOCH
)

object SettingsManager {
    private val settingsFile: File = File(getProgramDirectory(), "settings.json")
    private var settings: Settings = Settings()
    val jsonFormat = Json { prettyPrint = true }

    init {
        loadSettings()
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

    private fun getProgramDirectory(): String {
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
        }
    }

    private fun saveSettings() {
        val json = jsonFormat.encodeToString(settings)
        settingsFile.writeText(json)
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

                    val unzippedMapsDirectory : String = Paths.get(getProgramDirectory(), "maps").toString()
                    val mapsDir = File(unzippedMapsDirectory)
                    if (!mapsDir.exists()) {
                        mapsDir.mkdir()
                    }
                    unzipFile(Paths.get(getProgramDirectory(), "maps.zip").toString(), unzippedMapsDirectory)

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
}

// A custom serializer for type 'Instant'
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())  // ISO-8601 format by default
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
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