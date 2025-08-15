package ru.adan.silmaril.misc

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipInputStream

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

fun getOperatingSystem(): String {
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
        // for Windows, we're in user's Documents\Silmaril -- C:\Users\<YourUsername>\Documents\Silmaril\
        // for macOS, we're in ~/Documents/Silmaril -- /Users/<YourUsername>/Documents/Silmaril/
        // for Linux, we're in ~/.Silmaril -- /home/<YourUsername>/Documents/Silmaril/
        os == "Windows" || os == "MacOS" || os == "Linux" -> Paths.get(userHome, "Documents", "Silmaril").toString()
        else -> Paths.get(userHome, "Silmaril").toString()
    }

    // Ensure the program directory exists
    val programDir = File(programPath)
    if (!programDir.exists()) {
        programDir.mkdirs() // create all non-existent parent directories
    }

    return programPath
}

fun getProfileDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "profiles").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getTriggersDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "triggers").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getAliasesDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "aliases").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun String.capitalized(): String {
    return this.replaceFirstChar { it.uppercaseChar() }
}

fun MatchGroupCollection.getOrNull(name: String): MatchGroup? {
    // This is the key: Wrap the potentially crashing call in a try-catch block.
    return try {
        this[name]
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * A helper function to find an enum value by name, ignoring case.
 * Returns a default value if no match is found.
 */
inline fun <reified T : Enum<T>> enumValueOfIgnoreCase(name: String?, default: T): T {
    return enumValues<T>().find { it.name.equals(name, ignoreCase = true) } ?: default
}

fun List<String>.joinOrNone(): String {
    return if (this.isEmpty()) {
        "NOBITS"
    } else {
        this.joinToString(" ")
    }
}

fun minutesToDaysFormatted(minutes: Int): String {
    val days = minutes / 1440
    val lastDigit = days % 10
    val lastTwoDigits = days % 100

    if (lastTwoDigits in 11..14) {
        return "$days дней"
    }

    return when (lastDigit) {
        1 -> "$days день"
        2, 3, 4 -> "$days дня"
        else -> "$days дней"
    }
}