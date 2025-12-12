package ru.adan.silmaril.platform

/**
 * Platform-specific zlib decompression for MCCP (Mud Client Compression Protocol)
 */
expect class ZlibDecompressor() {
    /**
     * Decompress zlib-compressed data
     * @param input compressed data
     * @param inputOffset offset in input array
     * @param inputLength length of compressed data
     * @return decompressed data, or null if decompression fails
     */
    fun decompress(input: ByteArray, inputOffset: Int, inputLength: Int): ByteArray?

    /**
     * Reset the decompressor state
     */
    fun reset()

    /**
     * Close and release resources
     */
    fun close()
}
