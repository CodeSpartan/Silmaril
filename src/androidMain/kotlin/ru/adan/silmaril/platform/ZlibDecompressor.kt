package ru.adan.silmaril.platform

import java.util.zip.Inflater

actual class ZlibDecompressor actual constructor() {
    private val inflater = Inflater()
    private val outputBuffer = ByteArray(65536)

    actual fun decompress(input: ByteArray, inputOffset: Int, inputLength: Int): ByteArray? {
        return try {
            inflater.setInput(input, inputOffset, inputLength)

            val result = mutableListOf<Byte>()

            while (!inflater.needsInput()) {
                val outputLength = inflater.inflate(outputBuffer)
                if (outputLength > 0) {
                    for (i in 0 until outputLength) {
                        result.add(outputBuffer[i])
                    }
                } else {
                    break
                }
            }

            result.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    actual fun reset() {
        inflater.reset()
    }

    actual fun close() {
        inflater.end()
    }
}
