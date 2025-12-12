package ru.adan.silmaril.misc

import androidx.compose.runtime.Immutable

/**
 * A wrapper to carry a stable, monotonic id for LazyColumn item stability.
 * This prevents unnecessary recompositions when new messages arrive -
 * LazyColumn only recomposes new items, not existing ones.
 */
@Immutable
data class OutputItem(val id: Long, val message: ColorfulTextMessage) {
    companion object {
        private var nextId = 0L
        fun new(message: ColorfulTextMessage) = OutputItem(nextId++, message)
    }
}
