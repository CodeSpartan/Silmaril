package viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import model.MudConnection
import mud_messages.TextMessageChunk
import mud_messages.ColorfulTextMessage

// ViewModel that holds the list of strings and manages the TCP connection
class MainViewModel(private val client: MudConnection) {

    // Expose the list of messages as a StateFlow for UI to observe
    private val _messages = MutableStateFlow<List<ColorfulTextMessage>>(emptyList())
    val messages: StateFlow<List<ColorfulTextMessage>> get() = _messages

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
        // Adding the sent message to the list locally
        _messages.value += ColorfulTextMessage(arrayOf(TextMessageChunk(AnsiColor.Yellow, AnsiColor.Black, false, ">$message")))

        // Send the message over TCP asynchronously
        client.sendMessage(message)
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