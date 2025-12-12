package ru.adan.silmaril.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import ru.adan.silmaril.model.AndroidProfile
import ru.adan.silmaril.model.ConnectionState

/**
 * Right drawer content showing list of open profiles with management options.
 * Supports reordering via up/down arrow buttons.
 */
@Composable
fun ProfileDrawer(
    profiles: Map<String, AndroidProfile>,
    currentProfileName: String,
    allSavedProfiles: List<String>,
    onCreateProfile: () -> Unit,
    onOpenProfile: () -> Unit,
    onSwitchProfile: (String) -> Unit,
    onCloseProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onReorderProfiles: (List<String>) -> Unit
) {
    // Derive profile order directly from the profiles map (LinkedHashMap preserves insertion order)
    val profileOrder = profiles.keys.toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Profiles",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons - styled to match Connect button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCreateProfile,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF5a5a5a),
                    contentColor = Color.White
                )
            ) {
                Text("New")
            }

            Button(
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF5a5a5a),
                    contentColor = Color.White
                )
            ) {
                Text("Open")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Divider(color = Color(0xFF3a3a3a))

        Spacer(modifier = Modifier.height(8.dp))

        // Profile list with up/down reordering
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(
                items = profileOrder,
                key = { _, profileName -> profileName }
            ) { index, profileName ->
                val profile = profiles[profileName]
                if (profile != null) {
                    val connectionState by profile.client.connectionState.collectAsState()

                    ProfileListItem(
                        name = profileName,
                        connectionState = connectionState,
                        isCurrent = profileName.equals(currentProfileName, ignoreCase = true),
                        onSwitch = { onSwitchProfile(profileName) },
                        onClose = { onCloseProfile(profileName) },
                        onDelete = { onDeleteProfile(profileName) },
                        onMoveUp = {
                            if (index > 0) {
                                val newOrder = profileOrder.toMutableList()
                                val temp = newOrder[index]
                                newOrder[index] = newOrder[index - 1]
                                newOrder[index - 1] = temp
                                onReorderProfiles(newOrder)
                            }
                        },
                        onMoveDown = {
                            if (index < profileOrder.size - 1) {
                                val newOrder = profileOrder.toMutableList()
                                val temp = newOrder[index]
                                newOrder[index] = newOrder[index + 1]
                                newOrder[index + 1] = temp
                                onReorderProfiles(newOrder)
                            }
                        },
                        canMoveUp = index > 0,
                        canMoveDown = index < profileOrder.size - 1
                    )

                    if (index < profileOrder.size - 1) {
                        Divider(
                            color = Color(0xFF2a2a2a),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        // Note about reordering
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tip: Use ▲▼ arrows to reorder profiles",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
