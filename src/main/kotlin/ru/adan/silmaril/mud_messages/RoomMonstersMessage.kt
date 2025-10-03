package ru.adan.silmaril.mud_messages

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import io.github.oshai.kotlinlogging.KotlinLogging

data class RoomMonstersMessage(
    @field:JacksonXmlProperty(isAttribute = true, localName = "IsRound")
    val isRound: Boolean = false,

    @field:JacksonXmlElementWrapper(useWrapping = true, localName = "Monsters")
    @field:JacksonXmlProperty(localName = "Monster")
    val monsters: List<Monster> = emptyList()
) {

    val allCreatures: List<Creature>
        get() = monsters.map { it.toCreature() }

    companion object {
        private val logger = KotlinLogging.logger {}

        val EMPTY = RoomMonstersMessage(isRound = false, monsters = emptyList())

        fun fromXml(xml: String): RoomMonstersMessage? {
            val xmlMapper = XmlMapper()
            return try {
                xmlMapper.readValue(xml, RoomMonstersMessage::class.java)
            } catch (e: Exception) {
                logger.warn { "Offending XML: $xml" }
                logger.error(e) { "An unexpected error occurred." }
                null
            }
        }
    }
}

data class Monster(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "TargetName")
    val targetName: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Position")
    val position: Position = Position.Standing,

    @field:JacksonXmlProperty(isAttribute = true, localName = "HitsPercent")
    val hitsPercent: Double = 0.0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "MovesPercent")
    val movesPercent: Double = 0.0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "IsAttacked")
    val isAttacked: Boolean = false,

    @field:JacksonXmlProperty(isAttribute = true, localName = "IsPlayerCharacter")
    val isPlayerCharacter: Boolean = false,

    @field:JacksonXmlProperty(isAttribute = true, localName = "IsBoss")
    val isBoss: Boolean = false,

    @field:JacksonXmlElementWrapper(useWrapping = true, localName = "Affects")
    @field:JacksonXmlProperty(localName = "Affect")
    val affects: List<Affect> = emptyList()
)

data class RoomMobs(
    val newRound: Boolean = false,
    val mobs: List<Creature> = emptyList()
) {
    companion object {
        val EMPTY = RoomMobs(newRound = false, mobs = emptyList())
    }
}