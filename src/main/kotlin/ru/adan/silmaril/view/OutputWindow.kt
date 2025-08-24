package ru.adan.silmaril.view

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.OutputItem
import ru.adan.silmaril.misc.TextSize
import ru.adan.silmaril.visual_styles.StyleManager
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.viewmodel.MainViewModel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

@Composable
fun AdditionalOutputWindow(outputWindowModel: OutputWindowModel, logger: KLogger) {

    val settingsManager: SettingsManager = koinInject()
    val settings by settingsManager.settings.collectAsState()

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
        if (outputMessages.isNotEmpty()) {
            listState.scrollToItem(outputMessages.size - 1)
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
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
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
                                        color = currentColorStyle.getAnsiColor(
                                            chunk.fgColor,
                                            chunk.isBright
                                        ),
                                        fontSize = when (chunk.textSize) {
                                            TextSize.Small -> (currentFontSize - 4).sp
                                            TextSize.Normal -> currentFontSize.sp
                                            TextSize.Large -> (currentFontSize + 4).sp
                                        }
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