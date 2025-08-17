package ru.adan.silmaril.xml_schemas

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import javax.xml.namespace.QName
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.jsontype.TypeSerializer

/**
 * start of polymorphic stuff
 * */

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes(
    JsonSubTypes.Type(Enhance::class,      name = "Enhance"),
    JsonSubTypes.Type(SkillResist::class,  name = "SkillResist"),
    JsonSubTypes.Type(SkillEnhance::class, name = "SkillEnhance"),
    JsonSubTypes.Type(MagicArrows::class,  name = "MagicArrows"),
    JsonSubTypes.Type(Envenom::class,      name = "Envenom"),
)
interface Affect

interface SetPrerequisite {
    val necessarySetItemsCount: Int
}

open class BasePrerequisiteCount : SetPrerequisite, Affect {
    @field:JacksonXmlProperty(isAttribute = true, localName = "NecessarySetItemsCount")
    override val necessarySetItemsCount: Int = 0
}

open class BaseAffectsContainer {
    @JsonIgnore
    private val _affects = mutableListOf<Affect>()

    @get:JacksonXmlElementWrapper(useWrapping = false)
    @get:JsonSerialize(contentUsing = AffectItemXmlSerializer::class)
    open val affects: List<Affect>
        get() = _affects

    @JsonAnySetter
    fun addUnknown(name: String, node: JsonNode) {
        val wrapper = MAPPER.nodeFactory.objectNode().also { it.set<JsonNode>(name, node) }
        _affects += MAPPER.treeToValue(wrapper, Affect::class.java)
    }

    companion object {
        private val MAPPER: ObjectMapper = XmlMapper().registerKotlinModule()
    }
}

class AppliedAffects : BaseAffectsContainer()

data class ItemSetAffects(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = ""
) : BaseAffectsContainer()

// for serialization to xml
// this is a custom serializer for the polymorphic Affect, because standard tools just can't handle it correctly
class AffectItemXmlSerializer : JsonSerializer<Affect>() {
    override fun serialize(value: Affect, gen: JsonGenerator, serializers: SerializerProvider) {
        val elemName = value.javaClass.getAnnotation(JsonTypeName::class.java)?.value
            ?: value.javaClass.simpleName
        (gen as? ToXmlGenerator)?.setNextName(QName("", elemName))

        // Serialize concrete subtype (no polymorphic wrapper)
        serializers.findValueSerializer(value.javaClass, null)
            .serialize(value, gen, serializers)
    }

    // Suppress polymorphic type id handling; we already control the element name
    override fun serializeWithType(
        value: Affect,
        gen: JsonGenerator,
        serializers: SerializerProvider,
        typeSer: TypeSerializer
    ) {
        serialize(value, gen, serializers)
    }
}


// implementations of Affect

@JsonTypeName("SkillResist")
data class SkillResist(
    @field:JacksonXmlProperty(isAttribute = true, localName = "SkillName")
    val skillName: String = "",
    @field:JacksonXmlProperty(isAttribute = true, localName = "ResistValue")
    val resistValue: Int = 0
) : BasePrerequisiteCount()

@JsonTypeName("SkillEnhance")
data class SkillEnhance(
    @field:JacksonXmlProperty(isAttribute = true, localName = "SkillName")
    val skillName: String = "",
    @field:JacksonXmlProperty(isAttribute = true, localName = "EnhanceValue")
    val enhanceValue: Int = 0
) : BasePrerequisiteCount()

@JsonTypeName("MagicArrows")
data class MagicArrows(
    @field:JacksonXmlProperty(isAttribute = true, localName = "MagicType")
    val magicType: String = "",
    @field:JacksonXmlProperty(isAttribute = true, localName = "DiceSides")
    val diceSides: Int = 0,
    @field:JacksonXmlProperty(isAttribute = true, localName = "DiceCount")
    val diceCount: Int = 0,
    @field:JacksonXmlProperty(isAttribute = true, localName = "Duration")
    val duration: Int = 0
) : BasePrerequisiteCount()

@JsonTypeName("Envenom")
data class Envenom(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Duration")
    val duration: Int = 0
) : BasePrerequisiteCount()

@JsonTypeName("Enhance")
data class Enhance(
    @field:JacksonXmlProperty(isAttribute = true, localName = "SourceSkill")
    val sourceSkill: String = "",
    @field:JacksonXmlProperty(isAttribute = true, localName = "Type")
    val modifiedParameter: String = "",
    @field:JacksonXmlProperty(isAttribute = true, localName = "Value")
    val value: Int = 0,
    @field:JacksonXmlProperty(isAttribute = true, localName = "Duration")
    val duration: Int = 0
) : BasePrerequisiteCount()

/**
 * end of polymorphic stuff
 * */


// Corresponds to the <ArmorStats ... /> element
data class ArmorStats(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Armor")
    val armor: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "ArmorClass")
    val armorClass: Int = 0
)

data class ScrollOrPotionSpell(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = ""
)

data class WandOrStaffSpell(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "TotalCharges")
    val totalCharges: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "ChargesLeft")
    val chargesLeft: Int = 0
)

data class WearingAffect(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val affectName: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Level")
    val level: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "ResetTimeout")
    val resetTimeout: Int = 0
)

data class WeaponStats(
    @field:JacksonXmlProperty(isAttribute = true, localName = "RequiredSkill")
    val requiredSkill: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "AverageDamage")
    val averageDamage: Float = 0.0f,

    @field:JacksonXmlProperty(isAttribute = true, localName = "DiceSides")
    val diceSides: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "DiceCount")
    val diceCount: Int = 0
)

data class SpellBook(
    @field:JacksonXmlProperty(isAttribute = true, localName = "SpellName")
    val spellName: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Profession")
    val profession: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "CastCount")
    val castCount: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "LearnLevel")
    val learnLevel: Int = 0
)

data class Ingredient(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Color")
    val color: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Power")
    val power: Int = 0
)

data class Recipe(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Description")
    val description: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "MinSkillLevel")
    val minSkillLevel: Int = 0,

    @field:JacksonXmlProperty(isAttribute = true, localName = "MinLevel")
    val minLevel: Int = 0
)

// For <Wear><WearSlot>...</WearSlot></Wear>
data class Wear(
    // The combination of these two annotations is the fix
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "WearSlot")
    val wearSlots: List<String> = emptyList()
)

// For <Flags><Flag>...</Flag></Flags>
data class Flags(
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "Flag")
    val flags: List<String> = emptyList()
)

// For <ObjectAffects><ObjectAffect>...</ObjectAffect></ObjectAffects>
data class ObjectAffects(
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "ObjectAffect")
    val objectAffects: List<String> = emptyList()
)

// For <RestrictionFlags><RestrictionFlag>...</RestrictionFlag></RestrictionFlags>
data class RestrictionFlags(
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "RestrictionFlag")
    val restrictionFlags: List<String> = emptyList()
)

// For <NoFlags><NoFlag>...</NoFlag></NoFlags>
data class NoFlags(
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "NoFlag")
    val noFlags: List<String> = emptyList()
)

// For <Affects><Affect>...</Affect></Affects>
data class Affects(
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "Affect")
    val affects: List<String> = emptyList()
)

// For <ScrollOrPotionSpells><Spell>...</Spell></ScrollOrPotionSpells>
data class ScrollOrPotionSpells(
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = "Spell")
    val spells: List<ScrollOrPotionSpell> = emptyList()
)