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
import kotlin.math.roundToInt


/**
 * The interface for the global hover service.
 * Any component can get this from the CompositionLocal and use it.
 */
interface HoverManager {
    fun show(relativePosition: Offset, content: @Composable () -> Unit)
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
    content: @Composable () -> Unit
) {
    if (show.value) {
        DialogWindow(
            create = {
                ComposeDialog(owner = owner).apply {
                    size = Dimension(300, 200)
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
    var windowGlobalPosition by remember { mutableStateOf(Point(0, 0)) }

    val manager = remember {
        object : HoverManager {
            override fun show(relativePosition: Offset, content: @Composable () -> Unit) {
                if (windowName == "") {
                    windowGlobalPosition = owner.location
                    println("location: ${owner.location}")
                    println("manual coords: ${owner.x}, ${owner.y}")
                } else {
                    windowGlobalPosition = settings.getFloatingWindowState(windowName).windowPosition
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

    FloatingTooltipContainer(
        show = showTooltip,
        owner = owner,
        // Give a bit of offset to the tooltip relative to the mouse cursor
        position = Point(
            tooltipPosition.x + 15,
            tooltipPosition.y + 15
        ),
        content = {
            tooltipContent()
        }
    )
}