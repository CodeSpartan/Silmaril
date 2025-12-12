package ru.adan.silmaril.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.adan.silmaril.model.ConnectionState

/**
 * A single profile item in the profile drawer list.
 * Shows connection status, profile name, and action buttons (close/delete/switch).
 * Includes visual drag handle (☰) and up/down arrow buttons for reordering.
 */
@Composable
fun ProfileListItem(
    name: String,
    connectionState: ConnectionState,
    isCurrent: Boolean,
    onSwitch: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isCurrent) Color(0xFF2a2a2a) else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle (3 horizontal lines, non-clickable visual indicator)
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Drag to reorder",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Reorder buttons (up/down arrows)
        Column(
            modifier = Modifier.width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Up arrow
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canMoveUp) Color.Gray else Color(0xFF3a3a3a),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Down arrow
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canMoveDown) Color.Gray else Color(0xFF3a3a3a),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // RIGHT SIDE: Connection status + Profile name (clickable together)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !isCurrent) { onSwitch() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection status indicator (colored circle)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> Color(0xFF4CAF50) // Green
                            ConnectionState.CONNECTING -> Color(0xFFFFC107) // Yellow
                            ConnectionState.DISCONNECTED -> Color(0xFFF44336) // Red
                            ConnectionState.FAILED -> Color(0xFFF44336) // Red (same as disconnected)
                        },
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Profile name
            Text(
                text = name + if (isCurrent) " •" else "",
                color = Color.White,
                fontSize = 14.sp
            )
        }

        // Delete button (trash icon)
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete profile",
                tint = Color(0xFFe94747),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Close button (X icon)
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close profile",
                tint = Color(0xFFe94747),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
