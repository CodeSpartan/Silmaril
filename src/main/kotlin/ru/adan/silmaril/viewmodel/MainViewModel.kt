package ru.adan.silmaril.viewmodel

import androidx.compose.ui.focus.FocusRequester
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.misc.TextMessageChunk
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.model.LoreManager

// ViewModel that holds the list of strings and manages the TCP connection
class MainViewModel(
    private val client: MudConnection,
    val onSystemMessage: (String, Int) -> Unit,
    val onInsertVariables: (String) -> String,
    val onProcessAliases: (String) -> Pair<Boolean, String?>,
    private val onMessageReceived: (String) -> Unit,
    private val onRunSubstitutes: (ColorfulTextMessage) -> ColorfulTextMessage?,
    private val loreManager: LoreManager,
    private val settingsManager: SettingsManager
) {

    val logger = KotlinLogging.logger {}

    // Expose the list of messages as a StateFlow for UI to observe
    private val _messages = MutableSharedFlow<ColorfulTextMessage>()
    val messages = _messages.asSharedFlow()

    // a flow, to which MapWindow emits and then MainWindow brings itself to front and focuses the input field
    val focusTarget = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val isEnteringPassword: StateFlow<Boolean> = client.isEchoOn

    // Coroutine scope tied to the lifecycle of the ViewModel
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun initAndConnect() {
        viewModelScope.launch {
            // Collect the model's flow of received bytes
            client.colorfulTextMessages.collect { message ->

                val afterLoreInserts = loreManager.insertLoreLinks(message)

                // Append the received message to the list and expose it via StateFlow
                val afterSubs = onRunSubstitutes(afterLoreInserts)
                if (afterSubs != null) {
                    val originalText = message.chunks.joinToString(separator = "") { it.toString() }
                    val newText = afterSubs.chunks.joinToString(separator = "") { it.toString() }
                    // drop line if new text is empty, but originally there was some text
                    if (newText == "" && originalText != "") {
                        logger.debug { "Dropped line: $originalText" }
                    }
                    else {
                        _messages.emit(afterSubs)
                    }
                }
            }
        }
        // Launch a coroutine for network I/O
        if (settingsManager.settings.value.autoReconnect)
            client.connect()
    }

    fun isInputExemptFromVarInsertion(message: String): Boolean {
        val prefixes = listOf("#act", "#grep", "#unact", "#ungrep", "#al", "#unal", "#alias", "#unalias", "#hot",
            "#hotkey", "#unhot", "#unhotkey", "#sub", "#unsub")
        for (prefix in prefixes) {
            if (message.startsWith("$prefix ")) {
                return true
            }
        }
        return false
    }

    // Function that reads user's text input
    fun treatUserInput(message: String, displayAsUserInput: Boolean = true, recursionLevel: Int = 0) {
        //logger.info { "Sending: $message" }
        val playerCommands = splitOnTopLevelSemicolon(message)
        val splitCommands = settingsManager.settings.value.splitCommands

        if (playerCommands.size > 1) {
            if (!splitCommands) {
                emitMessage(ColorfulTextMessage(arrayOf(TextMessageChunk("> $message", AnsiColor.Yellow))))
                client.logGameplayTextSynchronously("> $message")
            }
            for (command in playerCommands) {
                treatUserInput(command, splitCommands)
            }
            return
        }

        if (message.startsWith("#")) {
            val displayFeedback = !message.startsWith("#output") && !message.startsWith("#window")

            // don't resolve variables inside #act and #grep statements, we need them as $vars
            val withVariables = if (isInputExemptFromVarInsertion(message)) message else onInsertVariables(message)
            if (displayAsUserInput && displayFeedback) {
                if (withVariables != message) {
                    emitMessage(ColorfulTextMessage(arrayOf(
                        TextMessageChunk(">> $message ",AnsiColor.Black, AnsiColor.None, true),
                        TextMessageChunk("> $withVariables", AnsiColor.Yellow, AnsiColor.None, true),
                    )))
                    client.logGameplayTextSynchronously(">> $message > $withVariables")
                } else {
                    emitMessage(ColorfulTextMessage(arrayOf(
                        TextMessageChunk(">> $withVariables", AnsiColor.Yellow, AnsiColor.None, true),
                    )))
                    client.logGameplayTextSynchronously(">> $withVariables")
                }
            }
            if (recursionLevel >= 10) {
                displayTaggedText("<color=yellow>Рекурсивный алиас остановлен (больше 10 уровней вложения.)</color>")
                return
            }
            onSystemMessage(withVariables, recursionLevel+1)
        }
        else {
            // Apply Aliases and Variable substitutions
            val (wasThereAnAlias, msgAfterAliasProcess) = onProcessAliases(message)
            var withVariables : String
            if (wasThereAnAlias) {
                withVariables = if (msgAfterAliasProcess != null) onInsertVariables(msgAfterAliasProcess) else "lambda"
            } else {
                withVariables = onInsertVariables(message)
            }

            val wasMessageChanged = withVariables != message

            if (displayAsUserInput) {
                if (isEnteringPassword.value) {
                    emitMessage(ColorfulTextMessage(arrayOf(
                        TextMessageChunk("> ********", AnsiColor.Yellow)
                    )))
                    client.logGameplayTextSynchronously("> ********")
                }
                else if (wasMessageChanged) {
                    emitMessage(ColorfulTextMessage(arrayOf(
                        TextMessageChunk("> $message ", AnsiColor.Black, AnsiColor.None, true),
                        TextMessageChunk("> $withVariables", AnsiColor.Yellow),
                    )))
                    client.logGameplayTextSynchronously("> $message > $withVariables")
                } else {
                    emitMessage(ColorfulTextMessage(arrayOf(
                        TextMessageChunk("> $withVariables", AnsiColor.Yellow),
                    )))
                    client.logGameplayTextSynchronously("> $withVariables")
                }
            }
            if (recursionLevel >= 10) {
                displayTaggedText("<color=yellow>Рекурсивный алиас остановлен (больше 10 уровней вложения.)</color>")
            }
            if (wasThereAnAlias && msgAfterAliasProcess != null && recursionLevel < 10) {
                treatUserInput(withVariables, false, recursionLevel+1)
            }
            else if (!wasThereAnAlias || recursionLevel >= 10) {
                client.enqueueString(withVariables)
            }
            if (!client.isConnected) {
                emitMessage(ColorfulTextMessage(arrayOf(
                    TextMessageChunk("Вы не подключены.", AnsiColor.Yellow, AnsiColor.None, true),
                )))
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

    fun displayColoredMessage(message: String, color: AnsiColor = AnsiColor.None, isBright: Boolean = false) {
        onMessageReceived(message)
        emitMessage(ColorfulTextMessage(arrayOf(TextMessageChunk(message, color, AnsiColor.None, isBright))))
    }

    fun displaySystemMessage(message: String) {
        onMessageReceived(message)
        emitMessage(ColorfulTextMessage(arrayOf(TextMessageChunk(message, AnsiColor.White, AnsiColor.None, true))))
    }

    fun displayErrorMessage(message: String) {
        onMessageReceived(message)
        emitMessage(ColorfulTextMessage(arrayOf(TextMessageChunk(message, AnsiColor.Yellow, AnsiColor.None, true))))
    }

    fun displayChunks(chunks: Array<TextMessageChunk>) {
        val message = chunks.joinToString("") { it.text }
        onMessageReceived(message)
        emitMessage(ColorfulTextMessage(chunks))
    }

    fun displayTaggedText(taggedText: String, brightWhiteAsDefault: Boolean = true) {
        displayChunks(ColorfulTextMessage.makeColoredChunksFromTaggedText(taggedText, brightWhiteAsDefault))
    }

    fun emitMessage(message: ColorfulTextMessage) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    // Clean up when needed
    fun cleanup() {
        viewModelScope.cancel()
        client.closeDefinitive()
    }
}