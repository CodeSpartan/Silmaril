package ru.adan.silmaril.xml_schemas

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import ru.adan.silmaril.misc.RoomColor
import ru.adan.silmaril.misc.RoomIcon

@JacksonXmlRootElement(localName = "ArrayOfAdditionalRoomParameters")
data class ArrayOfAdditionalRoomParameters(
    @JacksonXmlProperty(localName = "AdditionalRoomParameters")
    @JacksonXmlElementWrapper(useWrapping = false)
    val items: List<AdditionalRoomParameters> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true) // safely ignore RoomAlias and ActionsToExecuteOnRoomEntry
data class AdditionalRoomParameters(
    @JacksonXmlProperty(localName = "RoomId", isAttribute = true)
    val roomId: Int,

    @JacksonXmlProperty(localName = "HasBeenVisited", isAttribute = true)
    val hasBeenVisited: Boolean,

    @JacksonXmlProperty(localName = "Color", isAttribute = true)
    val color: RoomColor,

    @JacksonXmlProperty(localName = "Icon", isAttribute = true)
    val icon: RoomIcon,

    // Optional element; may be absent. Supports multiline UTF-8 text.
    @JacksonXmlProperty(localName = "Comments")
    val comments: String? = null
)