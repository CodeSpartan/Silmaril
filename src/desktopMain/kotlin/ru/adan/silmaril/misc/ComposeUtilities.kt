package ru.adan.silmaril.misc

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// a wrapper to carry a stable, monotonic id
@Immutable
data class OutputItem(val id: Long, val message: ColorfulTextMessage) {
    companion object {
        private var nextId = 0L
        fun new(message: ColorfulTextMessage) = OutputItem(nextId++, message)
    }
}

fun Modifier.doubleClickOrSingle(
    doubleClickTimeoutMs: Long = 250,
    doubleClickSlopDp: Float = 6f,
    onDoubleClick: (Offset) -> Unit,
    onSingleClick: (Offset) -> Unit
) = composed {
// Always call the latest lambdas (prevents stale captures)
    val latestOnDouble by rememberUpdatedState(onDoubleClick)
    val latestOnSingle by rememberUpdatedState(onSingleClick)


    pointerInput(doubleClickTimeoutMs, doubleClickSlopDp) {
        awaitEachGesture {
            val slopPx = with(density) { doubleClickSlopDp.dp.toPx() }

            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val firstUp = waitForUpOrCancellation() ?: return@awaitEachGesture

            val secondDown = withTimeoutOrNull(doubleClickTimeoutMs) {
                awaitFirstDown(requireUnconsumed = false)
            }

            if (secondDown != null &&
                (secondDown.position - firstDown.position).getDistance() <= slopPx
            ) {
                val secondUp = waitForUpOrCancellation()
                latestOnDouble(secondUp?.position ?: secondDown.position)
            } else {
                latestOnSingle(firstUp.position)
            }
        }
    }
}

@Composable
fun rememberIsAtBottom(state: LazyListState, fullyVisible: Boolean = false): State<Boolean> {
    return remember(state, fullyVisible) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) true
            else {
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                val isLastIndex = last.index == total - 1
                if (!isLastIndex) return@derivedStateOf false

                if (fullyVisible) {
                    // last item fully within viewport
                    (last.offset + last.size) <= info.viewportEndOffset
                } else {
                    // last item at least partially visible
                    true
                }
            }
        }
    }
}

object BuildInfo {
    val version: String =
        BuildInfo::class.java.`package`?.implementationVersion ?: "dev"
}
