package ru.adan.silmaril.view

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import org.koin.compose.koinInject
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/***
 * A floating window is an undecorated window, like Map, Monsters, Players and Output Window
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingWindow(
    show: MutableState<Boolean>,
    visible: MutableState<Boolean>,
    owner: ComposeWindow, // owner is necessary for correct focus behavior
    windowName: String,
    profileManager: ProfileManager,
    content: @Composable () -> Unit
) {
    val settingsManager: SettingsManager = koinInject()
    if (show.value) {
        DialogWindow(
            visible = !visible.value,
            create = {
                ComposeDialog(owner = owner).apply { // Set the owner as the window
                    val windowInitialState = settingsManager.getFloatingWindowState(windowName)
                    size = Dimension(windowInitialState.windowSize.width, windowInitialState.windowSize.height)
                    isFocusable = true
                    isUndecorated = true
                    location = windowInitialState.windowPosition

                    // Listen to "x" button being pressed
                    addWindowListener(object : WindowAdapter() {
                        override fun windowClosing(e: WindowEvent) {
                            show.value = false
                            settingsManager.updateFloatingWindowState(windowName, false)
                        }
                    })

                    // Listen to position/size changes
                    addComponentListener(object : ComponentAdapter() {
                        override fun componentMoved(e: ComponentEvent) {
                            settingsManager.updateFloatingWindow(windowName, location, size)
                        }

                        override fun componentResized(e: ComponentEvent) {
                            settingsManager.updateFloatingWindow(windowName, location, size)
                            profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit)
                        }
                    })
                }
            },
            dispose = ComposeDialog::dispose,
        ) {
            val titleBarHeight = 15.dp
            // Provides the real OwnerWindow down the hierarchy
            CompositionLocalProvider(OwnerWindow provides window) {
                CompositionLocalProvider(LocalTopBarHeight provides titleBarHeight) {
                    Column(Modifier.background(Color.Black)) {
                        AppWindowTitleBar(
                            titleBarHeight,
                            onInteract = { profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit) }
                        )
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowScope.AppWindowTitleBar(
    height: Dp,
    onInteract: () -> Unit // called on left-release or right-press
) = WindowDraggableArea {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.DarkGray)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var primaryDown = false
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Final)
                        when (e.type) {
                            PointerEventType.Press -> {
                                if (e.buttons.isPrimaryPressed) primaryDown = true
                                if (e.buttons.isSecondaryPressed) {
                                    onInteract()
                                    // consume so nothing else tries to show a menu
                                    e.changes.forEach { it.consume() }
                                }
                            }
                            PointerEventType.Release -> {
                                if (primaryDown) {
                                    primaryDown = false
                                    onInteract() // click or drag finished
                                }
                            }
                        }
                    }
                }
            }
    )
}

// This will provide any composable with a reference to its immediate containing window.
val OwnerWindow = staticCompositionLocalOf<Window> {
    error("No Window provided. Wrap your content in a provider.")
}

/**
 * Provides the height of the title/drag bar of a FloatingWindow.
 * This allows descendant composables to account for the obscured space
 * at the top of the window without having to hardcode the value.
 */
val LocalTopBarHeight = compositionLocalOf { 0.dp }