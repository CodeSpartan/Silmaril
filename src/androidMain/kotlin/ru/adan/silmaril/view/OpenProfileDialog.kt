package ru.adan.silmaril.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dialog for opening an existing profile.
 * Shows all saved profiles, grays out those already open.
 */
@Composable
fun OpenProfileDialog(
    allProfiles: List<String>,
    openProfiles: Set<String>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Open Profile", color = Color.White)
        },
        text = {
            if (allProfiles.isEmpty()) {
                Text("No saved profiles found.", color = Color.White)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(allProfiles) { profileName ->
                        val isOpen = openProfiles.any { it.equals(profileName, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isOpen) {
                                    onOpen(profileName)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = profileName,
                                color = if (isOpen) Color.Gray else Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            if (isOpen) {
                                Text(
                                    text = "(in use)",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.caption
                                )
                            }
                        }
                        if (profileName != allProfiles.last()) {
                            Divider(color = Color(0xFF3a3a3a))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF5a5a5a),
                    contentColor = Color.White
                )
            ) {
                Text("Close")
            }
        },
        backgroundColor = Color(0xFF1a1a1a),
        contentColor = Color.White
    )
}
