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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.DropdownLink
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.SplitLayoutState
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticalSplitLayout
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LocalTooltipStyle
import org.jetbrains.jewel.ui.component.styling.MenuColors
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.TooltipAutoHideBehavior
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.FontManager.getFontLineHeight
import ru.adan.silmaril.misc.OutputItem
import ru.adan.silmaril.misc.TextSize
import ru.adan.silmaril.visual_styles.ColorStyle
import ru.adan.silmaril.misc.rememberIsAtBottom
import ru.adan.silmaril.view.hovertooltips.LoreTooltip
import kotlin.collections.forEach
import kotlin.random.Random

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
    val listStateAutoScrollDown1 = rememberLazyListState()
    val listStateAutoScrollDown2 = rememberLazyListState()
    var showSplitScreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var textWindowHeight = 600f

    var inputTextField by remember { mutableStateOf(TextFieldValue("")) }

    val density = LocalDensity.current.density

    // Scrolls the primary window down completely
    // Scrolls the secondary window down incompletely
    suspend fun scrollDown(primaryWindow: Boolean = true) {
        try {
            if (messages.isNotEmpty()) {
                if (primaryWindow) {
                    if (listStateAutoScrollDown1.layoutInfo.totalItemsCount > 0)
                        listStateAutoScrollDown1.scrollToItem(listStateAutoScrollDown1.layoutInfo.totalItemsCount - 1)

                    if (listStateAutoScrollDown2.layoutInfo.totalItemsCount > 0)
                        listStateAutoScrollDown2.scrollToItem(listStateAutoScrollDown2.layoutInfo.totalItemsCount - 1)

                    if (!showSplitScreen) {
                        if (listStateNoAutoScroll.layoutInfo.totalItemsCount > 0)
                            listStateNoAutoScroll.scrollToItem(listStateNoAutoScroll.layoutInfo.totalItemsCount - 1)
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
        if (event.type != KeyEventType.KeyUp) return false
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
    LaunchedEffect(isFocused, inputFieldReady) {
        if (isFocused && inputFieldReady) {
            focusRequester.requestFocus()
        }
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
            val paddingLeft = ((width - 680.dp) / density).coerceAtLeast(0.dp)
            val paddingRight = ((width - 680.dp) / density - 300.dp).coerceAtLeast(0.dp)

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

                    VerticalSplitLayout(
                        state = splitState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(alpha = if (showSplitScreen) 1f else 0f)
                            .zIndex(if (showSplitScreen) 1f else 0f),
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
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
    val loreLinkTextStyle = remember {
        TextStyle(
            fontSize = currentFontSize.sp,
            fontFamily = FontManager.getFont(currentFontFamily)
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
                            val annotatedText = remember (
                                messages[idx].id,
                                currentColorStyle,
                                currentFontSize,
                                currentFontFamily
                            ) {
                                buildAnnotatedString {
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
                            }

                            if (message.loreItem == null) {
                                Text(
                                    text = annotatedText,
                                    modifier = Modifier.fillMaxWidth(), // This makes the whole line selectable
                                    fontSize = currentFontSize.sp,
                                    fontFamily = FontManager.getFont(currentFontFamily)
                                )
                            } else {
                                val showPopupMenu = remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = message.chunks[0].text,
                                        fontSize = currentFontSize.sp,
                                        color = currentColorStyle.getAnsiColor(AnsiColor.None, false),
                                        fontFamily = FontManager.getFont(currentFontFamily)
                                    )


                                    Link(
                                        text = message.loreItem,
                                        textStyle = loreLinkTextStyle,
                                        onClick = { showPopupMenu.value = !showPopupMenu.value },
                                        style = LinkStyle.dark(colors = LinkColors.dark(
                                            //content = currentColorStyle.getAnsiColor(AnsiColor.Cyan, true)
                                            //content = currentColorStyle.getAnsiColor(AnsiColor.Cyan, false)
                                            content = currentColorStyle.getUiColor(UiColor.Link)
                                        )),
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

                                    if (message.chunks.size == 2)
                                        Text(
                                            text = message.chunks[1].text,
                                            color = currentColorStyle.getAnsiColor(AnsiColor.None, false),
                                            fontSize = currentFontSize.sp,
                                            fontFamily = FontManager.getFont(currentFontFamily)
                                        )
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