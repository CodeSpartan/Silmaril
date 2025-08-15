package ru.adan.silmaril.mud_messages

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.misc.joinOrNone
import ru.adan.silmaril.misc.minutesToDaysFormatted
import ru.adan.silmaril.misc.toSmartString

data class LoreMessage(
    // --- Attributes ---
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    val name: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Type")
    val type: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "Weight")
    val weight: Double? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "Price")
    val price: Int? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "Rent")
    val rent: Int? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "RentEquipped")
    val rentEquipped: Int? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "Timer")
    val timer: Int? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "OfflineTimer")
    val offlineTimer: Int? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "Material")
    val material: String? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "MinLevel")
    val minLevel: Int? = null,

    @field:JacksonXmlProperty(isAttribute = true, localName = "IsFull")
    val isFull: Boolean = false,

    // --- Child Elements ---
    @field:JacksonXmlElementWrapper(localName = "Wear")
    @field:JacksonXmlProperty(localName = "WearSlot")
    val wearSlots: List<String> = emptyList(),

    @field:JacksonXmlElementWrapper(localName = "ObjectAffects")
    @field:JacksonXmlProperty(localName = "ObjectAffect")
    val objectAffects: List<String> = emptyList(),

    @field:JacksonXmlElementWrapper(localName = "Flags")
    @field:JacksonXmlProperty(localName = "Flag")
    val flags: List<String> = emptyList(),

    @field:JacksonXmlElementWrapper(localName = "RestrictionFlags")
    @field:JacksonXmlProperty(localName = "RestrictionFlag")
    val restrictionFlags: List<String> = emptyList(),

    @field:JacksonXmlElementWrapper(localName = "NoFlags")
    @field:JacksonXmlProperty(localName = "NoFlag")
    val noFlags: List<String> = emptyList(),

    @field:JacksonXmlElementWrapper(localName = "Affects")
    @field:JacksonXmlProperty(localName = "Affect")
    val affects: List<String> = emptyList(),

    @field:JacksonXmlProperty(localName = "WearingAffect")
    val wearingAffect: WearingAffect? = null,

    @field:JacksonXmlElementWrapper(localName = "ScrollOrPotionSpells")
    @field:JacksonXmlProperty(localName = "Spell")
    val scrollOrPotionSpells: List<ScrollOrPotionSpell> = emptyList(),

    @field:JacksonXmlProperty(localName = "WandOrStaffSpell")
    val wandOrStaffSpell: WandOrStaffSpell? = null,

    @field:JacksonXmlProperty(localName = "ArmorStats")
    val armorStats: ArmorStats? = null,

    @field:JacksonXmlProperty(localName = "WeaponStats")
    val weaponStats: WeaponStats? = null,

    @field:JacksonXmlProperty(localName = "SpellBook")
    val spellBook: SpellBook? = null,

    @field:JacksonXmlProperty(localName = "Ingredient")
    val ingredient: Ingredient? = null,

    @field:JacksonXmlProperty(localName = "Recipe")
    val recipe: Recipe? = null,

    // The polymorphic list
    @field:JacksonXmlElementWrapper(localName = "AppliedAffects")
    val appliedAffects: List<AppliedAffect> = emptyList(),
) {

    companion object {
        private val logger = KotlinLogging.logger {}
        val EMPTY = LoreMessage()

        fun fromXml(xml: String): LoreMessage? {
            logger.info { xml }
            val xmlMapper = XmlMapper()
            return try {
                xmlMapper.readValue(xml, LoreMessage::class.java)
            } catch (e: Exception) {
                logger.warn { "Offending XML: $xml" }
                logger.error(e) { "An unexpected error occurred." }
                null
            }
        }
    }

    fun getLoreAsTaggedTexts(): List<String> {
        if (!isFull) {
            return mutableListOf(
                "Вы узнали некоторую информацию:",
                "Объект '<color=cyan>$name</color>', тип: $type",
                "Вес: $weight, Цена: $price, Рента: $rent($rentEquipped), Таймер: $timer (${minutesToDaysFormatted(timer ?: 0)}), Оффлайн таймер: ${offlineTimer?:0} (${minutesToDaysFormatted(offlineTimer ?: 0)}), Материал: $material",
                *getWearSlots(),
                *getWandOrStaffSpell(),
                // заклинание
                // заряд
            )
        } else {
            return listOfNotNull(
                "Вы узнали некоторую информацию:",
                "Объект '<color=cyan>$name</color>', тип: $type",
                *getWearSlots(),
                "Флаги предмета        : <color=dark-cyan>${flags.joinOrNone()}</color>",
                "Флаги запрета         : <color=dark-cyan>${restrictionFlags.joinOrNone()}</color>",
                "Флаги неудобств       : <color=dark-cyan>${noFlags.joinOrNone()}</color>",
                if ((minLevel ?: 0) > 1) "Требуемый уровень : <color=dark-cyan>${minLevel}</color>" else null,
                "Аффекты               : <color=dark-cyan>${affects.joinOrNone()}</color>",
                "Вес:  ${weight?.toSmartString()}, Цена: $price, Рента: $rent($rentEquipped), Таймер: $timer (${minutesToDaysFormatted(timer ?: 0)}), Оффлайн таймер: ${offlineTimer ?: 0} (${minutesToDaysFormatted(offlineTimer ?: 0)}), Материал: $material",
                *getWandOrStaffSpell(),
            )
        }
    }

    fun getWandOrStaffSpell(): Array<String> {
        if (wandOrStaffSpell != null) {
            return arrayOf(
                "Заклинания:  <color=dark-green>${wandOrStaffSpell.name}</color>",
                "Имеет максимально ${wandOrStaffSpell.totalCharges} заряд, из них ${wandOrStaffSpell.chargesLeft} осталось."
            )
        } else {
            return emptyArray()
        }
    }

    fun getWearSlots(): Array<String> {
        return if (wearSlots.isEmpty()) {
            emptyArray()
        } else {
            val stringBuilderList = mutableListOf<String>()
            for (wearSlot in wearSlots) {
                when (wearSlot) {
                    "ABOUT" -> "Наверное, вы сможете надеть это на плечи. ?"
                    "ARMS" -> "Наверное, вы сможете надеть это на руки. ?"
                    "BODY" -> "Наверное, вы сможете надеть это на тело. ?"
                    "DEF1" -> "доп слот 1?"
                    "DEF2" -> "доп слот 2?"
                    "DEF3" -> "доп слот 3?"
                    "DWIELD" -> "Наверное, вы сможете держать это в обеих руках."
                    "EARS" -> "Наверное, вы сможете надеть это на уши. ?"
                    "EYES" -> "Наверное, вы сможете надеть это на глаза. ?"
                    "FEET" -> "Наверное, вы сможете надеть это на ступни. ?"
                    "FINGER" -> "Наверное, вы сможете надеть это на палец. ?"
                    "HANDS" -> "Наверное, вы сможете надеть это на кисти рук. ?"
                    "HEAD" -> "Наверное, вы сможете надеть это на голову."
                    "HOLD" -> "Наверное, вы сможете держать это в левой руке. ?"
                    "LEGS" -> "Наверное, вы сможете надеть это на ноги. ?"
                    "NECK" -> "Наверное, вы сможете надеть это на шею."
                    "WAIST" -> "Наверное, вы сможете надеть это на талию. ?" // вокруг талии?
                    "WIELD" -> "Наверное, вы сможете держать это в правой руке."
                    "WRIST" -> "Наверное, вы сможете надеть это на запястья. ?"
                    else -> "Наверное, вы сможете надеть это в $wearSlot."
                }
                stringBuilderList.add("Можно одеть на: <color=green>${wearSlot}</color>")
            }
            stringBuilderList.toTypedArray()
        }
    }
}