package ru.adan.silmaril.xml_schemas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

// Schema to read composeResources/files/zones_info.yaml

@Serializable
data class ZonesYaml(
    val zones: List<ZoneYaml>
)

@Serializable
data class ZoneYaml(
    val id: Int,
    val name: String,
    @SerialName("level_range") @Serializable(with = IntRangeAsListSerializer::class)
    val levelRange: IntRange,
    val type: ZoneType,
    val authors: String
)

@Serializable
enum class ZoneType {
    @SerialName("solo") SOLO,
    @SerialName("groups") GROUPS
}

object IntRangeAsListSerializer : KSerializer<IntRange> {
    private val listSer = ListSerializer(Int.serializer())
    override val descriptor: SerialDescriptor = listSer.descriptor

    override fun deserialize(decoder: Decoder): IntRange {
        val list = decoder.decodeSerializableValue(listSer)
        require(list.size == 2) { "level_range must be a list of two integers, got: $list" }
        return list[0]..list[1]
    }

    override fun serialize(encoder: Encoder, value: IntRange) {
        encoder.encodeSerializableValue(listSer, listOf(value.first, value.last))
    }
}
