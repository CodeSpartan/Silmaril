package ru.adan.silmaril.view

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.OutputItem
import ru.adan.silmaril.misc.TextSize
import ru.adan.silmaril.visual_styles.StyleManager
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.view.small_widgets.EmptyOverlayReturnsFocus

@Composable
fun AdditionalOutputWindow(outputWindowModel: OutputWindowModel, logger: KLogger, profileManager: ProfileManager) {

    val settingsManager: SettingsManager = koinInject()
    val settings by settingsManager.settings.collectAsState()

    val clearFocusRequester = remember { FocusRequester() }

    val currentFontFamily = settings.font
    val currentFontSize = settings.fontSize
    val currentColorStyleName = settings.colorStyle
    val currentColorStyle = remember(currentColorStyleName) { StyleManager.getStyle(currentColorStyleName) }

    val customTextSelectionColors = remember(currentColorStyle) {
        TextSelectionColors(
            handleColor = Color.Transparent,
            backgroundColor = currentColorStyle.getUiColor(UiColor.MainWindowSelectionBackground)
        )
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val outputMessages = remember { mutableStateListOf<OutputItem>() }

    suspend fun scrollDown() {
        try {
            if (outputMessages.isNotEmpty()) {
                listState.scrollToItem(outputMessages.size - 1)
            }
        } catch (e: CancellationException) {
            // If our Job is actually cancelled, propagate it
            if (!currentCoroutineContext().isActive) throw e
            // Otherwise it's a MutatorMutex cancellation (e.g., user scroll); ignore
            logger.debug { "Scroll cancelled (higher-priority mutation). This is normal, ignoring." }
        }
    }

    LaunchedEffect(outputWindowModel) {
        // Optional: buffer/conflate if events are very bursty
        outputWindowModel.colorfulTextMessages.collect { msg ->
            val item = OutputItem.new(msg) // wrap with a monotonically increasing id
            outputMessages += item

            // Trim to cap (e.g., 100_000)
            val cap = 5_000
            if (outputMessages.size > cap) {
                val toDrop = outputMessages.size - cap
                // Efficient trim: drop from the front without per-item recompositions
                outputMessages.removeRange(0, toDrop)
            }

            scrollDown()
        }
    }

    // When font changes, scroll down
    LaunchedEffect(currentFontFamily) {
        scrollDown()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentColorStyle.getUiColor(UiColor.AdditionalWindowBackground))
            .border(1.dp, color = if (currentColorStyle.borderAroundFloatWidgets()) JewelTheme.globalColors.borders.normal else Color.Unspecified)
    ) {
        // Invisible focus sink
        Box(
            Modifier
                .size(1.dp)
                .focusRequester(clearFocusRequester)
                .focusable()
        )

        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
            OutputWindowCopyOnLeftRelease_NoMenu(profileManager, clearFocusRequester) {
                SelectionContainer(
                    // keeps the arrow when hovering text, stop the cursor from turning into a caret
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .fillMaxHeight(),
                        state = listState,
                        verticalArrangement = Arrangement.Bottom, // reverseLayout = true ?
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(
                            count = outputMessages.size,
                            key = { idx -> outputMessages[idx].id }
                        ) { idx ->
                            val message = outputMessages[idx].message
                            // Combine chunks into a single AnnotatedString
                            val annotatedText = buildAnnotatedString {
                                message.chunks.forEach { chunk ->
                                    withStyle(
                                        style = SpanStyle(
                                            color = if (chunk.fg.isLiteral) chunk.fg.color!! else currentColorStyle.getAnsiColor(
                                                chunk.fg.ansi,
                                                chunk.fg.isBright
                                            ),
                                            fontSize = when (chunk.textSize) {
                                                TextSize.Small -> (currentFontSize - 4).sp
                                                TextSize.Normal -> currentFontSize.sp
                                                TextSize.Large -> (currentFontSize + 4).sp
                                            },
                                            background = if (chunk.bg.isLiteral)
                                                chunk.bg.color!!
                                            else if (chunk.bg.ansi != AnsiColor.None) currentColorStyle.getAnsiColor(
                                                chunk.bg.ansi,
                                                chunk.bg.isBright
                                            ) else Color.Unspecified,
                                            // You can add other styles like fontWeight here if needed
                                        )
                                    ) {
                                        append(chunk.text)
                                    }
                                }
                                append("\r") // helps format the copied text, otherwise it has no newlines
                            }

                            Text(
                                text = annotatedText,
                                modifier = Modifier.fillMaxWidth(), // This makes the whole line selectable
                                fontSize = currentFontSize.sp,
                                fontFamily = FontManager.getFont(currentFontFamily)
                            )
                        }
                    }
                }
            }
        }

        // if there are no messages, fill the window with an empty overlay that returns the focus to main window
        if (outputMessages.isEmpty()) {
            EmptyOverlayReturnsFocus(profileManager)
        }

        // Vertical scrollbar on the right side
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .offset(x = (-4).dp)
                .width(8.dp),      // width for the scrollbar
            adapter = rememberScrollbarAdapter(listState),
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = Color.Gray,    // Color when not hovered
                hoverColor = Color.White      // Color when hovered
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OutputWindowCopyOnLeftRelease_NoMenu(
    profileManager: ProfileManager,
    giveFocusTo: FocusRequester,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTextContextMenu provides object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit
            ) {
                Box(
                    Modifier.pointerInput(textManager) {
                        awaitPointerEventScope {
                            var leftDown = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (event.buttons.isPrimaryPressed) leftDown = true
                                        if (event.buttons.isSecondaryPressed) {
                                            // Right click: just give focus away; no menu (we don't use ContextMenuArea)
                                            giveFocusTo.requestFocus()
                                            profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit)
                                            // Consume so nothing else shows a menu
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        if (leftDown) {
                                            leftDown = false
                                            // Left button up: copy if anything is selected, then defocus
                                            if (textManager.selectedText.isNotEmpty()) {
                                                textManager.copy?.invoke()
                                            }
                                            giveFocusTo.requestFocus()
                                            profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit)
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    content()
                }
            }
        }
    ) {
        content()
    }
}