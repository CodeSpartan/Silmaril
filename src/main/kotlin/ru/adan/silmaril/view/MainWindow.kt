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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

@Composable
fun MainWindow(
    mainViewModel: MainViewModel,
    settingsManager: SettingsManager,
    owner: ComposeWindow,
    isFocused: Boolean,
    windowId: Int,
) {
    // Observe messages from the ViewModel
    val messages by mainViewModel.messages.collectAsState()

    val settings by settingsManager.settings.collectAsState()

    val currentFontFamily = settings.font
    val currentFontSize = settings.fontSize
    val currentColorStyleName = settings.colorStyle

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var inputTextField by remember { mutableStateOf(TextFieldValue("")) }

    suspend fun scrollDown() {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }

    var paddingLeft by remember { mutableStateOf(maxOf((owner.width.dp - 680.dp) / 2, 0.dp)) }
    var paddingRight by remember { mutableStateOf(maxOf((owner.width.dp - 680.dp) / 2 - 300.dp, 0.dp)) }

    //@TODO: can this be done in a modern way? Like in Main.kt
    owner.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
            // owner size isn't real size, but oh well, this formula works
            // @TODO: what is that 2 - dpi? needs to be fixed
            paddingLeft = maxOf((owner.width.dp - 680.dp) / 2, 0.dp)
            paddingRight = maxOf((owner.width.dp - 680.dp) / 2 - 300.dp, 0.dp)
            runBlocking {
                scrollDown()
            }
        }
    })

    val currentColorStyle = StyleManager.getStyle(currentColorStyleName)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // testing shaders
            //.exampleShaderWithIntArray(Color.Red, intArrayOf(0, 1, -20, 300, 400, 5000, 9500, -700000))
        ,
        color = currentColorStyle.getUiColor(UiColor.MainWindowBackground)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier
                    .padding(start=paddingLeft)
                    .weight(1f)
                ) {
                    SelectionContainer (
                        // keeps the arrow when hovering text, stop the cursor from turning into a caret
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
                    ){
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f, true) // true = take up all remaining space horizontally
                                .padding(end = paddingRight)
                                .fillMaxHeight(),
                            state = listState,
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(messages) { message ->
                                // Combine chunks into a single AnnotatedString
                                val annotatedText = buildAnnotatedString {
                                    message.chunks.forEach { chunk ->
                                        withStyle(
                                            style = SpanStyle(
                                                color = currentColorStyle.getAnsiColor(
                                                    chunk.foregroundColor,
                                                    chunk.isBright
                                                ),
                                                // You can add other styles like fontWeight here if needed
                                            )
                                        ) {
                                            append(chunk.text)
                                        }
                                    }
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

                    // Vertical scrollbar on the right side
                    VerticalScrollbar(
                        modifier = Modifier
                            .padding(end = 4.dp).fillMaxHeight()
                            .width(8.dp)      // Set a sensible width for the scrollbar
                            .padding(end = 4.dp), // Add padding between the scrollbar and content
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