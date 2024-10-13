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
    private val _dataFlow = MutableSharedFlow<String>()
    val dataFlow = _dataFlow.asSharedFlow()  // Expose as immutable flow to MainViewModel

    // when we receive a custom message, read its type and store it in this variable
    // when we get out of a custom message, set it back to -1
    private var _customMessageType : Int = -1

    // messages of custom protocol
    private val _customMessages = MutableSharedFlow<String>()
    val customMessages = _customMessages.asSharedFlow()

    private var gluedMessage : String = ""
    val mainBuffer = ByteArray(32767)

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

    // Attempt to establish the connection
    fun connect(): Boolean {
        return try {
            println("Connecting to $host:$port (thread: ${Thread.currentThread().name})")
            socket = Socket(host, port)  // Try to connect to the host, blocking call?
            println("Connection established to $host:$port")
            inputStream = socket?.getInputStream()  // Get the input stream
            outputStream = socket?.getOutputStream()  // Get the output stream
            startReadingData()
            true  // Connection successful, return true
        } catch (e: UnknownHostException) {
            println("Unknown host: ${e.message}")  // Handle unknown host error
            false  // Connection failed, return false
        } catch (e: IOException) {
            println("Connection failed: ${e.message}")  // Handle connection failure
            false  // Connection failed, return false
        }
    }

    // this closes the connection without possibility of reconnection, e.g. when closing the window
    fun closeDefinitive() {
        clientScope.cancel()
        close()
    }

    // Close the connection during reconnect
    fun close() {
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

        // Loop through the buffer and duplicate any 255 (IAC) bytes
        while (currentInBuffer < buffer.size) {
            // Check if byte is an IAC (255)
            if (buffer[currentInBuffer] == TelnetConstants.InterpretAsCommand) {
                // Duplicate IAC byte (as per Telnet ToS)
                processingBuffer[currentInProcessingBuffer] = TelnetConstants.InterpretAsCommand
                currentInProcessingBuffer++
            }

            // Copy the buffer byte to processing buffer
            processingBuffer[currentInProcessingBuffer] = buffer[currentInBuffer]
            currentInProcessingBuffer++
            currentInBuffer++
        }

        // Return only the portion of the buffer used
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
                    val newBytes = fixDoubleYa(buffer, bytesRead!!) // in-place fix of letter 'ya'
                    var processedBytes = 0
                    while (processedBytes < newBytes)
                        processedBytes = processData(buffer, processedBytes, newBytes)
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

    // This can be telnet commands, XML or text. We'll detect & treat each type here.
    private suspend fun processData(data : ByteArray, startOffset: Int, byteLength: Int) : Int {
        println("Processing data of length: $byteLength")
        var iacPosition = findIAC(data, startOffset, byteLength)
        // if we're directly in front of an IAC command, process it
        if (iacPosition == startOffset) {
            val commandLength = processIAC(data, startOffset, byteLength)
            println("Processed command bytes $commandLength")
            // Debug: Build a string from the byte array representation in hex
            if (commandLength > 0) {
                val hexString = data.copyOfRange(startOffset, startOffset + commandLength)
                    .joinToString(separator = " ") { byte -> "%02X".format(byte) }
                // _dataFlow.emit(hexString)
                println("Received Bytes (Hex): $hexString")
            }

            if (commandLength > 0) {
                return startOffset + commandLength
            } else {
                iacPosition = findIAC(data, startOffset+1, byteLength)
            }
        }
        // if the message doesn't have an IAC command, process it all as text
        if (iacPosition == -1) {
            val byteMsg = data.copyOfRange(startOffset, byteLength)

            if (byteLength - startOffset >= 2) {
                val hexString = byteMsg.copyOfRange(0, 2).joinToString(separator = " ") { byte -> "%02X".format(byte) }
                println("First bytes: $hexString")
            }

            val message = String(byteMsg, charset)
            if (_customMessageType != -1) {
                _customMessages.emit(message)
            } else {
                _dataFlow.emit(message)
            }
            println("Received String length ${byteLength - startOffset} (no iac): $message")

            return byteLength
        }

        // if the message has an IAC command down the line, process anything before it as text
        val byteMsg = data.copyOfRange(startOffset, iacPosition)

        if (iacPosition - startOffset >= 2) {
            val hexString = byteMsg.copyOfRange(0, 2).joinToString(separator = " ") { byte -> "%02X".format(byte) }
            println("First bytes: $hexString")
        }

        val message = String(byteMsg, charset)
        if (_customMessageType != -1) {
            _customMessages.emit(message)
        } else {
            _dataFlow.emit(message)
        }
        println("Received String length ${iacPosition - startOffset} (until iac $iacPosition): $message")

        // keep processing data recursively
        return iacPosition


    }

    // the letter 'я' in cp1251 is FF, but in Telnet FF is reserved for IAC, so Mud sends us FFFF for letter 'я'
    // we go over the ByteArray and replace all occurrences of 'яя' with 'я'
    private fun fixDoubleYa(data: ByteArray, byteLength: Int): Int {
        var writeIndex = 0  // Destination index for writing non-duplicate 'я' (0xFF)
        var readIndex = 0   // Current position in the input byte array

        while (readIndex < byteLength) {
            val currentByte = data[readIndex]  // Read the current byte

            // Check if current byte is 'я' (0xFF) and the next one is 'я' too
            if (currentByte == 0xFF.toByte() && readIndex + 1 < byteLength && data[readIndex + 1] == 0xFF.toByte()) {
                data[writeIndex] = 0xFF.toByte()   // Write one instance of 0xFF
                writeIndex++                       // Move the write pointer
                readIndex += 2                     // Skip the next byte (duplicate 'я')
            } else {
                data[writeIndex] = data[readIndex]  // Otherwise copy the byte
                writeIndex++                        // Move both pointers
                readIndex++
            }
        }

        // Return new length, which is where the writeIndex stopped
        return writeIndex
    }

    private fun findIAC(data: ByteArray, offset: Int, bytesLength: Int): Int {
        // Search for Interpret As Command (255 / 0xFF) in the buffer
        for (i in offset until bytesLength) {
            if(data[i] == TelnetConstants.InterpretAsCommand) {
                if (i + 1 <= bytesLength && (
                        data[i + 1] == TelnetConstants.Will ||
                        data[i + 1] == TelnetConstants.WillNot ||
                        data[i + 1] == TelnetConstants.SubNegotiationStart ||
                        data[i + 1] == TelnetConstants.SubNegotiationEnd ||
                        data[i + 1] == TelnetConstants.GoAhead
                        )) {
                    return i
                }
            }
        }
        return -1 // Return -1 if IAC not found
    }

    // Process commands for negotiation
    // Send back appropriate response
    // Return the number of processed bytes
    private suspend fun processIAC(data: ByteArray, offset: Int, byteLength: Int): Int {
        val bytesCount = byteLength - offset

        // remove double IAC
        if (byteLength >= 2
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.InterpretAsCommand) {
            return 1
        }

        // Are we receiving IAC WILL MCCP (Compression)?
        if (!_compressionEnabled && bytesCount >= 3
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.Will
            && data[offset + 2] == TelnetConstants.Compress) {

            // Respond to IAC WILL COMPRESS with IAC DO COMPRESS
            _compressionEnabled = true
            println("Detected command: IAC WILL COMPRESS")
            sendRaw(byteArrayOf(TelnetConstants.InterpretAsCommand, TelnetConstants.Do, TelnetConstants.Compress))
            return 3
        }

        // Handle IAC WILL Custom Protocol
        if (!_customProtocolEnabled && bytesCount >= 3
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.Will
            && data[offset + 2] == TelnetConstants.CustomProtocol) {

            // Enable the custom protocol and respond with IAC DO CUSTOM_PROTOCOL
            _customProtocolEnabled = true
            println("Detected command: IAC WILL CUSTOM")
            sendRaw(byteArrayOf(TelnetConstants.InterpretAsCommand, TelnetConstants.Do, TelnetConstants.CustomProtocol))
            return 3
        }

        // Beginning of MCCP Compression negotiation: IAC SB COMPRESS2 WILL SE
        if (_compressionEnabled && bytesCount >= 5
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.SubNegotiationStart
            && data[offset + 2] == TelnetConstants.Compress
            && data[offset + 3] == TelnetConstants.Will
            && data[offset + 4] == TelnetConstants.SubNegotiationEnd) {
            println("Detected command: IAC SUB_START COMPRESS WILL SUB_STOP")

            _compressionInProgress = true
            _zlibDecompressionStream = InflaterInputStream(inputStream)  // Start decompression
            return 5
        }

        // Detect beginning of custom protocol message
        if (_customProtocolEnabled && _customMessageType == -1 && bytesCount >= 4
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.SubNegotiationStart
            && data[offset + 2] == TelnetConstants.CustomProtocol) {
            _customMessageType = data[offset + 3].toInt() // 4th byte is supposed to mean message type
            println("Detected command: custom message start ${data[offset + 3]}")
            //_dataFlow.emit("Custom protocol begin: ${data[offset + 3]}")
            return 4
        }

        // Detect end of custom protocol
        if (_customProtocolEnabled && _customMessageType != -1 && bytesCount >= 2
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.SubNegotiationEnd) {
            println("Detected command: custom message end")
            _customMessageType = -1
            //_dataFlow.emit("Custom protocol end")
            return 2
        }

        // process IAC GA as new line
        // only process IAC GA at the end of the message
        if (byteLength >= 2 && offset + 2 == byteLength
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.GoAhead)
        {
            println("Detected command: IAC GA")
            // new line ? or ignore it?
            data[offset + 1] = 0xA.toByte()
            return 2
        }

        // ignore echo mode for now
        if (byteLength >= 3
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.Will
            && data[offset + 2] == TelnetConstants.Echo)
        {
            println("Detected command: IAC WILL ECHO")
            return 3
        }

        // ignore echo mode for now
        if (byteLength >= 3
            && data[offset] == TelnetConstants.InterpretAsCommand
            && data[offset + 1] == TelnetConstants.WillNot
            && data[offset + 2] == TelnetConstants.Echo)
        {
            println("Detected command: IAC WONT ECHO")
            return 3
        }

        return 0 // If it doesn't match any known IAC patterns
    }
}