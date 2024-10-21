package viewmodel

import mud_messages.CurrentRoomMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import model.MudConnection

class MapViewModel(private val client: MudConnection) {

    private val _currentRoomMessages = MutableSharedFlow<CurrentRoomMessage>()
    val currentRoomMessages = _currentRoomMessages.asSharedFlow()

    private val managerJob = Job()
    private val managerScope = CoroutineScope(Dispatchers.Main + managerJob)

    init {
        collectRoomMessages()
    }

    private fun collectRoomMessages() {
        managerScope.launch {

            client.currentRoomMessages.collect { message ->
                // Handle the new message here
                println("New message received: $message")
                // You might update state or notify listeners here
                emitMessage(message)
            }
        }
    }

    // Method to emit a new message
    private fun emitMessage(message: CurrentRoomMessage) {
        managerScope.launch {
            _currentRoomMessages.emit(message)
        }
    }

    fun cleanup() {
        managerJob.cancel()
    }
}