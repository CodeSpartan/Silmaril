package ru.adan.silmaril.view.hovertooltips

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.SwingDialog
import androidx.compose.ui.window.DialogWindow
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.swing.JDialog

/**
 * The interface for the global hover service.
 * Any component can get this from the CompositionLocal and use it.
 */
interface HoverManager {
    fun show(ownerWindow: Window?, relativePosition: Offset, width: Int, assumedHeight: Int, uniqueKey: Int, content: @Composable () -> Unit)
    fun hide()
}

/**
 * The CompositionLocal key. Components will use this to access the manager.
 */
val LocalHoverManager = staticCompositionLocalOf<HoverManager> {
    // Provide a default implementation that crashes if the provider is not set.
    error("No HoverManager provided")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingTooltipContainer(
    show: MutableState<Boolean>,
    owner: ComposeWindow,
    position: MutableState<Point>,
    width: Dp,
    uniqueKey: MutableState<Int>,
    content: @Composable () -> Unit
) {
    if (show.value) {
        SwingDialog(
            create = {
                ComposeDialog(owner).apply {
                    isFocusable = false
                    isUndecorated = true
                    isTransparent = true
                    focusableWindowState = false
                    defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
                }
            },
            dispose = ComposeDialog::dispose,
            update = { dialog ->
                // This is called on every recomposition.
                // Present at all times; just toggle visibility and update location
                if (dialog.isVisible != show.value) dialog.isVisible = show.value
                if (dialog.location != position.value) dialog.location = position.value
                dialog.pack()
            }
        ) {
            // Because often .pack() won't work correctly after a recomposition (bug?), we force it through a hack
            LaunchedEffect(uniqueKey) {
                try {
                    withContext(Dispatchers.Swing) {
                        yield()
                        val dialogWindow = (window as? JDialog) ?: return@withContext
                        if (!dialogWindow.isDisplayable) return@withContext
                        dialogWindow.setSize(dialogWindow.width, dialogWindow.height - 1)
                        yield()
                        if (!dialogWindow.isDisplayable) return@withContext
                        dialogWindow.pack()
                    }
                } catch (_: CancellationException) {
                } catch (t: Throwable) {
                    val logger = KotlinLogging.logger {}
                    logger.warn { t.message }
                    logger.warn { t.stackTrace.joinToString("\n") }
                }
            }
            Column(
                Modifier
                    .width(width)
                    .wrapContentHeight()
                    .background(Color.Transparent)
            ) {
                key(position.value.x, position.value.y, uniqueKey.value) {
                    content()
                }
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
    var tooltipWidth by remember { mutableStateOf(0) }
    var tooltipHeight by remember { mutableStateOf(500) } // an assumption
    var tooltipUniqueKey by remember { mutableStateOf(-1) }
    // by default, assume ru.adan.silmaril.main window owns everything, but later show() will potentially provide another owner
    var tooltipParentWindow by remember { mutableStateOf<Window>(mainWindow) }

    val manager = remember {
        object : HoverManager {
            override fun show(ownerWindow: Window?, relativePosition: Offset, width: Int, assumedHeight: Int, uniqueKey: Int, content: @Composable () -> Unit) {
                tooltipWidth = width
                tooltipHeight = assumedHeight
                tooltipUniqueKey = uniqueKey
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
            desiredPosition.y + tooltipHeight > screenBounds.y + (screenBounds.height - 96) -> // 96 is Windows 11 start bar height
                desiredPosition.y - (desiredPosition.y + tooltipHeight - (screenBounds.y + screenBounds.height - 96)) - reverseOffset.y.toInt()
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
        position = mutableStateOf(getIdealPosition()),
        width = tooltipWidth.dp,
        uniqueKey = mutableStateOf(tooltipUniqueKey),
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