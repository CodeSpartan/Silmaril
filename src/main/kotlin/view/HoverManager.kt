package view

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Point
import kotlin.math.roundToInt
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.window.DialogWindow

/**
 * The interface for the global hover service.
 * Any component can get this from the CompositionLocal and use it.
 */
interface HoverManager {
    fun show(ownerWindow: Window?, relativePosition: Offset, width: Int, content: @Composable () -> Unit)
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
    width: Dp,
    content: @Composable () -> Unit
) {
    if (show.value) {
        DialogWindow(
            create = {
                ComposeDialog(owner).apply {
                    isFocusable = false
                    isUndecorated = true
                    isTransparent = true
                }
            },
            dispose = ComposeDialog::dispose,
            update = { dialog ->
                // This is called on every recomposition.
                dialog.pack()
                dialog.location = position
            }
        ) {
            Column(
                Modifier
                    .width(width)
                    .wrapContentHeight()
                    .background(Color.Black)
            ) {
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
    var tooltipWidth by remember { mutableStateOf(300) }
    val tooltipHeight = 500 // an assumption
    // by default, assume main window owns everything, but later show() will potentially provide another owner
    var tooltipParentWindow by remember { mutableStateOf<Window>(mainWindow) }

    val manager = remember {
        object : HoverManager {
            override fun show(ownerWindow: Window?, relativePosition: Offset, width: Int, content: @Composable () -> Unit) {
                tooltipWidth = width
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
        val tinyOffset = Offset(0f, 0f)
        val reverseOffset = Offset(15f, 15f)
        val desiredPosition = Point(tooltipPosition.x + tinyOffset.x.toInt(),tooltipPosition.y + tinyOffset.y.toInt())
        val screenBounds = getScreenBoundsForWindow(tooltipParentWindow)

        val finalX = when {
            // Check if it goes off the right edge
            desiredPosition.x + tooltipWidth > screenBounds.x + screenBounds.width ->
                desiredPosition.x - (desiredPosition.x + tooltipWidth - (screenBounds.x + screenBounds.width)) - reverseOffset.x.toInt()
            else -> desiredPosition.x
        }

        val finalY = when {
            // Check if it goes off the bottom edge
            desiredPosition.y + tooltipHeight > screenBounds.y + screenBounds.height ->
                desiredPosition.y - (desiredPosition.y + tooltipHeight - (screenBounds.y + screenBounds.height)) - reverseOffset.y.toInt()
            else -> desiredPosition.y
        }

        // special case: if we're overflowing both on Y and X, just flip Y and X around the cursor
        if (desiredPosition.x + tooltipWidth > screenBounds.x + screenBounds.width
            && desiredPosition.y + tooltipHeight > screenBounds.y + screenBounds.height)
        {
            return Point(
                desiredPosition.x - reverseOffset.x.toInt() - tooltipWidth,
                desiredPosition.y - reverseOffset.y.toInt() - tooltipHeight,
            )
        }
        return Point(finalX, finalY)
    }

    FloatingTooltipContainer(
        show = showTooltip,
        owner = mainWindow,
        position = getIdealPosition(),
        width = tooltipWidth.dp,
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