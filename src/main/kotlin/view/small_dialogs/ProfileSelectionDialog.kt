package view.small_dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.flow.StateFlow
import model.SettingsManager

// Data class remains the same
data class Profile(val name: String, val selectable: Boolean = true)

@Composable
fun ProfileDialog(
    showProfileDialog: MutableState<Boolean>,
    gameWindows: MutableMap<String, profiles.Profile>,
    settings: SettingsManager,
    onAddWindow: (windowName: String) -> Unit
) {
    if (showProfileDialog.value) {
        ProfileSelectionDialogWindow(
            settings = settings,
            gameWindows = gameWindows,
            onCloseRequest = {
                showProfileDialog.value = false
            },
            onAddWindow = { windowName ->
                showProfileDialog.value = false // Always close the dialog
                onAddWindow(windowName)
            }
        )
    }
}

@Composable
fun ProfileSelectionDialogWindow(
    settings: SettingsManager,
    gameWindows: Map<String, profiles.Profile>,
    onCloseRequest: () -> Unit,
    onAddWindow: (windowName: String) -> Unit
) {
    var selectedProfile by remember { mutableStateOf<String?>(null) }
    var newProfileName by remember { mutableStateOf("") }
    var isAddWindowEnabled by remember { mutableStateOf(false) }

    val profileList by settings.profiles.collectAsState()

    // Use DialogWindow instead of the deprecated Dialog
    DialogWindow(
        onCloseRequest = { onCloseRequest() },
        state = rememberDialogState(width = 400.dp, height = 500.dp),
        undecorated = true, // Set undecorated to true for a custom title bar
        resizable = false,
        title = "Select Profile" // Still useful for taskbar/OS identification
    ) {
        Surface(modifier = Modifier.fillMaxSize(), elevation = 4.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                // --- Custom Title Bar ---
                // This is necessary because undecorated=true removes the default one.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Выберите или создайте профиль", style = MaterialTheme.typography.h6)
                    IconButton(onClick = { onCloseRequest() }) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть окно")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Profiles List ---
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(profileList) { profile ->
                        val isSelected = selectedProfile == profile
                        val backgroundColor = if (isSelected) MaterialTheme.colors.secondary.copy(alpha = 0.5f) else Color.Transparent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backgroundColor)
                                .clickable(enabled = !gameWindows.containsKey(profile)) {
                                    selectedProfile = profile
                                    isAddWindowEnabled = true
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = profile,
                                color = if (!gameWindows.containsKey(profile)) LocalContentColor.current else LocalContentColor.current.copy(alpha = ContentAlpha.disabled)
                            )
                            if (gameWindows.containsKey(profile)) {
                                Text(" (используется)", style = MaterialTheme.typography.caption, fontStyle = FontStyle.Italic, color = LocalContentColor.current.copy(alpha = ContentAlpha.disabled))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Create New Profile ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Создать профиль:")
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(value = newProfileName, onValueChange = { newProfileName = it }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (newProfileName.isNotBlank()) {
                            selectedProfile = newProfileName
                            isAddWindowEnabled = true
                            settings.createProfile(newProfileName)
                            newProfileName = ""
                        }
                    }, enabled = newProfileName.isNotBlank()) {
                        Text("Создать")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Action Buttons: Add Window ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = {
                        selectedProfile?.let { profileNameToAdd ->
                            onAddWindow(profileNameToAdd)
                            onCloseRequest()
                        }
                    }, enabled = isAddWindowEnabled) {
                        Text("Добавить окно")
                    }
                }
            }
        }
    }
}