package ru.adan.silmaril.view.small_dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.model.SettingsManager
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import ru.adan.silmaril.model.Profile
import ru.adan.silmaril.visual_styles.AppColors

@Composable
fun ProfileDialog(
    showProfileDialog: MutableState<Boolean>,
    gameWindows: Map<String, Profile>,
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
    gameWindows: Map<String, Profile>,
    onCloseRequest: () -> Unit,
    onAddWindow: (windowName: String) -> Unit
) {
    var selectedProfile by remember { mutableStateOf<String?>(null) }
    var newProfileName by remember { mutableStateOf("") }
    var isAddWindowEnabled by remember { mutableStateOf(false) }

    val profileList by settings.profiles.collectAsState()
    val robotoFont = remember { FontManager.getFont("RobotoClassic") }

    MaterialTheme(colors = AppColors.DarkColorPalette) {
        DialogWindow(
            onCloseRequest = { onCloseRequest() },
            state = rememberDialogState(width = 400.dp, height = 500.dp),
            undecorated = true,
            resizable = false,
            title = "Select Profile",
            transparent = true,
        ) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colors.surface,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column {
                    // --- Custom Title Bar ---
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 7.dp, end = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier) {
                            Text(
                                "Выберите профиль",
                                fontFamily = robotoFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = MaterialTheme.colors.onSurface, // Use theme color
                            )
                        }
                        Box(modifier = Modifier.scale(0.8f)) {
                            IconButton(
                                onClick = { onCloseRequest() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.5.dp,
                                        color = AppColors.closeButton,
                                        shape = CircleShape,
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Закрыть окно",
                                    tint = AppColors.closeButton
                                )
                            }
                        }
                    }

                    // --- Content of the window
                    Column(modifier = Modifier.padding(16.dp)) {
                        // --- Profiles List ---
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .background(color = AppColors.List.background, shape = RoundedCornerShape(5.dp))
                        ) {
                            items(profileList) { profile ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val isHovered by interactionSource.collectIsHoveredAsState()
                                val isSelected = selectedProfile == profile
                                val backgroundColor = when {
                                    isSelected -> MaterialTheme.colors.secondary.copy(alpha = 0.7f)
                                    isHovered -> MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
                                    else -> Color.Transparent // Use transparent to show the LazyColumn's background
                                }
                                Box(modifier = Modifier.padding(horizontal = 10.dp).clip(RoundedCornerShape(5.dp))) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(backgroundColor)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                enabled = !gameWindows.containsKey(profile)
                                            ) {
                                                selectedProfile = profile
                                                isAddWindowEnabled = true
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val textColor = if (!gameWindows.containsKey(profile)) {
                                            LocalContentColor.current
                                        } else {
                                            LocalContentColor.current.copy(alpha = ContentAlpha.disabled)
                                        }
                                        Text(text = profile, fontFamily = robotoFont, color = textColor)
                                        if (gameWindows.containsKey(profile)) {
                                            Text(
                                                " (используется)",
                                                fontFamily = robotoFont,
                                                fontSize = 10.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Создать профиль", fontFamily = robotoFont, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            // --- Create New Profile ---
                            var isFocused by remember { mutableStateOf(false) }
                            val borderColor = if (isFocused) AppColors.TextField.focusedBorder else AppColors.TextField.unfocusedBorder

                            val textStyle = TextStyle(
                                color = MaterialTheme.colors.onSurface,
                                fontSize = 16.sp,
                                fontFamily = robotoFont,
                                fontWeight = FontWeight.Light,
                            )

                            BasicTextField(
                                value = newProfileName,
                                onValueChange = { newProfileName = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                                    .background(
                                        color = AppColors.TextField.background,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                                textStyle = textStyle,
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        innerTextField()
                                        if (newProfileName.isEmpty()) {
                                            Text(
                                                text = "Имя профиля...",
                                                style = textStyle,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(onClick = {
                                if (newProfileName.isNotBlank()) {
                                    selectedProfile = newProfileName
                                    isAddWindowEnabled = true
                                    settings.createProfile(newProfileName)
                                    newProfileName = ""
                                }
                            }, enabled = !profileList.contains(newProfileName) && newProfileName.isNotBlank()) {
                                Text("Создать", fontFamily = robotoFont, fontWeight = FontWeight.Normal, letterSpacing = 1.sp)
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
                                Text("Добавить окно", fontFamily = robotoFont, fontWeight = FontWeight.Normal, letterSpacing = 1.sp)
                            }

                            Button(
                                onClick = {
                                    selectedProfile?.let { profileToDelete ->
                                        selectedProfile = ""
                                        isAddWindowEnabled = false
                                        settings.deleteProfile(profileToDelete)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = AppColors.Button.deleteBackground,
                                    disabledBackgroundColor = AppColors.Button.disabledDeleteBackground,
                                    contentColor = MaterialTheme.colors.error, // Use the 'error' color from the palette
                                    disabledContentColor = AppColors.Button.disabledDeleteContent
                                ),
                                enabled = isAddWindowEnabled
                            ) {
                                Text("Удалить", fontFamily = robotoFont, fontWeight = FontWeight.Normal, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}