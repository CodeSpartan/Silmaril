package view.small_dialogs

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
import misc.FontManager
import model.SettingsManager
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.clip

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

// Define a modern dark color palette inspired by Discord
private val DarkColorPalette = darkColors(
    primary = Color(0xFF7289DA), // A vibrant blue for primary actions
    primaryVariant = Color(0xFF5B6EAE),
    secondary = Color(0xFF7289DA), // A consistent secondary color
    background = Color(0xFF2C2F33), // Deep dark grey for the window background
    surface = Color(0xFF36373e),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

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

    val robotoFont = remember { FontManager.getFont("RobotoClassic")}

    MaterialTheme(colors = DarkColorPalette) {
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
                //elevation = 4.dp, // this thing breaks the background color
            ) {
                Column {
                    // --- Custom Title Bar ---
                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)
                            .background(Color(0xFF36373e))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Выберите профиль",
                                fontFamily = robotoFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = Color.White,
                            )
                            IconButton(onClick = { onCloseRequest() }) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть окно")
                            }
                        }
                    }

                    // --- Content of the window
                    Column(modifier = Modifier.padding(16.dp)) {
                        // --- Profiles List ---
                        LazyColumn(modifier = Modifier
                            .weight(1f)
                            .background(color = Color(0xFF2F3036), shape = RoundedCornerShape(5.dp))
                        ) {
                            items(profileList) { profile ->
                                // 1. Create an InteractionSource for each item.
                                val interactionSource = remember { MutableInteractionSource() }
                                // 2. Track the hover state from the InteractionSource.
                                val isHovered by interactionSource.collectIsHoveredAsState()
                                val isSelected = selectedProfile == profile
                                val backgroundColor = when {
                                    isSelected -> MaterialTheme.colors.secondary.copy(alpha = 0.7f)
                                    isHovered -> Color.White.copy(alpha = 0.1f)
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
                                        Text(
                                            text = profile,
                                            fontFamily = robotoFont,
                                            color = textColor
                                        )
                                        if (gameWindows.containsKey(profile)) {
                                            Text(
                                                " (используется)",
                                                //style = MaterialTheme.typography.caption,
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

                        Row (){
                            // --- Create New Profile ---
                            // --- Custom BasicTextField Implementation ---
                            var isFocused by remember { mutableStateOf(false) }

                            // --- START OF CUSTOMIZATION ---
                            val borderColor = if (isFocused) Color(0xFF83adf6) else Color(0xFF414148)

                            // 4. Control text properties
                            val textStyle = TextStyle(
                                color = MaterialTheme.colors.onSurface,
                                fontSize = 16.sp,
                                fontFamily = robotoFont,
                                fontWeight = FontWeight.Light,
                            )
                            // --- END OF CUSTOMIZATION ---

                            BasicTextField(
                                value = newProfileName,
                                onValueChange = { newProfileName = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                                    .background(
                                        color = Color(0xFF2b2c32),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                                textStyle = textStyle,
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                                decorationBox = { innerTextField ->
                                    // This Box provides the layout and padding for the text field
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        // 1. Control the text's padding
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
                            // --- End of Custom BasicTextField ---

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

                            Button(onClick = {
                                selectedProfile?.let { profileToDelete ->
                                    selectedProfile = ""
                                    isAddWindowEnabled = false
                                    settings.deleteProfile(profileToDelete)
                                }
                            },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF3e3f45),
                                    disabledBackgroundColor = Color(0xFF4e4f55),
                                    contentColor = Color(0xFFfa928d),
                                    disabledContentColor = Color(0xFF919296)
                                ),

                                enabled = isAddWindowEnabled) {
                                Text("Удалить", fontFamily = robotoFont, fontWeight = FontWeight.Normal, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
