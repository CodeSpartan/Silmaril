package ru.adan.silmaril.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected
}

@Composable
fun ConnectionStatusIndicator(
    state: ConnectionState,
    serverName: String = "",
    modifier: Modifier = Modifier
) {
    val colors = SilmarilTheme.colors

    val statusColor = when (state) {
        ConnectionState.Disconnected -> colors.disconnected
        ConnectionState.Connecting -> colors.connecting
        ConnectionState.Connected -> colors.connected
    }

    val statusText = when (state) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Connecting -> "Connecting..."
        ConnectionState.Connected -> if (serverName.isNotEmpty()) "Connected to $serverName" else "Connected"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = statusText,
            color = colors.textSecondary,
            fontSize = 14.sp
        )
    }
}
