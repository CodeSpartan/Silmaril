package ru.adan.silmaril.view.small_widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import ru.adan.silmaril.model.ProfileManager

/** Used in a couple of widgets like map and monsters, until they're filled with content. Before they're filled, clicking them would return focus to the main window. */
@Composable
fun BoxScope.EmptyOverlayReturnsFocus(profileManager: ProfileManager) {
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        when (e.type) {
                            PointerEventType.Press -> if (e.buttons.isSecondaryPressed) {
                                profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit)
                                e.changes.forEach { it.consume() }
                            }
                            PointerEventType.Release -> {
                                profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit)
                            }
                        }
                    }
                }
            }
    )
}