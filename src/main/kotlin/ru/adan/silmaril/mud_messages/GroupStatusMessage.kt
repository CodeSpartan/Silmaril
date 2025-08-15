package ru.adan.silmaril.mud_messages

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import io.github.oshai.kotlinlogging.KotlinLogging

enum class Position {
    Dying,
    Sleeping,
    Resting,
    Sitting,
    Fighting,
    Standing,
    Riding;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromString(value: String): Position = Position.valueOf(value)
    }
}

data class Affect(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Duration")
    val duration: Int? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "Rounds")
    val rounds: Int? = null
)

data class GroupMates(
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "GroupMate")
    val groupMates: List<GroupMate> = emptyList(),

    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "Pet")
    val pets: List<Pet> = emptyList()
)


data class GroupStatusMessage(
    @field:JacksonXmlProperty(localName = "GroupMates")
    val groupMates: GroupMates = GroupMates()
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        val EMPTY = GroupStatusMessage(groupMates = GroupMates())

        fun fromXml(xml: String): GroupStatusMessage? {
            val xmlMapper = XmlMapper()
            return try {
                xmlMapper.readValue(xml, GroupStatusMessage::class.java)
            } catch (e: Exception) {
                logger.warn { "Offending XML: $xml" }
                logger.error(e) { "An unexpected error occurred." }
                null
            }
        }
    }
}

data class GroupMate(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "TargetName")
    val targetName: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Position")
    val position: Position = Position.Standing,

    @field:JacksonXmlProperty(isAttribute = true, localName = "InSameRoom")
    val inSameRoom: Boolean = false,

    @field:JacksonXmlProperty(isAttribute = true, localName = "HitsPercent")
    val hitsPercent: Double = 0.0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "MovesPercent")
    val movesPercent: Double = 0.0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "IsAttacked")
    val isAttacked: Boolean = false,

    @field:JacksonXmlProperty(isAttribute = true, localName = "MemTime")
    val memTime: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "WaitState")
    val waitState: Double = 0.0,

    @field:JacksonXmlElementWrapper(useWrapping = true, localName = "Affects")
    @field:JacksonXmlProperty(localName = "Affect")
    val affects: List<Affect> = emptyList()
)

data class Pet(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "TargetName")
    val targetName: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Owner")
    val owner: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Position")
    val position: Position = Position.Standing,

    @field:JacksonXmlProperty(isAttribute = true, localName = "InSameRoom")
    val inSameRoom: Boolean = false,

    @field:JacksonXmlProperty(isAttribute = true, localName = "HitsPercent")
    val hitsPercent: Double = 0.0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "MovesPercent")
    val movesPercent: Double = 0.0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "IsAttacked")
    val isAttacked: Boolean = false,

    @field:JacksonXmlElementWrapper(useWrapping = true, localName = "Affects")
    @field:JacksonXmlProperty(localName = "Affect")
    val affects: List<Affect> = emptyList()
)