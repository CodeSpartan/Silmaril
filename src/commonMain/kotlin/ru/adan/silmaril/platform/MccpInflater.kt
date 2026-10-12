package ru.adan.silmaril.platform

/**
 * Streaming zlib inflater for MCCP (Mud Client Compression Protocol).
 * Provides more control than ZlibDecompressor for handling stream state.
 */
expect class MccpInflater() {
    /**
     * Decompress data from the stream.
     * @param input compressed data
     * @return decompressed data
     */
    fun decompress(input: ByteArray): ByteArray

    /**
     * Check if the stream has ended (Z_STREAM_END received)
     */
    fun isStreamEnded(): Boolean

    /**
     * After the stream has ended: the bytes of the last input that came after the end of the
     * compressed stream (uncompressed data the server sent once it turned compression off).
     */
    fun remainingInput(): ByteArray

    /**
     * Reset the inflater state
     */
    fun reset()

    /**
     * Close and release resources
     */
    fun close()
}
