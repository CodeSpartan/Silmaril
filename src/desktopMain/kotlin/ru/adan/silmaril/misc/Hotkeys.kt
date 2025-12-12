package ru.adan.silmaril.misc

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

/**
 * Desktop-specific Hotkey class that wraps HotkeyData and adds Compose Key for validation.
 */
data class Hotkey(
    val keyboardKey: Key,
    val isCtrlPressed: Boolean,
    val isShiftPressed: Boolean,
    val isAltPressed: Boolean,
    val keyString: String,
    val actionText: String,
    val priority: Int
) {
    /** Convert to platform-agnostic HotkeyData */
    fun toHotkeyData(): HotkeyData = HotkeyData(
        keyCode = keyboardKey.keyCode,
        isCtrlPressed = isCtrlPressed,
        isShiftPressed = isShiftPressed,
        isAltPressed = isAltPressed,
        keyString = keyString,
        actionText = actionText,
        priority = priority
    )

    companion object {
        fun create(keyString: String, actionText: String, priority: Int): Hotkey? {
            val parts = keyString.split('+').map { it.trim() }
            if (parts.isEmpty()) return null

            val keyName = parts.last().takeIf { it.length > 1 } ?: parts.last().uppercase()
            val keyboardKey = keyMap[keyName] ?: return null // Return null if the key is not in our map

            val modifiers = parts.dropLast(1).map { it.uppercase() }.toSet()

            return Hotkey(
                keyboardKey = keyboardKey,
                isCtrlPressed = "CTRL" in modifiers,
                isShiftPressed = "SHIFT" in modifiers,
                isAltPressed = "ALT" in modifiers,
                keyString = keyString,
                actionText = actionText,
                priority = priority,
            )
        }

        fun isKeyValid(keyEvent: KeyEvent): Boolean {
            return keyEvent.key in validKeys
        }

        fun isKeyCodeValid(keyCode: Long): Boolean {
            return HotkeyKeyMap.isKeyCodeValid(keyCode)
        }

        // Created once using lazy initialization when first accessed.
        private val validKeys: Set<Key> by lazy { keyMap.values.toSet() }

        // A map to convert string representations to Key objects.
        // You can expand this map with any other keys you need.
        private val keyMap: Map<String, Key> = mapOf(
            "F1" to Key.F1, "F2" to Key.F2, "F3" to Key.F3, "F4" to Key.F4,
            "F5" to Key.F5, "F6" to Key.F6, "F7" to Key.F7, "F8" to Key.F8,
            "F9" to Key.F9, "F10" to Key.F10, "F11" to Key.F11, "F12" to Key.F12,

            "`" to Key.Grave, """\""" to Key.Backslash,
            "0" to Key.Zero, "1" to Key.One, "2" to Key.Two, "3" to Key.Three,
            "4" to Key.Four, "5" to Key.Five, "6" to Key.Six, "7" to Key.Seven,
            "8" to Key.Eight, "9" to Key.Nine, "-" to Key.Minus, "=" to Key.Equals,

            "A" to Key.A, "B" to Key.B, "C" to Key.C, "D" to Key.D, "E" to Key.E,
            "F" to Key.F, "G" to Key.G, "H" to Key.H, "I" to Key.I, "J" to Key.J,
            "K" to Key.K, "L" to Key.L, "M" to Key.M, "N" to Key.N, "O" to Key.O,
            "P" to Key.P, "Q" to Key.Q, "R" to Key.R, "S" to Key.S, "T" to Key.T,
            "U" to Key.U, "V" to Key.V, "W" to Key.W, "X" to Key.X, "Y" to Key.Y,
            "Z" to Key.Z,

            "NumPad0" to Key.NumPad0, "NumPad1" to Key.NumPad1, "NumPad2" to Key.NumPad2,
            "NumPad3" to Key.NumPad3, "NumPad4" to Key.NumPad4, "NumPad5" to Key.NumPad5,
            "NumPad6" to Key.NumPad6, "NumPad7" to Key.NumPad7, "NumPad8" to Key.NumPad8,
            "NumPad9" to Key.NumPad9,
            "NumPadMultiply" to Key.NumPadMultiply, "NumPadAdd" to Key.NumPadAdd,
            "NumPadSubtract" to Key.NumPadSubtract, "NumPadDot" to Key.NumPadDot,
            "NumPadDivide" to Key.NumPadDivide,
        )
    }

    override fun toString(): String {
        return "Hotkey(key='$keyboardKey', action='$actionText')"
    }
}

/**
 * Convert a Compose KeyEvent to a platform-agnostic HotkeyEvent.
 */
fun KeyEvent.toHotkeyEvent(): HotkeyEvent = HotkeyEvent(
    keyCode = this.key.keyCode,
    isAltPressed = this.isAltPressed,
    isCtrlPressed = this.isCtrlPressed,
    isShiftPressed = this.isShiftPressed
)