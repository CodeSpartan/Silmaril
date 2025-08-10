package ru.adan.silmaril.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.mud_messages.TextMessageChunk
import ru.adan.silmaril.mud_messages.ColorfulTextMessage

// ViewModel that holds the list of strings and manages the TCP connection
class MainViewModel(
    private val client: MudConnection,
    val onSystemMessage: (String) -> Unit,
    val onInsertVariables: (String) -> String,
    private val onMessageReceived: (String) -> Unit,
    private val settingsManager: SettingsManager
) {

    // Expose the list of messages as a StateFlow for UI to observe
    private val _messages = MutableStateFlow<List<ColorfulTextMessage>>(emptyList())
    val messages: StateFlow<List<ColorfulTextMessage>> get() = _messages

    val isEnteringPassword: StateFlow<Boolean> = client.isEchoOn

    // Coroutine scope tied to the lifecycle of the ViewModel
    private val viewModelScope = CoroutineScope(Dispatchers.IO)

    fun initAndConnect() {
        viewModelScope.launch {
            // Collect the model's flow of received bytes
            client.colorfulTextMessages.collect { message ->
                //val message = dataToString(data)  // Convert the data (bytes) to String
                // Append the received message to the list and expose it via StateFlow
                _messages.value += message
            }
        }
        // Launch a coroutine for network I/O
        if (settingsManager.settings.value.autoReconnect)
            client.connect()
    }

    // Function that reads user's text input
    fun treatUserInput(message: String, displayAsUserInput: Boolean = true) {
        // println("Sending: $message")
        val playerCommands = splitOnTopLevelSemicolon(message)
        val splitCommands = settingsManager.settings.value.splitCommands

        if (playerCommands.size > 1) {
            if (!splitCommands) {
                _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, false, "> $message")))
                client.logGameplayTextSynchronously("> $message")
            }
            for (command in playerCommands) {
                treatUserInput(command, splitCommands)
            }
            return
        }

        if (message.startsWith("#")) {
            val withVariables = onInsertVariables(message)
            if (displayAsUserInput) {
                if (withVariables != message) {
                    _messages.value += ColorfulTextMessage(arrayOf(
                        TextMessageChunk(AnsiColor.Black, AnsiColor.Black, true, ">> $message "),
                        TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, true, "> $withVariables"),
                    ))
                    client.logGameplayTextSynchronously(">> $message > $withVariables")
                } else {
                    _messages.value += ColorfulTextMessage(arrayOf(
                        TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, true, ">> $withVariables"),
                    ))
                    client.logGameplayTextSynchronously(">> $withVariables")
                }
            }
            onSystemMessage(withVariables)
        }
        else {
            val withVariables = onInsertVariables(message)
            if (displayAsUserInput) {
                if (isEnteringPassword.value) {
                    _messages.value += ColorfulTextMessage(arrayOf(
                        TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, false, "> ********")
                    ))
                    client.logGameplayTextSynchronously("> ********")
                }
                else if (withVariables != message) {
                    _messages.value += ColorfulTextMessage(arrayOf(
                        TextMessageChunk(AnsiColor.Black, AnsiColor.Black, true, "> $message "),
                        TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, false, "> $withVariables"),
                    ))
                    client.logGameplayTextSynchronously("> $message > $withVariables")
                } else {
                    _messages.value += ColorfulTextMessage(arrayOf(
                        TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, false, "> $withVariables"),
                    ))
                    client.logGameplayTextSynchronously("> $withVariables")
                }
            }
            client.sendMessage(withVariables)
            if (!client.isConnected) {
                _messages.value += ColorfulTextMessage(arrayOf(
                    TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, true, "Вы не подключены."),
                ))
            }
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

    fun displayColoredMessage(message: String, color: AnsiColor, isBright: Boolean) {
        onMessageReceived(message)
        _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(color, AnsiColor.Black, isBright, message)))
    }

    fun displaySystemMessage(message: String) {
        onMessageReceived(message)
        _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.White, AnsiColor.Black, true, message)))
    }

    fun displayErrorMessage(message: String) {
        onMessageReceived(message)
        _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, true, message)))
    }

    // Clean up when needed
    fun cleanup() {
        viewModelScope.cancel()
        client.closeDefinitive()
    }
}