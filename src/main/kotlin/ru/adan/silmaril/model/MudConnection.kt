package ru.adan.silmaril.model

import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.mud_messages.CurrentRoomMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.adan.silmaril.mud_messages.TextMessageChunk
import ru.adan.silmaril.mud_messages.ColorfulTextMessage
import java.io.*
import java.net.Socket
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.InflaterInputStream

// Useful link: https://www.ascii-code.com/CP1251

class MudConnection(
    var host: String,
    var port: Int,
    private val onMessageReceived: (String) -> Unit,
    private val settingsManager: SettingsManager,
) {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // Flow to emit received text messages to whoever is listening (MainViewModel in this case)
    private val _colorfulTextMessages = MutableSharedFlow<ColorfulTextMessage>()
    val colorfulTextMessages = _colorfulTextMessages.asSharedFlow()  // Expose flow to MainViewModel

    private val _currentRoomMessages = MutableStateFlow(CurrentRoomMessage.EMPTY)
    val currentRoomMessages: StateFlow<CurrentRoomMessage> get() = _currentRoomMessages

    private val _isEchoOn = MutableStateFlow(false)
    val isEchoOn: StateFlow<Boolean> get() = _isEchoOn

    // when we receive a custom message, read its type and store it in this variable
    // when we get out of a custom message, set it back to -1
    private var _customMessageType : Int = -1

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

    private var clientJob: Job? = null
    private val clientScope = CoroutineScope(Dispatchers.IO)
    private val reconnectScope = CoroutineScope(Dispatchers.IO)
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lastSendTimestamp = AtomicLong(System.currentTimeMillis())

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

    init {
        launchKeepAliveJob()
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
        keepAliveScope.cancel()
        reconnectScope.cancel()
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
        _customMessageType = -1
        mainBufferPointer = 0
        mainBuffer.fill(0)
    }

    fun forceDisconnect() {
        close()
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

    fun isConnected(): Boolean {
        return socket?.isConnected ?: false
    }

    /************** SEND *************/

    fun sendMessage(message: String) {
        lastSendTimestamp.set(System.currentTimeMillis())

        // Convert the message to bytes using the correct charset (Windows-1251)
        val messageBytes = message.toByteArray(charset) + ControlCharacters.LineFeed

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
        lastSendTimestamp.set(System.currentTimeMillis())
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

    // Sends '\n' every 28-29 minutes if there hasn't been any messages sent in that period
    private fun launchKeepAliveJob() {
        keepAliveScope.launch {
            val keepAliveIntervalMilliseconds = TimeUnit.MINUTES.toMillis(28L)
            while (true) {
                delay(TimeUnit.MINUTES.toMillis(1L)) // try every minute
                val timeSinceLastSend = System.currentTimeMillis() - lastSendTimestamp.get()
                if (timeSinceLastSend >= keepAliveIntervalMilliseconds && socket?.isConnected == true) {
                    sendRaw(byteArrayOf(ControlCharacters.LineFeed))
                }
            }
        }
    }

    /**************** RECEIVE ****************/

    // Start receiving messages asynchronously using a coroutine
    private fun startReadingData() {
        clientJob = clientScope.launch {
            try {
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
                // If we exit the loop, reconnect (return to ru.adan.silmaril.main thread)
                // If an exception happens, also reconnect
                withContext(Dispatchers.Main) {
                    if (settingsManager.settings.value.autoReconnect)
                        reconnect()
                }
            }
        }
    }

    /**************** PROCESS ****************/

    private suspend fun processData(data : ByteArray, byteLength: Int)
    {
        val debug = true
        var skipCount = 0
        for (offset in 0 until byteLength) {
            if (skipCount > 0) {
                skipCount--
                continue
            }

            // Handle IAC WILL COMPRESS
            if (!_compressionEnabled && offset + 2 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.Will
                && data[offset + 2] == TelnetConstants.Compress) {
                // Respond with IAC DO COMPRESS
                _compressionEnabled = true
                if (debug) {
                    val str = "Detected command: IAC WILL COMPRESS"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
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
                if (debug) {
                    val str = "Detected command: IAC WILL CUSTOM"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
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
                if (debug) {
                    val str = "Detected command: IAC SUB_START COMPRESS WILL SUB_STOP"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
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
                if (debug) {
                    val str = "Detected command: custom message start ${data[offset + 3]}"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
                // _textMessages.emit(whiteTextMessage("Custom protocol begin: ${data[offset + 3]}"))
                skipCount = 3
                //flushMainBuffer()
                continue
            }

            // Detect end of custom protocol
            // It can fail, if IAC has already arrived, but SNE hasn't yet.
            if (_customProtocolEnabled && _customMessageType != -1 && offset + 1 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.SubNegotiationEnd) {
                flushMainBuffer()
                if (debug) {
                    val str = "Detected command: custom message end"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
                _customMessageType = -1
                //_dataFlow.emit("Custom protocol end")
                skipCount = 1
                continue
            }

            // A different attempt to detect end of custom protocol, catching what the first one missed
            if (_customProtocolEnabled && _customMessageType != -1) {
                if (data[offset] == TelnetConstants.SubNegotiationEnd) {
                    if (mainBufferPointer > 0 && mainBuffer[mainBufferPointer-1] == TelnetConstants.InterpretAsCommand) {
                        mainBufferPointer--
                        flushMainBuffer()
                        if (debug) {
                            val str = "Detected command: custom message end (FALLBACK)"
                            //println(str)
                            FileLogger.log("MudConnection", str)
                        }
                        _customMessageType = -1
                        continue
                    }
                }
            }


            // when "will echo" is on, it means the next thing we enter will be a password
            if (offset + 2 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.Will
                && data[offset + 2] == TelnetConstants.Echo)
            {
                if (debug) {
                    val str = "Detected command: IAC WILL ECHO"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
                skipCount = 2
                _isEchoOn.value = true
                //flushMainBuffer()
                continue
            }

            // when "will echo" is off, means we're no longer entering a password
            if (offset + 2 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.WillNot
                && data[offset + 2] == TelnetConstants.Echo)
            {
                if (debug) {
                    val str = "Detected command: IAC WONT ECHO"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
                skipCount = 2
                _isEchoOn.value = false
                //flushMainBuffer()
                continue
            }

            // if encountering IAC GA (at the beginning of the message), flush the buffer
            if (offset == 0
                && 1 < byteLength
                && data[0] == TelnetConstants.InterpretAsCommand
                && data[1] == TelnetConstants.GoAhead) {
                if (debug) {
                    val str = "Detected command: IAC GA"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
                skipCount = 1
                // Update, no I don't think this solves anything. I'm commenting this out.
                /**
                * _customMessageType = -1 // added after a bug, watch that this doesn't break anything
                * // the reason being, we've had this scenario: custom message 14 arrives, then IAC, and then text, as if IAC ends any custom messages
                 */
                flushMainBuffer()
                continue
            }

            // if encountering IAC GA (not at the beginning of the message), flush the buffer
            if (offset > 0
                && offset + 1 < byteLength
                && data[offset] == TelnetConstants.InterpretAsCommand
                && data[offset + 1] == TelnetConstants.GoAhead
                && data[offset - 1] != TelnetConstants.InterpretAsCommand) {
                if (debug) {
                    val str = "Detected command: IAC GA"
                    //println(str)
                    FileLogger.log("MudConnection", str)
                }
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

            // if encountering \n, we should normally flush the buffer (this happens rarely without the preceding \r, but it does)
            // however, if this happens during a borked message (e.g. text message placed inside a custom message protocol),
            // this would break up the xml line by line and we don't want that
            if (_customMessageType == -1 && data[offset] == ControlCharacters.LineFeed) {
                flushMainBuffer()
                continue
            }

            mainBuffer[mainBufferPointer] = data[offset]
            mainBufferPointer++
        }
    }

    private fun bufferToColorfulText(buffer: ByteArray, bufferEndPointer: Int) : ColorfulTextMessage {
        val debug = true
        // print bytes
        if (debug) {
            val copiedBytes = buffer.copyOfRange(0, bufferEndPointer)
            val hexString = copiedBytes.joinToString(separator = " ") { byte -> String.format("%02X", byte) }
            val str = "Bytes in bufferToColorfulText: $hexString"
            //println(str)
            FileLogger.log("MudConnection", str)
        }

        colorTreatmentPointer = 0

        val gatheredChunks : MutableList<TextMessageChunk> = mutableListOf()

        var parsingFirstParam = false
        var parsingSecondParam = false
        var firstParam = ""
        var secondParam = ""

        var skipNextByte = false
        for (offset in 0 until bufferEndPointer) {
            if (skipNextByte) {
                skipNextByte = false
                continue
            }

            // the color can be serialized in two ways
            // A one param color: '0x1B'[<color>m -- this always means the <color> isn't bright
            // A two param color: '0x1B'[<bright>;<color>m
            // The color is sent not by bytes, but by string, which needs to be converted into int
            // And then you subtract 30 from it, and you get the color from the AnsiColor enum by order
            // So for example, color "31" sent as two bytes is red because AnsiColor[1] is red
            if (buffer[offset] == ControlCharacters.Escape && offset + 1 < bufferEndPointer
                && buffer[offset + 1] == 0x5B.toByte()) {
                // when we hit a new color, flush any text
                if (colorTreatmentPointer > 0) {
                    val byteMsg = colorTreatmentBuffer.copyOfRange(0, colorTreatmentPointer)
                    val text = String(byteMsg, charset)
                    gatheredChunks.add(TextMessageChunk(currentColor, AnsiColor.Black, isColorBright, text))
                    colorTreatmentPointer = 0
                }
                parsingFirstParam = true
                skipNextByte = true
                continue
            }

            if (parsingFirstParam || parsingSecondParam) {
                // when we hit 'm', we're done parsing color
                if (buffer[offset] == 0x6D.toByte()) {
                    parsingFirstParam = false
                    parsingSecondParam = false
                    updateCurrentColor(firstParam, secondParam)
                    firstParam = ""
                    secondParam = ""
                }
                // when we hit ';', it means we're done parsing the first param and we're now parsing the second param
                else if (buffer[offset] == 0x3B.toByte()) {
                    parsingFirstParam = false
                    parsingSecondParam = true
                }
                else {
                    if (parsingFirstParam){
                        firstParam += buffer[offset].toInt().toChar()
                    } else {
                        secondParam += buffer[offset].toInt().toChar()
                    }
                }
                continue
            }
            // if we're not dealing with a color, just copy the string bytes
            colorTreatmentBuffer[colorTreatmentPointer] = buffer[offset]
            colorTreatmentPointer++
        }
        // once we're done with the message, flush any remaining bytes
        if (colorTreatmentPointer > 0) {
            val byteMsg = colorTreatmentBuffer.copyOfRange(0, colorTreatmentPointer)
            var text = String(byteMsg, charset)
            // adan uses the 'bell' character, which made sounds in telnet, so replace these bells with a warning emoji
            //@TODO: move it to a substitute system
            if (text.startsWith('\u0007'))
                text = text.replace("\u0007", "⚠")
            gatheredChunks.add(TextMessageChunk(currentColor, AnsiColor.Black, isColorBright, text))
            colorTreatmentPointer = 0
        }

        return ColorfulTextMessage(chunks = gatheredChunks.toTypedArray())
    }

    // returns a TextMessageChunk without text, but with correctly set color information
    private fun updateCurrentColor(param1 : String, param2: String)
    {
        var col: Int
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
            val debug = true
            var skipBytes = 0

            // Due to an ADAN bug, some messages can arrive inside a custom message, e.g.
            //  - "custom protocol begin" bytes
            //  - text message "Enter charset:" arrives
            //  - <ProtocolVersionMessage ... />
            //  - "end of custom protocol" bytes
            // Because of this we need to extract any text from the message before letting xml deserializer process it
            if (mainBuffer[0] != 0x3C.toByte()) { // if xml message doesn't start right away (with '<'), find text inside it and process it
                skipBytes = processBorkedTextMessage(mainBuffer, mainBufferPointer)
            }

            val byteMsg = mainBuffer.copyOfRange(skipBytes, mainBufferPointer)
            val msg = String(byteMsg, charset)
            when (_customMessageType) {
                14 -> CurrentRoomMessage.fromXml(msg)?.let { _currentRoomMessages.value = it }
            }
            if (debug) {
                val str1 = "- Custom message: $_customMessageType"
                //println(str1)
                FileLogger.log("MudConnection", str1)

                //println(msg)
                FileLogger.log("MudConnection", msg)

                val str2 = "- End of custom message"
                //println(str2)
                FileLogger.log("MudConnection", str2)

                //_textMessages.emit(whiteTextMessage(msg)) // print custom message into ru.adan.silmaril.main window
            }
        } else {
            processTextMessage(mainBuffer, mainBufferPointer, true)
        }
        mainBufferPointer = 0
    }

    private suspend fun processTextMessage(buffer: ByteArray, bufferEndPointer: Int, emitEmpty: Boolean) {
        val debug = true
        val gluedMessage = bufferToColorfulText(buffer, bufferEndPointer)
        if (gluedMessage.chunks.isNotEmpty()) {
            val gluedString = gluedMessage.chunks.joinToString(separator = "", transform = { chunk -> chunk.text})
            // send gluedString to the trigger system
            onMessageReceived(gluedString)
            
            if (debug) {
                var str = "Text message: \n"
                for (chunk in gluedMessage.chunks) {
                    str += chunk.text
                }
                str += "\n- End of text message\n"
                //println(str)
                FileLogger.log("MudConnection", str)
            }
            _colorfulTextMessages.emit(gluedMessage)
        } else if (emitEmpty) {
            _colorfulTextMessages.emit(emptyTextMessage())
        }
    }

    private suspend fun processBorkedTextMessage(buffer: ByteArray, bufferEndPointer: Int) : Int {
        val debug = true
        val xmlStartOffset = findXmlOffset(buffer, bufferEndPointer)
        if (debug) {
            val str1 = "Borked message detected"
            //println(str1)
            FileLogger.log("MudConnection", str1)

            val str2 = "Xml start offset: $xmlStartOffset"
            //println(str2)
            FileLogger.log("MudConnection", str2)

            val byteMsg = buffer.copyOfRange(0, bufferEndPointer)
            val hexString = byteMsg.joinToString(separator = " ") { byte -> String.format("%02X", byte) }
            val str3 = "Bytes of the borked message: $hexString"
            //println(str3)
            FileLogger.log("MudConnection", str3)

            val msg = String(byteMsg, charset)
            val str4 = "Borked message: $msg"
            //println(str4)
            FileLogger.log("MudConnection", str4)
        }
        if (xmlStartOffset > 0)
            processTextMessage(buffer, xmlStartOffset, false)
        return xmlStartOffset
    }

    private fun findXmlOffset(buffer: ByteArray, bufferEndPointer: Int) : Int {
        // first three characters of custom xml messages
        val strings = listOf("Lor", "Pro", "Gro", "Roo", "Cur")
        val byteArrays = strings.map { str ->
            str.toByteArray(Charset.forName("Windows-1251"))
        }

        // try to find "<Lor", "<Pro", "<Gro", "<Roo", "<Cur" in the buffer to find the beginning of xml message
        for (offset in 0 until bufferEndPointer) {
            if (buffer[offset] == 0x3C.toByte() && offset + 3 < bufferEndPointer) { // 0x3C is the '<' character
                for (byteArray in byteArrays) {
                    if (buffer[offset+1] == byteArray[0] &&
                        buffer[offset+2] == byteArray[1] &&
                        buffer[offset+3] == byteArray[2])
                        return offset
                }
            }
        }
        return 0
    }

    private fun emptyTextMessage() : ColorfulTextMessage {
        return ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.None, AnsiColor.None, false, "")))
    }

    private fun whiteTextMessage(text : String) : ColorfulTextMessage {
        return ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.White, AnsiColor.None, true, text)))
    }
}