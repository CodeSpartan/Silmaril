package viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import misc.AnsiColor
import model.MudConnection
import mud_messages.TextMessageChunk
import mud_messages.ColorfulTextMessage

// ViewModel that holds the list of strings and manages the TCP connection
class MainViewModel(private val client: MudConnection) {

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
    fun sendMessage(message: String) {
        // println("Sending: $message")
        // @TODO: Integrate this special case into the whole system later. We don't want to break triggers, etc.
        if (isEnteringPassword.value) {
            _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.Yellow,AnsiColor.Black,false,"> ********")))
            client.sendMessage(message)
            return;
        }

        // @TODO: Add some class that treats input messages here. If it's a system message, there's a lot to do.
        // @TODO: Current message.split(;) won't work, it's a temporary solution. We need to separate by ; only if it's not inside {}.
        // @TODO: Besides, this needs to work: "look;#test" the same as "#test;look", which it currently doesn't.
        // @TODO: And possibly each separate command needs to go through sendMessage recursively to be displayed correctly.
        // @TODO: Whether or not to display commands separated by ; on one line or multiple needs to be a configurable option.
        if (message.startsWith("#")) {
            // system message, not to be sent
            _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.White, AnsiColor.Black, true, "> $message")))
        }
        else {
            _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, false, "> $message")))
            val substrings = message.split(";")
            for (substring in substrings) {
                client.sendMessage(substring)
            }
        }
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