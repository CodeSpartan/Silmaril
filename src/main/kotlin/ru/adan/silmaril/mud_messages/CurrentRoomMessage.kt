package ru.adan.silmaril.mud_messages

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import ru.adan.silmaril.model.FileLogger

/***
 * Construct this object from xml:
 * <CurrentRoomMessage RoomId="2044" ZoneId="20" />
 */
data class CurrentRoomMessage(
    @JacksonXmlProperty(isAttribute = true, localName = "RoomId")
    val roomId: Int,

    @JacksonXmlProperty(isAttribute = true, localName = "ZoneId")
    val zoneId: Int
) {
    companion object {
        fun fromXml(xml: String): CurrentRoomMessage? {
            val xmlMapper = XmlMapper()
            return try {
                xmlMapper.readValue(xml, CurrentRoomMessage::class.java)
            } catch (e: Exception) {
                System.err.println("Offending XML: $xml")
                e.printStackTrace()
                FileLogger.log("CurrentRoomMessage", "Offending XML: $xml")
                FileLogger.log("CurrentRoomMessage", e.stackTrace.toString())
                null
            }
        }
        val EMPTY = CurrentRoomMessage(roomId = -1, zoneId = -100)
    }
}