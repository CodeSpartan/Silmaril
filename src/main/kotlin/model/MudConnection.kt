package model

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.Charset

class MudConnection(private val host: String, private val port: Int) {

    private var socket: Socket? = null

    fun connect() {
        socket = Socket(host, port)
    }

    // Send message with Windows-1251 encoding
    fun sendMessage(message: String) {
        socket?.getOutputStream()?.let { outputStream ->
            val writer = OutputStreamWriter(outputStream, Charset.forName("Windows-1251"))
            writer.write(message + "\n")
            writer.flush()
        }
    }

    // Receive message with Windows-1251 encoding
    fun receiveMessage(): String? {
        socket?.getInputStream()?.let { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName("Windows-1251")))
            return reader.readLine() // Receive data from the input stream and decode it
        }
        return null
    }

    // Ensure proper connection closing
    fun close() {
        socket?.close()
    }
}