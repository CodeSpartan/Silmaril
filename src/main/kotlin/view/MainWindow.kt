package view
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.desktop.ui.tooling.preview.Preview
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import viewmodel.MainViewModel
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import misc.FontManager
import misc.StyleManager
import misc.UiColor
import org.jetbrains.skia.ImageFilter
import shaders.*
import viewmodel.SettingsViewModel
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.imageio.ImageIO
import kotlin.math.absoluteValue
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeShaderBuilder
import java.io.InputStream

fun loadResourceImageBitmap(resourcePath: String): ImageBitmap {
    val inputStream: InputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
        ?: throw IllegalArgumentException("Resource not found: $resourcePath")
    return Image.makeFromEncoded(inputStream.readBytes()).toComposeImageBitmap()
}

fun skiaImageToImageBitmap(skiaImage: Image): ImageBitmap {
    return skiaImage.toComposeImageBitmap()
}

@Composable
@Preview
fun MainWindow(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    owner: ComposeWindow,
) {
    // Observe messages from the ViewModel
    val messages by mainViewModel.messages.collectAsState()

    // Observe currentFontFamily from the SettingsViewModel
    val currentFontFamily by settingsViewModel.currentFontFamily.collectAsState()
    val currentFontSize by settingsViewModel.currentFontSize.collectAsState()
    val currentColorStyle by settingsViewModel.currentColorStyle.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    suspend fun scrollDown() {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    var paddingLeft by remember { mutableStateOf(maxOf((owner.width.dp - 680.dp) / 2, 0.dp)) }
    var paddingRight by remember { mutableStateOf(maxOf((owner.width.dp - 680.dp) / 2 - 300.dp, 0.dp)) }
    var windowRealSize by remember { mutableStateOf(Pair(800.0f, 600.0f))} // Useful for shaders

    val time by produceDrawLoopCounter(speed = 1f)

    owner.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
            // real size that depends on density
            windowRealSize = Pair(owner.contentPane.width * density.density, owner.contentPane.height * density.density)
            // println("Window size: ${windowRealSize.first} x ${windowRealSize.second}")
            // println(time.absoluteValue)

            // owner size isn't real size, but oh well, this formula works
            paddingLeft = maxOf((owner.width.dp - 680.dp) / 2, 0.dp)
            paddingRight = maxOf((owner.width.dp - 680.dp) / 2 - 300.dp, 0.dp)
            runBlocking {
                scrollDown()
            }
        }

        override fun componentMoved(e: ComponentEvent?) {
            windowRealSize = Pair(owner.contentPane.width * density.density, owner.contentPane.height * density.density)
        }
    })

    val skiaImage = remember {
        loadSkiaImage("icon.png")
    }

    val imageBitmap = remember { skiaImageToImageBitmap(skiaImage) }

    val filter = ImageFilter.makeImage(image = skiaImage)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            //.snowShader(windowRealSize.first, windowRealSize.second, time)
            //.crtShader(windowRealSize.first, windowRealSize.second)
            //.tintShader(uniformNames=arrayOf("image"), imageFilters=emptyArray<ImageFilter?>(), Color.Red, 0.75f)
            .tintShader(uniformNames=arrayOf("image"), imageFilters=arrayOf(filter), Color.Red, 0.25f)
        ,
        color = StyleManager.getStyle(currentColorStyle).getUiColor(UiColor.MainWindowBackground)
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
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, true) // true = take up all remaining space horizontally
                            .padding(end=paddingRight)
                            .fillMaxHeight(),
                        state = listState,
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(messages) { message ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                message.chunks.forEach { chunk ->
                                    Text(
                                        text = chunk.text,
                                        color = StyleManager.getStyle(currentColorStyle).getAnsiColor(chunk.foregroundColor, chunk.isBright),
                                        fontSize = currentFontSize.sp,
                                        fontFamily = FontManager.getFont(currentFontFamily),
                                        // fontWeight = FontWeight.Bold,
                                    )
                                }
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
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.BottomCenter // Center TextField horizontally
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier
                            .width(600.dp)
                            //.height(40.dp)
                            .focusRequester(focusRequester)
                            .background(
                                StyleManager.getStyle(currentColorStyle).getUiColor(UiColor.InputField),
                                RoundedCornerShape(32.dp)
                            ) // Add background with clipping to the rounded shape
                            .focusable()
                            .padding(8.dp), // Apply padding as necessary
                        textStyle = TextStyle(
                            color = StyleManager.getStyle(currentColorStyle).getUiColor(UiColor.InputFieldText),
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(Color.White), // Change the caret (cursor) color to white
                        singleLine = true, // Handle single line input
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, textFieldValue.text.length) // Select all text
                                )
                                mainViewModel.sendMessage(textFieldValue.text)
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

    // Request focus after the first composition
    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose { }
    }

    // Scroll to the bottom when a new string is added
    DisposableEffect(messages) {
        scope.launch {
            scrollDown()
        }
        onDispose { }
    }
}

@Composable
fun produceDrawLoopCounter(speed: Float = 1f): State<Float> {
    return produceState(0f) {
        while (true) {
            withInfiniteAnimationFrameMillis {
                value = it / 1000f * speed
            }
        }
    }
}