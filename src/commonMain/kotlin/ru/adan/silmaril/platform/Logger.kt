package ru.adan.silmaril.platform

/**
 * Platform-agnostic logging interface
 */
interface Logger {
    fun debug(message: () -> String)
    fun info(message: () -> String)
    fun warn(message: () -> String)
    fun error(message: () -> String)
    fun error(throwable: Throwable, message: () -> String)
}

/**
 * Factory for creating platform-specific loggers
 */
expect fun createLogger(tag: String): Logger

/**
 * Set MDC (Mapped Diagnostic Context) profile for log file sifting.
 * On Desktop, this sets SLF4J MDC to route logs to profile-specific files.
 * On Android, this is a no-op (Logcat doesn't support sifting).
 */
expect fun setMdcProfile(profileName: String)

/**
 * Clear MDC profile context.
 */
expect fun clearMdcProfile()
