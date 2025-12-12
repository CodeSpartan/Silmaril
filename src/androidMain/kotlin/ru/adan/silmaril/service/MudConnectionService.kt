package ru.adan.silmaril.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import ru.adan.silmaril.MainActivity
import ru.adan.silmaril.R
import ru.adan.silmaril.model.ConnectionState
import ru.adan.silmaril.platform.createLogger

/**
 * Foreground service that keeps MUD connections alive when the app is backgrounded.
 *
 * This service:
 * - Maintains a persistent notification showing connection status
 * - Acquires a WakeLock to keep CPU awake for TCP processing
 * - Exempts the app from Doze mode restrictions
 * - Allows connections to survive screen-off and app backgrounding
 */
class MudConnectionService : Service() {
    private val logger = createLogger("MudConnectionService")
    private val binder = LocalBinder()

    private var wakeLock: PowerManager.WakeLock? = null
    private val connectionStates = mutableMapOf<String, ConnectionState>()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mud_connection_channel"
        private const val CHANNEL_NAME = "MUD Connection"

        // Intent actions
        const val ACTION_START_SERVICE = "ru.adan.silmaril.START_SERVICE"
        const val ACTION_STOP_SERVICE = "ru.adan.silmaril.STOP_SERVICE"
    }

    /**
     * Binder for local service binding.
     * Allows MainActivity to communicate with this service.
     */
    inner class LocalBinder : Binder() {
        fun getService(): MudConnectionService = this@MudConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        logger.info { "MudConnectionService created" }

        // Create notification channel (required for Android 8.0+)
        createNotificationChannel()

        // Acquire WakeLock to keep CPU awake during connections
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.info { "onStartCommand: ${intent?.action}" }

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                // Start as foreground service with notification
                startForeground(NOTIFICATION_ID, createNotification())
                logger.info { "Service started in foreground" }
            }
            ACTION_STOP_SERVICE -> {
                // Stop the service
                stopSelf()
            }
        }

        // If service is killed by Android, don't recreate it automatically
        // The user will need to reconnect manually
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        logger.info { "onBind called" }
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.info { "MudConnectionService destroyed" }

        // Release WakeLock
        releaseWakeLock()

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Updates connection states and refreshes the notification.
     * Called by MainActivity when profile connection states change.
     *
     * @param states Map of profile names to their connection states
     */
    fun updateConnectionStates(states: Map<String, ConnectionState>) {
        connectionStates.clear()
        connectionStates.putAll(states)

        val activeCount = connectionStates.count { it.value == ConnectionState.CONNECTED }
        val connectingCount = connectionStates.count { it.value == ConnectionState.CONNECTING }
        logger.debug { "Connection states updated: $activeCount active, $connectingCount connecting" }

        // Update notification with current state (even if 0 connections - let notification show status)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())

        // Note: We no longer auto-stop when connections reach 0.
        // The service will be stopped explicitly via ACTION_STOP_SERVICE or when Activity calls stopService.
        // This prevents race conditions during Activity recreation where connections temporarily drop to 0.
    }

    /**
     * Creates the notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound/vibration
            ).apply {
                description = "Shows status of MUD connections"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            logger.info { "Notification channel created" }
        }
    }

    /**
     * Creates the notification to display in the status bar.
     */
    private fun createNotification(): Notification {
        val activeConnections = connectionStates.filter { it.value == ConnectionState.CONNECTED }
        val connectingCount = connectionStates.count { it.value == ConnectionState.CONNECTING }
        val activeCount = activeConnections.size

        // Build notification text based on connection states
        val contentText = when {
            activeCount == 0 && connectingCount > 0 -> {
                "Connecting..."
            }
            activeCount == 1 -> {
                "Connected as ${activeConnections.keys.first()}"
            }
            activeCount > 1 -> {
                "$activeCount connections active"
            }
            else -> {
                "No active connections"
            }
        }

        // Create intent to open MainActivity when notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        // Note: setOngoing(true) should prevent dismissal, but MIUI and some ROMs ignore this.
        // The service will keep running even if notification is dismissed.
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Silmaril MUD Client")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with app icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Extra flag to prevent dismissal (works on most ROMs)
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        return notification
    }

    /**
     * Acquires a partial WakeLock to keep CPU awake during connections.
     * This allows TCP processing to continue even when the screen is off.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Silmaril::MudConnectionWakeLock"
            ).apply {
                acquire()
                logger.info { "WakeLock acquired" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to acquire WakeLock" }
        }
    }

    /**
     * Releases the WakeLock when service is destroyed.
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                logger.info { "WakeLock released" }
            }
        }
        wakeLock = null
    }
}
