package viewmodel

import mud_messages.CurrentRoomMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import model.MudConnection
import mud_messages.ColorfulTextMessage

class MapViewModel(private val client: MudConnection) {

    private val _currentRoomMessages = MutableSharedFlow<CurrentRoomMessage>(replay = 1)
    val currentRoomMessages : MutableSharedFlow<CurrentRoomMessage> get() = _currentRoomMessages

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