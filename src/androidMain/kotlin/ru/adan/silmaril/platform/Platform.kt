package ru.adan.silmaril.platform

import android.content.Context
import java.io.File

actual object Platform {
    actual val name: String = "Android"
    actual val isDesktop: Boolean = false
    actual val isAndroid: Boolean = true
}

// Android context holder for accessing file directories
object AndroidContext {
    private var _context: Context? = null

    fun initialize(context: Context) {
        _context = context.applicationContext
    }

    val context: Context
        get() = _context ?: throw IllegalStateException(
            "AndroidContext not initialized. Call AndroidContext.initialize(context) in Application.onCreate()"
        )
}

actual fun getDocumentsDirectory(): String {
    val context = AndroidContext.context
    return File(context.getExternalFilesDir(null), "Silmaril").apply {
        mkdirs()
    }.absolutePath
}

actual fun getConfigDirectory(): String {
    val context = AndroidContext.context
    return context.filesDir.absolutePath
}
