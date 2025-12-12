package ru.adan.silmaril.platform

/**
 * Platform-specific information and utilities
 */
expect object Platform {
    val name: String
    val isDesktop: Boolean
    val isAndroid: Boolean
}

/**
 * Get the application documents directory for storing user data
 */
expect fun getDocumentsDirectory(): String

/**
 * Get the application cache/config directory
 */
expect fun getConfigDirectory(): String
