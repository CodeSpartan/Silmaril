package mud_messages

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

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
                println(xml)
                e.printStackTrace()
                null
            }
        }
    }
}