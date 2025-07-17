package viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import misc.AnsiColor
import model.MudConnection
import model.SettingsManager
import mud_messages.TextMessageChunk
import mud_messages.ColorfulTextMessage

// ViewModel that holds the list of strings and manages the TCP connection
class MainViewModel(private val client: MudConnection, private val settings: SettingsManager) {

    // Expose the list of messages as a StateFlow for UI to observe
    private val _messages = MutableStateFlow<List<ColorfulTextMessage>>(emptyList())
    val messages: StateFlow<List<ColorfulTextMessage>> get() = _messages

    val isEnteringPassword: StateFlow<Boolean> = client.isEchoOn

    // Coroutine scope tied to the lifecycle of the ViewModel
    private val viewModelScope = CoroutineScope(Dispatchers.IO)

    fun reconnect() {
        client.forceDisconnect()
    }

    // Function to connect to the TCP server
    fun connect() {
        // Launch a coroutine for network I/O
        var connected = false
        while (!connected)
            connected = client.connect()
        viewModelScope.launch {
            // Collect the model's flow of received bytes
            client.colorfulTextMessages.collect { message ->
                //val message = dataToString(data)  // Convert the data (bytes) to String

                // Append the received message to the list and expose it via StateFlow
                _messages.value += message
            }
        }
    }

    // Function to send a message via TCP
    fun sendMessage(message: String, displayAsUserInput: Boolean = true) {
        // println("Sending: $message")
        val playerCommands = splitOnTopLevelSemicolon(message)
        val splitCommands = settings.splitCommands.value

        if (playerCommands.size > 1) {
            if (!splitCommands) {
                _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, false, "> $message")))
            }
            for (command in playerCommands) {
                sendMessage(command, splitCommands)
            }
            return
        }

        if (message.startsWith("#")) {
            // @TODO: Add some class that treats input messages here. If it's a system message, there's a lot to do.
            if (displayAsUserInput)
                _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.White, AnsiColor.Black, true, "> $message")))
        }
        else {
            if (displayAsUserInput) {
                if (isEnteringPassword.value)
                    _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.Yellow,AnsiColor.Black,false,"> ********")))
                else
                    _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, false, "> $message")))
            }
            client.sendMessage(message)
        }
    }

    // Splits commands separated by semicolons into substrings. Only uses top level semicolons. Semicolons inside {} are ignored.
    private fun splitOnTopLevelSemicolon(input: String): List<String> {
        val result = mutableListOf<String>()
        var braceDepth = 0
        var lastSplitIndex = 0

        for (i in input.indices) {
            when (input[i]) {
                '{' -> braceDepth++
                '}' -> {
                    if (braceDepth > 0) {
                        braceDepth--
                    }
                }
                ';' -> {
                    if (braceDepth == 0) {
                        result.add(input.substring(lastSplitIndex, i))
                        lastSplitIndex = i + 1
                    }
                }
            }
        }

        result.add(input.substring(lastSplitIndex))
        return result.map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun displaySystemMessage(message: String) {
        _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.White, AnsiColor.Black, true, message)))
    }

    // Clean up when needed
    fun cleanup() {
        viewModelScope.cancel()
        client.closeDefinitive()
    }
}