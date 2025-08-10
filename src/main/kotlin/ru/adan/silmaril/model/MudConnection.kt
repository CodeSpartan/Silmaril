package ru.adan.silmaril.model

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.mud_messages.CurrentRoomMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.slf4j.MDCContext
import ru.adan.silmaril.mud_messages.TextMessageChunk
import ru.adan.silmaril.mud_messages.ColorfulTextMessage
import java.io.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.MDC
import com.jcraft.jzlib.Inflater
import com.jcraft.jzlib.JZlib
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

// Useful link: https://www.ascii-code.com/CP1251

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

class MudConnection(
    var host: String,
    var port: Int,
    var profileName: String,
    private val onMessageReceived: (String) -> Unit,
    private val settingsManager: SettingsManager,
) {
    private val logger = KotlinLogging.logger {}
    private val gameEventsLogger = KotlinLogging.logger("GameEvents")

    private var socket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null
    val isConnected: Boolean
        get() { // The connection is alive if both channels are non-null and not closed.
            return readChannel?.isClosedForRead == false &&
                    writeChannel?.isClosedForWrite == false
        }

    // Private mutable state that the class can change
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    // Public, read-only state for the UI to collect
    val connectionState = _connectionState.asStateFlow()


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

    private var mainBufferLastValidIndex = 0
    private val mainBuffer = ByteArray(32767)
    private var lastByte: Byte = ControlCharacters.NonControlCharacter
    private var headerFoundBytes = 0
    private var colorTreatmentPointer = 0
    private val colorTreatmentBuffer = ByteArray(32767)
    private var currentColor : AnsiColor = AnsiColor.None
    private var isColorBright : Boolean = false

    private val charset = Charset.forName("Windows-1251")

    private var _compressionEnabled = false
    private var _customProtocolEnabled = false
    private var _compressionInProgress = false
    private var inflater: Inflater? = null

    private var clientJob: Job? = null
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
        const val NonControlCharacter : Byte = 0xB5.toByte() // just a random character that's not part of TelnetConstants or ControlCharacters
    }

    init {
        launchKeepAliveJob()
    }

    fun connect() {
        // Prevent multiple connection attempts
        if (_connectionState.value == ConnectionState.CONNECTING || _connectionState.value == ConnectionState.CONNECTED) {
            logger.info { "connect aborted, the state is wrong" }
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        connectionScope.launch {
            var success = false
            while (!success) {
                success = performConnect() // Call the internal suspend function
                if (success) {
                    _connectionState.value = ConnectionState.CONNECTED
                    startReadingData() // Start the read loop only after a successful connection
                } else {
                    _connectionState.value = ConnectionState.FAILED
                }
            }
        }
    }


    // Attempt to establish the connection
    suspend fun performConnect(): Boolean {
        return try {
            logger.info { "Connecting to $host:$port" }
            val selectorManager = SelectorManager(Dispatchers.IO)
            // aSocket().tcp().connect is a suspend function for non-blocking connection
            socket = aSocket(selectorManager).tcp().connect(host, port)
            logger.info { "Connection established to $host:$port" }

            // Get the read and write channels from the socket
            readChannel = socket?.openReadChannel()
            writeChannel = socket?.openWriteChannel(autoFlush = true)

            true  // Connection successful, return true
        }
        catch (e: Exception) {
            // Log the specific exception for better debugging
            logger.warn(e) { "Connection failed to $host:$port" }
            cleanupOnDisconnect()
            false
        }
    }

    // this closes the connection without possibility of reconnection, e.g. when closing the window
    fun closeDefinitive() {
        keepAliveScope.cancel()
        connectionScope.cancel()
        closeClientJob()
    }

    private fun cleanupOnDisconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            logger.warn(e) { "Error while closing connection" }
        } finally {
            stopDecompression()
            socket = null
            _customProtocolEnabled = false
            _customMessageType = -1
            mainBufferLastValidIndex = 0
            mainBuffer.fill(0)
            lastByte = ControlCharacters.NonControlCharacter
            headerFoundBytes = 0
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // Close the connection
    private fun closeClientJob() {
        println("close")
        try {
            clientJob?.cancel()
            cleanupOnDisconnect()
        } catch (e: Exception) {
            logger.warn(e) { "Error while closing connection" }
        } finally {
            clientJob = null
        }
    }

    fun forceDisconnect() {
        socket?.close()
    }

    fun forceReconnect() {
        socket?.close()
        connectionScope.launch {
            // wait until the cleanup is done
            _connectionState.first { it == ConnectionState.DISCONNECTED }
            reconnect()
        }
    }

    private fun reconnect() {
        // Don't try to reconnect if another attempt is already in progress
        if (_connectionState.value != ConnectionState.DISCONNECTED) return

        connectionScope.launch {
            cleanupOnDisconnect()
            logger.info { "Reconnecting..." }
            connect()
        }
    }

    /************** SEND *************/

    inline fun <T> withMdc(vararg pairs: Pair<String, String>, block: () -> T): T {
        val keys = pairs.map { it.first }
        try {
            pairs.forEach { (key, value) -> MDC.put(key, value) }
            return block()
        } finally {
            keys.forEach { MDC.remove(it) }
        }
    }

    // use it to log user's input
    fun logGameplayTextSynchronously(eventMessage: String) {
        // Use the withMdc helper to wrap the logging call.
        // The MDC context only exists for the duration of this lambda.
        withMdc("profile" to profileName) {
            gameEventsLogger.info { eventMessage } // Use lazy evaluation for performance
        }
    }

    fun sendMessage(message: String) {
        // Don't try to send if not connected
        if (_connectionState.value != ConnectionState.CONNECTED) return
        connectionScope.launch {
            performSend(message)
        }
    }

    private suspend fun performSend(message: String) {
        lastSendTimestamp.set(System.currentTimeMillis())

        // Convert the message to bytes using the correct charset (Windows-1251)
        val messageBytes = message.toByteArray(charset) + ControlCharacters.LineFeed
        val processedMessage = duplicateIACBytes(messageBytes)

        try {
            writeChannel?.writeByteArray(processedMessage)
        } catch (e: Exception) {
            logger.warn { "Error sending string message: ${e.message}" }
        }
    }

    // send raw bytes, don't duplicate IAC bytes
    private suspend fun sendRaw(messageBytes : ByteArray) {
        lastSendTimestamp.set(System.currentTimeMillis())
        try {
            writeChannel?.writeByteArray(messageBytes)
        } catch (e: Exception) {
            logger.warn { "Error sending raw message: ${e.message}" }
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
                if (timeSinceLastSend >= keepAliveIntervalMilliseconds && isConnected) {
                    sendRaw(byteArrayOf(ControlCharacters.LineFeed))
                }
            }
        }
    }

    /**************** RECEIVE ****************/

    // Start receiving messages asynchronously using a coroutine
    private fun startReadingData() {
        clientJob = connectionScope.launch (
            // this provides the "profile" to the SLF4J logger
            MDCContext(mapOf("profile" to profileName))
        ){
            try {
                val socketBuffer = ByteArray(32767)
                // Reading from a ByteReadChannel is suspending
                while (isActive) {
                    var bytesRead: Int?
                    // adan always sends IAC_GA at the end of text messages, while other muds may not do that
                    // so in their case, we time out after 50ms of inactivity and flush the buffer if there's anything on it
                    if (host == "adan.ru") {
                        bytesRead = readChannel?.readAvailable(socketBuffer) ?: -1
                        if (bytesRead == -1)  {
                            logger.info { "Connection closed by peer." }
                            break
                        }
                    } else {
                        bytesRead = withTimeoutOrNull(50) {
                            readChannel?.readAvailable(socketBuffer)
                        }
                        if (bytesRead == null) {
                            if (readChannel?.isClosedForRead == true) {
                                logger.info { "Connection closed by peer." }
                                break
                            }
                            tryFlushMainBuffer()
                            continue
                        }
                    }

                    if (bytesRead > 0) {
                        // Create a view of the buffer with only the data we read
                        val dataChunk = socketBuffer.copyOf(bytesRead)

                        if (_compressionInProgress) {
                            val decompressedData = decompress(dataChunk)
                            processData(decompressedData)
                        } else {
                            processData(dataChunk)
                        }
                    }
                }
            } catch (e: io.ktor.utils.io.CancellationException) {
                logger.info { "Reading data job was cancelled." }
            }
            catch (e: IOException) {
                logger.error { "Error while receiving data" }
            } finally {
                _colorfulTextMessages.emit(whiteTextMessage("Связь потеряна."))
                _connectionState.value = ConnectionState.DISCONNECTED
                if (settingsManager.settings.value.autoReconnect) {
                    reconnect()
                } else {
                    cleanupOnDisconnect()
                }
            }
        }
    }

    private fun startDecompression() {
        _compressionInProgress = true
        inflater = Inflater()
        inflater?.init(JZlib.W_ZLIB)
    }

    private fun decompress(data: ByteArray): ByteArray {
        inflater?.setInput(data)
        // This buffer will hold the decompressed output
        val outputBuffer = ByteArray(32767)
        var totalDecompressed = 0

        while ((inflater?.avail_in ?: 0) > 0) {
            inflater?.setOutput(outputBuffer, totalDecompressed, outputBuffer.size - totalDecompressed)
            val err = inflater?.inflate(JZlib.Z_SYNC_FLUSH)
            if (err != JZlib.Z_OK) {
                logger.warn { "Zlib inflation error: $err" }
                break
            }
            totalDecompressed += (inflater?.next_out_index ?: 0) - totalDecompressed
        }
        return outputBuffer.copyOf(totalDecompressed)
    }

    private fun stopDecompression() {
        _compressionInProgress = false
        _compressionEnabled = false
        inflater?.end()
        inflater = null
    }

    /**************** PROCESS ****************/

    private suspend fun processData(data: ByteArray) {
        for (offset in 0 until data.size) {
            val newByte = data[offset]

            when (lastByte) {
                // IAC means the beginning of a header message
                TelnetConstants.InterpretAsCommand -> {
                    when (newByte) {
                        // (IAC, IAC) means letter 'я', so it's not a header. Put 'я' in main buffer and reset the last byte state.
                        // reason: the letter 'я' in cp1251 is FF, but in Telnet FF is reserved for IAC, so Mud sends us FFFF for letter 'я'
                        TelnetConstants.InterpretAsCommand -> {
                            mainBuffer[mainBufferLastValidIndex] = data[offset]
                            mainBufferLastValidIndex++
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                        TelnetConstants.Will -> lastByte = TelnetConstants.Will
                        TelnetConstants.WillNot -> lastByte = TelnetConstants.WillNot
                        TelnetConstants.SubNegotiationStart -> lastByte = TelnetConstants.SubNegotiationStart
                        // IAC, SubNegEnd means the end of custom message
                        TelnetConstants.SubNegotiationEnd -> {
                            logger.debug { "Detected command: custom message end (IAC, SubNegEnd)" }
                            flushMainBuffer()
                            _customMessageType = -1
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                        // (IAC, GA) means flush buffer
                        TelnetConstants.GoAhead -> {
                            logger.debug { "Detected command: IAC GA" }
                            lastByte = ControlCharacters.NonControlCharacter
                            flushMainBuffer()
                        }
                        // if (IAC is followed by any unexpected character) -- this should never happen
                        else -> {
                            logger.error { "Unexpected byte followed by IAC: $newByte" }
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                    }
                }
                ControlCharacters.CarriageReturn -> {
                    when (newByte) {
                        // when (\r\n)
                        ControlCharacters.LineFeed -> {
                            lastByte = ControlCharacters.NonControlCharacter
                            flushMainBuffer()
                        }
                        // when (\r, IAC)
                        TelnetConstants.InterpretAsCommand -> lastByte = TelnetConstants.InterpretAsCommand
                        // when (\r, Any Byte)
                        else -> {
                            mainBuffer[mainBufferLastValidIndex] = ControlCharacters.CarriageReturn
                            mainBufferLastValidIndex++
                            mainBuffer[mainBufferLastValidIndex] = data[offset]
                            mainBufferLastValidIndex++

                            lastByte = ControlCharacters.NonControlCharacter
                        }
                    }
                }
                TelnetConstants.Will -> {
                    when (newByte) {
                        // on (IAC, WILL, COMPRESS) respond with (IAC, DO, COMPRESS)
                        TelnetConstants.Compress -> {
                            logger.debug { "Detected command: IAC WILL COMPRESS" }
                            _compressionEnabled = true
                            sendRaw(byteArrayOf(TelnetConstants.InterpretAsCommand, TelnetConstants.Do, TelnetConstants.Compress))
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                        //when (IAC, WILL, ECHO), it means the next thing we enter will be a password
                        TelnetConstants.Echo -> {
                            logger.debug { "Detected command: IAC WILL ECHO" }
                            _isEchoOn.value = true
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                        // when (IAC, WILL, CUSTOM_PROTOCOL), it means custom protocol will now be turned on
                        TelnetConstants.CustomProtocol -> {
                            logger.debug { "Detected command: IAC WILL CUSTOM" }
                            _customProtocolEnabled = true
                            sendRaw(byteArrayOf(TelnetConstants.InterpretAsCommand, TelnetConstants.Do, TelnetConstants.CustomProtocol))
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                        TelnetConstants.SubNegotiationEnd -> {
                            // Beginning of MCCP Compression negotiation:
                            // (InterpretAsCommand, SubNegotiationStart, Compress, Will, SubNegotiationEnd)
                            if (headerFoundBytes == 4) {
                                logger.debug { "Detected command: IAC SUB_START COMPRESS WILL SUB_STOP" }
                                startDecompression()
                            }
                            // if (IAC, WILL, SE) -- this should never happen
                            else {
                                logger.error { "Unexpected byte sequence: (IAC, WILL, SE)" }
                            }
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                        // when IAC, WILL is followed by something unexpected, this should never happen
                        else -> {
                            logger.error { "Unexpected byte sequence: (IAC, WILL, $newByte)" }
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                    }
                }
                TelnetConstants.WillNot -> {
                    when (newByte) {
                        TelnetConstants.Echo -> {
                            logger.debug { "Detected command: IAC WONT ECHO" }
                            _isEchoOn.value = false
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                    }
                }
                TelnetConstants.SubNegotiationStart -> {
                    when (newByte) {
                        // on IAC, SubNegStart, CustomProtocol, get ready to read the next byte, which will carry the custom message type
                        TelnetConstants.CustomProtocol -> {
                            lastByte = TelnetConstants.CustomProtocol
                        }
                        // on IAC, SubNegStart, Compress
                        TelnetConstants.Compress -> {
                            lastByte = TelnetConstants.Compress
                        }
                        // on IAC, SubNegStart, unexpected byte
                        else -> {
                            logger.error { "Unexpected byte sequence: (IAC, SubNegStart, $newByte)" }
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                    }
                }
                TelnetConstants.Compress -> {
                    when (newByte) {
                        // on IAC, SubNegStart, Compress, Will
                        TelnetConstants.Will -> {
                            headerFoundBytes = 4
                            lastByte = TelnetConstants.Will
                        }
                        // on IAC, SubNegStart, Compress, unexpected byte
                        else -> {
                            logger.error { "Unexpected byte sequence: (IAC, SubNegStart, Compress, $newByte)" }
                            lastByte = ControlCharacters.NonControlCharacter
                        }
                    }
                }
                // if IAC, SubNegStart, CustomProtocol, the next byte is the custom message type
                TelnetConstants.CustomProtocol -> {
                    _customMessageType = newByte.toInt()
                    lastByte = ControlCharacters.NonControlCharacter
                }
                else -> {
                    when (newByte) {
                        // when (Any non-header Byte, IAC)
                        TelnetConstants.InterpretAsCommand -> lastByte = TelnetConstants.InterpretAsCommand
                        // when (Any non-header Byte, \n)
                        ControlCharacters.LineFeed -> {
                            lastByte = ControlCharacters.NonControlCharacter
                            // if encountering \n, we should normally flush the buffer (this happens rarely without the preceding \r, but it does)
                            // however, if this happens during a borked message (e.g. text message placed inside a custom message protocol),
                            // this would break up the xml line by line and we don't want that
                            if (_customMessageType == -1) {
                                flushMainBuffer()
                            } else {
                                mainBuffer[mainBufferLastValidIndex] = data[offset]
                                mainBufferLastValidIndex++
                            }
                        }
                        // when (Any non-header Byte, \r)
                        ControlCharacters.CarriageReturn -> {
                            lastByte = ControlCharacters.CarriageReturn
                        }
                        // when (Any non-header Byte is followed by Any Byte that's not IAC, \n or \r), we're dealing with just text
                        else -> {
                            lastByte = ControlCharacters.NonControlCharacter
                            mainBuffer[mainBufferLastValidIndex] = data[offset]
                            mainBufferLastValidIndex++
                        }
                    }
                }
            }
            if (lastByte == ControlCharacters.NonControlCharacter)
                headerFoundBytes = 0
        }
    }

    private fun bufferToColorfulText(buffer: ByteArray, bufferEndPointer: Int) : ColorfulTextMessage {
        // print bytes
        val copiedBytes = buffer.copyOfRange(0, bufferEndPointer)
        val hexString = copiedBytes.joinToString(separator = " ") { byte -> String.format("%02X", byte) }
        logger.debug { "Bytes in bufferToColorfulText: $hexString" }

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

    // a function for non-adan muds that don't send IAC_GA at the end of message, and just idle after sending it (not even postfixed with \n)
    private suspend fun tryFlushMainBuffer() {
        if (mainBufferLastValidIndex > 0) {
            logger.info { "Flushed buffer due to no IAC_GA" }
            flushMainBuffer()
        }
    }

    private suspend fun flushMainBuffer() {
        if (_customMessageType != -1) {
            var skipBytes = 0

            // Due to an ADAN bug, some messages can arrive inside a custom message, e.g.
            //  - "custom protocol begin" bytes
            //  - text message "Enter charset:" arrives
            //  - <ProtocolVersionMessage ... />
            //  - "end of custom protocol" bytes
            // Because of this we need to extract any text from the message before letting xml deserializer process it
            if (mainBuffer[0] != 0x3C.toByte()) { // if xml message doesn't start right away (with '<'), find text inside it and process it
                skipBytes = processBorkedTextMessage(mainBuffer, mainBufferLastValidIndex)
            }

            val byteMsg = mainBuffer.copyOfRange(skipBytes, mainBufferLastValidIndex)
            val msg = String(byteMsg, charset)
            when (_customMessageType) {
                14 -> CurrentRoomMessage.fromXml(msg)?.let { _currentRoomMessages.value = it }
            }
            logger.debug { "- Custom message: $_customMessageType" }
            logger.debug { msg }
            logger.debug { "- End of custom message" }
        } else {
            processTextMessage(mainBuffer, mainBufferLastValidIndex, true)
        }
        mainBufferLastValidIndex = 0
    }

    private suspend fun processTextMessage(buffer: ByteArray, bufferEndPointer: Int, emitEmpty: Boolean) {
        val gluedMessage = bufferToColorfulText(buffer, bufferEndPointer)
        if (gluedMessage.chunks.isNotEmpty()) {
            val gluedString = gluedMessage.chunks.joinToString(separator = "", transform = { chunk -> chunk.text})
            // send glued string to the logger
            gameEventsLogger.info { gluedString }

            // send glued string to the trigger system
            onMessageReceived(gluedString)

            var str = "Text message: \n"
            for (chunk in gluedMessage.chunks) {
                str += chunk.text
            }
            str += "\n- End of text message"
            logger.debug { str }

            _colorfulTextMessages.emit(gluedMessage)
        } else if (emitEmpty) {
            gameEventsLogger.info { "" }
            _colorfulTextMessages.emit(emptyTextMessage())
        }
    }

    private suspend fun processBorkedTextMessage(buffer: ByteArray, bufferEndPointer: Int) : Int {
        val xmlStartOffset = findXmlOffset(buffer, bufferEndPointer)

        logger.debug { "Borked message detected" }
        logger.debug { "Xml start offset: $xmlStartOffset" }

        val byteMsg = buffer.copyOfRange(0, bufferEndPointer)
        val hexString = byteMsg.joinToString(separator = " ") { byte -> String.format("%02X", byte) }
        logger.debug { "Bytes of the borked message: $hexString" }

        val msg = String(byteMsg, charset)
        logger.debug { "Borked message: $msg" }

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