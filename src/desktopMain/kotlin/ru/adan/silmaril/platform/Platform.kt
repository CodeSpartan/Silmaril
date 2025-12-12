package ru.adan.silmaril.platform

import java.io.File
import java.nio.file.Paths

actual object Platform {
    actual val name: String = "Desktop"
    actual val isDesktop: Boolean = true
    actual val isAndroid: Boolean = false
}

actual fun getDocumentsDirectory(): String {
    val userHome = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()

    return when {
        os.contains("win") -> Paths.get(userHome, "Documents", "Silmaril").toString()
        os.contains("mac") -> Paths.get(userHome, "Documents", "Silmaril").toString()
        else -> Paths.get(userHome, "Documents", "Silmaril").toString()
    }.also {
        File(it).mkdirs()
    }
}

actual fun getConfigDirectory(): String {
    val userHome = System.getProperty("user.home")
    return Paths.get(userHome, ".silmaril").toString().also {
        File(it).mkdirs()
    }
}
