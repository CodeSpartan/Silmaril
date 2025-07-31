package ru.adan.silmaril.model

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {

    private var logFile: File? = null

    /**
     * Initializes the logger, creating a log file in a dedicated folder within the user's home directory.
     * @param appName The name of your application, used to create a log folder (e.g., ".my-app/logs").
     */
    fun initialize(appName: String) {
        try {
            // Get the user's home directory in a platform-independent way
            val userHome = System.getProperty("user.home")

            // Create a path for our logs, e.g., C:\Users\YourUser\.appName\logs
            val logDir = File(userHome, ".$appName/logs")

            // Create the directories if they don't exist
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            // Define the log file name with a timestamp
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "log_$timeStamp.txt"
            logFile = File(logDir, fileName)

            println("FileLogger initialized. Log file at: ${logFile?.absolutePath}")

        } catch (e: Exception) {
            println("Failed to initialize FileLogger: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Writes a message to the log file.
     * @param tag A tag to categorize the log message (e.g., the class name).
     * @param message The message to log.
     */
    fun log(tag: String, message: String) {
        logFile?.let { file ->
            val logMessage = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())} [$tag]: $message\n"
            try {
                // Use FileOutputStream with 'true' to append to the file
                FileOutputStream(file, true).use { stream ->
                    stream.write(logMessage.toByteArray())
                }
            } catch (e: Exception) {
                // If logging fails, print the error to the console
                e.printStackTrace()
            }
        }
    }
}