package ru.adan.silmaril.platform

/**
 * Platform-specific charset encoding for Windows-1251 (Cyrillic)
 */
expect object CharsetProvider {
    fun encodeToWindows1251(text: String): ByteArray
    fun decodeFromWindows1251(bytes: ByteArray): String
    fun decodeFromWindows1251(bytes: ByteArray, offset: Int, length: Int): String
}
