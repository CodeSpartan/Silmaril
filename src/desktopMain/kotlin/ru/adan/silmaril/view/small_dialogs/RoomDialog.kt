package ru.adan.silmaril.view.small_dialogs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.selectedItemIndex
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.misc.getRoomColorOption
import ru.adan.silmaril.misc.getRoomIconOption
import ru.adan.silmaril.misc.roomColorOptions
import ru.adan.silmaril.misc.roomIconOptions
import ru.adan.silmaril.misc.toOptionColor
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.visual_styles.AppColors
import ru.adan.silmaril.visual_styles.StyleManager
import ru.adan.silmaril.xml_schemas.Room

@OptIn(ExperimentalJewelApi::class)
@Composable
fun RoomDialog(
    room: MutableState<Room?>,
    settingsManager: SettingsManager,
    roomDataManager: RoomDataManager,
    profileManager: ProfileManager,
    logger: KLogger,
) {
    val settings by settingsManager.settings.collectAsState()
    val colorStyleName = settings.colorStyle
    val colorStyle = remember(colorStyleName) { StyleManager.getStyle(colorStyleName) }

    val scope = rememberCoroutineScope()

    val commentTextFieldState = rememberTextFieldState("")
    var selectedIconIndex by remember { mutableIntStateOf(0) }
    val selectedIconListState: SelectableLazyListState = rememberSelectableLazyListState()
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    val selectedColorListState: SelectableLazyListState = rememberSelectableLazyListState()
    val onEntryTriggerState = rememberTextFieldState("")

    LaunchedEffect(room.value?.id) {
        val newRoom = room.value
        if (newRoom != null) {
            commentTextFieldState.edit {
                replace(0, length, roomDataManager.getRoomComment(newRoom.id) ?: "")
            }
            onEntryTriggerState.edit {
                replace(0, length, roomDataManager.getRoomTrigger(newRoom.id) ?: "")
            }
            selectedIconIndex = getRoomIconOption(roomDataManager.getRoomCustomIcon(newRoom.id))
            selectedIconListState.selectedKeys = setOf(selectedIconIndex) // magic line without which the combo box is buggy
            selectedColorIndex = getRoomColorOption(roomDataManager.getRoomCustomColor(newRoom.id))
            selectedColorListState.selectedKeys = setOf(selectedColorIndex) // magic line without which the combo box is buggy
        } else {
            selectedIconIndex = 0
            selectedIconListState.selectedKeys = setOf(selectedIconIndex)
            selectedColorIndex = 0
            selectedColorListState.selectedKeys = setOf(selectedColorIndex)
        }
    }

    fun closeRoomDialog() {
        room.value = null
        scope.launch {
            withFrameNanos { }
            profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit)
        }
    }

    fun saveRoomData() {
        val curRoom = room.value
        if (curRoom != null) {
            roomDataManager.setRoomComment(curRoom.id, commentTextFieldState.text.toString())
            roomDataManager.setRoomTrigger(curRoom.id, onEntryTriggerState.text.toString())
            roomDataManager.setRoomIcon(curRoom.id, roomIconOptions[selectedIconIndex].roomIcon)
            roomDataManager.setRoomColor(curRoom.id, roomColorOptions[selectedColorIndex])
        }
    }

    if (room.value != null) {

        MaterialTheme(colors = AppColors.DarkColorPalette) {
            DialogWindow(
                onCloseRequest = { room.value = null },
                state = rememberDialogState(width = 500.dp, height = 450.dp),
                undecorated = true,
                resizable = false,
                title = "Комната №${room.value?.id}",
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
                                SelectionContainer {
                                    Text(
                                        text = "Комната №${room.value?.id}",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colors.onSurface, // Use theme color

                                    )
                                }
                            }
                            Box(modifier = Modifier.scale(0.8f)) {
                                IconButton(
                                    onClick = ::closeRoomDialog,
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

                        // --- Content of the window ---

                        val scrollState = rememberScrollState()

                        var viewportHeightPx by remember { mutableIntStateOf(0) }
                        var topFixedPx by remember { mutableIntStateOf(0) }     // description + pickers
                        var bottomFixedPx by remember { mutableIntStateOf(0) }  // trigger field (or anything after the TextArea)

                        val density = LocalDensity.current
                        val minTextAreaPx = with(density) { 75.dp.roundToPx() }
                        val spacerPx = with(density) { 8.dp.roundToPx() }       // we’ll use two 8.dp spacers around the TextArea

                        // Compute the TextArea height in px, then convert to dp
                        val textAreaHeightDp by remember {
                            derivedStateOf {
                                val available = viewportHeightPx - topFixedPx - bottomFixedPx - spacerPx * 2
                                with(density) { maxOf(minTextAreaPx, available).toDp() }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                .weight(1f)                  // this is the bounded viewport between title and buttons
                                .fillMaxWidth()
                                .onSizeChanged { viewportHeightPx = it.height }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(end = 12.dp),   // keep content off the scrollbar
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { topFixedPx = it.height },
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = buildAnnotatedString {
                                                withStyle(style = SpanStyle(color = colorStyle.getAnsiColor(AnsiColor.Cyan, true))) {
                                                    append(room.value?.name + "\n")
                                                }
                                                withStyle(style = SpanStyle(color = colorStyle.getAnsiColor(AnsiColor.None, true))) {
                                                    append("   ${room.value?.description?.replace('\n', ' ')}")
                                                }
                                            }
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            InfoText(text = "Иконка")
                                            ListComboBox(
                                                items = roomIconOptions,
                                                selectedIndex = selectedIconIndex,
                                                modifier = Modifier.fillMaxWidth(),
                                                onSelectedItemChange = { index -> selectedIconIndex = index },
                                                itemKeys = { index, _ -> index },
                                                listState = selectedIconListState,
                                                itemContent = { item, isSelected, isActive ->
                                                    SimpleListItem(
                                                        text = item.roomIcon.toString(),
                                                        selected = isSelected,
                                                        active = isActive,
                                                        iconContentDescription = item.roomIcon.toString(),
                                                        icon = item.icon,
                                                        colorFilter = ColorFilter.tint(colorStyle.getUiColor(UiColor.MapNeutralIcon)),
                                                    )
                                                },
                                            )
                                        }

                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            InfoText(text = "Цвет")
                                            ListComboBox(
                                                items = roomColorOptions,
                                                selectedIndex = selectedColorIndex,
                                                modifier = Modifier.fillMaxWidth(),
                                                onSelectedItemChange = { index -> selectedColorIndex = index },
                                                itemKeys = { index, _ -> index },
                                                listState = selectedColorListState,
                                                itemContent = { item, isSelected, isActive ->
                                                    SimpleListItem(
                                                        text = item.toString(),
                                                        selected = isSelected,
                                                        active = isActive,
                                                        iconContentDescription = item.toString(),
                                                        icon = AllIconsKeys.Debugger.ThreadAtBreakpoint,
                                                        colorFilter = ColorFilter.tint(item.toOptionColor()),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }

                                // --- TextArea that grows to the leftover viewport height (but never below 75.dp) ---
                                TextArea(
                                    state = commentTextFieldState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(textAreaHeightDp), // computed exact height
                                    placeholder = { Text("Запись о клетке...") },
                                )
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { bottomFixedPx = it.height }
                                ) {
                                    TextField(
                                        state = onEntryTriggerState,
                                        placeholder = { Text("Триггер при входе") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                adapter = rememberScrollbarAdapter(scrollState)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 16.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DefaultButton(
                                onClick = {
                                    saveRoomData()
                                    closeRoomDialog()
                                }
                            ) {
                                Text("Сохранить")
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            OutlinedButton(onClick = ::closeRoomDialog) {
                                Text("Отмена")
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = JewelTheme.globalColors.text.info
    )
}