package ru.adan.silmaril.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Dialog for creating a new profile.
 * Validates that the name is unique and not blank.
 */
@Composable
fun CreateProfileDialog(
    existingProfiles: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var profileName by remember { mutableStateOf("") }
    val trimmedName = profileName.trim()
    val isDuplicate = existingProfiles.any { it.equals(trimmedName, ignoreCase = true) }
    val isValid = trimmedName.isNotBlank() && !isDuplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Profile", color = Color.White)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name", color = Color.White) },
                    isError = trimmedName.isNotBlank() && !isValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF5a5a5a),
                        unfocusedBorderColor = Color(0xFF3a3a3a),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color(0xFF999999)
                    )
                )
                if (trimmedName.isNotBlank() && isDuplicate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Profile already exists",
                        color = Color.Red,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(trimmedName)
                    onDismiss()
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF5a5a5a),
                    disabledBackgroundColor = Color(0xFF3a3a3a),
                    contentColor = Color.White,
                    disabledContentColor = Color(0xFF666666)
                )
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF5a5a5a),
                    contentColor = Color.White
                )
            ) {
                Text("Cancel")
            }
        },
        backgroundColor = Color(0xFF1a1a1a),
        contentColor = Color.White
    )
}
