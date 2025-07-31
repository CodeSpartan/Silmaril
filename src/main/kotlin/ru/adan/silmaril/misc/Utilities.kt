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