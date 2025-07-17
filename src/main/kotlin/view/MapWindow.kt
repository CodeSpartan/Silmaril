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
import visual_styles.StyleManager
import misc.UiColor
import mud_messages.CurrentRoomMessage
import viewmodel.MapViewModel
import viewmodel.SettingsViewModel
import xml_schemas.Room
import xml_schemas.Zone
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.ExperimentalComposeUiApi

@Composable
@Preview
fun MapWindow(mapViewModel: MapViewModel, settingsViewModel: SettingsViewModel) {

    val currentColorStyle by settingsViewModel.currentColorStyleName.collectAsState()

    var lastZone = -100 // -1 zone is reserved for roads
    var lastRoom = -1

    val curZoneState = remember { mutableStateOf(mapViewModel.getZone(lastZone)) }
    var curZoneRooms: Map<Int, Room> = mapViewModel.getRooms(lastZone)

    val curRoomState = remember { mutableStateOf(curZoneRooms[lastRoom]) }

    var lastCurrentRoomMessage: CurrentRoomMessage? by remember { mutableStateOf(null) }

    // Collect SharedFlow in a LaunchedEffect
    LaunchedEffect(mapViewModel) {
        mapViewModel.currentRoomMessages.collect { message ->
            // Update the state with the new message to trigger recomposition
            lastCurrentRoomMessage = message
        }
    }

    // React to changes in currentRoom
    LaunchedEffect(lastCurrentRoomMessage) {
        lastCurrentRoomMessage?.let { roomMessage ->
            println("new room")
            if (roomMessage.zoneId != lastZone) {
                curZoneState.value = mapViewModel.getZone(roomMessage.zoneId)
                curZoneRooms = mapViewModel.getRooms(roomMessage.zoneId)
            }
            if (roomMessage.roomId != lastRoom) {
                curRoomState.value = curZoneRooms[roomMessage.roomId]
            }
            lastZone = roomMessage.zoneId
            lastRoom = roomMessage.roomId
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StyleManager.getStyle(currentColorStyle).getUiColor(UiColor.AdditionalWindowBackground))
    ) {
        RoomsCanvas(curZoneState)
        // Draw something on the Canvas
//        Canvas(modifier = Modifier.fillMaxSize()) {
//            val canvasWidth = size.width
//            val canvasHeight = size.height
//
//            // Example: Draw a simple circle at the center
//            drawCircle(
//                color = Color.Cyan,
//                radius = 50f,
//                center = Offset(x = canvasWidth / 2, y = canvasHeight / 2)
//            )
//        }

        // Overlay two texts in the bottom left corner of the Box
        // Room name
        Text(
            text = curRoomState.value?.name?:"",
            color = Color.White,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 32.dp) // adjustments for positioning
                .align(Alignment.BottomStart)
        )
        // Zone name
        Text(
            text = curZoneState.value?.name?: "",
            color = Color.White,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 8.dp) // further down
                .align(Alignment.BottomStart)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RoomsCanvas(curZoneState: MutableState<Zone?>) {
    // 1. State for tracking zoom (scale) and pan (offset)
    var scale by remember { mutableStateOf(0.5f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // 2. Add pointer input modifier for Panning (drag)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    panOffset += dragAmount
                }
            }
            // 3. Add pointer event modifier for Zooming (scroll)
            .onPointerEvent(PointerEventType.Scroll) {
                val zoomFactor = 1.1f
                // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-mouse-events.html#scroll-listeners
                if (it.changes.first().scrollDelta.y < 0) { // Scroll up -> Zoom in
                    scale *= zoomFactor
                } else { // Scroll down -> Zoom out
                    scale /= zoomFactor
                }
            }
    ) {
        if (curZoneState.value == null) return@Canvas
        val roomsAtZ0 = curZoneState.value!!.roomsList.filter { it.z == 5 }

        if (roomsAtZ0.isEmpty()) {
            return@Canvas
        }

        // --- 4. ADJUST DRAWING LOGIC ---

        // Base values
        val baseRoomRadius = 50f
        val baseRoomSpacing = baseRoomRadius * 3 // Space for a room + gap

        // Scaled values based on the zoom state
        val scaledRoomRadius = baseRoomRadius * scale
        val scaledRoomSpacing = baseRoomSpacing * scale

        // Find min coordinates for relative positioning
        val minX = roomsAtZ0.minOf { it.x }
        val minY = roomsAtZ0.minOf { it.y }

        // This offset centers the entire drawing initially
        val centeringOffset = Offset(
            x = (size.width - (roomsAtZ0.maxOf { it.x } - minX) * scaledRoomSpacing) / 2,
            y = (size.height - (roomsAtZ0.maxOf { it.y } - minY) * scaledRoomSpacing) / 2
        )

        // The final offset combines centering with user panning
        val totalOffset = centeringOffset + panOffset

        // Create a map of room objects to their final calculated screen positions
        val roomToOffsetMap = roomsAtZ0.associateWith { room ->
            Offset(
                x = totalOffset.x + (room.x - minX) * scaledRoomSpacing,
                y = totalOffset.y + (room.y - minY) * scaledRoomSpacing
            )
        }

        // Draw the connections (Lines)
        roomsAtZ0.forEach roomLoop@{ room ->
            val startOffset = roomToOffsetMap[room] ?: return@roomLoop

            room.exitsList.forEach exitLoop@{ exit ->
                if (room.id < exit.roomId) {
                    val endRoom = roomsAtZ0.find { it.id == exit.roomId }
                    if (endRoom != null) {
                        val endOffset = roomToOffsetMap[endRoom] ?: return@exitLoop
                        drawLine(
                            color = Color.Gray,
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = Stroke.DefaultMiter * scale // Make line width scale slightly
                        )
                    }
                }
            }
        }

        // Draw the rooms (Circles)
        roomToOffsetMap.values.forEach { offset ->
            drawCircle(
                color = Color.Cyan,
                radius = scaledRoomRadius,
                center = offset
            )
        }
    }
}