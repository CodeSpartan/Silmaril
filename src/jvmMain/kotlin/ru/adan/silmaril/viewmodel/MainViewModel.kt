package ru.adan.silmaril.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.SettingsProvider
import ru.adan.silmaril.misc.TextMessageChunk
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.platform.createLogger

// ViewModel that holds the list of strings and manages the TCP connection
class MainViewModel(
    private val client: MudConnection,
    val onSystemMessage: (String, Int) -> Unit,
    val onInsertVariables: (String) -> String,
    val onProcessAliases: (String) -> Pair<Boolean, String?>,
    private val onMessageReceived: (String) -> Unit,
    private val onRunSubstitutes: (ColorfulTextMessage) -> ColorfulTextMessage?,
    private val loreManager: LoreManager,
    private val settingsProvider: SettingsProvider
) {

    val logger = createLogger("ru.adan.silmaril.viewmodel.MainViewModel")

    // Expose the list of messages as a SharedFlow for UI to observe
    // replay=100 ensures late collectors (like Android UI) can see recent messages
    // extraBufferCapacity=100 prevents suspension when emitting
    private val _messages = MutableSharedFlow<ColorfulTextMessage>(replay = 100, extraBufferCapacity = 100)
    val messages = _messages.asSharedFlow()

    // a flow, to which MapWindow emits and then MainWindow brings itself to front and focuses the input field
    val focusTarget = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val isEnteringPassword: StateFlow<Boolean> = client.isEchoOn

    // Coroutine scope tied to the lifecycle of the ViewModel
    private val viewModelScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun initAndConnect() {
        viewModelScope.launch {
            logger.info { "initAndConnect: Starting collection from colorfulTextMessages" }
            // Collect the model's flow of received bytes
            client.colorfulTextMessages.collect { message ->
                logger.debug { "initAndConnect: Received message from colorfulTextMessages" }

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
                        logger.debug { "initAndConnect: Emitting message to _messages" }
                        _messages.emit(afterSubs)
                    }
                } else {
                    logger.debug { "initAndConnect: onRunSubstitutes returned null, message suppressed" }
                }
            }
        }
        // Launch a coroutine for network I/O
        if (settingsProvider.coreSettings.value.autoReconnect)
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
        val splitCommands = settingsProvider.coreSettings.value.splitCommands

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

            // After variable/expression evaluation, the result may contain semicolons
            // (e.g., $if() expanded to "#echo yes;#var x 1"). Re-split and process each part.
            val expandedCommands = splitOnTopLevelSemicolon(withVariables)
            if (expandedCommands.size > 1) {
                for (cmd in expandedCommands) {
                    treatUserInput(cmd, false, recursionLevel + 1)
                }
            } else {
                onSystemMessage(withVariables, recursionLevel + 1)
            }
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
                return
            }

            // After variable/expression evaluation, the result may contain semicolons
            // (e.g., $if() expanded to "cmd1;cmd2"). Re-split and process each part.
            val expandedCommands = splitOnTopLevelSemicolon(withVariables)
            if (expandedCommands.size > 1) {
                // Multiple commands after expansion - process each recursively
                for (cmd in expandedCommands) {
                    treatUserInput(cmd, false, recursionLevel + 1)
                }
            } else if (wasThereAnAlias && msgAfterAliasProcess != null) {
                // Single command from alias - recurse to allow nested aliases
                treatUserInput(withVariables, false, recursionLevel + 1)
            } else if (!wasThereAnAlias) {
                // No alias - check if result starts with # (could happen after $if evaluation)
                if (withVariables.startsWith("#")) {
                    // Result became a system command after expression evaluation, recurse to handle it
                    treatUserInput(withVariables, false, recursionLevel + 1)
                } else {
                    // Send to server
                    client.enqueueString(withVariables)
                }
            }
            // If wasThereAnAlias && msgAfterAliasProcess == null, the alias (e.g., Kotlin DSL)
            // handled everything itself via send() etc., so we do nothing here

            if (!client.isConnected) {
                emitMessage(ColorfulTextMessage(arrayOf(
                    TextMessageChunk("Вы не подключены.", AnsiColor.Yellow, AnsiColor.None, true),
                )))
            }
        }
    }

    /**
     * Splits input on semicolons, but respects:
     * - Brace depth: content inside {} is not split
     * - $math(...): content inside is not split
     * - $if(...)(...)(...): content inside all three parenthesis groups is not split
     */
    private fun splitOnTopLevelSemicolon(input: String): List<String> {
        val result = mutableListOf<String>()
        var braceDepth = 0
        var lastSplitIndex = 0

        // Expression tracking
        var inExpression = false
        var expressionParenDepth = 0
        var ifGroupsCompleted = 0  // For $if, need to complete 3 groups; -1 means $math

        var i = 0
        while (i < input.length) {
            // Check for start of $math( or $if( when not already in an expression
            if (!inExpression && braceDepth == 0) {
                if (i + 6 <= input.length && input.substring(i, i + 6) == "\$math(") {
                    inExpression = true
                    expressionParenDepth = 1
                    ifGroupsCompleted = -1  // -1 means it's $math, not $if
                    i += 6
                    continue
                }
                if (i + 4 <= input.length && input.substring(i, i + 4) == "\$if(") {
                    inExpression = true
                    expressionParenDepth = 1
                    ifGroupsCompleted = 0  // Need to complete 3 groups for $if
                    i += 4
                    continue
                }
            }

            val char = input[i]

            if (inExpression) {
                // Track parentheses within expression
                when (char) {
                    '(' -> expressionParenDepth++
                    ')' -> {
                        expressionParenDepth--
                        if (expressionParenDepth == 0) {
                            if (ifGroupsCompleted == -1) {
                                // $math() completed
                                inExpression = false
                            } else {
                                // $if() - one group completed
                                ifGroupsCompleted++
                                if (ifGroupsCompleted == 3) {
                                    // All three groups of $if()()() completed
                                    inExpression = false
                                }
                                // else: wait for next '(' to start next group
                            }
                        }
                    }
                }
            } else {
                // Not in expression - track braces and look for semicolons
                when (char) {
                    '{' -> braceDepth++
                    '}' -> {
                        if (braceDepth > 0) braceDepth--
                    }
                    ';' -> {
                        if (braceDepth == 0) {
                            result.add(input.substring(lastSplitIndex, i))
                            lastSplitIndex = i + 1
                        }
                    }
                }
            }

            i++
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
