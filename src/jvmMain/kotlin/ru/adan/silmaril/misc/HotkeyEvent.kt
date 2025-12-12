package ru.adan.silmaril.misc

/**
 * Platform-agnostic representation of a keyboard hotkey event.
 * On Desktop, this is created from Compose's KeyEvent.
 * On Android, this could be created from KeyEvent or touch shortcuts.
 */
data class HotkeyEvent(
    val keyCode: Long,      // Maps to Key.keyCode on desktop
    val isAltPressed: Boolean,
    val isCtrlPressed: Boolean,
    val isShiftPressed: Boolean
) {
    companion object {
        val EMPTY = HotkeyEvent(0, false, false, false)
    }
}

/**
 * Platform-agnostic hotkey configuration data.
 * This holds the data needed to match and execute a hotkey without
 * depending on Compose's Key type.
 */
data class HotkeyData(
    val keyCode: Long,
    val isCtrlPressed: Boolean,
    val isShiftPressed: Boolean,
    val isAltPressed: Boolean,
    val keyString: String,
    val actionText: String,
    val priority: Int
) {
    override fun toString(): String {
        return "HotkeyData(keyString='$keyString', action='$actionText')"
    }
}

/**
 * Map of key names to their key codes.
 * This allows creating hotkeys from string representations on any platform.
 *
 * Key codes are from Compose's Key class (androidx.compose.ui.input.key.Key).
 * These values match Key.*.keyCode and are stable across platforms.
 */
object HotkeyKeyMap {
    // Key codes from Compose Key - verified against actual Key.*.keyCode values
    // Letters use 0x100000000 + USB HID usage codes (A=0x04, B=0x05, ..., Z=0x1D mapped to 0x44-0x5D)
    // Actually Compose uses different mapping: A=0x41, B=0x42, etc. (ASCII-like)
    private val keyCodeMap: Map<String, Long> = mapOf(
        // Function keys - these are correct
        "F1" to 0x100000070L, "F2" to 0x100000071L, "F3" to 0x100000072L, "F4" to 0x100000073L,
        "F5" to 0x100000074L, "F6" to 0x100000075L, "F7" to 0x100000076L, "F8" to 0x100000077L,
        "F9" to 0x100000078L, "F10" to 0x100000079L, "F11" to 0x10000007AL, "F12" to 0x10000007BL,

        // Number row (top of keyboard) - 0x100000030-0x100000039
        "0" to 0x100000030L, "1" to 0x100000031L, "2" to 0x100000032L, "3" to 0x100000033L,
        "4" to 0x100000034L, "5" to 0x100000035L, "6" to 0x100000036L, "7" to 0x100000037L,
        "8" to 0x100000038L, "9" to 0x100000039L, "-" to 0x10000002DL, "=" to 0x10000002EL,

        // Letters A-Z: 0x100000041-0x10000005A (ASCII uppercase mapping)
        "A" to 0x100000041L, "B" to 0x100000042L, "C" to 0x100000043L, "D" to 0x100000044L, "E" to 0x100000045L,
        "F" to 0x100000046L, "G" to 0x100000047L, "H" to 0x100000048L, "I" to 0x100000049L, "J" to 0x10000004AL,
        "K" to 0x10000004BL, "L" to 0x10000004CL, "M" to 0x10000004DL, "N" to 0x10000004EL, "O" to 0x10000004FL,
        "P" to 0x100000050L, "Q" to 0x100000051L, "R" to 0x100000052L, "S" to 0x100000053L, "T" to 0x100000054L,
        "U" to 0x100000055L, "V" to 0x100000056L, "W" to 0x100000057L, "X" to 0x100000058L, "Y" to 0x100000059L,
        "Z" to 0x10000005AL,

        // NumPad keys use 0x400000000 prefix
        // All values verified from debug output
        "NumPad0" to 0x400000060L, "NumPad1" to 0x400000061L, "NumPad2" to 0x400000062L,
        "NumPad3" to 0x400000063L, "NumPad4" to 0x400000064L, "NumPad5" to 0x400000065L,
        "NumPad6" to 0x400000066L, "NumPad7" to 0x400000067L, "NumPad8" to 0x400000068L,
        "NumPad9" to 0x400000069L,
        "NumPadMultiply" to 0x40000006AL, "NumPadAdd" to 0x40000006BL,
        "NumPadSubtract" to 0x40000006DL, "NumPadDot" to 0x40000006EL,
        "NumPadDivide" to 0x40000006FL,
    )

    // Created once using lazy initialization when first accessed.
    val validKeyCodes: Set<Long> by lazy { keyCodeMap.values.toSet() }

    fun getKeyCode(keyName: String): Long? = keyCodeMap[keyName]

    fun isKeyCodeValid(keyCode: Long): Boolean = keyCode in validKeyCodes

    /**
     * Creates a HotkeyData from a key string like "F1", "Ctrl+F2", "Alt+Shift+A"
     */
    fun createHotkeyData(keyString: String, actionText: String, priority: Int): HotkeyData? {
        val parts = keyString.split('+').map { it.trim() }
        if (parts.isEmpty()) return null

        val keyName = parts.last().takeIf { it.length > 1 } ?: parts.last().uppercase()
        val keyCode = getKeyCode(keyName) ?: return null

        val modifiers = parts.dropLast(1).map { it.uppercase() }.toSet()

        return HotkeyData(
            keyCode = keyCode,
            isCtrlPressed = "CTRL" in modifiers,
            isShiftPressed = "SHIFT" in modifiers,
            isAltPressed = "ALT" in modifiers,
            keyString = keyString,
            actionText = actionText,
            priority = priority,
        )
    }
}
