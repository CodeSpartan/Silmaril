package ru.adan.silmaril.platform

import com.jcraft.jzlib.Inflater
import com.jcraft.jzlib.JZlib

actual class ZlibDecompressor actual constructor() {
    private val inflater = Inflater()
    private val outputBuffer = ByteArray(65536)

    init {
        inflater.init()
    }

    actual fun decompress(input: ByteArray, inputOffset: Int, inputLength: Int): ByteArray? {
        return try {
            inflater.setInput(input, inputOffset, inputLength, false)

            val result = mutableListOf<Byte>()

            while (true) {
                inflater.setOutput(outputBuffer, 0, outputBuffer.size)
                val status = inflater.inflate(JZlib.Z_SYNC_FLUSH)

                if (status != JZlib.Z_OK && status != JZlib.Z_BUF_ERROR) {
                    break
                }

                val outputLength = outputBuffer.size - inflater.avail_out
                if (outputLength > 0) {
                    for (i in 0 until outputLength) {
                        result.add(outputBuffer[i])
                    }
                }

                if (inflater.avail_in == 0) {
                    break
                }
            }

            result.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    actual fun reset() {
        inflater.init()
    }

    actual fun close() {
        inflater.end()
    }
}
