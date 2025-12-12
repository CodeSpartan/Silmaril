package ru.adan.silmaril.mud_messages

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import ru.adan.silmaril.platform.createLogger

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
        private val logger = createLogger("ru.adan.silmaril.mud_messages.CurrentRoomMessage")

        val EMPTY = CurrentRoomMessage(roomId = -1, zoneId = -100)

        fun fromXml(xml: String): CurrentRoomMessage? {
            val xmlMapper = XmlMapper()
            return try {
                xmlMapper.readValue(xml, CurrentRoomMessage::class.java)
            } catch (e: Exception) {

                logger.warn { "Offending XML: $xml" }
                logger.error(e) { "An unexpected error occurred." }
                null
            }
        }
    }
}
