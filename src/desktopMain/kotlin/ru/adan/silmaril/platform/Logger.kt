package ru.adan.silmaril.platform

import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC

/**
 * Desktop logger implementation using kotlin-logging (SLF4J/Logback)
 */
class DesktopLogger(tag: String) : Logger {
    private val logger = KotlinLogging.logger(tag)

    override fun debug(message: () -> String) {
        logger.debug { message() }
    }

    override fun info(message: () -> String) {
        logger.info { message() }
    }

    override fun warn(message: () -> String) {
        logger.warn { message() }
    }

    override fun error(message: () -> String) {
        logger.error { message() }
    }

    override fun error(throwable: Throwable, message: () -> String) {
        logger.error(throwable) { message() }
    }
}

actual fun createLogger(tag: String): Logger = DesktopLogger(tag)

/**
 * Set MDC profile for log file sifting via logback's SiftingAppender.
 */
actual fun setMdcProfile(profileName: String) {
    MDC.put("profile", profileName)
}

/**
 * Clear MDC profile context.
 */
actual fun clearMdcProfile() {
    MDC.remove("profile")
}
