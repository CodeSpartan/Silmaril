package view

import OwnerWindow
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import java.awt.Dimension

@Composable
@Preview
fun MapWindow(mapViewModel: MapViewModel, settingsViewModel: SettingsViewModel) {

    val currentColorStyle by settingsViewModel.currentColorStyleName.collectAsState()

    var lastZone = -100 // -1 zone is reserved for roads
    var lastRoom = -1

    val curZoneState = remember { mutableStateOf(mapViewModel.getZone(lastZone)) }
    var curZoneRooms: Map<Int, Room> = mapViewModel.getRooms(lastZone)
    val curRoomState = remember { mutableStateOf(curZoneRooms[lastRoom]) }
    var lastRoomMessage: CurrentRoomMessage? by remember { mutableStateOf(null) }

    var currentHoverRoom: Room? = null

    val hoverManager = LocalHoverManager.current
    val ownerWindow = OwnerWindow.current
    var internalPadding by remember { mutableStateOf(Offset.Zero) }
    var tooltipOffset by remember { mutableStateOf(Offset.Zero) }

    val dpi = LocalDensity.current.density

    LaunchedEffect(mapViewModel) {
        mapViewModel.currentRoomMessages.collect { message ->
            // Update the state with the new message to trigger recomposition
            lastRoomMessage = message
        }
    }

    // React to changes in currentRoom
    LaunchedEffect(lastRoomMessage) {
        lastRoomMessage?.let { roomMessage ->
            println("new room")
            if (roomMessage.zoneId != lastZone) {
                curZoneState.value = mapViewModel.getZone(roomMessage.zoneId)
                curZoneRooms = mapViewModel.getRooms(roomMessage.zoneId)
                mapViewModel.squashRooms(curZoneRooms)
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
            .onGloballyPositioned { layoutCoordinates ->
                internalPadding = layoutCoordinates.positionInWindow()
            }
    ) {
        RoomsCanvas(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            curZoneState,
            onRoomHover = { room, position ->
                // This callback is executed inside RoomsCanvas whenever a hover event occurs.
                // It updates the state that is held here, in the parent.
                if (room != null) {
                    if (currentHoverRoom != room) {
                        tooltipOffset = (position + internalPadding) / dpi
                        hoverManager.show(ownerWindow, tooltipOffset, Dimension(300, 200)) {
                            MapRoomTooltip(room, curZoneState.value)
                        }
                        currentHoverRoom = room
                    }
                }
                else {
                    currentHoverRoom = null
                    hoverManager.hide()
                }
            }
        )

        // Overlay two texts in the bottom left corner of the Box
        Box(
            modifier = Modifier
                .padding(start = 8.dp, bottom = 8.dp) // adjustments for positioning
                .align(Alignment.BottomStart)
                .background(StyleManager.getStyle(currentColorStyle).getUiColor(UiColor.AdditionalWindowBackground).copy(alpha = 0.8f))
        ) {
            Column {
                // Room name
                Text(
                    text = curRoomState.value?.name ?: "",
                    color = Color.White,
                    modifier = Modifier.padding(6.dp, 4.dp, 6.dp, 1.dp)
                )
                // Zone name
                Text(
                    text = curZoneState.value?.name ?: "",
                    color = Color.White,
                    modifier = Modifier.padding(6.dp, 2.dp, 6.dp, 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RoomsCanvas(
    modifier: Modifier = Modifier,
    curZoneState: MutableState<Zone?>,
    // The canvas accepts a callback to report hover events up to its parent
    onRoomHover: (room: Room?, position: Offset) -> Unit
    ) {

    val dpi = LocalDensity.current.density

    // BoxWithConstraints provides the layout size (maxWidth, maxHeight) to its children.
    BoxWithConstraints(modifier = modifier) {
        // States for tracking zoom (scale) and pan (offset)
        var scaleLogical by remember { mutableStateOf(0.25f) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }

        if (curZoneState.value == null) return@BoxWithConstraints
        val rooms = curZoneState.value!!.roomsList
        if (rooms.isEmpty()) {
            return@BoxWithConstraints
        }

        // Base values
        val baseRoomRadius = 50f
        val baseRoomSpacing = baseRoomRadius * 3 // Space for a room + gap

        // Scaled values based on the zoom state
        val scaledRoomRadius = baseRoomRadius * scaleLogical * dpi
        val scaledRoomSpacing = baseRoomSpacing * scaleLogical * dpi

        // Find min coordinates for relative positioning
        val minX = rooms.minOf { it.x }
        val minY = rooms.minOf { it.y }
        val maxX = rooms.maxOf { it.x }
        val maxY = rooms.maxOf { it.y }

        // This offset centers the entire drawing initially
        val centeringOffset = Offset(
            x = (this.maxWidth.value - (maxX - minX) * scaledRoomSpacing) / 2,
            y = (this.maxHeight.value - (maxY - minY) * scaledRoomSpacing) / 2
        )

        // The final offset combines centering with user panning
        val totalOffset = centeringOffset + panOffset

        // Create a map of room objects to their final calculated screen positions
        val roomToOffsetMap = rooms.associateWith { room ->
            Offset(
                x = totalOffset.x + (room.x - minX) * scaledRoomSpacing,
                y = totalOffset.y + (room.y - minY) * scaledRoomSpacing
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize() // Canvas fills the BoxWithConstraints
                // Add pointer input modifier for Panning (drag)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffset += dragAmount
                    }
                }
                // Pointer event modifier for Zooming (scroll)
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val zoomFactor = 1.2f
                    // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-mouse-events.html#scroll-listeners
                    if (event.changes.first().scrollDelta.y < 0) { // Scroll up -> Zoom in
                        scaleLogical *= zoomFactor
                    } else { // Scroll down -> Zoom out
                        scaleLogical /= zoomFactor
                    }
                }
                // Pointer event modifier for Moving the mouse around, to catch a hover
                .onPointerEvent(PointerEventType.Move) { event ->
                    // hover logic
                    val mousePosition = event.changes.first().position
                    val roomUnderMouse = roomToOffsetMap.entries.find { (_, center) ->
                        (mousePosition - center).getDistance() <= scaledRoomRadius
                    }?.key
                    onRoomHover(
                        roomUnderMouse,
                        mousePosition
                    )
                }
                // If the mouse leaves the Map window, the hover certainly ends
                .onPointerEvent(PointerEventType.Exit) { event ->
                    onRoomHover(null, event.changes.first().position)
                }
        ) {
            // Draw the connections (Lines)
            rooms.forEach roomLoop@{ room ->
                val startOffset = roomToOffsetMap[room] ?: return@roomLoop
                room.exitsList.forEach exitLoop@{ exit ->
                    if (room.id < exit.roomId) {
                        val endRoom = rooms.find { it.id == exit.roomId }
                        if (endRoom != null) {
                            val endOffset = roomToOffsetMap[endRoom] ?: return@exitLoop
                            drawLine(
                                color = Color.Gray,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = Stroke.DefaultMiter * scaleLogical * dpi // Make line width scale slightly
                            )
                        }
                    }
                }
            }

            // Draw the rooms (Circles)
            roomToOffsetMap.values.forEach { offset ->
                drawCircle(color = Color.Cyan, radius = scaledRoomRadius, center = offset)
            }
        }
    }
}

@Composable
fun MapRoomTooltip(room: Room, zone: Zone?) {
    Column(modifier = Modifier.padding(8.dp).fillMaxSize()) {
        Text(room.name, color = Color.White)
        //if (zone != null) Text(zone.name, color = Color.White)
        Text("Description: ${room.description}", color = Color.White)
        Text("Room ID: ${room.id} (Coords: ${room.x}, ${room.y}, ${room.z})", color = Color.White)
        Text("Room Exits: ${room.exitsList}", color = Color.White)
    }
}