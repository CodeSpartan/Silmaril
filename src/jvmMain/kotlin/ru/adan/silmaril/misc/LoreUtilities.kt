package ru.adan.silmaril.misc

/**
 * Utility functions shared between Android and Desktop for LoreMessage display.
 */

fun minutesToDaysFormatted(minutes: Int): String {
    val days = minutes / 1440
    val lastDigit = days % 10
    val lastTwoDigits = days % 100

    if (lastTwoDigits in 11..14) {
        return "$days дней"
    }

    return when (lastDigit) {
        1 -> "$days день"
        2, 3, 4 -> "$days дня"
        else -> "$days дней"
    }
}

fun Double.toSmartString(): String {
    // Check if the double has no fractional part.
    return if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        this.toString()
    }
}

fun formatDuration(totalHours: Int): String {
    if (totalHours <= 0) {
        return "0 час."
    }

    return if (totalHours < 24) {
        "$totalHours час."
    } else {
        val days = totalHours / 24
        val hours = totalHours % 24
        "$days дн. $hours час."
    }
}

fun List<String>.joinOrNone(): String {
    return if (isEmpty()) "NOBITS" else joinToString(" ")
}
