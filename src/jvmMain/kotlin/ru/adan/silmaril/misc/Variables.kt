package ru.adan.silmaril.misc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.time.Instant

/**
 * Variable types that can be saved in profile settings.
 */
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
val variableSerializerModule = SerializersModule {
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
