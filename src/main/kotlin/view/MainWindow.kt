package view
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
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

@Composable
@Preview
fun MainWindow(viewModel: MainViewModel) {
    //val strings = remember { mutableStateOf(listOf("Default String 1", "Default String 2")) }
    // Observe messages from the ViewModel
    val messages by viewModel.messages.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(modifier = Modifier) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, true) // true = take up all remaining space horizontally
                        .fillMaxHeight()
                        .focusRequester(focusRequester)
                        .focusable() // Make the LazyColumn focusable
                        .onKeyEvent {
                            if (it.key == Key.K && it.type == KeyEventType.KeyDown) {
                                val randomString = generateRandomString()
                                // viewModel.sendMessage(randomString)
                                viewModel.reconnect()
                                //strings.value += randomString
                                // println("Added random string: $randomString")
                                true
                            } else {
                                false
                            }
                        },
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
                    style = androidx.compose.foundation.ScrollbarStyle(
                        minimalHeight = 16.dp,
                        thickness = 8.dp,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        hoverDurationMillis = 300,
                        unhoverColor = Color.Gray,    // Color when not hovered
                        hoverColor = Color.White      // Color when hovered
                    )
                )
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

private fun generateRandomString(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val length = (10..20).random()
    return (1..length)
        .map { chars.random() }
        .joinToString("")
}