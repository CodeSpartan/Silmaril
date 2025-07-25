package view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.Dimension
import java.awt.Point
import kotlin.math.roundToInt
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import java.awt.EventQueue
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.platform.LocalDensity
import java.awt.geom.AffineTransform
import kotlin.coroutines.CoroutineContext

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
        key(content, width, position) {
            DisposableEffect(Unit) {
                val effectId = (0..1_000_000).random()
                println("[$effectId] DisposableEffect: LAUNCHED.")
                var dialog: ComposeDialog? = null
                val isDisposed = AtomicBoolean(false)
                // A flag to ensure we only move the window into view once.
                val hasMovedOnscreen = AtomicBoolean(false)

                EventQueue.invokeLater {
                    println("[$effectId] AWT: Creating dialog OFF-SCREEN.")
                    dialog = ComposeDialog(owner = owner).apply {
                        isFocusable = false
                        isUndecorated = true
                        isAutoRequestFocus = false
                        background = java.awt.Color(0, 0, 0, 0)
                        location = Point(-10000, -10000)
                        size = Dimension(1, 1)
                        isVisible = true

                        setContent {
                            Column(
                                Modifier
                                    .width(width)
                                    .wrapContentHeight()
                                    .background(Color.Black)
                                    .onSizeChanged { newSize ->
                                        if (isDisposed.get() || newSize.width <= 0 || newSize.height <= 0) return@onSizeChanged
                                        println("[$effectId] COMPOSE-onSizeChanged: Size reported: $newSize")

                                        EventQueue.invokeLater {
                                            if (isDisposed.get()) return@invokeLater

                                            // 1. Always pack the off-screen window to the latest size.
                                            // This correctly handles DPI and content changes.
                                            println("[$effectId] AWT-Packing: Packing to new size.")
                                            pack()

                                            // 2. Move the window into view ONLY ONCE.
                                            // compareAndSet is an atomic operation that sets the flag
                                            // to true only if it was false, and returns true if it succeeded.
                                            if (hasMovedOnscreen.compareAndSet(false, true)) {
                                                println("[$effectId] AWT-Moving: First valid size. Moving to final position.")
                                                location = position
                                                println("[$effectId] AWT-Moved: Final size on first move is $size")
                                            }
                                        }
                                    }
                            ) {
                                content()
                            }
                        }
                    }
                }

                onDispose {
                    println("[$effectId] DisposableEffect: DISPOSED.")
                    isDisposed.set(true)
                    EventQueue.invokeLater {
                        dialog?.isVisible = false
                        dialog?.dispose()
                    }
                }
            }
        }
    }
}

/**
 * A CoroutineDispatcher for the AWT Event Dispatch Thread.
 */
val Dispatchers.AWT: CoroutineDispatcher
    get() = AwtDispatcher

private object AwtDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        EventQueue.invokeLater(block)
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