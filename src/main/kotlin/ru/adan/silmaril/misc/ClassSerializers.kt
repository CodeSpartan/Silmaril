package ru.adan.silmaril.misc

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.awt.Dimension
import java.awt.Point
import java.time.Instant

/** This file contains serializable classes and serializers for classes that need to be saved into settings.json */

@Serializable
data class WindowSettings(
    var windowPlacement: WindowPlacement = WindowPlacement.Maximized,
    @Serializable(with = WindowPositionSerializer::class)
    var windowPosition: WindowPosition = WindowPosition.Absolute(100.dp, 100.dp),
    @Serializable(with = DpSizeSerializer::class)
    var windowSize: DpSize = DpSize(800.dp, 600.dp),
)

@Serializable
data class FloatWindowSettings(
    val show: Boolean = false,
    @Serializable(with = PointSerializer::class)
    val windowPosition: Point = Point(100, 100),
    @Serializable(with = DimensionSerializer::class)
    val windowSize: Dimension = Dimension(400, 400),
)

@Serializable
sealed class Variable {
    @Serializable
    data class StringValue(val value: String) : Variable() {
        override fun toString(): String = value
    }
    @Serializable
    data class IntValue(val value: Int) : Variable() {
        override fun toString(): String = value.toString()
    }
    @Serializable
    data class FloatValue(val value: Float) : Variable() {
        override fun toString(): String = value.toString()
    }
}

// The module is necessary to serialize the Variable type
// It needs to be explicitly specified in jsonFormat when reading/writing
val module = SerializersModule {
    polymorphic(Variable::class) {
        subclass(Variable.StringValue::class)
        subclass(Variable.IntValue::class)
        subclass(Variable.FloatValue::class)
    }
}

fun String.toVariable(): Variable {
    return when {
        this.toIntOrNull() != null -> Variable.IntValue(this.toInt())
        this.toFloatOrNull() != null -> Variable.FloatValue(this.toFloat())
        else -> Variable.StringValue(this)
    }
}

fun Any.toVariable(): Variable {
    return when (this) {
        is String -> Variable.StringValue(this)
        is Int -> Variable.IntValue(this)
        is Float -> Variable.FloatValue(this)
        else -> Variable.StringValue("invalid")
    }
}

@Serializable
data class ProfileData(
    val enabledGroups: Set<String> = emptySet(),
    val variables: Map<String, Variable> = mapOf(),
)

// A custom serializer for type 'Instant'
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())  // ISO-8601 format by default
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

// Custom serializer for DpSize
object DpSizeSerializer : KSerializer<DpSize> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DpSize") {
        element<Float>("width")
        element<Float>("height")
    }

    override fun serialize(encoder: Encoder, value: DpSize) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeFloatElement(descriptor, 0, value.width.value)
        composite.encodeFloatElement(descriptor, 1, value.height.value)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): DpSize {
        val composite = decoder.beginStructure(descriptor)
        var width = 0f
        var height = 0f
        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> width = composite.decodeFloatElement(descriptor, index)
                1 -> height = composite.decodeFloatElement(descriptor, index)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        composite.endStructure(descriptor)
        return DpSize(width.dp, height.dp)
    }
}

// Custom serializer for WindowPosition
object WindowPositionSerializer : KSerializer<WindowPosition> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WindowPosition") {
        element<Float>("x")
        element<Float>("y")
    }

    override fun serialize(encoder: Encoder, value: WindowPosition) {
        val composite = encoder.beginStructure(descriptor)
        if (value is WindowPosition.Absolute) {
            composite.encodeFloatElement(descriptor, 0, value.x.value)
            composite.encodeFloatElement(descriptor, 1, value.y.value)
        } else {
            throw SerializationException("Only supports Absolute WindowPosition for serialization")
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): WindowPosition {
        val composite = decoder.beginStructure(descriptor)
        var x = 0f
        var y = 0f
        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> x = composite.decodeFloatElement(descriptor, index)
                1 -> y = composite.decodeFloatElement(descriptor, index)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        composite.endStructure(descriptor)
        return WindowPosition.Absolute(x.dp, y.dp)
    }
}

// Custom serializer for Point
object PointSerializer : KSerializer<Point> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Point") {
        element<Int>("x")
        element<Int>("y")
    }

    override fun serialize(encoder: Encoder, value: Point) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeIntElement(descriptor, 0, value.x)
        composite.encodeIntElement(descriptor, 1, value.y)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Point {
        val composite = decoder.beginStructure(descriptor)
        var x = 0
        var y = 0

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> x = composite.decodeIntElement(descriptor, index)
                1 -> y = composite.decodeIntElement(descriptor, index)
                else -> throw IllegalStateException("Unexpected index: $index")
            }
        }

        composite.endStructure(descriptor)
        return Point(x, y)
    }
}

// Custom serializer for Dimension
object DimensionSerializer : KSerializer<Dimension> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Dimension") {
        element<Int>("width")
        element<Int>("height")
    }

    override fun serialize(encoder: Encoder, value: Dimension) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeIntElement(descriptor, 0, value.width)
        composite.encodeIntElement(descriptor, 1, value.height)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Dimension {
        val composite = decoder.beginStructure(descriptor)
        var width = 0
        var height = 0

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> width = composite.decodeIntElement(descriptor, index)
                1 -> height = composite.decodeIntElement(descriptor, index)
                else -> throw IllegalStateException("Unexpected index: $index")
            }
        }

        composite.endStructure(descriptor)
        return Dimension(width, height)
    }
}
