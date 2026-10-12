package ru.adan.silmaril.platform

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

actual class MccpInflater actual constructor() {
    private val inflater = Inflater()
    private var streamEnded = false
    private var remaining = byteArrayOf()

    actual fun decompress(input: ByteArray): ByteArray {
        if (streamEnded) return byteArrayOf()

        inflater.setInput(input)

        val out = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)

        while (!inflater.needsInput()) {
            val produced = inflater.inflate(chunk)
            if (produced > 0) {
                out.write(chunk, 0, produced)
            }

            // check before the produced == 0 exit: the last call may only consume the zlib trailer
            if (inflater.finished()) {
                streamEnded = true
                remaining = input.copyOfRange(input.size - inflater.remaining, input.size)
                break
            }

            if (produced == 0) break
        }

        return out.toByteArray()
    }

    actual fun isStreamEnded(): Boolean = streamEnded

    actual fun remainingInput(): ByteArray = remaining

    actual fun reset() {
        streamEnded = false
        remaining = byteArrayOf()
        inflater.reset()
    }

    actual fun close() {
        inflater.end()
    }
}
