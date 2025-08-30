package ru.adan.silmaril.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.adan.silmaril.visual_styles.StyleManager
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.xml_schemas.Room
import ru.adan.silmaril.xml_schemas.Zone
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oshai.kotlinlogging.KLogger
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.SettingsManager
import kotlin.collections.get
import org.koin.compose.koinInject
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.map_highlight
import ru.adan.silmaril.generated.resources.warn
import ru.adan.silmaril.misc.doubleClickOrSingle
import ru.adan.silmaril.misc.toComposeColor
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.view.hovertooltips.LocalHoverManager
import ru.adan.silmaril.view.hovertooltips.MapHoverTooltip
import ru.adan.silmaril.viewmodel.MapViewModel
import ru.adan.silmaril.viewmodel.UnifiedMapsViewModel


@Composable
fun MapWindow(
    mapViewModel: MapViewModel,
    profileManager: ProfileManager,
    logger: KLogger
) {
    val mapModel: MapModel = koinInject()
    val settingsManager: SettingsManager = koinInject()
    val settings by settingsManager.settings.collectAsState()
    val currentColorStyle = settings.colorStyle

    val robotoFont = remember {FontManager.getFont("RobotoClassic")}

    var lastZone = -1000000 // -1 zone is reserved for roads
    var lastRoom = -1000000

    val curZoneState = remember { mutableStateOf(mapModel.getZone(lastZone)) }
    var curZoneRooms: Map<Int, Room> = mapModel.getRooms(lastZone)
    val curRoomState = remember { mutableStateOf(curZoneRooms[lastRoom]) }

    // State to hold the ID of the room to center on. This will be passed to the RoomsCanvas.
    var centerOnRoomId by remember { mutableStateOf<Int?>(null) }

    var currentHoverRoom: Room? = null

    val hoverManager = LocalHoverManager.current
    val ownerWindow = OwnerWindow.current
    var internalPadding by remember { mutableStateOf(Offset.Zero) }
    var tooltipOffset by remember { mutableStateOf(Offset.Zero) }
    val dpi = LocalDensity.current.density

    LaunchedEffect(mapViewModel) {
        mapViewModel.currentRoom.collect { roomMessage ->
            // Update the state with the new message to trigger recomposition
            if (roomMessage.zoneId != lastZone) {
                logger.debug { "Entered zone: ${roomMessage.zoneId}" }
                curZoneState.value = mapModel.getZone(roomMessage.zoneId)
                curZoneRooms = mapModel.getRooms(roomMessage.zoneId)
                mapModel.squashRooms(curZoneRooms, roomMessage.zoneId)
                lastZone = roomMessage.zoneId
            }
            // If the room has changed, update the state and trigger the centering
            if (roomMessage.roomId != lastRoom) {
                logger.debug { "Entered room: ${roomMessage.roomId}" }
                curRoomState.value = curZoneRooms[roomMessage.roomId]
                centerOnRoomId = roomMessage.roomId
                lastRoom = roomMessage.roomId
            }
            lastZone = roomMessage.zoneId
            lastRoom = roomMessage.roomId
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StyleManager.getStyle(currentColorStyle).getUiColor(UiColor.AdditionalWindowBackground))
            .onGloballyPositioned { layoutCoordinates -> internalPadding = layoutCoordinates.positionInWindow() }
    ) {
        RoomsCanvas(
            mapViewModel = mapViewModel,
            profileManager = profileManager,
            settingsManager = settingsManager,
            logger = logger,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            zoneState = curZoneState,
            centerOnRoomId = centerOnRoomId, // Pass the target ID to the canvas
            topBarPixelHeight = LocalTopBarHeight.current, // height of the bar by which we drag the window, provided
            onRoomHover = { room, position ->
                // This callback is executed inside RoomsCanvas whenever a hover event occurs.
                // It updates the state that is held here, in the parent.
                if (room == null) {
                    currentHoverRoom = null
                    hoverManager.hide()
                    return@RoomsCanvas
                }
                if (currentHoverRoom == room)
                    return@RoomsCanvas
                tooltipOffset = (position + internalPadding) / dpi
                hoverManager.show(
                    ownerWindow,
                    tooltipOffset,
                    500,
                    room.id,
                ) {
                    MapHoverTooltip(room, curZoneState.value, mapModel, StyleManager.getStyle(currentColorStyle))
                }
                currentHoverRoom = room
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
                    fontFamily = robotoFont,
                    color = Color.White,
                    modifier = Modifier.padding(6.dp, 4.dp, 6.dp, 1.dp)
                )

                // Zone name
                Text(
                    text = curZoneState.value?.fullName ?: "",
                    fontFamily = robotoFont,
                    color = Color.White,
                    modifier = Modifier.padding(6.dp, 2.dp, 6.dp, 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun RoomsCanvas(
    mapViewModel: MapViewModel,
    profileManager: ProfileManager,
    settingsManager: SettingsManager,
    logger: KLogger,
    modifier: Modifier = Modifier,
    zoneState: MutableState<Zone?>,
    centerOnRoomId: Int?,
    topBarPixelHeight: Dp,
    // The canvas accepts a callback to report hover events up to its parent
    onRoomHover: (room: Room?, position: Offset) -> Unit
) {
    val mapModel: MapModel = koinInject()
    val settings = settingsManager.settings.collectAsState()
    val dpi = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    val roomDataManager: RoomDataManager = koinInject()
    val currentColorStyle = StyleManager.getStyle(settings.value.colorStyle)
    val unifiedMapsViewModel: UnifiedMapsViewModel = koinInject()
    val textMeasurer = rememberTextMeasurer()
    val pathToHighlight by mapViewModel.pathToHighlight.collectAsState()
    val robotoFont = remember {FontManager.getFont("RobotoClassic")}

    val mapInfoByRoom by unifiedMapsViewModel.mapUpdatesForRooms
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    // Pencil icon: display it on top of rooms that have comments
    val commentKey: IconKey = remember { AllIconsKeys.General.Inline_edit }
    val commentIconPath = remember(commentKey, true) { commentKey.path(true) }
    val commentPainterProvider = rememberResourcePainterProvider(commentIconPath, commentKey.iconClass)
    val commentPainter by commentPainterProvider.getPainter()
    val commentDesiredSize = remember { Size(40f, 40f) }

    // Generic icon: display it on top of rooms that have a custom icon set
    val roomIconKey: IconKey = remember { AllIconsKeys.Status.FailedInProgress }
    val roomIconPath = remember(roomIconKey, true) { roomIconKey.path(true) }
    val roomIconPainterProvider = rememberResourcePainterProvider(roomIconPath, roomIconKey.iconClass)
    val roomIconPainter by roomIconPainterProvider.getPainter()
    val roomIconDesiredSize = remember { Size(70f, 70f) }

    // Warn icon: display it on top of rooms where groupmates are in-fight
    val roomWarnPainter = painterResource(Res.drawable.warn)
    val roomWarnDesiredSize = remember { Size(70f, 61f) }

    // Warn icon: display it on top of rooms where groupmates are in-fight
    val roomHighlightPainter = painterResource(Res.drawable.map_highlight)
    val roomHighlightDesiredSize = remember { Size(200f, 200f) }

    val lastMousePos = remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier) {
        var scaleLogical by remember { mutableStateOf(0.25f) }
        val panOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

        if (zoneState.value == null) return@BoxWithConstraints

        val roomsList = zoneState.value!!.roomsList
        if (roomsList.isEmpty()) {
            return@BoxWithConstraints
        }
        val roomsMap = zoneState.value!!.roomsList.associateBy { it.id }.toMap()

        val minX = roomsList.minOf { it.x }
        val minY = roomsList.minOf { it.y }
        val maxX = roomsList.maxOf { it.x }
        val maxY = roomsList.maxOf { it.y }

        // Effect responsible for centering the view on a specific room
        LaunchedEffect(centerOnRoomId, zoneState.value) {
            val zone = zoneState.value
            if (centerOnRoomId == null || zone == null) return@LaunchedEffect

            val targetRoom = roomsList.find { it.id == centerOnRoomId }

            if (targetRoom != null && roomsList.isNotEmpty()) {
                val baseRoomSpacing = 100f * 1.5f
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

        // Base values
        val baseRoomSize = 100f
        val baseRoomSpacing = baseRoomSize * 1.5f

        // Scaled values based on the zoom state
        val scaledRoomSize = baseRoomSize * scaleLogical * dpi
        val scaledRoomSpacing = baseRoomSpacing * scaleLogical * dpi

        val roomIconScaledSize = roomIconDesiredSize * scaleLogical * dpi
        val commentScaledSize = commentDesiredSize * scaleLogical * dpi
        val roomHighlightScaledSize = roomHighlightDesiredSize * scaleLogical * dpi

        val dragBarHeightInPixels: Float = with(LocalDensity.current) {
            topBarPixelHeight.toPx()
        }

        // This offset centers the entire drawing initially. It changes with zoom.
        val centeringOffset = Offset(
            x = (constraints.maxWidth - (maxX - minX) * scaledRoomSpacing) / 2f,
            y = (constraints.maxHeight - (maxY - minY) * scaledRoomSpacing) / 2f - (dragBarHeightInPixels / 2)
        )

        // The final total offset combines the dynamic centering with user panning
        val totalOffset = centeringOffset + panOffset.value

        // A map of room IDs to their final calculated screen positions
        val roomToOffsetMap = roomsMap.values.associate { room ->
            room.id to
                    Offset(
                        x = totalOffset.x + (room.x - minX) * scaledRoomSpacing,
                        y = totalOffset.y + (room.y - minY) * scaledRoomSpacing
                    )
        }

        val getRoomUnderCursor by rememberUpdatedState(newValue = { mousePosition: Offset ->
            val halfRoomSize = scaledRoomSize / 2f
            val checkBeyondRoomSize = halfRoomSize * 1.5f

            val roomUnderMouse = roomToOffsetMap.entries.find { (_, center) ->
                mousePosition.x >= center.x - checkBeyondRoomSize &&
                        mousePosition.x <= center.x + checkBeyondRoomSize &&
                        mousePosition.y >= center.y - checkBeyondRoomSize &&
                        mousePosition.y <= center.y + checkBeyondRoomSize
            }?.key
            roomUnderMouse
        })

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // We chain the pointer input modifiers. They are processed in order.
                // 1. Double left-click on room - move there
                // Use custom double click detection, because compose's native detection doesn't work very well - it misses quick double-clicks
                .doubleClickOrSingle(
                    onDoubleClick = { mousePos ->
                        val roomUnderMouse = getRoomUnderCursor(lastMousePos.value)
                        if (roomUnderMouse != null) {

                            // don't detect double clicks on hidden rooms
                            val isRoomVisited = roomDataManager.isRoomVisited(zoneState.value?.id ?: -100, roomUnderMouse)
                            val hideRoom = !isRoomVisited && !mapModel.areRoomsConnected(centerOnRoomId, roomUnderMouse)
                            if (hideRoom) return@doubleClickOrSingle
                            
                            profileManager.currentMainViewModel.value.treatUserInput("#path $roomUnderMouse")
                        }
                    },
                    onSingleClick = { }
                )
                // 2. Single right-click - open room menu
                .onClick(
                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                    onClick = {
                        val roomUnderMouse = getRoomUnderCursor(lastMousePos.value)
                        if (roomUnderMouse != null) {

                            // don't treat RMB clicks on hidden rooms
                            val isRoomVisited = roomDataManager.isRoomVisited(zoneState.value?.id ?: -100, roomUnderMouse)
                            val hideRoom = !isRoomVisited && !mapModel.areRoomsConnected(centerOnRoomId, roomUnderMouse)
                            if (hideRoom) return@onClick

                            println("RMB on room $roomUnderMouse")
                        }
                    }
                )
                // 3. Panning (drag)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            panOffset.snapTo(panOffset.value + dragAmount)
                        }
                    }
                }
                // 4. Zooming (scroll)
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.first()
                    val scrollDelta = change.scrollDelta.y
                    val mousePosition = change.position

                    val oldScale = scaleLogical
                    val zoomFactor = 1.2f
                    val newScale = if (scrollDelta < 0) {
                        (oldScale * zoomFactor).coerceIn(0.1f, 5f) // Zoom in
                    } else {
                        (oldScale / zoomFactor).coerceIn(0.1f, 5f) // Zoom out
                    }

                    if (newScale != oldScale) {
                        // This is the crucial part.
                        // 1. Calculate the total offset at the moment of the event.
                        // We must recalculate the centeringOffset with the old scale to get the true total offset.
                        val oldCenteringOffset = Offset(
                            x = (constraints.maxWidth - (maxX - minX) * baseRoomSpacing * oldScale * dpi) / 2f,
                            y = (constraints.maxHeight - (maxY - minY) * baseRoomSpacing * oldScale * dpi) / 2f - (dragBarHeightInPixels / 2)
                        )
                        val oldTotalOffset = oldCenteringOffset + panOffset.value

                        // 2. The vector from the top-left of the pannable content to the mouse pointer.
                        val mouseVector = mousePosition - oldTotalOffset

                        // 3. The new total offset is calculated to keep the mouseVector's endpoint stationary.
                        val newTotalOffset = mousePosition - mouseVector * (newScale / oldScale)

                        // 4. Calculate what the new centeringOffset will be after recomposition.
                        val newCenteringOffset = Offset(
                            x = (constraints.maxWidth - (maxX - minX) * baseRoomSpacing * newScale * dpi) / 2f,
                            y = (constraints.maxHeight - (maxY - minY) * baseRoomSpacing * newScale * dpi) / 2f - (dragBarHeightInPixels / 2)
                        )

                        // 5. From that, we can derive the new pan offset we need to set.
                        val newPan = newTotalOffset - newCenteringOffset

                        // Update states simultaneously
                        scaleLogical = newScale
                        coroutineScope.launch {
                            panOffset.snapTo(newPan)
                        }
                    }
                    change.consume()
                }
                // 5. Hovering (move)
                .onPointerEvent(PointerEventType.Move) { event ->
                    lastMousePos.value = event.changes.first().position
                    val halfRoomSize = scaledRoomSize / 2f

                    val roomUnderMouse = getRoomUnderCursor(lastMousePos.value)

                    // don't display hover for unvisited rooms
                    val isRoomVisited = roomDataManager.isRoomVisited(zoneState.value?.id ?: -100, roomUnderMouse ?: -1)
                    if (!isRoomVisited) {
                        onRoomHover(null, event.changes.first().position)
                        return@onPointerEvent
                    }

                    onRoomHover(roomsMap[roomUnderMouse], Offset(
                        roomToOffsetMap[roomUnderMouse]?.x?.plus(halfRoomSize)?:lastMousePos.value.x,
                        roomToOffsetMap[roomUnderMouse]?.y?.plus(halfRoomSize*2)?:lastMousePos.value.y)
                    )
                }
                // 6. Hover End (exit)
                .onPointerEvent(PointerEventType.Exit) { event ->
                    onRoomHover(null, event.changes.first().position)
                }
        ) {
            val zoneId = zoneState.value?.id ?: -1

            // Draw the connections (Lines) only for East/West/North/South exits
            val drawnConnections : Map<Int, MutableSet<Int>> = roomsMap.keys.associateWith { mutableSetOf() }
            roomsMap.values.forEach roomLoop@{ room ->
                val startOffset = roomToOffsetMap[room.id] ?: return@roomLoop
                room.exitsList.forEach exitLoop@{ exit ->
                    if (exit.direction == "Up" || exit.direction == "Down") {
                        // the "stairs" are drawn after the rooms, not here
                        return@exitLoop
                    }

                    // don't draw connections if one of the rooms is hidden

                    val isRoom1Visited = roomDataManager.isRoomVisited(mapModel.getZoneByRoomId(room.id)?.id ?: zoneId, room.id)
                    val hideRoom1 = !isRoom1Visited && !mapModel.areRoomsConnected(centerOnRoomId, room.id)
                    val isRoom2Visited = roomDataManager.isRoomVisited(mapModel.getZoneByRoomId(exit.roomId)?.id ?: zoneId, exit.roomId)
                    val hideRoom2 = !isRoom2Visited && !mapModel.areRoomsConnected(centerOnRoomId, exit.roomId)
                    if (hideRoom1 || hideRoom2) return@exitLoop

                    val connectionColor =
                        if (exit.roomId == centerOnRoomId || room.id == centerOnRoomId) Color.White
                        else Color.Gray

                    // avoid drawing the same connection twice, e.g. x->y and y->x
                    if (drawnConnections[room.id]?.contains(exit.roomId) != true && drawnConnections[exit.roomId]?.contains(room.id) != true) {
                        drawnConnections[room.id]?.add(exit.roomId)
                        if (roomsMap[exit.roomId] != null) {
                            val endOffset = roomToOffsetMap[exit.roomId] ?: return@exitLoop
                            drawLine(
                                color = connectionColor,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = Stroke.DefaultMiter * scaleLogical * dpi // Make line width scale slightly
                            )
                        } else {
                            // Here, we handle drawing a connection to a room that's not in the current zone.
                            // We'll draw a shorter line to indicate the direction of the exit.
                            val lineLength = scaledRoomSpacing / 1.5f // Make the line stop short of a hypothetical next room
                            val endOffset: Offset

                            // Calculate the end point of the line based on the exit direction
                            when (exit.direction) {
                                "North" -> endOffset = startOffset.copy(y = startOffset.y - lineLength)
                                "South" -> endOffset = startOffset.copy(y = startOffset.y + lineLength)
                                "East"  -> endOffset = startOffset.copy(x = startOffset.x + lineLength)
                                "West"  -> endOffset = startOffset.copy(x = startOffset.x - lineLength)
                                else -> return@exitLoop // If the direction is unknown, draw nothing
                            }

                            // Draw the ru.adan.silmaril.main part of the line
                            drawLine(
                                color = connectionColor,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = Stroke.DefaultMiter * scaleLogical * dpi
                            )

                            // Now, let's draw a small arrowhead at the end of the line
                            val arrowHeadSize = 20f * scaleLogical * dpi
                            val path = Path()
                            when (exit.direction) {
                                "North" -> {
                                    path.moveTo(endOffset.x, endOffset.y)
                                    path.lineTo(endOffset.x - arrowHeadSize, endOffset.y + arrowHeadSize)
                                    path.lineTo(endOffset.x + arrowHeadSize, endOffset.y + arrowHeadSize)
                                }
                                "South" -> {
                                    path.moveTo(endOffset.x, endOffset.y)
                                    path.lineTo(endOffset.x - arrowHeadSize, endOffset.y - arrowHeadSize)
                                    path.lineTo(endOffset.x + arrowHeadSize, endOffset.y - arrowHeadSize)
                                }
                                "East" -> {
                                    path.moveTo(endOffset.x, endOffset.y)
                                    path.lineTo(endOffset.x - arrowHeadSize, endOffset.y + arrowHeadSize)
                                    path.lineTo(endOffset.x - arrowHeadSize, endOffset.y - arrowHeadSize)
                                }
                                "West" -> {
                                    path.moveTo(endOffset.x, endOffset.y)
                                    path.lineTo(endOffset.x + arrowHeadSize, endOffset.y + arrowHeadSize)
                                    path.lineTo(endOffset.x + arrowHeadSize, endOffset.y - arrowHeadSize)
                                }
                            }
                            path.close()
                            drawPath(
                                path = path,
                                color = connectionColor
                            )
                        }
                    }
                }
            }

            // Draw the rooms (Squares with rounded corners)
            roomToOffsetMap.entries.forEach { (roomId, centerOffset) ->
                val isRoomVisited = roomDataManager.isRoomVisited(zoneId, roomId)

                // don't draw unvisited rooms, unless they're adjacent to the one we're standing in
                if (!isRoomVisited && !mapModel.areRoomsConnected(centerOnRoomId, roomId)) return@forEach

                val cornerRadiusValue = scaledRoomSize * 0.15f // 15% of the size for the corner radius
                val roomTopLeft = Offset(
                    x = centerOffset.x - (scaledRoomSize / 2),
                    y = centerOffset.y - (scaledRoomSize / 2)
                )
                val roomSize = Size(scaledRoomSize, scaledRoomSize)
                val roomCornerRadius = CornerRadius(cornerRadiusValue, cornerRadiusValue)

                // start->end: from top-right corner to bottom-left corner
                val roomBrush = Brush.linearGradient(
                    colors = currentColorStyle.getUiColorList(
                        if (isRoomVisited) UiColor.MapRoomVisited else UiColor.MapRoomUnvisited
                    ),
                    start = Offset(x = roomTopLeft.x + roomSize.width, y = roomTopLeft.y),
                    end = Offset(x = roomTopLeft.x, y = roomTopLeft.y + roomSize.height)
                )

                drawRoundRect(
                    // The draw function needs the top-left corner, not the center.
                    topLeft = roomTopLeft,
                    size = roomSize,
                    cornerRadius = roomCornerRadius,
                    style = Fill,
                    brush = roomBrush,
                    // Apply a tint if the room has a custom color. Works okay for most cases, except applying a red tint to a blue room (in classic black color style)
                    colorFilter =
                        if (roomDataManager.hasColor(roomId))
                            ColorFilter.tint(roomDataManager.getRoomCustomColor(roomId)!!.toComposeColor(), BlendMode.Softlight)
                        // if the room is on the pathfinding route
                        else if (pathToHighlight.contains(roomId)) ColorFilter.tint(Color.Green, BlendMode.Softlight)
                        else null
                )

                // Draw "has a note" icon if there's a note
                if (roomDataManager.hasComment(roomId)) {
                    translate(left = roomTopLeft.x, top = roomTopLeft.y) {
                        with(commentPainter) {
                            draw(
                                size = commentScaledSize,
                                colorFilter = ColorFilter.tint(currentColorStyle.getUiColor(UiColor.MapNeutralIcon))
                            )
                        }
                    }
                }

                // Draw the number of groupmates and enemies in this room
                val roomInfo = mapInfoByRoom[roomId]
                if (roomInfo != null) {
                    // there's a compose bug that crashes if drawText(topLeft is outside of Canvas bounds), so we translate instead
                    val baseStyle = TextStyle(
                        fontFamily = robotoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = (24 * scaleLogical * dpi).sp
                    )

                    val gmText = roomInfo.groupMates.toString()
                    val gmLayout = textMeasurer.measure(text = gmText, style = baseStyle)
                    val gmColor = if (roomInfo.groupMatesInFight) currentColorStyle.getUiColor(UiColor.MapWarningIcon)
                        else currentColorStyle.getUiColor(UiColor.InputFieldText)

                    translate(
                        left = roomTopLeft.x - roomSize.width * 0.33f,
                        top = roomTopLeft.y - roomSize.height * 0.2f
                    ) {
                        // Note: this overload takes a TextLayoutResult and a color override
                        drawText(
                            textLayoutResult = gmLayout,
                            color = gmColor,
                            topLeft = Offset.Zero
                        )
                    }

                    // Enemies
                    val enemiesText = roomInfo.monsters.toString()
                    val enemiesLayout = textMeasurer.measure(text = enemiesText, style = baseStyle)
                    val enemiesColor = if (roomInfo.groupMatesInFight) currentColorStyle.getUiColor(UiColor.MapWarningIcon)
                        else currentColorStyle.getUiColor(UiColor.InputFieldText)

                    translate(
                        left = roomTopLeft.x - roomSize.width * 0.33f,
                        top = roomTopLeft.y + roomSize.height * 0.61f
                    ) {
                        drawText(
                            textLayoutResult = enemiesLayout,
                            color = enemiesColor,
                            topLeft = Offset.Zero
                        )
                    }
                }

                // Draw a custom icon if there's an icon
                val customIcon = roomDataManager.getRoomCustomIcon(roomId)
                if (customIcon != null && (roomInfo == null || !roomInfo.groupMatesInFight || roomId == centerOnRoomId)) {
                    translate(left = roomTopLeft.x + scaledRoomSize / 2 - roomIconScaledSize.width / 2, top = roomTopLeft.y + scaledRoomSize / 2 - roomIconScaledSize.height / 2) {
                        with(roomIconPainter) {
                            draw(
                                size = roomIconDesiredSize * scaleLogical * dpi,
                                colorFilter = ColorFilter.tint(currentColorStyle.getUiColor(UiColor.MapNeutralIcon))
                            )
                        }
                    }
                }
                // Draw a warn icon if there's a groupmate in-fight
                else if ((roomId != centerOnRoomId) && roomInfo != null && roomInfo.groupMatesInFight){
                    // red glow
                    translate(
                        left = roomTopLeft.x + scaledRoomSize * 0.5f - roomHighlightScaledSize.width * 0.5f,
                        top = roomTopLeft.y + scaledRoomSize * 0.5f - roomHighlightScaledSize.height * 0.5f
                    ) {
                        with(roomHighlightPainter) {
                            draw(
                                size = roomHighlightDesiredSize * scaleLogical * dpi,
                            )
                        }
                    }
                    // warn icon
                    translate(
                        left = roomTopLeft.x + scaledRoomSize / 2 - roomIconScaledSize.width / 2,
                        top = roomTopLeft.y + scaledRoomSize / 2 - roomIconScaledSize.height / 2
                    ) {
                        with(roomWarnPainter) {
                            draw(
                                size = roomWarnDesiredSize * scaleLogical * dpi,
                                colorFilter = ColorFilter.tint(currentColorStyle.getUiColor(UiColor.MapWarningIcon))
                            )
                        }
                    }
                }

                // if the player is in the roomId, draw a white stroke over it
                // if a groupmate is in the roomId, draw a gray stroke over it
                if (roomId == centerOnRoomId || (roomInfo != null && !roomInfo.groupMatesInFight)) {
                    val strokeWidth = 15f * scaleLogical // Make the stroke responsive to zoom
                    drawRoundRect(
                        color = if (roomId == centerOnRoomId) currentColorStyle.getUiColor(UiColor.MapRoomStroke)
                                else currentColorStyle.getUiColor(UiColor.MapRoomStrokeSecondary),
                        topLeft = roomTopLeft,
                        size = roomSize,
                        cornerRadius = roomCornerRadius,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }

            // Draw staircases
            roomsMap.values.forEach roomLoop@{ room ->
                val startOffset = roomToOffsetMap[room.id] ?: return@roomLoop
                room.exitsList.forEach { exit ->
                    if (exit.direction == "Up" || exit.direction == "Down") {

                        // don't draw connections if one of the rooms is hidden
                        val isRoom1Visited = roomDataManager.isRoomVisited(mapModel.getZoneByRoomId(room.id)?.id ?: zoneId, room.id)
                        val hideRoom1 = !isRoom1Visited && !mapModel.areRoomsConnected(centerOnRoomId, room.id)
                        val isRoom2Visited = roomDataManager.isRoomVisited(mapModel.getZoneByRoomId(exit.roomId)?.id ?: zoneId, exit.roomId)
                        val hideRoom2 = !isRoom2Visited && !mapModel.areRoomsConnected(centerOnRoomId, exit.roomId)
                        if (hideRoom1 || hideRoom2) return@forEach

                        // We need the top-left corner of the room to position the stairs.
                        // `startOffset` is the center of the room, which we already have.
                        val roomTopLeft = Offset(
                            x = startOffset.x - (scaledRoomSize / 2),
                            y = startOffset.y - (scaledRoomSize / 2)
                        )

                        // --- Stairs Drawing Logic ---
                        val stairsStrokeWidth = Stroke.DefaultMiter * scaleLogical * dpi * 1.5f

                        // Define stairs dimensions relative to the room size for scalability.
                        val stairsWidth = scaledRoomSize * 0.3f  // The width of the staircase.
                        val stairsHeight = scaledRoomSize * 0.3f // The height of the staircase.
                        val padding = scaledRoomSize * 0.16f     // Padding from the room's corner.

                        // Determine the top-left corner of the stairs drawing area based on the exit direction.
                        val stairsTopLeft: Offset = if (exit.direction == "Up") {
                            Offset(
                                x = roomTopLeft.x + scaledRoomSize - stairsWidth + padding,
                                y = roomTopLeft.y - padding
                            )
                        } else { // This handles the "Down" case.
                            Offset(
                                x = roomTopLeft.x + scaledRoomSize - stairsWidth + padding,
                                y = roomTopLeft.y + scaledRoomSize - stairsHeight + padding
                            )
                        }

                        val connectionColor =
                            if (exit.roomId == centerOnRoomId || room.id == centerOnRoomId) Color.White
                            else Color.Gray

                        // Draw the two vertical lines (the sides of the stairs).
                        val leftVerticalStart = Offset(stairsTopLeft.x, stairsTopLeft.y - stairsHeight * 0.2f)
                        val leftVerticalEnd = Offset(stairsTopLeft.x, stairsTopLeft.y + stairsHeight * 1.2f)
                        drawLine(connectionColor, leftVerticalStart, leftVerticalEnd, stairsStrokeWidth)

                        val rightVerticalStart = Offset(stairsTopLeft.x + stairsWidth, stairsTopLeft.y - stairsHeight * 0.2f)
                        val rightVerticalEnd = Offset(stairsTopLeft.x + stairsWidth, stairsTopLeft.y + stairsHeight * 1.2f)
                        drawLine(connectionColor, rightVerticalStart, rightVerticalEnd, stairsStrokeWidth)

                        // Draw the three horizontal lines for the steps.
                        val stepCount = 3
                        for (i in 0 until stepCount) {
                            val stepY = stairsTopLeft.y + (i.toFloat() / (stepCount - 1)) * stairsHeight
                            val stepStart = Offset(stairsTopLeft.x, stepY)
                            val stepEnd = Offset(stairsTopLeft.x + stairsWidth, stepY)
                            drawLine(connectionColor, stepStart, stepEnd, stairsStrokeWidth)
                        }

                        // if the stairacases lead to another zone, additionally draw an arrow
                        if (roomsMap[exit.roomId] == null) {
                            val arrowHeadSize = 15f * scaleLogical * dpi
                            val path = Path()
                            when (exit.direction) {
                                "Up" -> {
                                    val endOffset = Offset(stairsTopLeft.x + stairsWidth/2, stairsTopLeft.y - arrowHeadSize * 1.75f)
                                    path.moveTo(endOffset.x, endOffset.y)
                                    path.lineTo(endOffset.x - arrowHeadSize, endOffset.y + arrowHeadSize)
                                    path.lineTo(endOffset.x + arrowHeadSize, endOffset.y + arrowHeadSize)
                                }
                                "Down" -> {
                                    val endOffset = Offset(stairsTopLeft.x + stairsWidth/2, stairsTopLeft.y + stairsHeight + arrowHeadSize * 1.75f)
                                    path.moveTo(endOffset.x, endOffset.y)
                                    path.lineTo(endOffset.x - arrowHeadSize, endOffset.y - arrowHeadSize)
                                    path.lineTo(endOffset.x + arrowHeadSize, endOffset.y - arrowHeadSize)
                                }
                            }
                            path.close()
                            drawPath(
                                path = path,
                                color = connectionColor
                            )
                        }
                    }
                }
            }
        }
    }
}