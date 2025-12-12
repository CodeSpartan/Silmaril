package ru.adan.silmaril.platform

import android.util.Log

/**
 * Android logger implementation using Logcat
 */
class AndroidLogger(private val tag: String) : Logger {

    override fun debug(message: () -> String) {
        Log.d(tag, message())
    }

    override fun info(message: () -> String) {
        Log.i(tag, message())
    }

    override fun warn(message: () -> String) {
        Log.w(tag, message())
    }

    override fun error(message: () -> String) {
        Log.e(tag, message())
    }

    override fun error(throwable: Throwable, message: () -> String) {
        Log.e(tag, message(), throwable)
    }
}

actual fun createLogger(tag: String): Logger = AndroidLogger(tag)

/**
 * No-op on Android - Logcat doesn't support MDC-based log file sifting.
 */
actual fun setMdcProfile(profileName: String) {
    // No-op on Android
}

/**
 * No-op on Android.
 */
actual fun clearMdcProfile() {
    // No-op on Android
}
