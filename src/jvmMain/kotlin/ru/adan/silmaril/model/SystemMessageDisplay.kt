package ru.adan.silmaril.model

/**
 * Interface for displaying system messages to the user.
 * Implemented by ProfileManager in desktopMain.
 */
interface SystemMessageDisplay {
    fun displaySystemMessage(message: String)
}
