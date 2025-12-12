package ru.adan.silmaril.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.ui.SilmarilTheme
import ru.adan.silmaril.viewmodel.MapViewModel
import ru.adan.silmaril.viewmodel.MapUpdate
import ru.adan.silmaril.xml_schemas.Room
import ru.adan.silmaril.xml_schemas.Zone

/**
 * Map view composable for rendering the MUD map in a square container.
 * Simplified version of the desktop MapWindow without hover tooltips or right-click menus.
 * Supports:
 * - Pan gestures (drag)
 * - Pinch-to-zoom
 * - Double-tap on room to navigate via #path command
 */
@Composable
fun MapView(
    mapViewModel: MapViewModel?,
    mapModel: MapModel,
    roomDataManager: RoomDataManager,
    mapInfoByRoom: Map<Int, MapUpdate>,
    onPathCommand: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mapViewModel == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MapColors.mapBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Карта недоступна",
                color = SilmarilTheme.colors.textSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    val currentRoom by mapViewModel.currentRoom.collectAsState()
    val pathToHighlight by mapViewModel.pathToHighlight.collectAsState()

    // Track current zone and rooms
    var currentZone by remember { mutableStateOf<Zone?>(null) }
    var currentRooms by remember { mutableStateOf<Map<Int, Room>>(emptyMap()) }

    // Update zone when room changes
    LaunchedEffect(currentRoom.zoneId) {
        if (currentRoom.zoneId >= 0) {
            currentZone = mapModel.getZone(currentRoom.zoneId)
            currentRooms = mapModel.getRooms(currentRoom.zoneId)
            if (currentRooms.isNotEmpty()) {
                mapModel.squashRooms(currentRooms, currentRoom.zoneId)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapColors.mapBackground)
    ) {
        if (currentZone == null || currentRooms.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentRoom.zoneId < 0) "Не в зоне" else "Загрузка...",
                    color = SilmarilTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            MapCanvas(
                zone = currentZone!!,
                rooms = currentRooms,
                currentRoomId = currentRoom.roomId,
                pathToHighlight = pathToHighlight,
                mapInfoByRoom = mapInfoByRoom,
                mapModel = mapModel,
                roomDataManager = roomDataManager,
                onRoomDoubleTap = onPathCommand
            )

            // Overlay with current room and zone name
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.BottomStart)
                    .background(MapColors.mapBackground.copy(alpha = 0.85f))
                    .padding(4.dp)
            ) {
                Column {
                    currentRooms[currentRoom.roomId]?.name?.let { roomName ->
                        Text(
                            text = roomName,
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = currentZone?.fullName ?: "",
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MapCanvas(
    zone: Zone,
    rooms: Map<Int, Room>,
    currentRoomId: Int,
    pathToHighlight: Set<Int>,
    mapInfoByRoom: Map<Int, MapUpdate>,
    mapModel: MapModel,
    roomDataManager: RoomDataManager,
    onRoomDoubleTap: (Int) -> Unit
) {
    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()

    // Pan offset state
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Zoom state - default is more zoomed out
    var zoomScale by remember { mutableFloatStateOf(0.175f) }
    val minZoom = 0.08f
    val maxZoom = 1.0f

    // Effective scale combines user zoom with density
    val scaleLogical = zoomScale

    // Calculate bounds
    val roomsList = zone.roomsList
    if (roomsList.isEmpty()) return

    val minX = roomsList.minOf { it.x }
    val minY = roomsList.minOf { it.y }
    val maxX = roomsList.maxOf { it.x }
    val maxY = roomsList.maxOf { it.y }

    // Base values
    val baseRoomSize = 100f
    val baseRoomSpacing = baseRoomSize * 1.5f

    // Scaled values
    val scaledRoomSize = baseRoomSize * scaleLogical * density
    val scaledRoomSpacing = baseRoomSpacing * scaleLogical * density

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        // Center the map initially on the current room
        val currentRoomObj = rooms[currentRoomId]

        // Auto-center on current room when it changes
        LaunchedEffect(currentRoomId) {
            if (currentRoomObj != null) {
                val targetX = -((currentRoomObj.x - minX) - (maxX - minX) / 2f) * scaledRoomSpacing
                val targetY = -((currentRoomObj.y - minY) - (maxY - minY) / 2f) * scaledRoomSpacing
                panOffset = Offset(targetX, targetY)
            }
        }

        // Centering offset
        val centeringOffset = Offset(
            x = (canvasWidth - (maxX - minX) * scaledRoomSpacing) / 2f,
            y = (canvasHeight - (maxY - minY) * scaledRoomSpacing) / 2f
        )

        val totalOffset = centeringOffset + panOffset

        // Room positions map - recalculated on each recomposition when zoom/pan changes
        val roomToOffsetMap = remember(totalOffset, scaledRoomSpacing, rooms) {
            rooms.values.associate { room ->
                room.id to Offset(
                    x = totalOffset.x + (room.x - minX) * scaledRoomSpacing,
                    y = totalOffset.y + (room.y - minY) * scaledRoomSpacing
                )
            }
        }

        // Use rememberUpdatedState to ensure the lookup function always uses current values
        // This is critical because pointerInput captures the lambda at creation time,
        // but rememberUpdatedState ensures it accesses the latest values when invoked
        val getRoomUnderPosition by rememberUpdatedState { position: Offset ->
            val halfRoomSize = scaledRoomSize / 2f
            val checkBeyond = halfRoomSize * 1.5f

            roomToOffsetMap.entries.find { (_, center) ->
                position.x >= center.x - checkBeyond &&
                        position.x <= center.x + checkBeyond &&
                        position.y >= center.y - checkBeyond &&
                        position.y <= center.y + checkBeyond
            }?.key
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Apply zoom with limits
                        val newZoom = (zoomScale * zoom).coerceIn(minZoom, maxZoom)

                        // Adjust pan to zoom around the centroid
                        if (newZoom != zoomScale) {
                            val zoomDelta = newZoom / zoomScale
                            panOffset = (panOffset + centroid) * zoomDelta - centroid
                        }

                        zoomScale = newZoom
                        panOffset += pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { position ->
                            val roomUnderTap = getRoomUnderPosition(position)
                            if (roomUnderTap != null) {
                                // don't detect double taps on hidden rooms
                                val isRoomVisited = roomDataManager.isRoomVisited(zone.id, roomUnderTap)
                                val hideRoom = !isRoomVisited && !mapModel.areRoomsConnected(currentRoomId, roomUnderTap)
                                if (hideRoom) {
                                    return@detectTapGestures
                                }

                                onRoomDoubleTap(roomUnderTap)
                            }
                        }
                    )
                }
        ) {
            val zoneId = zone.id

            // Track drawn connections to avoid duplicates
            val drawnConnections: MutableMap<Int, MutableSet<Int>> = mutableMapOf()

            // Draw connections first (under rooms)
            rooms.values.forEach { room ->
                val startOffset = roomToOffsetMap[room.id] ?: return@forEach
                drawnConnections.getOrPut(room.id) { mutableSetOf() }

                room.exitsList.forEach exitLoop@{ exit ->
                    if (exit.direction == "Up" || exit.direction == "Down") return@exitLoop

                    // don't draw connections if one of the rooms is hidden
                    val isRoom1Visited = roomDataManager.isRoomVisited(zoneId, room.id)
                    val hideRoom1 = !isRoom1Visited && !mapModel.areRoomsConnected(currentRoomId, room.id)
                    val isRoom2Visited = roomDataManager.isRoomVisited(zoneId, exit.roomId)
                    val hideRoom2 = !isRoom2Visited && !mapModel.areRoomsConnected(currentRoomId, exit.roomId)
                    if (hideRoom1 || hideRoom2) return@exitLoop

                    val connectionColor = when {
                        exit.roomId == currentRoomId || room.id == currentRoomId -> MapColors.connectionCurrent
                        pathToHighlight.contains(room.id) && pathToHighlight.contains(exit.roomId) -> MapColors.connectionPath
                        else -> MapColors.connectionNormal
                    }

                    // Avoid drawing same connection twice
                    if (drawnConnections[room.id]?.contains(exit.roomId) != true &&
                        drawnConnections[exit.roomId]?.contains(room.id) != true
                    ) {
                        drawnConnections[room.id]?.add(exit.roomId)

                        if (rooms[exit.roomId] != null) {
                            val endOffset = roomToOffsetMap[exit.roomId] ?: return@exitLoop
                            drawLine(
                                color = connectionColor,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = Stroke.DefaultMiter * scaleLogical * density
                            )
                        } else {
                            // Inter-zone exit - draw shorter line with arrow
                            val lineLength = scaledRoomSpacing / 1.5f
                            val endOffset = when (exit.direction) {
                                "North" -> startOffset.copy(y = startOffset.y - lineLength)
                                "South" -> startOffset.copy(y = startOffset.y + lineLength)
                                "East" -> startOffset.copy(x = startOffset.x + lineLength)
                                "West" -> startOffset.copy(x = startOffset.x - lineLength)
                                else -> return@exitLoop
                            }

                            drawLine(
                                color = connectionColor,
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = Stroke.DefaultMiter * scaleLogical * density
                            )

                            // Arrow head
                            val arrowHeadSize = 15f * scaleLogical * density
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
                            drawPath(path = path, color = connectionColor)
                        }
                    }
                }
            }

            // Draw rooms
            roomToOffsetMap.forEach { (roomId, centerOffset) ->
                val isRoomVisited = roomDataManager.isRoomVisited(zoneId, roomId)

                // don't draw unvisited rooms, unless they're adjacent to the one we're standing in
                if (!isRoomVisited && !mapModel.areRoomsConnected(currentRoomId, roomId)) return@forEach

                val cornerRadiusValue = scaledRoomSize * 0.15f
                val roomTopLeft = Offset(
                    x = centerOffset.x - (scaledRoomSize / 2),
                    y = centerOffset.y - (scaledRoomSize / 2)
                )
                val roomSize = Size(scaledRoomSize, scaledRoomSize)
                val roomCornerRadius = CornerRadius(cornerRadiusValue, cornerRadiusValue)

                // Room gradient brush
                val roomBrush = if (isRoomVisited) {
                    Brush.linearGradient(
                        colors = listOf(
                            MapColors.roomVisitedStart,
                            MapColors.roomVisitedEnd,
                            MapColors.roomVisitedEnd.copy(alpha = 0.9f),
                            MapColors.roomVisitedEnd
                        ),
                        start = Offset(roomTopLeft.x + roomSize.width, roomTopLeft.y),
                        end = Offset(roomTopLeft.x, roomTopLeft.y + roomSize.height)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(MapColors.roomUnvisited, MapColors.roomUnvisited),
                        start = Offset(roomTopLeft.x + roomSize.width, roomTopLeft.y),
                        end = Offset(roomTopLeft.x, roomTopLeft.y + roomSize.height)
                    )
                }

                // Color filter for custom color or path highlight
                val colorFilter = when {
                    roomDataManager.hasColor(roomId) -> {
                        val customColor = roomDataManager.getRoomCustomColor(roomId)
                        if (customColor != null) {
                            ColorFilter.tint(MapColors.getRoomColorTint(customColor), BlendMode.Softlight)
                        } else null
                    }

                    pathToHighlight.contains(roomId) -> ColorFilter.tint(Color.Green, BlendMode.Softlight)
                    else -> null
                }

                drawRoundRect(
                    topLeft = roomTopLeft,
                    size = roomSize,
                    cornerRadius = roomCornerRadius,
                    style = Fill,
                    brush = roomBrush,
                    colorFilter = colorFilter
                )

                // Draw groupmate/monster counts if available
                val roomInfo = mapInfoByRoom[roomId]
                if (roomInfo != null) {
                    val baseStyle = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (36 * scaleLogical).sp
                    )

                    // Groupmates count (top)
                    if (roomInfo.groupMates > 0) {
                        val gmText = roomInfo.groupMates.toString()
                        val gmLayout = textMeasurer.measure(text = gmText, style = baseStyle)
                        val gmColor = if (roomInfo.groupMatesInFight) MapColors.warningIcon else MapColors.inputFieldText

                        drawText(
                            textLayoutResult = gmLayout,
                            color = gmColor,
                            topLeft = Offset(
                                roomTopLeft.x - roomSize.width * 0.33f,
                                roomTopLeft.y - roomSize.height * 0.2f
                            )
                        )
                    }

                    // Monsters count (bottom)
                    if (roomInfo.monsters > 0) {
                        val enemiesText = roomInfo.monsters.toString()
                        val enemiesLayout = textMeasurer.measure(text = enemiesText, style = baseStyle)
                        val enemiesColor = if (roomInfo.groupMatesInFight) MapColors.warningIcon else MapColors.inputFieldText

                        drawText(
                            textLayoutResult = enemiesLayout,
                            color = enemiesColor,
                            topLeft = Offset(
                                roomTopLeft.x - roomSize.width * 0.33f,
                                roomTopLeft.y + roomSize.height * 0.61f
                            )
                        )
                    }
                }

                // Draw stroke for current room or room with groupmates
                if (roomId == currentRoomId || (roomInfo != null && !roomInfo.groupMatesInFight)) {
                    val strokeWidth = 5f * scaleLogical * density
                    drawRoundRect(
                        color = if (roomId == currentRoomId) MapColors.roomStroke else MapColors.roomStrokeSecondary,
                        topLeft = roomTopLeft,
                        size = roomSize,
                        cornerRadius = roomCornerRadius,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }

            // Draw staircases (Up/Down exits)
            rooms.values.forEach { room ->
                val startOffset = roomToOffsetMap[room.id] ?: return@forEach
                room.exitsList.forEach { exit ->
                    if (exit.direction == "Up" || exit.direction == "Down") {

                        // don't draw staircases if one of the rooms is hidden
                        val isRoom1Visited = roomDataManager.isRoomVisited(zoneId, room.id)
                        val hideRoom1 = !isRoom1Visited && !mapModel.areRoomsConnected(currentRoomId, room.id)
                        val isRoom2Visited = roomDataManager.isRoomVisited(zoneId, exit.roomId)
                        val hideRoom2 = !isRoom2Visited && !mapModel.areRoomsConnected(currentRoomId, exit.roomId)
                        if (hideRoom1 || hideRoom2) return@forEach

                        val roomTopLeft = Offset(
                            x = startOffset.x - (scaledRoomSize / 2),
                            y = startOffset.y - (scaledRoomSize / 2)
                        )

                        val stairsStrokeWidth = Stroke.DefaultMiter * scaleLogical * density * 1.5f
                        val stairsWidth = scaledRoomSize * 0.3f
                        val stairsHeight = scaledRoomSize * 0.3f
                        val padding = scaledRoomSize * 0.16f

                        val stairsTopLeft = if (exit.direction == "Up") {
                            Offset(
                                x = roomTopLeft.x + scaledRoomSize - stairsWidth + padding,
                                y = roomTopLeft.y - padding
                            )
                        } else {
                            Offset(
                                x = roomTopLeft.x + scaledRoomSize - stairsWidth + padding,
                                y = roomTopLeft.y + scaledRoomSize - stairsHeight + padding
                            )
                        }

                        val connectionColor = when {
                            exit.roomId == currentRoomId || room.id == currentRoomId -> MapColors.connectionCurrent
                            pathToHighlight.contains(room.id) && pathToHighlight.contains(exit.roomId) -> MapColors.connectionPath
                            else -> MapColors.connectionNormal
                        }

                        // Draw vertical lines
                        val leftVerticalStart = Offset(stairsTopLeft.x, stairsTopLeft.y - stairsHeight * 0.2f)
                        val leftVerticalEnd = Offset(stairsTopLeft.x, stairsTopLeft.y + stairsHeight * 1.2f)
                        drawLine(connectionColor, leftVerticalStart, leftVerticalEnd, stairsStrokeWidth)

                        val rightVerticalStart = Offset(stairsTopLeft.x + stairsWidth, stairsTopLeft.y - stairsHeight * 0.2f)
                        val rightVerticalEnd = Offset(stairsTopLeft.x + stairsWidth, stairsTopLeft.y + stairsHeight * 1.2f)
                        drawLine(connectionColor, rightVerticalStart, rightVerticalEnd, stairsStrokeWidth)

                        // Draw horizontal step lines
                        val stepCount = 3
                        for (i in 0 until stepCount) {
                            val stepY = stairsTopLeft.y + (i.toFloat() / (stepCount - 1)) * stairsHeight
                            val stepStart = Offset(stairsTopLeft.x, stepY)
                            val stepEnd = Offset(stairsTopLeft.x + stairsWidth, stepY)
                            drawLine(connectionColor, stepStart, stepEnd, stairsStrokeWidth)
                        }

                        // Arrow for inter-zone exits
                        if (rooms[exit.roomId] == null) {
                            val arrowHeadSize = 12f * scaleLogical * density
                            val path = Path()
                            when (exit.direction) {
                                "Up" -> {
                                    val endOffset = Offset(stairsTopLeft.x + stairsWidth / 2, stairsTopLeft.y - arrowHeadSize * 1.75f)
                                    path.moveTo(endOffset.x, endOffset.y)
                                    path.lineTo(endOffset.x - arrowHeadSize, endOffset.y + arrowHeadSize)
                                    path.lineTo(endOffset.x + arrowHeadSize, endOffset.y + arrowHeadSize)
                                }

                                "Down" -> {
                                    val endOffset = Offset(stairsTopLeft.x + stairsWidth / 2, stairsTopLeft.y + stairsHeight + arrowHeadSize * 1.75f)
                                    path.moveTo(endOffset.x, endOffset.y)
                                    path.lineTo(endOffset.x - arrowHeadSize, endOffset.y - arrowHeadSize)
                                    path.lineTo(endOffset.x + arrowHeadSize, endOffset.y - arrowHeadSize)
                                }
                            }
                            path.close()
                            drawPath(path = path, color = connectionColor)
                        }
                    }
                }
            }
        }
    }
}
