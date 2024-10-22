package view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mud_messages.CurrentRoomMessage
import viewmodel.MapViewModel

@Composable
@Preview
fun MapWindow(viewModel: MapViewModel) {

    var currentRoom by remember { mutableStateOf(CurrentRoomMessage.empty()) }

    // Collect SharedFlow in a LaunchedEffect
    LaunchedEffect(viewModel) {
        viewModel.currentRoomMessages.collect { message ->
            // Update the state with the new message to trigger recomposition
            currentRoom = message
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Draw something on the Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Example: Draw a simple circle at the center
            drawCircle(
                color = Color.Cyan,
                radius = 50f,
                center = Offset(x = canvasWidth / 2, y = canvasHeight / 2)
            )
        }

        // Overlay two texts at the bottom left corner of the Box
        Text(
            text = currentRoom?.let { "Комната: ${it.roomId}" } ?: "",
            color = Color.White,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 32.dp) // adjustments for positioning
                .align(Alignment.BottomStart)
        )
        Text(
            text = currentRoom?.let { "Зона: ${it.zoneId}" } ?: "",
            color = Color.White,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 8.dp) // further down
                .align(Alignment.BottomStart)
        )
    }
}