package model

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.*
import java.net.Socket
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.zip.InflaterInputStream

class MudConnection(private val host: String, private val port: Int) {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // Flow to emit received data to whoever is listening (MainViewModel in this case)
    private val _textMessages = MutableSharedFlow<String>()
    val textMessages = _textMessages.asSharedFlow()  // Expose as immutable flow to MainViewModel

    // when we receive a custom message, read its type and store it in this variable
    // when we get out of a custom message, set it back to -1
    private var _customMessageType : Int = -1

    // messages of custom protocol
    private val _customMessages = MutableSharedFlow<String>()
    val customMessages = _customMessages.asSharedFlow()

    private var gluedMessage : String = ""
    private var mainBufferPointer = 0
    private val mainBuffer = ByteArray(32767)

    private val charset = Charset.forName("Windows-1251")

    private var _compressionEnabled = false
    private var _customProtocolEnabled = false
    private var _compressionInProgress = false
    private var _zlibDecompressionStream: InflaterInputStream? = null

    private val clientScope = CoroutineScope(Dispatchers.IO)
    private var clientJob: Job? = null
    private val reconnectScope = CoroutineScope(Dispatchers.IO)

    object TelnetConstants {
        const val GoAhead: Byte = 0xF9.toByte()
        const val InterpretAsCommand: Byte = 0xFF.toByte() // IAC: Interpret As Command
        const val Will: Byte = 0xFB.toByte()
        const val WillNot: Byte = 0xFC.toByte()
        const val Do: Byte = 0xFD.toByte()
        const val DoNot: Byte = 0xFE.toByte()
        const val Compress: Byte = 0x55.toByte()  // MCCP Compression (Mud Client Compression Protocol)
        const val CustomProtocol: Byte = 0x57.toByte() // Custom Protocol
        const val SubNegotiationStart: Byte = 0xFA.toByte() // SB
        const val SubNegotiationEnd: Byte = 0xF0.toByte() // SE
        const val Echo: Byte = 0x01.toByte()    // Echo
    }

    object ControlCharacters {
        const val CarriageReturn : Byte = 0x0D.toByte() // \r
        const val LineFeed : Byte = 0x0A.toByte() // \n
        const val Escape : Byte = 0x1B.toByte() // escape
    }

    // Attempt to establish the connection
    fun connect(): Boolean {
        return try {
            println("Connecting to $host:$port (thread: ${Thread.currentThread().name})")
            socket = Socket(host, port)  // Try to connect to the host, a blocking call
            println("Connection established to $host:$port")
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
            startReadingData()
            true  // Connection successful, return true
        } catch (e: UnknownHostException) {
            println("Unknown host: ${e.message}")
            false  // Connection failed, return false
        } catch (e: IOException) {
            println("Connection failed: ${e.message}")
            false  // Connection failed, return false
        }
    }

    // this closes the connection without possibility of reconnection, e.g. when closing the window
    fun closeDefinitive() {
        clientScope.cancel()
        close()
    }

    // Close the connection during reconnect
    private fun close() {
        inputStream?.close()
        outputStream?.close()
        socket?.close()
        _zlibDecompressionStream?.close()
        _zlibDecompressionStream = null
        _compressionInProgress = false
        _compressionEnabled = false
        _customProtocolEnabled = false
    }

    fun forceDisconnect() {
        socket?.close()
    }

    private fun reconnect() {
        reconnectScope.launch {
            println("Reconnecting...")
            //Thread.sleep(100)
            clientJob?.cancel()
            clientJob?.join()
            close()
            var connected = false
            while (!connected)
                connected = connect()
        }
    }

    /************** SEND *************/

    fun sendMessage(message: String) {
        // Convert the message to bytes using the correct charset (Windows-1251)
        val messageBytes = message.toByteArray(charset) + 0x0A.toByte()

        try {
            outputStream?.let { outStream ->
                val processedMessage = duplicateIACBytes(messageBytes)
                outStream.write(processedMessage)
                outStream.flush()  // Flush to ensure all bytes are sent
            }
        } catch (e: Exception) {
            println("Error sending string message: ${e.message}")
        }
    }

    // send raw bytes, don't duplicate IAC bytes
    private fun sendRaw(messageBytes : ByteArray) {
        try {
            outputStream?.let { outStream ->
                outStream.write(messageBytes)
                outStream.flush()  // Flush to ensure all bytes are sent
            }
        } catch (e: Exception) {
            println("Error sending raw message: ${e.message}")
        }
    }

    // Duplicate any IAC bytes, i.e. turn any occurrence of 255 into 255,255
    // This is because "я" is 255, but 255 is IAC in telnet, so mud expects double 255s
    private fun duplicateIACBytes(buffer: ByteArray): ByteArray {
        // Use a buffer to hold the expanded message with duplicated IAC bytes
        val processingBuffer = ByteArray(buffer.size * 2) // Maximum size to handle doubling

        var currentInProcessingBuffer = 0
        var currentInBuffer = 0

        while (currentInBuffer < buffer.size) {
            if (buffer[currentInBuffer] == TelnetConstants.InterpretAsCommand) {
                processingBuffer[currentInProcessingBuffer] = TelnetConstants.InterpretAsCommand
                currentInProcessingBuffer++
            }

            processingBuffer[currentInProcessingBuffer] = buffer[currentInBuffer]
            currentInProcessingBuffer++
            currentInBuffer++
        }

        return processingBuffer.copyOf(currentInProcessingBuffer)
    }

    /**************** RECEIVE ****************/

    // Start receiving messages asynchronously using a coroutine
    private fun startReadingData() {
        println("trying to launch a couroutine")

        clientJob = clientScope.launch {
            try {
                println("coroutine launched")
                val buffer = ByteArray(32767)
                while (true) {
                    // Choose the input stream based on whether compression is on or off
                    val currentStream = if (_compressionInProgress) {
                        _zlibDecompressionStream
                    } else {
                        inputStream
                    }
                    val bytesRead = currentStream?.read(buffer)
                    if (bytesRead == -1) break // Connection closed
                    processData(buffer, bytesRead!!)
                }
            } catch (e: IOException) {
                println("Error while receiving: ${e.message}")
            } finally {
                // If we exit the loop, reconnect (return to main thread)
                // If an exception happens, also reconnect
                withContext(Dispatchers.Main) {
                    reconnect()
                }
            }
        }
    }

    /**************** PROCESS ****************/

    private suspend fun processData(data : ByteArray, byteLength: Int)
    {
        val debug = false
        var skipCount = 0
        for (offset in 0 until byteLength) {
            if (skipCount > 0) {
                skipCount--
                continue
            }

            // Hamdle IAC WILL COMPRESS
            if (!_compressionEnabled && offset + 2 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.Will
                && data[offset + 2] == TelnetConstants.Compress) {
                // Respond with IAC DO COMPRESS
                _compressionEnabled = true
                if (debug)
                    println("Detected command: IAC WILL COMPRESS")
                sendRaw(byteArrayOf(TelnetConstants.InterpretAsCommand, TelnetConstants.Do, TelnetConstants.Compress))
                skipCount = 2
                //flushMainBuffer()
                continue
            }

            // Handle IAC WILL Custom Protocol
            if (!_customProtocolEnabled && offset + 2 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.Will
                && data[offset + 2] == TelnetConstants.CustomProtocol) {
                // Enable the custom protocol and respond with IAC DO CUSTOM_PROTOCOL
                _customProtocolEnabled = true
                if (debug)
                    println("Detected command: IAC WILL CUSTOM")
                sendRaw(byteArrayOf(TelnetConstants.InterpretAsCommand, TelnetConstants.Do, TelnetConstants.CustomProtocol))
                skipCount = 2
                //flushMainBuffer()
                continue
            }

            // Beginning of MCCP Compression negotiation: IAC SB COMPRESS2 WILL SE
            if (_compressionEnabled && offset + 4 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.SubNegotiationStart
                && data[offset + 2] == TelnetConstants.Compress
                && data[offset + 3] == TelnetConstants.Will
                && data[offset + 4] == TelnetConstants.SubNegotiationEnd) {
                if (debug)
                    println("Detected command: IAC SUB_START COMPRESS WILL SUB_STOP")
                _compressionInProgress = true
                _zlibDecompressionStream = InflaterInputStream(inputStream)  // Start decompression
                skipCount = 4
                //flushMainBuffer()
                continue
            }

            // Detect beginning of custom protocol message
            if (_customProtocolEnabled && _customMessageType == -1 && offset + 3 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.SubNegotiationStart
                && data[offset + 2] == TelnetConstants.CustomProtocol) {
                _customMessageType = data[offset + 3].toInt() // 4th byte carries the message type
                if (debug)
                    println("Detected command: custom message start ${data[offset + 3]}")
                //_dataFlow.emit("Custom protocol begin: ${data[offset + 3]}")
                skipCount = 3
                //flushMainBuffer()
                continue
            }

            // Detect end of custom protocol
            if (_customProtocolEnabled && _customMessageType != -1 && offset + 1 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.SubNegotiationEnd) {
                if (debug)
                    println("Detected command: custom message end")
                flushMainBuffer()
                _customMessageType = -1
                //_dataFlow.emit("Custom protocol end")
                skipCount = 1
                continue
            }

            // ignore echo mode for now
            if (offset + 2 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.Will
                && data[offset + 2] == TelnetConstants.Echo)
            {
                if (debug)
                    println("Detected command: IAC WILL ECHO")
                skipCount = 2
                //flushMainBuffer()
                continue
            }

            // ignore echo mode for now
            if (offset + 2 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.WillNot
                && data[offset + 2] == TelnetConstants.Echo)
            {
                if (debug)
                    println("Detected command: IAC WONT ECHO")
                skipCount = 2
                //flushMainBuffer()
                continue
            }

            // if encountering IAC GA (at the beginning of the message), flush the buffer
            if (offset == 0
                && 1 < byteLength
                && data[0] == TelnetConstants.InterpretAsCommand
                && data[1] == TelnetConstants.GoAhead) {
                if (debug)
                    println("Detected command: IAC GA")
                skipCount = 1
                flushMainBuffer()
                continue
            }

            // if encountering IAC GA (not at the beginning of the message), flush the buffer
            if (offset > 0
                && offset + 1 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.GoAhead
                && data[offset - 1] != TelnetConstants.InterpretAsCommand) {
                if (debug)
                    println("Detected command: IAC GA")
                skipCount = 1
                flushMainBuffer()
                continue
            }

            // end of telnet commands

            // remove double 255,255
            // reason: the letter 'я' in cp1251 is FF, but in Telnet FF is reserved for IAC, so Mud sends us FFFF for letter 'я'
            // at this point we've treated all known IAC commands, so it's safe to assume the rest is part of normal strings
            if (offset + 1 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.InterpretAsCommand) {
                mainBuffer[mainBufferPointer] = data[offset]
                mainBufferPointer++
                skipCount = 1
                continue
            }

            // if encountering \r\n, flush the buffer
            if (offset + 1 < byteLength
                && data[offset] == ControlCharacters.CarriageReturn
                && data[offset + 1] == ControlCharacters.LineFeed) {
                skipCount = 1
                flushMainBuffer()
                continue
            }

            mainBuffer[mainBufferPointer] = data[offset]
            mainBufferPointer++
        }
    }

    private suspend fun flushMainBuffer() {
        val byteMsg = mainBuffer.copyOfRange(0, mainBufferPointer)
        val message = String(byteMsg, charset)
        if (_customMessageType != -1) {
            _customMessages.emit(message)
        } else {
            _textMessages.emit(message)
        }
        mainBufferPointer = 0
    }
}