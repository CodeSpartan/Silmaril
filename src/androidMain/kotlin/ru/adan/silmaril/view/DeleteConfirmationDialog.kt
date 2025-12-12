package ru.adan.silmaril.view

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/**
 * Confirmation dialog for deleting a profile permanently.
 */
@Composable
fun DeleteConfirmationDialog(
    profileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Profile?", color = Color.White)
        },
        text = {
            Text(
                "Are you sure you want to permanently delete the profile \"$profileName\"? This action cannot be undone.",
                color = Color(0xFFe8e8e8)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFe94747) // Red for danger
                )
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF5a5a5a)
                )
            ) {
                Text("Cancel", color = Color(0xFFe8e8e8))
            }
        },
        backgroundColor = Color(0xFF1a1a1a)
    )
}
