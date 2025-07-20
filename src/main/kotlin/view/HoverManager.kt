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
import java.awt.Dimension
import java.awt.Point
import java.awt.Toolkit
import kotlin.math.roundToInt
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window

/**
 * The interface for the global hover service.
 * Any component can get this from the CompositionLocal and use it.
 */
interface HoverManager {
    fun show(ownerWindow: Window?, relativePosition: Offset, dimension: Dimension, content: @Composable () -> Unit)
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
                    isAutoRequestFocus = false // otherwise it still gets focused
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
    mainWindow: ComposeWindow,
    content: @Composable () -> Unit
) {
    // State for the floating window
    val showTooltip = remember { mutableStateOf(false) }
    var tooltipContent by remember { mutableStateOf<@Composable () -> Unit>({}) }
    var tooltipPosition by remember { mutableStateOf(Point(0, 0)) }
    var tooltipDimension by remember { mutableStateOf(Dimension(0, 0)) }
    // by default, assume main window owns everything, but later show() will potentially provide another owner
    var tooltipParentWindow by remember { mutableStateOf<Window>(mainWindow) }

    val manager = remember {
        object : HoverManager {
            override fun show(ownerWindow: Window?, relativePosition: Offset, dimension: Dimension, content: @Composable () -> Unit) {
                tooltipDimension = dimension
                if (ownerWindow != null)
                    tooltipParentWindow = ownerWindow
                tooltipPosition = Point(
                    tooltipParentWindow.location.x + relativePosition.x.roundToInt(),
                    tooltipParentWindow.location.y + relativePosition.y.roundToInt())
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
    // Then we give a bit of offset to the tooltip relative to the mouse cursor
    fun getIdealPosition() : Point {
        val tinyOffset = Offset(15f, 15f)
        val desiredPosition = Point(tooltipPosition.x + tinyOffset.x.toInt(),tooltipPosition.y + tinyOffset.y.toInt())
        val screenBounds = getScreenBoundsForWindow(tooltipParentWindow)

        val finalX = when {
            // Check if it goes off the right edge
            desiredPosition.x + tooltipDimension.width > screenBounds.x + screenBounds.width ->
                desiredPosition.x - (desiredPosition.x + tooltipDimension.width - (screenBounds.x + screenBounds.width)) - (tinyOffset.x.toInt() * 2)
            else -> desiredPosition.x
        }

        val finalY = when {
            // Check if it goes off the bottom edge
            desiredPosition.y + tooltipDimension.height > screenBounds.y + screenBounds.height ->
                desiredPosition.y - (desiredPosition.y + tooltipDimension.height - (screenBounds.y + screenBounds.height)) - (tinyOffset.y.toInt() * 2)
            else -> desiredPosition.y
        }

        // special case: if we're overflowing both on Y and X, just flip Y and X around the cursor
        if (desiredPosition.x + tooltipDimension.width > screenBounds.x + screenBounds.width
            && desiredPosition.y + tooltipDimension.height > screenBounds.y + screenBounds.height)
        {
            return Point(
                desiredPosition.x - tinyOffset.x.toInt() * 2 - tooltipDimension.width,
                desiredPosition.y - tinyOffset.y.toInt() * 2 - tooltipDimension.height,
            )
        }
        return Point(finalX, finalY)
    }

    FloatingTooltipContainer(
        show = showTooltip,
        owner = mainWindow,
        position = getIdealPosition(),
        dimension = tooltipDimension,
        content = {
            tooltipContent()
        }
    )
}

/**
 * Finds the screen device that the given AWT window is currently on and returns its bounds.
 */
fun getScreenBoundsForWindow(window: Window): Rectangle {
    val windowCenter = window.location.apply {
        x += window.width / 2
        y += window.height / 2
    }
    val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    // Find the screen device that contains the window's center point
    val currentScreen = screenDevices.find { screen ->
        screen.defaultConfiguration.bounds.contains(windowCenter)
    }
    // Return the bounds of the found screen, or the default screen's bounds as a fallback.
    // The bounds property gives the true, physical pixel dimensions.
    return currentScreen?.defaultConfiguration?.bounds
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
}