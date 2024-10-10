package viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import model.MudConnection

// ViewModel that holds the list of strings and manages the TCP connection
class MainViewModel(private val client: MudConnection) {

    // Expose the list of messages as a StateFlow for UI to observe
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> get() = _messages

    // Coroutine scope tied to the lifecycle of the ViewModel
    private val scope = CoroutineScope(Dispatchers.IO)

    // Function to connect to the TCP server
    fun connect() {
        // Launch a coroutine for network I/O
        client.connect()
    }

    // Function to send a message via TCP
    fun sendMessage(message: String) {
        // Adding the sent message to the list locally
        _messages.value = _messages.value + "You: $message"

        // Send the message over TCP asynchronously
        client.sendMessage(message)
    }

    // Function to add a message received from the server
    fun receiveMessage() {
        // Launching coroutine to handle message reception in the background
        scope.launch {
            val response = client.receiveMessage() ?: ""
            _messages.value += "Server: $response"
        }
    }

    // Clean up when needed
    fun close() {
        client.close()
    }
}