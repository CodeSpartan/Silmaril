package model

import AnsiColor
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
    private val _textMessages = MutableSharedFlow<TextMessageData>()
    val textMessages = _textMessages.asSharedFlow()  // Expose as immutable flow to MainViewModel

    // when we receive a custom message, read its type and store it in this variable
    // when we get out of a custom message, set it back to -1
    private var _customMessageType : Int = -1

    // messages of custom protocol
    private val _customMessages = MutableSharedFlow<String>()
    val customMessages = _customMessages.asSharedFlow()

    private var mainBufferPointer = 0
    private val mainBuffer = ByteArray(32767)
    private var colorTreatmentPointer = 0
    private val colorTreatmentBuffer = ByteArray(32767)
    private var currentColor : AnsiColor = AnsiColor.None
    private var isColorBright : Boolean = false

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

    private fun bufferToColorfulText() : TextMessageData {
        colorTreatmentPointer = 0

        val gatheredChunks : MutableList<TextMessageChunk> = mutableListOf()

        var parsingFirstParam: Boolean = false
        var parsingSecondParam: Boolean = false
        var firstParam: String = ""
        var secondParam: String = ""

        var skipNextByte: Boolean = false
        for (offset in 0 until mainBufferPointer) {
            if (skipNextByte) {
                skipNextByte = false
                continue
            }

            // the color can be serialized in two ways
            // A one param color: '\033'[<color>m -- this always means the <color> isn't bright
            // A two param color: '\033'[<bright>;<color>m
            // The color is sent not by bytes, but by string, which needs to be converted into int
            // And then you subtract 30 from it, and you get the color from the AnsiColor enum by order
            // So for example, color "31" sent as two bytes is red because AnsiColor[1] is red
            if (mainBuffer[offset] == ControlCharacters.Escape && offset + 1 < mainBufferPointer
                && mainBuffer[offset + 1] == 0x5B.toByte()) {
                // when we hit a new color, flush any text
                if (colorTreatmentPointer > 0) {
                    val byteMsg = colorTreatmentBuffer.copyOfRange(0, colorTreatmentPointer)
                    gatheredChunks.add(TextMessageChunk(currentColor, AnsiColor.Black, isColorBright, String(byteMsg, charset)))
                    colorTreatmentPointer = 0
                }
                parsingFirstParam = true
                skipNextByte = true
                continue
            }

            if (parsingFirstParam || parsingSecondParam) {
                // when we hit 'm', we're done parsing color
                if (mainBuffer[offset] == 0x6D.toByte()) {
                    parsingFirstParam = false
                    parsingSecondParam = false
                    updateCurrentColor(firstParam, secondParam)
                    firstParam = ""
                    secondParam = ""
                }
                // when we hit ';', it means we're done parsing the first param and we're now parsing the second param
                else if (mainBuffer[offset] == 0x3B.toByte()) {
                    parsingFirstParam = false
                    parsingSecondParam = true
                }
                else {
                    if (parsingFirstParam){
                        firstParam += mainBuffer[offset].toInt().toChar()
                    } else {
                        secondParam += mainBuffer[offset].toInt().toChar()
                    }
                }
                continue
            }
            // if we're not dealing with a color, just copy the string bytes
            colorTreatmentBuffer[colorTreatmentPointer] = mainBuffer[offset]
            colorTreatmentPointer++
        }
        // once we're done with the message, flush any remaining bytes
        if (colorTreatmentPointer > 0) {
            val byteMsg = colorTreatmentBuffer.copyOfRange(0, colorTreatmentPointer)
            gatheredChunks.add(TextMessageChunk(currentColor, AnsiColor.Black, isColorBright, String(byteMsg, charset)))
            colorTreatmentPointer = 0
        }

        return TextMessageData(chunks = gatheredChunks.toTypedArray())
    }

    // returns a TextMessageChunk without text, but with correctly set color information
    private fun updateCurrentColor(param1 : String, param2: String)
    {
        var col = 0
        if (param1.isNotEmpty() && param2.isNotEmpty()) {
            isColorBright = param1.toInt() == 1
            col = param2.toInt()
        } else {
            isColorBright = false
            col = param1.toInt()
        }
        if (col != 0)
            currentColor = AnsiColor.entries.toTypedArray()[col - 30]
        else
            currentColor = AnsiColor.None
    }


    private suspend fun flushMainBuffer() {
        if (_customMessageType != -1) {
            val byteMsg = mainBuffer.copyOfRange(0, mainBufferPointer)
            _customMessages.emit(String(byteMsg, charset))
        } else {
            val gluedMessage = bufferToColorfulText()
            if (gluedMessage.chunks.isNotEmpty()) {
                for (chunk in gluedMessage.chunks) {
                    print(chunk.text)
                }
                print('\n')
                _textMessages.emit(gluedMessage)
            } else {
                _textMessages.emit(emptyTextMessage())
            }
        }
        mainBufferPointer = 0
    }

    private fun emptyTextMessage() : TextMessageData {
        return TextMessageData(arrayOf(TextMessageChunk(AnsiColor.None, AnsiColor.None, false, "")))
    }
}