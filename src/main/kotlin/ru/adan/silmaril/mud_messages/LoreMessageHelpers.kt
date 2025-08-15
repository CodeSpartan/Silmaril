package ru.adan.silmaril.mud_messages

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

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

// This sealed class is the key to handling the polymorphic list in <AppliedAffects>
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.WRAPPER_OBJECT,
    property = ""
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SkillResist::class, name = "SkillResist"),
    JsonSubTypes.Type(value = SkillEnhance::class, name = "SkillEnhance"),
    JsonSubTypes.Type(value = MagicArrows::class, name = "MagicArrows"),
    JsonSubTypes.Type(value = Envenom::class, name = "Envenom"),
    JsonSubTypes.Type(value = Enhance::class, name = "Enhance")
)
sealed class AppliedAffect {
    @field:JacksonXmlProperty(isAttribute = true, localName = "NecessarySetItemsCount")
    open val necessarySetItemsCount: Int = 0
}

@JsonTypeName("SkillResist")
data class SkillResist(
    @field:JacksonXmlProperty(isAttribute = true, localName = "SkillName")
    val skillName: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "ResistValue")
    val resistValue: Int = 0
) : AppliedAffect()

@JsonTypeName("SkillEnhance")
data class SkillEnhance(
    @field:JacksonXmlProperty(isAttribute = true, localName = "SkillName")
    val skillName: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "EnhanceValue")
    val enhanceValue: Int = 0
) : AppliedAffect()

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
) : AppliedAffect()

@JsonTypeName("Envenom")
data class Envenom(
    @field:JacksonXmlProperty(isAttribute = true, localName = "Duration")
    val duration: Int = 0
) : AppliedAffect()

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
) : AppliedAffect()

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