package ru.adan.silmaril.model

import kotlinx.serialization.Serializable

/**
 * Simple serializable data classes for triggers, aliases, substitutes, and hotkeys.
 * These are used for saving/loading text-based macros to YAML files.
 */

@Serializable
data class SimpleTriggerData(
    val condition: String,
    val action: String,
    val priority: Int,
    val isRegex: Boolean
)

@Serializable
data class SimpleAliasData(
    val shorthand: String,
    val action: String,
    val priority: Int,
)

@Serializable
data class SimpleHotkeyData(
    val hotkeyString: String,
    val action: String,
    val priority: Int,
)
