package view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogWindow
import model.SettingsManager
import java.awt.Dimension
import java.awt.Point
import java.awt.Toolkit
import kotlin.math.roundToInt


/**
 * The interface for the global hover service.
 * Any component can get this from the CompositionLocal and use it.
 */
interface HoverManager {
    fun show(relativePosition: Offset, dimension: Dimension, content: @Composable () -> Unit)
    fun hide()
}

/**
 * The CompositionLocal key. Components will use this to access the manager.
 */
val LocalHoverManager = staticCompositionLocalOf<HoverManager> {
    // Provide a default implementation that crashes if the provider is not set.
    error("No HoverManager provided")
}

@Composable
fun FloatingTooltipContainer(
    show: MutableState<Boolean>,
    owner: ComposeWindow,
    position: Point,
    dimension: Dimension,
    content: @Composable () -> Unit
) {
    if (show.value) {
        DialogWindow(
            create = {
                ComposeDialog(owner = owner).apply {
                    size = dimension
                    isFocusable = false // Tooltips shouldn't steal focus
                    isUndecorated = true
                    location = position
                }
            },
            dispose = ComposeDialog::dispose,
        ) {
            Column(Modifier.background(Color.Black)) {
                content()
            }
        }
    }
}

@Composable
fun HoverManagerProvider(
    owner: ComposeWindow,
    settings: SettingsManager,
    windowName: String,
    content: @Composable () -> Unit
) {
    // State for the floating window
    val showTooltip = remember { mutableStateOf(false) }
    var tooltipContent by remember { mutableStateOf<@Composable () -> Unit>({}) }
    var tooltipPosition by remember { mutableStateOf(Point(0, 0)) }
    var tooltipDimension by remember { mutableStateOf(Dimension(0, 0)) }
    var windowGlobalPosition by remember { mutableStateOf(Point(0, 0)) }
    val screenSize by remember { mutableStateOf(Toolkit.getDefaultToolkit().screenSize) }

    val manager = remember {
        object : HoverManager {
            override fun show(relativePosition: Offset, dimension: Dimension, content: @Composable () -> Unit) {
                tooltipDimension = dimension
                // I couldn't find a better way. If the tooltip is for the main window, we'll use its location
                // If it's for a floating window, we'll get its position from settings (ugly, but works)
                windowGlobalPosition = if (windowName == "") {
                    owner.location // same as owner.x, owner.y ?
                } else {
                    settings.getFloatingWindowState(windowName).windowPosition
                }
                tooltipPosition = Point(
                    windowGlobalPosition.x + relativePosition.x.roundToInt(),
                    windowGlobalPosition.y + relativePosition.y.roundToInt())
                tooltipContent = content
                showTooltip.value = true
            }

            override fun hide() {
                showTooltip.value = false
            }
        }
    }

    CompositionLocalProvider(LocalHoverManager provides manager) {
        content()
    }

    // A tooltip can overflow beyond screen borders. We fix it here.
    fun getIdealPosition() : Point {
        val tinyOffset = Offset(15f, 15f)
        val desiredPosition = Point(tooltipPosition.x + tinyOffset.x.toInt(),tooltipPosition.y + tinyOffset.y.toInt())

        val finalX = when {
            // Check if it goes off the right edge
            desiredPosition.x + tooltipDimension.width > screenSize.width ->
                desiredPosition.x - (desiredPosition.x + tooltipDimension.width - screenSize.width) - (tinyOffset.x.toInt() * 2)
            else -> desiredPosition.x
        }

        val finalY = when {
            // Check if it goes off the bottom edge
            desiredPosition.y + tooltipDimension.height > screenSize.height ->
                desiredPosition.y - (desiredPosition.y + tooltipDimension.height - screenSize.height) - (tinyOffset.y.toInt() * 2)
            else -> desiredPosition.y
        }

        return Point(finalX, finalY)
    }

    FloatingTooltipContainer(
        show = showTooltip,
        owner = owner,
        // Give a bit of offset to the tooltip relative to the mouse cursor
        position = getIdealPosition(),
        dimension = tooltipDimension,
        content = {
            tooltipContent()
        }
    )
}