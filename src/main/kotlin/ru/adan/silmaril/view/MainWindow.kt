package ru.adan.silmaril.view
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.adan.silmaril.viewmodel.MainViewModel
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.runBlocking
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.visual_styles.StyleManager
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.model.SettingsManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.OutputItem
import ru.adan.silmaril.misc.TextSize

@Composable
fun MainWindow(
    mainViewModel: MainViewModel,
    owner: ComposeWindow,
    isFocused: Boolean,
    windowId: Int,
    logger: KLogger,
) {
    val settingsManager: SettingsManager = koinInject()

    // consider RingBuffer<OutputItem>(capacity = 100_000) if current setup doesn't work out
    val messages = remember { mutableStateListOf<OutputItem>() }
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

    val focusRequester = remember { FocusRequester() }
    var inputFieldReady by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var inputTextField by remember { mutableStateOf(TextFieldValue("")) }

    suspend fun scrollDown() {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(mainViewModel) {

        mainViewModel.messages
            .onEach { msg ->
                logger.debug { "mainWindow: collection start" }
                val item = OutputItem.new(msg) // wrap with a monotonically increasing id
                messages += item

                // Trim to cap (e.g., 100_000)
                val cap = 50_000
                if (messages.size > cap) {
                    val toDrop = messages.size - cap
                    // Efficient trim: drop from the front without per-item recompositions
                    messages.removeRange(0, toDrop)
                }

                scrollDown()
                logger.debug { "mainWindow: collection success" }
            }
            .catch { e ->
                logger.error(e) { "messages collector error" }
            }
            .onCompletion { cause ->
                logger.warn { "messages collector completed. cause=$cause" }
            }
            .launchIn(this) // terminal

//        mainViewModel.messages.collect { msg ->
//
//        }
    }

    LaunchedEffect(isFocused, inputFieldReady) {
        if (isFocused && inputFieldReady) {
            focusRequester.requestFocus()
        }
    }

    val density = LocalDensity.current.density

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // testing shaders
            //.exampleShaderWithIntArray(Color.Red, intArrayOf(0, 1, -20, 300, 400, 5000, 9500, -700000))
        ,
        color = currentColorStyle.getUiColor(UiColor.MainWindowBackground)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val width = maxWidth
            val paddingLeft = ((width - 680.dp) / density).coerceAtLeast(0.dp)
            val paddingRight = ((width - 680.dp) / density - 300.dp).coerceAtLeast(0.dp)

            LaunchedEffect(maxHeight) {
                scrollDown()
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier
                    .padding(start=paddingLeft)
                    .weight(1f)
                ) {
                    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                        SelectionContainer(
                            // keeps the arrow when hovering text, stop the cursor from turning into a caret
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f, true) // true = take up all remaining space horizontally
                                    .padding(end = paddingRight)
                                    .fillMaxHeight(),
                                state = listState,
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(
                                    count = messages.size,
                                    key = { idx -> messages[idx].id}
                                ) { idx ->
                                    val message = messages[idx].message
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

                    // Vertical scrollbar on the right side
                    VerticalScrollbar(
                        modifier = Modifier
                            .padding(end = 4.dp).fillMaxHeight()
                            .width(8.dp)    // width for the scrollbar
                            .padding(end = 4.dp), // padding between the scrollbar and content
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

                // Input field at the bottom of the screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp, top = 3.dp),
                    contentAlignment = Alignment.BottomCenter // Center TextField horizontally
                ) {
                    BasicTextField(
                        value = inputTextField,
                        onValueChange = { inputTextField = it },
                        modifier = Modifier
                            .width(600.dp)
                            //.height(40.dp)
                            .focusRequester(focusRequester)
                            .onGloballyPositioned { inputFieldReady = true }
                            .background(
                                currentColorStyle.getUiColor(UiColor.InputField),
                                RoundedCornerShape(currentColorStyle.inputFieldCornerRoundness().dp)
                            ) // Add background with clipping to the rounded shape
                            .padding(8.dp), // Apply padding as necessary
                        textStyle = TextStyle(
                            color = currentColorStyle.getUiColor(UiColor.InputFieldText),
                            fontSize = currentFontSize.sp,
                            fontFamily = FontManager.getFont(currentFontFamily)
                        ),
                        cursorBrush = SolidColor(Color.White), // Change the caret (cursor) color to white
                        singleLine = true, // Handle single line input
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                inputTextField = inputTextField.copy(
                                    selection = TextRange(0, inputTextField.text.length) // Select all text
                                )
                                mainViewModel.treatUserInput(inputTextField.text)
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                innerTextField() // This is where the actual text field content is drawn
                            }
                        }
                    )
                }
            }
        }
    }

    // When font changes, scroll down
    LaunchedEffect(currentFontFamily) {
        scrollDown()
    }

    // Scroll to the bottom when a new string is added
    DisposableEffect(messages) {
        scope.launch {
            scrollDown()
        }
        onDispose { }
    }
}