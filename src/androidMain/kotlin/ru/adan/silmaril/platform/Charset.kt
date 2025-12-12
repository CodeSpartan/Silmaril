package ru.adan.silmaril.platform

import java.nio.charset.Charset

actual object CharsetProvider {
    private val windows1251 = Charset.forName("Windows-1251")

    actual fun encodeToWindows1251(text: String): ByteArray {
        return text.toByteArray(windows1251)
    }

    actual fun decodeFromWindows1251(bytes: ByteArray): String {
        return String(bytes, windows1251)
    }

    actual fun decodeFromWindows1251(bytes: ByteArray, offset: Int, length: Int): String {
        return String(bytes, offset, length, windows1251)
    }
}
