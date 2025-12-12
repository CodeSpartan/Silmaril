package ru.adan.silmaril.view

import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Simple drawer menu button that calls an onClick lambda.
 * Can be used for any drawer (left or right).
 */
@Composable
fun DrawerMenuButton(
    onClick: () -> Unit,
    icon: ImageVector = Icons.Default.Menu,
    contentDescription: String = "Menu",
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.then(Modifier.size(28.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
