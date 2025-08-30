package ru.adan.silmaril.view
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.visual_styles.StyleManager
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.model.SettingsManager
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import org.jetbrains.jewel.ui.component.SplitLayoutState
import org.jetbrains.jewel.ui.component.VerticalSplitLayout
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.FontManager.getFontLineHeight
import ru.adan.silmaril.misc.OutputItem
import ru.adan.silmaril.misc.TextSize
import ru.adan.silmaril.visual_styles.ColorStyle
import ru.adan.silmaril.misc.rememberIsAtBottom

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

    var splitState by remember { mutableStateOf(SplitLayoutState(0.5f)) }

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
    val listStateNoAutoScroll = rememberLazyListState()
    val listStateAutoScrollDown = rememberLazyListState()
    var showSplitScreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var textWindowHeight = 600f

    var inputTextField by remember { mutableStateOf(TextFieldValue("")) }

    // Scrolls the primary window down completely
    // Scrolls the secondary window down incompletely
    suspend fun scrollDown(primaryWindow: Boolean = true) {
        try {
            if (messages.isNotEmpty()) {
                if (primaryWindow && listStateAutoScrollDown.layoutInfo.totalItemsCount > 0) {
                    listStateAutoScrollDown.scrollToItem(messages.size - 1)
                }
                else {
                    val oneLineHeight = getFontLineHeight(currentFontFamily)
                    val linesOnScreen = textWindowHeight/oneLineHeight
                    if (listStateNoAutoScroll.layoutInfo.totalItemsCount >= (linesOnScreen + 5))
                        listStateNoAutoScroll.scrollToItem(listStateNoAutoScroll.layoutInfo.totalItemsCount - (linesOnScreen.toInt() + 5))
                }
            }
        } catch (e: CancellationException) {
            // If our Job is actually cancelled, propagate it
            if (!currentCoroutineContext().isActive) throw e
            // Otherwise it's a MutatorMutex cancellation (e.g., user scroll); ignore
            logger.debug { "Scroll cancelled (higher-priority mutation). This is normal, ignoring." }
        }
    }

    val secondaryAtBottom by rememberIsAtBottom(listStateNoAutoScroll)

    // When main screen isn't at bottom, display split screen. When upper split screen at bottom, hide it
    LaunchedEffect(secondaryAtBottom) {
        when {
            showSplitScreen && secondaryAtBottom -> {
                showSplitScreen = false
            }
        }
    }

    LaunchedEffect(mainViewModel) {
        mainViewModel.messages
            .onEach { msg ->
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
            }
            .catch { e ->
                if (e is CancellationException) throw e // let real cancellation propagate
                logger.error(e) { "messages collector error" }
            }
            .onCompletion { cause ->
                logger.warn { "messages collector completed. cause=$cause" }
            }
            .launchIn(this) // terminal
    }

    LaunchedEffect(isFocused, inputFieldReady) {
        if (isFocused && inputFieldReady) {
            focusRequester.requestFocus()
        }
    }

    fun displaySplitScreen() {
        if (!showSplitScreen) {
            showSplitScreen = true
            scope.launch {
                withFrameNanos { /* wait 1 frame */ }
                scrollDown()
                scrollDown(false)
            }
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
            textWindowHeight = maxHeight.value - 40f // minus input field, more or less
            val paddingLeft = ((width - 680.dp) / density).coerceAtLeast(0.dp)
            val paddingRight = ((width - 680.dp) / density - 300.dp).coerceAtLeast(0.dp)

            LaunchedEffect(maxHeight) {
                scrollDown()
            }

            Column {
                Box(Modifier.weight(1f)) {
                    if (!showSplitScreen) {
                        TextColumn(
                            paddingLeft,
                            customTextSelectionColors,
                            paddingRight,
                            listStateAutoScrollDown,
                            messages,
                            currentColorStyle,
                            currentFontSize,
                            currentFontFamily,
                            false,
                            ::displaySplitScreen
                        )
                    } else {
                        VerticalSplitLayout(
                            state = splitState,
                            modifier = Modifier.fillMaxWidth(),
                            firstPaneMinWidth = 200.dp,
                            secondPaneMinWidth = 200.dp,
                            first = {
                                TextColumn(
                                    paddingLeft,
                                    customTextSelectionColors,
                                    paddingRight,
                                    listStateNoAutoScroll,
                                    messages,
                                    currentColorStyle,
                                    currentFontSize,
                                    currentFontFamily,
                                    true,
                                    ::displaySplitScreen
                                )
                            },
                            second = {
                                TextColumn(
                                    paddingLeft,
                                    customTextSelectionColors,
                                    paddingRight,
                                    listStateAutoScrollDown,
                                    messages,
                                    currentColorStyle,
                                    currentFontSize,
                                    currentFontFamily,
                                    false,
                                    ::displaySplitScreen
                                )
                            },
                        )
                    }
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TextColumn(
    paddingLeft: Dp,
    customTextSelectionColors: TextSelectionColors,
    paddingRight: Dp,
    listState: LazyListState,
    messages: SnapshotStateList<OutputItem>,
    currentColorStyle: ColorStyle,
    currentFontSize: Int,
    currentFontFamily: String,
    withScrollbar: Boolean,
    showSplitScreen: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(start = paddingLeft)
                .weight(1f)
                .fillMaxHeight()
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                SelectionContainer(
                    // keeps the arrow when hovering text, stop the cursor from turning into a caret
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            //.weight(1f, true) // true = take up all remaining space horizontally
                            .padding(end = paddingRight)
                            .onPointerEvent(PointerEventType.Scroll) { event ->
                                if (!withScrollbar) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null && change.scrollDelta.y < 0) {
                                        change.consume()
                                        showSplitScreen()
                                    }
                                }
                            }
                            .fillMaxHeight(),
                        state = listState,
                        userScrollEnabled = withScrollbar,
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(
                            count = messages.size,
                            key = { idx -> messages[idx].id }
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
            if (withScrollbar)
                VerticalScrollbar(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .width(12.dp)    // width for the scrollbar
                        .padding(end = 4.dp), // padding between the scrollbar and content
                    adapter = rememberScrollbarAdapter(listState),
                    style = ScrollbarStyle(
                        minimalHeight = 16.dp,
                        thickness = 12.dp,
                        shape = RoundedCornerShape(4.dp),
                        hoverDurationMillis = 300,
                        unhoverColor = Color.Gray,    // Color when not hovered
                        hoverColor = Color.White      // Color when hovered
                    )
                )
        }
    }
}