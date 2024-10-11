package view
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import viewmodel.MainViewModel
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue

@Composable
@Preview
fun MainWindow(viewModel: MainViewModel) {
    // Observe messages from the ViewModel
    val messages by viewModel.messages.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, true) // true = take up all remaining space horizontally
                            .fillMaxHeight(),
                        state = listState,
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(messages) { item ->
                            Text(
                                text = item,
                                color = Color.White,
                                fontSize = 18.sp
                            )
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
                        //.padding(1.dp),
                    contentAlignment = Alignment.BottomCenter // Center TextField horizontally
                ) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier
                            .width(600.dp)
                            //.height(40.dp)
                            .focusRequester(focusRequester)
                            .background(Color.Transparent)
                            .focusable(),
                        textStyle = TextStyle(
                            //color = Color.White, // Set typed text color to white
                            fontSize = 16.sp
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = Color(0xFF424242), // Use this color for the background and it will clip to the rounded shape
                            cursorColor = Color.White,          // Change the caret (cursor) color to white
                            focusedIndicatorColor = Color.Transparent, // Remove the purple focus underline when focused
                            unfocusedIndicatorColor = Color.Transparent, // Remove the purple underline when unfocused
                            textColor = Color.White,           // White text color inside the field
                            placeholderColor = Color.LightGray // Grey color for the placeholder
                        ),
                        //contentPadding = PaddingValues(vertical = 4.dp),
                        singleLine = true, // Make the input handle a single line
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done // Keyboard Action to "Done"
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                // Optional: Handle submit action if IME "Done" button is pressed
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, textFieldValue.text.length) // Select all text
                                )
                                viewModel.sendMessage(textFieldValue.text)
                            }
                        )
                    )
                }
            }
        }
    }

    // Request focus after the first composition
    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose { }
    }

    // Scroll to the bottom when a new string is added
    DisposableEffect(messages) {
        scope.launch {
            if (messages.isNotEmpty()) {
                listState.scrollToItem(messages.size - 1)
            }
        }
        onDispose { }
    }
}