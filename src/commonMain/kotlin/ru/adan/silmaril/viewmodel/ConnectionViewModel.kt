package ru.adan.silmaril.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.adan.silmaril.ui.ConnectionState

/**
 * Shared connection state for the MUD client
 */
data class ConnectionInfo(
    val state: ConnectionState = ConnectionState.Disconnected,
    val serverHost: String = "",
    val serverPort: Int = 0,
    val characterName: String = "",
    val errorMessage: String? = null
)

/**
 * Shared ViewModel for connection state management
 * Platform-specific implementations will extend this with actual connection logic
 */
open class ConnectionViewModel {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionInfo = MutableStateFlow(ConnectionInfo())
    val connectionInfo: StateFlow<ConnectionInfo> = _connectionInfo.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    fun updateConnectionState(state: ConnectionState) {
        _connectionInfo.value = _connectionInfo.value.copy(state = state)
    }

    fun updateServerInfo(host: String, port: Int) {
        _connectionInfo.value = _connectionInfo.value.copy(
            serverHost = host,
            serverPort = port
        )
    }

    fun updateCharacterName(name: String) {
        _connectionInfo.value = _connectionInfo.value.copy(characterName = name)
    }

    fun setError(message: String?) {
        _connectionInfo.value = _connectionInfo.value.copy(errorMessage = message)
    }

    fun clearError() {
        setError(null)
    }

    // To be overridden by platform-specific implementations
    open fun connect(host: String, port: Int) {
        updateServerInfo(host, port)
        updateConnectionState(ConnectionState.Connecting)
    }

    open fun disconnect() {
        updateConnectionState(ConnectionState.Disconnected)
    }
}
