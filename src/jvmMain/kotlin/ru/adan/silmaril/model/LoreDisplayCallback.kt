package ru.adan.silmaril.model

/**
 * Interface for displaying lore-related messages to the user.
 * Implemented by ProfileManager in desktopMain.
 */
interface LoreDisplayCallback {
    fun displayTaggedText(text: String, brightWhiteAsDefault: Boolean)
    suspend fun processLoreLines(lines: List<String>)
}
