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
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.DisableSelection
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.zIndex
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.SplitLayoutState
import org.jetbrains.jewel.ui.component.VerticalSplitLayout
import org.jetbrains.jewel.ui.component.styling.DividerMetrics
import org.jetbrains.jewel.ui.component.styling.DividerStyle
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.MenuColors
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.FontManager.getFontLineHeight
import ru.adan.silmaril.misc.OutputItem
import ru.adan.silmaril.misc.TextMessageChunk
import ru.adan.silmaril.misc.TextSize
import ru.adan.silmaril.visual_styles.ColorStyle
import ru.adan.silmaril.misc.rememberIsAtBottom
import ru.adan.silmaril.view.hovertooltips.LoreTooltip

@Composable
fun MainWindow(
    mainViewModel: MainViewModel,
    owner: ComposeWindow,
    isFocused: Boolean,
    windowId: Int,
    logger: KLogger,
) {
    val settingsManager: SettingsManager = koinInject()

    val messages = remember { mutableStateListOf<OutputItem>() }
    val settings by settingsManager.settings.collectAsState()

    var splitState by remember { mutableStateOf(SplitLayoutState(0.8f)) }

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

    val inputFieldHasFocus = remember { mutableStateOf(false) } // currently not used for anything, consider removing
    val inputFieldFocusRequester = remember { FocusRequester() }
    val inputFieldReady = remember { mutableStateOf(false) }
    val listStateNoAutoScroll = rememberLazyListState()
    val listStateAutoScrollDown1 = rememberLazyListState()
    val listStateAutoScrollDown2 = rememberLazyListState()
    var showSplitScreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var textWindowHeight = 600f

    LaunchedEffect(Unit) {
        mainViewModel.focusTarget.collect {
            // Ensure window is active, then focus the field
            owner.toFront();
            //owner.requestFocus()
            //delay(1)
            inputFieldFocusRequester.requestFocus()
        }
    }

    // Scrolls the primary window down completely
    // Scrolls the secondary window down incompletely
    suspend fun scrollDown(primaryWindow: Boolean = true) {
        try {
            if (messages.isNotEmpty()) {
                if (primaryWindow) {
                    if (listStateAutoScrollDown1.layoutInfo.totalItemsCount > 0)
                        listStateAutoScrollDown1.scrollToItem(listStateAutoScrollDown1.layoutInfo.totalItemsCount - 1)

                    if (isFocused) {
                        if (listStateAutoScrollDown2.layoutInfo.totalItemsCount > 0)
                            listStateAutoScrollDown2.scrollToItem(listStateAutoScrollDown2.layoutInfo.totalItemsCount - 1)

                        if (!showSplitScreen) {
                            if (listStateNoAutoScroll.layoutInfo.totalItemsCount > 0)
                                listStateNoAutoScroll.scrollToItem(listStateNoAutoScroll.layoutInfo.totalItemsCount - 1)
                        }
                    }
                }
                else {
                    val oneLineHeight = getFontLineHeight(currentFontFamily)
                    val linesOnScreen = textWindowHeight/oneLineHeight
                    if (listStateNoAutoScroll.layoutInfo.totalItemsCount >= (linesOnScreen + 5))
                        listStateNoAutoScroll.animateScrollToItem(listStateNoAutoScroll.layoutInfo.totalItemsCount - (linesOnScreen.toInt() + 5))
                }
            }
        } catch (e: CancellationException) {
            // If our Job is actually cancelled, propagate it
            if (!currentCoroutineContext().isActive) throw e
            // Otherwise it's a MutatorMutex cancellation (e.g., user scroll); ignore
            logger.debug { "Scroll cancelled (higher-priority mutation). This is normal, ignoring." }
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

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        when (event.key) {
            Key.PageUp -> {
                scope.launch {
                    if (!showSplitScreen)
                        displaySplitScreen()
                    else {
                        val firstVisibleIndex = listStateNoAutoScroll.layoutInfo.visibleItemsInfo.first().index
                        val oneLineHeight = getFontLineHeight(currentFontFamily)
                        val linesOnScreen = textWindowHeight/oneLineHeight
                        val scrollToIndex = (firstVisibleIndex - linesOnScreen * 0.5f).toInt().coerceAtLeast(0)
                        if (listStateNoAutoScroll.layoutInfo.totalItemsCount >= scrollToIndex)
                            listStateNoAutoScroll.animateScrollToItem(scrollToIndex)
                    }
                }
                return true
            }
            Key.PageDown -> {
                scope.launch {
                    if (showSplitScreen)
                    {
                        val firstVisibleIndex = listStateNoAutoScroll.layoutInfo.visibleItemsInfo.first().index
                        val oneLineHeight = getFontLineHeight(currentFontFamily)
                        val linesOnScreen = textWindowHeight/oneLineHeight
                        val scrollToIndex = (firstVisibleIndex + linesOnScreen * 0.5f).toInt().coerceAtMost(listStateNoAutoScroll.layoutInfo.totalItemsCount-1)
                        listStateNoAutoScroll.animateScrollToItem(scrollToIndex)
                    }
                }
                return showSplitScreen
            }
            Key.MoveEnd -> {
                if (showSplitScreen && event.isCtrlPressed) {
                    scope.launch {
                        listStateNoAutoScroll.scrollToItem(listStateNoAutoScroll.layoutInfo.totalItemsCount - 1)
                    }
                }
                return showSplitScreen && event.isCtrlPressed
            }
        }
        return false
    }

    // Collect messages
    LaunchedEffect(mainViewModel) {
        mainViewModel.messages
            .onEach { msg ->
                val item = OutputItem.new(msg) // wrap with a monotonically increasing id
                messages += item

                // consider RingBuffer<OutputItem>(capacity = 50_000) if current setup doesn't work out
                // Trim to cap
                val cap = 50_000
                if (messages.size > cap) {
                    val toDrop = messages.size - cap
                    // Efficient trim: drop from the front without per-item recompositions
                    messages.removeRange(0, toDrop)
                }
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

    // When main screen isn't at bottom, display split screen. When upper split screen at bottom, hide it
    val upperScreenAtBottom by rememberIsAtBottom(listStateNoAutoScroll)
    LaunchedEffect(upperScreenAtBottom) {
        when {
            showSplitScreen && upperScreenAtBottom -> {
                showSplitScreen = false
            }
        }
    }

    // When the lower screen isn't at the bottom (e.g. the user moved the split separator), scroll it down
    val lowerScreenAtBottom by rememberIsAtBottom(listStateAutoScrollDown2)
    LaunchedEffect(lowerScreenAtBottom) {
        when {
            showSplitScreen && !lowerScreenAtBottom -> {
                scrollDown()
            }
        }
    }

    // When the window gets focused, give focus to input field
    LaunchedEffect(isFocused, inputFieldReady.value) {
        if (isFocused && inputFieldReady.value) {
            inputFieldFocusRequester.requestFocus()
        }
        // since we don't render the splitscreen of a non-focused window, now that we're focused, scroll it down
        scrollDown()
    }

    // Scroll when lastId changes (i.e., on each new message)
    val lastId by remember { derivedStateOf { messages.lastOrNull()?.id } } // Track the id of the last item
    LaunchedEffect(lastId) {
        if (lastId == null) return@LaunchedEffect
        // Wait one frame so LazyColumn is laid out with the new item
        withFrameNanos { }
        scrollDown()
    }

    // Composable itself
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent (::handleKey),
            // testing shaders
            //.exampleShaderWithIntArray(Color.Red, intArrayOf(0, 1, -20, 300, 400, 5000, 9500, -700000))
        color = currentColorStyle.getUiColor(UiColor.MainWindowBackground)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val width = maxWidth
            textWindowHeight = maxHeight.value - 40f // minus input field, more or less
            val paddingLeft = ((width - 680.dp) / 2).coerceAtLeast(0.dp)
            val paddingRight = ((width - 680.dp) / 2 - 300.dp).coerceAtLeast(0.dp)

            LaunchedEffect(maxHeight) {
                scrollDown()
            }

            Column {
                Box(Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer(alpha = if (!showSplitScreen) 1f else 0f)
                            .zIndex(if (!showSplitScreen) 1f else 0f),
                    ) {
                        TextColumn(
                            inputFieldFocusRequester,
                            paddingLeft,
                            customTextSelectionColors,
                            paddingRight,
                            listStateAutoScrollDown1,
                            messages,
                            currentColorStyle,
                            currentFontSize,
                            currentFontFamily,
                            false,
                            ::displaySplitScreen
                        )
                    }

                    if (isFocused)
                    VerticalSplitLayout(
                        state = splitState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(alpha = if (showSplitScreen) 1f else 0f)
                            .zIndex(if (showSplitScreen) 1f else 0f),
                        firstPaneMinWidth = 200.dp,
                        secondPaneMinWidth = 200.dp,
                        dividerStyle = DividerStyle(
                            color = Color(0xFF282a2e),
                            metrics = DividerMetrics.defaults(thickness = 2.dp)
                        ),
                        first = {
                            TextColumn(
                                inputFieldFocusRequester,
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
                                inputFieldFocusRequester,
                                paddingLeft,
                                customTextSelectionColors,
                                paddingRight,
                                listStateAutoScrollDown2,
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

                // Input field at the bottom of the screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp, top = 3.dp),
                    contentAlignment = Alignment.BottomCenter // Center TextField horizontally
                ) {
                    HistoryTextField(
                        isFocused = isFocused,
                        hasFocus = inputFieldHasFocus,
                        focusRequester = inputFieldFocusRequester,
                        inputFieldReady = inputFieldReady,
                        currentColorStyle = currentColorStyle,
                        currentFontSize = currentFontSize,
                        currentFontFamily = currentFontFamily,
                        mainViewModel = mainViewModel,
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TextColumn(
    inputFieldFocusRequester: FocusRequester,
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
    val fontFamily = remember(currentFontFamily) { FontManager.getFont(currentFontFamily) }

    val baseTextStyle = remember(currentFontSize, currentFontFamily) {
        TextStyle(
            fontSize = currentFontSize.sp,
            fontFamily = fontFamily,
            lineHeight = currentFontSize.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(start = paddingLeft)
                .weight(1f)
                .fillMaxHeight()
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                CopyOnLeftRelease_NoMenu(giveFocusTo = inputFieldFocusRequester) {
                    SelectionContainer(
                        // keeps the arrow when hovering text, stop the cursor from turning into a caret
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
                            // also collapse selection after Ctrl/Cmd+C
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyUp &&
                                    (e.isCtrlPressed || e.isMetaPressed) &&
                                    e.key == Key.C
                                ) {
                                    // Let the actual copy happen (return false), just defocus afterwards
                                    inputFieldFocusRequester.requestFocus()
                                }
                                false
                            }
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
                                val item = messages[idx]
                                val message = messages[idx].message
                                // Combine chunks into a single AnnotatedString
                                val annotatedText =
                                    remember(item.id, currentColorStyle, currentFontSize, currentFontFamily) {
                                        buildAnnotatedFromChunks(
                                            chunks = message.chunks,
                                            colorStyle = currentColorStyle,
                                            baseFontSize = currentFontSize,
                                        )
                                    }

                                if (message.loreItem == null) {
                                    Text(
                                        text = annotatedText,
                                        modifier = Modifier.fillMaxWidth(), // This makes the whole line selectable
                                        style = baseTextStyle,
                                    )
                                }
                                // if message with lore in it
                                else {
                                    val showPopupMenu = remember { mutableStateOf(false) }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        // text before the item link
                                        // assumption is that it's grey text, but if this changes, use annotatedString here
                                        Text(
                                            text = message.chunks[0].text,
                                            color = if (message.chunks[0].fg.isLiteral) message.chunks[0].fg.color!! else currentColorStyle.getAnsiColor(
                                                message.chunks[0].fg.ansi,
                                                message.chunks[0].fg.isBright
                                            ),
                                            style = baseTextStyle,
                                        )

                                        Link(
                                            text = message.loreItem + if (message.chunks.size < 2) "\r" else "",
                                            textStyle = baseTextStyle,
                                            onClick = {
                                                showPopupMenu.value = !showPopupMenu.value
                                            },
                                            style = LinkStyle.dark(
                                                colors = LinkColors.dark(
                                                    //content = currentColorStyle.getAnsiColor(AnsiColor.Cyan, true)
                                                    //content = currentColorStyle.getAnsiColor(AnsiColor.Cyan, false)
                                                    content = currentColorStyle.getUiColor(UiColor.Link)
                                                )
                                            ),
                                        )
                                        if (showPopupMenu.value) {
                                            DisableSelection {
                                                PopupMenu(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    style = MenuStyle.dark(
                                                        colors = MenuColors.dark(
                                                            border = Color.Unspecified,
                                                            background = currentColorStyle.getUiColor(UiColor.HoverBackground)
                                                        )
                                                    ),
                                                    onDismissRequest = {
                                                        showPopupMenu.value = false
                                                        inputFieldFocusRequester.requestFocus()
                                                        true
                                                    },
                                                    content = {
                                                        passiveItem {
                                                            LoreTooltip(message.loreItem, currentColorStyle)
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        // text after the link
                                        if (message.chunks.size >= 2) {
                                            val textAfterLink = remember(
                                                item.id, currentColorStyle, currentFontSize
                                            ) {
                                                buildAnnotatedFromChunks(
                                                    chunks = message.chunks.copyOfRange(1, message.chunks.size),
                                                    colorStyle = currentColorStyle,
                                                    baseFontSize = currentFontSize
                                                )
                                            }

                                            Text(
                                                text = textAfterLink,
                                                modifier = Modifier.fillMaxWidth(),
                                                style = baseTextStyle,
                                            )
                                        }
                                    }
                                }
                            }
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

private fun TextSize.toSp(base: Int): TextUnit = when (this) {
    TextSize.Small -> (base - 4).sp
    TextSize.Normal -> base.sp
    TextSize.Large -> (base + 4).sp
}

private fun spanStyleForChunk(
    chunk: TextMessageChunk,
    colorStyle: ColorStyle,
    baseFontSize: Int
): SpanStyle {
    val fg = if (chunk.fg.isLiteral) {
        chunk.fg.color!!
    } else {
        colorStyle.getAnsiColor(chunk.fg.ansi, chunk.fg.isBright)
    }

    val bg = when {
        chunk.bg.isLiteral -> chunk.bg.color!!
        chunk.bg.ansi != AnsiColor.None -> colorStyle.getAnsiColor(chunk.bg.ansi, chunk.bg.isBright)
        else -> Color.Unspecified
    }

    return SpanStyle(
        color = fg,
        background = bg,
        fontSize = chunk.textSize.toSp(baseFontSize),
        // add other properties if needed (e.g. fontWeight)
    )
}

/**
 * Pure function. No Compose state/capturing. Deterministic based on inputs.
 */
private fun buildAnnotatedFromChunks(
    chunks: Array<TextMessageChunk>,
    colorStyle: ColorStyle,
    baseFontSize: Int,
    appendCarriageReturn: Boolean = true
): AnnotatedString = buildAnnotatedString {
    for (chunk in chunks) {
        withStyle(spanStyleForChunk(chunk, colorStyle, baseFontSize)) {
            append(chunk.text)
        }
    }
    if (appendCarriageReturn) append("\r")
}

@Composable
fun HistoryTextField(
    isFocused: Boolean,
    hasFocus: MutableState<Boolean>,
    focusRequester: FocusRequester,
    inputFieldReady: MutableState<Boolean>,
    currentColorStyle: ColorStyle,
    currentFontSize: Int,
    currentFontFamily: String,
    mainViewModel: MainViewModel,
) {
    var inputTextField by remember { mutableStateOf(TextFieldValue("")) }
    val fontFamily = remember(currentFontFamily) { FontManager.getFont(currentFontFamily) }

    val history = remember { mutableStateListOf<String>() } // persist with Room/DataStore if needed
    var historyIndex by rememberSaveable { mutableStateOf(-1) } // -1 means “editing new entry”

    // When the window gets focused, select already existing text in the text field
    LaunchedEffect(isFocused) {
        if (isFocused) {
            inputTextField = inputTextField.copy(
                selection = TextRange(0, inputTextField.text.length) // Select all text
            )
        }
    }

    fun commit() {
        val t = inputTextField.text.trim()
        if (t.isNotEmpty()) {
            // avoid duplicate consecutive entries; cap size
            if (history.lastOrNull() != t) history.add(t)
            if (history.size > 50) history.removeAt(0)

            inputTextField = inputTextField.copy(
                selection = TextRange(0, inputTextField.text.length) // Select all text
            )
        }
        mainViewModel.treatUserInput(inputTextField.text)
        historyIndex = -1
    }

    fun recallUp() {
        if (history.isEmpty()) return
        historyIndex = when {
            historyIndex == -1 -> history.lastIndex
            historyIndex > 0 -> historyIndex - 1
            else -> 0
        }
        inputTextField = TextFieldValue(history[historyIndex], selection = TextRange(0, history[historyIndex].length))
    }

    fun recallDown() {
        if (history.isEmpty()) return
        when {
            historyIndex == -1 -> Unit // nothing to do
            historyIndex < history.lastIndex -> {
                historyIndex++
                inputTextField = TextFieldValue(history[historyIndex], selection = TextRange(0, history[historyIndex].length))
            }
            else -> { // past the most recent -> back to blank
                historyIndex = -1
            }
        }
    }

    BasicTextField(
        value = inputTextField,
        onValueChange = {
            inputTextField = it
            // if the user edits, consider we're no longer at a specific history index
            if (historyIndex != -1) historyIndex = -1
        },
        modifier = Modifier
            .width(600.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { recallUp(); true }
                    Key.DirectionDown -> { recallDown(); true }
                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                hasFocus.value = it.isFocused
            }
            .onGloballyPositioned { inputFieldReady.value = true }
            .background(
                currentColorStyle.getUiColor(UiColor.InputField),
                RoundedCornerShape(currentColorStyle.inputFieldCornerRoundness().dp)
            ) // Add background with clipping to the rounded shape
            .padding(8.dp),
        textStyle = TextStyle(
            color = currentColorStyle.getUiColor(UiColor.InputFieldText),
            fontSize = currentFontSize.sp,
            fontFamily = fontFamily
        ),
        cursorBrush = SolidColor(Color.White), // Change the caret (cursor) color to white
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                commit()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CopyOnLeftRelease_NoMenu(
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
                    Modifier.pointerInput(textManager, giveFocusTo) {
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