package ru.adan.silmaril.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.adan.silmaril.model.Creature
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.ui.ConnectionState
import ru.adan.silmaril.ui.ConnectionStatusIndicator
import ru.adan.silmaril.ui.SilmarilTheme
import ru.adan.silmaril.viewmodel.MainViewModel
import ru.adan.silmaril.viewmodel.MapViewModel
import ru.adan.silmaril.viewmodel.MapUpdate

@Composable
fun NavigationDrawer(
    scaffoldState: ScaffoldState,
    scope: CoroutineScope,
    connectionState: ConnectionState,
    serverName: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearOutput: () -> Unit,
    client: MudConnection?,
    mapViewModel: MapViewModel?,
    mapModel: MapModel?,
    roomDataManager: RoomDataManager?,
    mapInfoByRoom: Map<Int, MapUpdate>,
    mainViewModel: MainViewModel?,
    outputTextSize: Float,
    onOutputTextSizeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Silmaril MUD Client",
            color = SilmarilTheme.colors.textPrimary,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Divider(
            color = SilmarilTheme.colors.textSecondary.copy(alpha = 0.3f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Connection Status
        ConnectionStatusIndicator(
            state = connectionState,
            serverName = serverName,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Connection Controls - both buttons on same line, grey style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onConnect()
                    scope.launch { scaffoldState.drawerState.close() }
                },
                enabled = connectionState == ConnectionState.Disconnected,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF5a5a5a),
                    disabledBackgroundColor = Color(0xFF3a3a3a)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Connect", color = Color(0xFFe8e8e8))
            }

            Button(
                onClick = {
                    onDisconnect()
                    scope.launch { scaffoldState.drawerState.close() }
                },
                enabled = connectionState != ConnectionState.Disconnected,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFe94747), // Same red as HpBad
                    disabledBackgroundColor = Color(0xFF3a3a3a)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Disconnect", color = Color(0xFFe8e8e8))
            }
        }

        // Text size slider - grey to match desktop style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Текст: ${outputTextSize.toInt()}",
                color = SilmarilTheme.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(60.dp)
            )
            Slider(
                value = outputTextSize,
                onValueChange = onOutputTextSizeChange,
                valueRange = 8f..20f,
                steps = 11, // 8,9,10,11,12,13,14,15,16,17,18,19,20
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF888888),
                    activeTrackColor = Color(0xFF666666),
                    inactiveTrackColor = Color(0xFF444444)
                )
            )
        }

        // Map Section
        if (mapModel != null && roomDataManager != null) {
            Divider(
                color = SilmarilTheme.colors.textSecondary.copy(alpha = 0.3f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "Карта",
                color = SilmarilTheme.colors.textPrimary,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Square map container - takes available width and same height
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                val mapSize = minOf(maxWidth, maxHeight, 300.dp)

                Box(
                    modifier = Modifier
                        .size(mapSize)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MapColors.mapBackground)
                ) {
                    MapView(
                        mapViewModel = mapViewModel,
                        mapModel = mapModel,
                        roomDataManager = roomDataManager,
                        mapInfoByRoom = mapInfoByRoom,
                        onPathCommand = { roomId ->
                            mainViewModel?.treatUserInput("#path $roomId")
                            scope.launch { scaffoldState.drawerState.close() }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Help text for map
            Text(
                text = "Двойной тап - идти к клетке",
                color = SilmarilTheme.colors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun DrawerMenuButton(scaffoldState: ScaffoldState, scope: CoroutineScope) {
    IconButton(
        onClick = {
            scope.launch {
                if (scaffoldState.drawerState.isOpen) {
                    scaffoldState.drawerState.close()
                } else {
                    scaffoldState.drawerState.open()
                }
            }
        }
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Menu",
            tint = SilmarilTheme.colors.textPrimary
        )
    }
}

/**
 * Compact drawer menu button with smaller touch area
 */
@Composable
fun CompactDrawerMenuButton(scaffoldState: ScaffoldState, scope: CoroutineScope) {
    IconButton(
        onClick = {
            scope.launch {
                if (scaffoldState.drawerState.isOpen) {
                    scaffoldState.drawerState.close()
                } else {
                    scaffoldState.drawerState.open()
                }
            }
        },
        modifier = Modifier.size(28.dp) // Smaller touch area
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Menu",
            tint = SilmarilTheme.colors.textPrimary,
            modifier = Modifier.size(20.dp) // Same icon size
        )
    }
}
