package ru.adan.silmaril.xml_schemas

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Zone(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Id")
    val id: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Author")
    val author: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "MinLevel")
    val minLevel: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "MaxLevel")
    val maxLevel: Int = 0,

    // When isAttribute is false, you can't have a camelCase variable with the same name in localName starting with Uppercase
    @field:JacksonXmlElementWrapper(useWrapping = true, localName = "Rooms")
    @field:JacksonXmlProperty(localName = "Room")
    val roomsList: List<Room> = listOf()
)

data class Room(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Id")
    val id: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "XLocation")
    var x: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "YLocation")
    var y: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "ZLocation")
    var z: Int = 0,

    @field:JacksonXmlProperty(isAttribute = false, localName = "Description")
    val description: String = "",

    @field:JacksonXmlElementWrapper(useWrapping = true, localName = "Exits")
    @field:JacksonXmlProperty(localName = "RoomExit")
    val exitsList: List<RoomExit> = listOf()
)

data class RoomExit(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Direction")
    val direction: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "RoomId")
    val roomId: Int = 0
)