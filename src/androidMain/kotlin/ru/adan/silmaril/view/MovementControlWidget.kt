package ru.adan.silmaril.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Constants for styling
private val ButtonSize = 60.dp
private val ButtonSpacing = 1.dp
private val WidgetBackground = Color.Black.copy(alpha = 0.0f)
private val ButtonBackground = Color(0xFF4a6b8a).copy(alpha = 0.33f) // Blue-grey, semi-transparent
private val IconTextAlpha = 0.5f // Transparency for icons and text

/**
 * Translucent 3×3 movement control widget for Android MUD client.
 * Positioned in upper-left corner, sends movement commands to MUD server.
 *
 * Button layout:
 * [btn1]  [North ↑]  [up ⬆]
 * [West ←] [scan 👁] [East →]
 * [btn2]  [South ↓] [down ⬇]
 *
 * @param onCommand Callback to send command to MUD server
 * @param modifier Modifier for positioning and styling
 */
@Composable
fun MovementControlWidget(
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(ButtonSize * 3 + ButtonSpacing * 2) // 3 buttons + 2 gaps
            .clip(RoundedCornerShape(12.dp))
            .background(WidgetBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ButtonSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: btn1, north, up
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonSpacing, Alignment.CenterHorizontally)
            ) {
                MovementButton(
                    label = "B1",
                    onClick = { onCommand("btn1") }
                )
                MovementButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = "North",
                    onClick = { onCommand("north") }
                )
                MovementButton(
                    label = "⬆",
                    onClick = { onCommand("up") }
                )
            }

            // Row 2: west, scan, east
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonSpacing, Alignment.CenterHorizontally)
            ) {
                MovementButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "West",
                    onClick = { onCommand("west") }
                )
                MovementButton(
                    label = "👁",
                    onClick = { onCommand("scan") }
                )
                MovementButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "East",
                    onClick = { onCommand("east") }
                )
            }

            // Row 3: btn2, south, down
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonSpacing, Alignment.CenterHorizontally)
            ) {
                MovementButton(
                    label = "B2",
                    onClick = { onCommand("btn2") }
                )
                MovementButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = "South",
                    onClick = { onCommand("south") }
                )
                MovementButton(
                    label = "⬇",
                    onClick = { onCommand("down") }
                )
            }
        }
    }
}

/**
 * Individual movement button with either icon or text label.
 * Supports Material Icons for directional arrows and Unicode text for other buttons.
 *
 * @param icon Optional Material Icon (for N/S/E/W directions)
 * @param label Optional text label (for btn1, btn2, up, down, scan)
 * @param contentDescription Accessibility description for icon buttons
 * @param onClick Callback when button is pressed
 */
@Composable
private fun MovementButton(
    icon: ImageVector? = null,
    label: String? = null,
    contentDescription: String? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(ButtonSize),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = ButtonBackground),
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = IconTextAlpha),
                modifier = Modifier.size(24.dp)
            )
        } else if (label != null) {
            Text(
                text = label,
                color = Color.White.copy(alpha = IconTextAlpha),
                fontSize = if (label.length <= 2) 16.sp else 10.sp
            )
        }
    }
}
