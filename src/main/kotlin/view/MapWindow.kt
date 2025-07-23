package view

import LocalTopBarHeight
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
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp

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

    // State to hold the ID of the room to center on. This will be passed to the RoomsCanvas.
    var centerOnRoomId by remember { mutableStateOf<Int?>(null) }

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

    // React to changes from the currentRoom message
    LaunchedEffect(lastRoomMessage) {
        lastRoomMessage?.let { roomMessage ->
            if (roomMessage.zoneId != lastZone) {
                curZoneState.value = mapViewModel.getZone(roomMessage.zoneId)
                curZoneRooms = mapViewModel.getRooms(roomMessage.zoneId)
                mapViewModel.squashRooms(curZoneRooms)
            }
            // If the room has changed, update the state and trigger the centering
            if (roomMessage.roomId != lastRoom) {
                curRoomState.value = curZoneRooms[roomMessage.roomId]
                centerOnRoomId = roomMessage.roomId
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
            settingsViewModel = settingsViewModel,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            curZoneState = curZoneState,
            centerOnRoomId = centerOnRoomId, // Pass the target ID to the canvas
            topBarPixelHeight = LocalTopBarHeight.current, // height of the bar by which we drag the window, provided
            onRoomHover = { room, position ->
                // This callback is executed inside RoomsCanvas whenever a hover event occurs.
                // It updates the state that is held here, in the parent.
                if (room != null) {
                    if (currentHoverRoom != room) {
                        tooltipOffset = (position + internalPadding) / dpi
                        hoverManager.show(ownerWindow, tooltipOffset, 300) {
                            MapRoomTooltip(room, curZoneState.value)
                        }
                        currentHoverRoom = room
                    }
                } else {
                    currentHoverRoom = null
                    hoverManager.hide()
                }
            }
        )

        // Overlay with current room and zone name
        Box(
            modifier = Modifier
                .padding(start = 8.dp, bottom = 8.dp)
                .align(Alignment.BottomStart)
                .background(
                    StyleManager.getStyle(currentColorStyle)
                        .getUiColor(UiColor.AdditionalWindowBackground)
                        .copy(alpha = 0.8f)
                )
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
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    curZoneState: MutableState<Zone?>,
    centerOnRoomId: Int?,
    topBarPixelHeight: Dp,
    // The canvas accepts a callback to report hover events up to its parent
    onRoomHover: (room: Room?, position: Offset) -> Unit
) {
    val dpi = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    val currentColorStyleName by settingsViewModel.currentColorStyleName.collectAsState()
    val currentColorStyle = StyleManager.getStyle(currentColorStyleName)

    BoxWithConstraints(modifier = modifier) {
        var scaleLogical by remember { mutableStateOf(0.25f) }

        // This teaches the animation system how to handle the Offset type.
        val panOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

        // effect responsible for panning after centerOnRoomId changes
        LaunchedEffect(centerOnRoomId, curZoneState.value) {
            val zone = curZoneState.value
            if (centerOnRoomId == null || zone == null) return@LaunchedEffect

            val rooms = zone.roomsList
            val targetRoom = rooms.find { it.id == centerOnRoomId }

            if (targetRoom != null && rooms.isNotEmpty()) {
                val minX = rooms.minOf { it.x }
                val minY = rooms.minOf { it.y }
                val maxX = rooms.maxOf { it.x }
                val maxY = rooms.maxOf { it.y }

                val baseRoomRadius = 50f
                val baseRoomSpacing = baseRoomRadius * 3
                val scaledRoomSpacing = baseRoomSpacing * scaleLogical * dpi

                val newPanTargetX = -((targetRoom.x - minX) - (maxX - minX) / 2f) * scaledRoomSpacing
                val newPanTargetY = -((targetRoom.y - minY) - (maxY - minY) / 2f) * scaledRoomSpacing

                coroutineScope.launch {
                    panOffset.animateTo(
                        targetValue = Offset(newPanTargetX, newPanTargetY),
                        animationSpec = tween(durationMillis = 150)
                    )
                }
            }
        }

        if (curZoneState.value == null) return@BoxWithConstraints
        val rooms = curZoneState.value!!.roomsList
        if (rooms.isEmpty()) {
            return@BoxWithConstraints
        }

        // Base values
        val baseRoomSize = 100f
        val baseRoomSpacing = baseRoomSize * 1.5f

        // Scaled values based on the zoom state
        val scaledRoomSize = baseRoomSize * scaleLogical * dpi
        val scaledRoomSpacing = baseRoomSpacing * scaleLogical * dpi

        // Find min coordinates for relative positioning
        val minX = rooms.minOf { it.x }
        val minY = rooms.minOf { it.y }
        val maxX = rooms.maxOf { it.x }
        val maxY = rooms.maxOf { it.y }

        val dragBarHeightInPixels: Float = with(LocalDensity.current) {
            topBarPixelHeight.toPx()
        }

        // This offset centers the entire drawing initially
        val centeringOffset = Offset(
            x = (constraints.maxWidth - (maxX - minX) * scaledRoomSpacing) / 2f,
            // constraints.maxHeight is the entire height of the window
            // but at the top, we have a top bar, by which we can drag the window. half of it needs to be subtracted
            y = (constraints.maxHeight - (maxY - minY) * scaledRoomSpacing) / 2f - (dragBarHeightInPixels/2)
        )

        // The final offset combines centering with user panning
        val totalOffset = centeringOffset + panOffset.value

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
                        // instead of "panOffset += dragAmount", animate to the new offset
                        coroutineScope.launch {
                            panOffset.snapTo(panOffset.value + dragAmount)
                        }
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
                    val mousePosition = event.changes.first().position
                    val halfRoomSize = scaledRoomSize / 2f

                    // hover logic
                    // Find the first room whose bounding box contains the mouse pointer
                    val roomUnderMouse = roomToOffsetMap.entries.find { (_, center) ->
                        mousePosition.x >= center.x - halfRoomSize &&
                                mousePosition.x <= center.x + halfRoomSize &&
                                mousePosition.y >= center.y - halfRoomSize &&
                                mousePosition.y <= center.y + halfRoomSize
                    }?.key // Get the Room object (the key) from the map entry
                    onRoomHover(roomUnderMouse, mousePosition)
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
            roomToOffsetMap.entries.forEach { (room, centerOffset) ->
                val cornerRadiusValue = scaledRoomSize * 0.15f // 15% of the size for the corner radius
                val roomTopLeft = Offset(
                    x = centerOffset.x - (scaledRoomSize / 2),
                    y = centerOffset.y - (scaledRoomSize / 2)
                )
                val roomSize = Size(scaledRoomSize, scaledRoomSize)
                val roomCornerRadius = CornerRadius(cornerRadiusValue, cornerRadiusValue)

                // start->end: from top-right corner to bottom-left corner
                val roomBrush = Brush.linearGradient(
                    colors = currentColorStyle.getUiColorList(UiColor.MapRoomVisited),
                    start = Offset(x = roomTopLeft.x + roomSize.width, y = roomTopLeft.y),
                    end = Offset(x = roomTopLeft.x, y = roomTopLeft.y + roomSize.height)
                )

                drawRoundRect(
                    // The draw function needs the top-left corner, not the center.
                    topLeft = roomTopLeft,
                    size = roomSize,
                    cornerRadius = roomCornerRadius,
                    style = Fill,
                    brush = roomBrush, // Use the brush here
                )

                // if the player is in the room, draw a stroke over it
                if (room.id == centerOnRoomId) {
                    val strokeWidth = 15f * scaleLogical // Make the stroke responsive to zoom
                    drawRoundRect(
                        color = currentColorStyle.getUiColor(UiColor.MapRoomStroke),
                        topLeft = roomTopLeft,
                        size = roomSize,
                        cornerRadius = roomCornerRadius,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }
    }
}

@Composable
fun MapRoomTooltip(room: Room, zone: Zone?) {
    Column(modifier = Modifier
        .padding(8.dp)
    ) {
        Text(room.name, color = Color.White)
        //if (zone != null) Text(zone.name, color = Color.White)
        Text("Description: ${room.description}", color = Color.White)
        Text("Room ID: ${room.id} (Coords: ${room.x}, ${room.y}, ${room.z})", color = Color.White)
        Text("Room Exits: ${room.exitsList}", color = Color.White)
    }
}