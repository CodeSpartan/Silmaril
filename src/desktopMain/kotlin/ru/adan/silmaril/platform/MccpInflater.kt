package ru.adan.silmaril.platform

import com.jcraft.jzlib.Inflater
import com.jcraft.jzlib.JZlib
import java.io.ByteArrayOutputStream

actual class MccpInflater actual constructor() {
    private val inflater = Inflater()
    private var streamEnded = false

    init {
        inflater.init(JZlib.W_ZLIB)
    }

    actual fun decompress(input: ByteArray): ByteArray {
        if (streamEnded) return byteArrayOf()

        inflater.setInput(input)

        val out = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)

        while (true) {
            inflater.setOutput(chunk, 0, chunk.size)

            val beforeInAvail = inflater.avail_in
            val beforeOutIdx = inflater.next_out_index

            val rc = inflater.inflate(JZlib.Z_SYNC_FLUSH)

            val produced = inflater.next_out_index - beforeOutIdx
            val consumed = beforeInAvail - inflater.avail_in

            if (produced > 0) {
                out.write(chunk, 0, produced)
            }

            when (rc) {
                JZlib.Z_OK -> {
                    if (consumed == 0 && produced == 0) break
                }
                JZlib.Z_STREAM_END -> {
                    streamEnded = true
                    break
                }
                JZlib.Z_BUF_ERROR -> {
                    // Not fatal, continue
                }
                else -> {
                    // Error
                    break
                }
            }

            if (inflater.avail_in == 0 && produced == 0) break
        }

        return out.toByteArray()
    }

    actual fun isStreamEnded(): Boolean = streamEnded

    actual fun reset() {
        streamEnded = false
        inflater.init(JZlib.W_ZLIB)
    }

    actual fun close() {
        inflater.end()
    }
}
