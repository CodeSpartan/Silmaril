package ru.adan.silmaril.platform

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

actual class MccpInflater actual constructor() {
    private val inflater = Inflater()
    private var streamEnded = false

    actual fun decompress(input: ByteArray): ByteArray {
        if (streamEnded) return byteArrayOf()

        inflater.setInput(input)

        val out = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)

        while (!inflater.needsInput()) {
            val produced = inflater.inflate(chunk)
            if (produced > 0) {
                out.write(chunk, 0, produced)
            } else {
                break
            }

            if (inflater.finished()) {
                streamEnded = true
                break
            }
        }

        return out.toByteArray()
    }

    actual fun isStreamEnded(): Boolean = streamEnded

    actual fun reset() {
        streamEnded = false
        inflater.reset()
    }

    actual fun close() {
        inflater.end()
    }
}
