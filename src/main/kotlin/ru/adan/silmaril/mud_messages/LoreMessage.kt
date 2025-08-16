package ru.adan.silmaril.mud_messages

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.misc.formatDuration
import ru.adan.silmaril.misc.joinOrNone
import ru.adan.silmaril.misc.minutesToDaysFormatted
import ru.adan.silmaril.misc.toSmartString
import kotlin.collections.mutableListOf

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
    @field:JacksonXmlProperty(localName = "AppliedAffects")
    val appliedEffects: AppliedAffects? = null,

    @field:JacksonXmlProperty(localName = "ItemSetAffects")
    val itemSetAffects: ItemSetAffects? = null,

    @field:JacksonXmlProperty(localName = "Comments")
    val comment: String? = null
) {

    companion object {
        private val logger = KotlinLogging.logger {}

        fun fromXml(xml: String): LoreMessage? {
            logger.info { "LoreMessage.fromXml(): $xml" }
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

    fun toXml(): String {
        val xmlMapper = XmlMapper().apply {
            enable(SerializationFeature.INDENT_OUTPUT) // Enable pretty-printing
            // We will add our own declaration, so we disable Jackson's default one
            configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, false)
        }
        val xmlBody = xmlMapper.writeValueAsString(this)
        val xmlDeclaration = "<?xml version=\"1.0\" encoding=\"utf-16\"?>"
        return "$xmlDeclaration\n$xmlBody"
    }

    fun loreAsTaggedTexts(): List<String> {
        if (!isFull) {
            return listOfNotNull(
                "Вы узнали некоторую информацию:",
                "Объект '<color=cyan>$name</color>', тип: $type",
                if ((minLevel ?: 0) > 1) "Требуемый уровень     : <color=dark-cyan>${minLevel}</color>" else null,
                "Вес: ${weight?.toSmartString()}, Цена: $price, Рента: $rent($rentEquipped), Таймер: $timer (${minutesToDaysFormatted(timer ?: 0)}), Оффлайн таймер: ${offlineTimer?:0} (${minutesToDaysFormatted(offlineTimer ?: 0)}), Материал: $material",
                *printWearSlots(),
                printWearingAffect(),
                printScrollOrPotionSpells(),
                *printWandOrStaffSpell(),
                *printWeaponStats(),
                *printArmorStats(),
                *printSpellBook(),
                *printIngredient(),
                *printRecipe(),
                *printAppliedEffects(),
                *printItemSetEffects(),
                printCommentText(),
            )
        } else {
            return listOfNotNull(
                "Вы узнали некоторую информацию:",
                "Объект '<color=cyan>$name</color>', тип: $type",
                *printWearSlots(),
                "Флаги предмета        : <color=dark-cyan>${flags.joinOrNone()}</color>",
                "Флаги запрета         : <color=dark-cyan>${restrictionFlags.joinOrNone()}</color>",
                "Флаги неудобств       : <color=dark-cyan>${noFlags.joinOrNone()}</color>",
                if ((minLevel ?: 0) > 1) "Требуемый уровень     : <color=dark-cyan>${minLevel}</color>" else null,
                "Аффекты               : <color=dark-cyan>${affects.joinOrNone()}</color>",
                "Вес: ${weight?.toSmartString()}, Цена: $price, Рента: $rent($rentEquipped), Таймер: $timer (${minutesToDaysFormatted(timer ?: 0)}), Оффлайн таймер: ${offlineTimer ?: 0} (${minutesToDaysFormatted(offlineTimer ?: 0)}), Материал: $material",
                printWearingAffect(),
                printScrollOrPotionSpells(),
                *printWandOrStaffSpell(),
                *printWeaponStats(),
                *printArmorStats(),
                *printSpellBook(),
                *printIngredient(),
                *printRecipe(),
                *printAppliedEffects(),
                *printItemSetEffects(),
                printCommentText(),
            )
        }
    }

    fun printWandOrStaffSpell(): Array<String> {
        if (wandOrStaffSpell != null) {
            return arrayOf(
                "Заклинания:  <color=dark-green>${wandOrStaffSpell.name}</color>",
                "Имеет максимально ${wandOrStaffSpell.totalCharges} заряд, из них ${wandOrStaffSpell.chargesLeft} осталось."
            )
        } else {
            return emptyArray()
        }
    }

    fun printScrollOrPotionSpells(): String? {
        return if (scrollOrPotionSpells.isNotEmpty()) {
            "Заклинания: ${scrollOrPotionSpells.joinToString { "<color=dark-green>"+it.name+"</color>" }}"
        } else {
            null
        }
    }

    fun printWearingAffect(): String? {
        // @TODO: this string hasn't been checked
        return if (wearingAffect != null) {
            "Эффект при надевании или вооружении: ${wearingAffect.affectName}, Уровень: ${wearingAffect.level}, Время ${wearingAffect.resetTimeout}"
        } else null
    }

    fun printWeaponStats(): Array<String> {
        return if (weaponStats != null) {
            arrayOf(
                "Сила удара '${weaponStats.diceCount}D${weaponStats.diceSides}', средняя сила удара в раунд ${weaponStats.averageDamage}.",
                "Требует знаний в области '${weaponStats.requiredSkill}'"
            )
        } else emptyArray()
    }

    fun printArmorStats(): Array<String> {
        return if (armorStats != null) {
            arrayOf(
                "Класс защиты(AC): ${armorStats.armorClass}",
                "Класс брони: ${armorStats.armor}"
            )
        } else emptyArray()
    }

    fun printSpellBook(): Array<String> {
        return if (spellBook != null) {
            arrayOf(
                "Кто может прочитать: ${spellBook.profession}",
                "Наименьший уровень: ${spellBook.learnLevel}",
                "Заклинание: ${spellBook.spellName}",
                "Сколько раз можно произнести: ${if (spellBook.castCount == 0) "бесконечно" else "${spellBook.castCount}"}",
            )
        } else emptyArray()
    }

    fun printIngredient(): Array<String> {
        return if (ingredient != null) {
            arrayOf(
                "Цвет: ${ingredient.color}",
                "Сила: ${ingredient.power}",
            )
        } else emptyArray()
    }

    fun printRecipe(): Array<String> {
        return if (recipe != null) {
            arrayOf(
                "Что: ${recipe.name}",
                "Описание: ${recipe.description}",
                "Уровень использования: ${recipe.minLevel}",
                "Минимальное умение для изучения: ${recipe.minSkillLevel}%",
            )
        } else emptyArray()
    }

    fun printCommentText(): String? {
        return if (comment != null && comment != "") {
            "Заметка: $comment"
        } else null
    }

    fun printAppliedEffects(): Array<String> {
        // appliedEffects is a polymorphic list of multiple types of effects
        return if (appliedEffects == null ||
            (appliedEffects.enhances.isEmpty() && appliedEffects.skillEnhances.isEmpty()
                && appliedEffects.skillResists.isEmpty() && appliedEffects.envenoms.isEmpty() && appliedEffects.magicArrows.isEmpty()
            )
        ) {
            emptyArray()
        } else {
            val stringBuilderList = mutableListOf<String>()

            stringBuilderList.add("Эффекты на вас:")

            val allAffects = appliedEffects.enhances +
                    appliedEffects.skillResists +
                    appliedEffects.skillEnhances +
                    appliedEffects.magicArrows +
                    appliedEffects.envenoms

            val sortedBySetItems = allAffects.sortedBy { it.necessarySetItemsCount }
            printEffectsAsStrings(stringBuilderList, sortedBySetItems)

            stringBuilderList.toTypedArray()
        }
    }

    fun printItemSetEffects(): Array<String> {
        // appliedEffects is a polymorphic list of multiple types of effects
        return if (itemSetAffects == null ||
            (itemSetAffects.enhances.isEmpty() && itemSetAffects.skillEnhances.isEmpty()
                    && itemSetAffects.skillResists.isEmpty() && itemSetAffects.envenoms.isEmpty() && itemSetAffects.magicArrows.isEmpty()
                    )
        ) {
            emptyArray()
        } else {
            val stringBuilderList = mutableListOf<String>()

            stringBuilderList.add("Аффекты набора ${itemSetAffects.name}:")

            val allAffects = itemSetAffects.enhances +
                    itemSetAffects.skillResists +
                    itemSetAffects.skillEnhances +
                    itemSetAffects.magicArrows +
                    itemSetAffects.envenoms

            val sortedBySetItems = allAffects.sortedBy { it.necessarySetItemsCount }
            printEffectsAsStrings(stringBuilderList, sortedBySetItems)

            stringBuilderList.toTypedArray()
        }
    }

    fun printEffectsAsStrings(stringBuilderList : MutableList<String>, sortedBySetItems: List<BasePrerequisiteCount>) {
        sortedBySetItems.forEach { effect ->
            when(effect) {
                is Enhance -> {
                    val effectStringBuilder = StringBuilder()
                    effectStringBuilder.append(" <color=black>${effect.modifiedParameter}</color>: ")
                    effectStringBuilder.append(if (effect.value > 0) "<color=green>+" else "<color=red>")
                    effectStringBuilder.append("${effect.value}")
                    effectStringBuilder.append("</color>")
                    if (effect.sourceSkill != "")
                        effectStringBuilder.append(" (${effect.sourceSkill})")
                    if (effect.duration > 0)
                        effectStringBuilder.append(" [${formatDuration(effect.duration)}]")
                    if (effect.necessarySetItemsCount > 0)
                        effectStringBuilder.append(" (Необходимо ${effect.necessarySetItemsCount} предмет${if (effect.necessarySetItemsCount < 5) "а" else "ов"} из набора)")
                    stringBuilderList.add(effectStringBuilder.toString())
                }

                is SkillEnhance -> {
                    val skillEnhanceBuilder = StringBuilder()
                    val plusOrMinus = if (effect.enhanceValue > 0) "+" else ""
                    skillEnhanceBuilder.append(" $plusOrMinus${effect.enhanceValue} к заклинанию/умению '${effect.skillName}'")
                    if (effect.necessarySetItemsCount > 0)
                        skillEnhanceBuilder.append(" (Необходимо ${effect.necessarySetItemsCount} предмет${if (effect.necessarySetItemsCount < 5) "а" else "ов"} из набора)")
                    stringBuilderList.add(skillEnhanceBuilder.toString())
                }

                is SkillResist -> {
                    val skillResistBuilder = StringBuilder()
                    val plusOrMinus = if (effect.resistValue > 0) "+" else ""
                    skillResistBuilder.append(" Сопротивление заклинанию/умению '${effect.skillName}' $plusOrMinus${effect.resistValue}%")
                    if (effect.necessarySetItemsCount > 0)
                        skillResistBuilder.append(" (Необходимо ${effect.necessarySetItemsCount} предмет${if (effect.necessarySetItemsCount < 5) "а" else "ов"} из набора)")
                    stringBuilderList.add(skillResistBuilder.toString())
                }

                is Envenom -> {
                    val envenomBuilder = StringBuilder()
                    envenomBuilder.append(" Отравить: [${formatDuration(effect.duration)}]")
                    if (effect.necessarySetItemsCount > 0)
                        envenomBuilder.append(" (Необходимо ${effect.necessarySetItemsCount} предмет${if (effect.necessarySetItemsCount < 5) "а" else "ов"} из набора)")
                    stringBuilderList.add(envenomBuilder.toString())
                }

                is MagicArrows -> {
                    val magicArrowsBuilder = StringBuilder()
                    val magicType = when(effect.magicType) {
                        "FIRE" -> "огня"
                        "WATER" -> "воды"
                        "AIR" -> "воздуха"
                        "EARTH" -> "земли"
                        else -> "неизвестного типа"
                    }
                    if (effect.duration > 0)
                        magicArrowsBuilder.append(" Магические стрелы: доп. повреждения магией $magicType ${effect.diceCount}D${effect.diceSides} [${formatDuration(effect.duration)}]")
                    else
                        magicArrowsBuilder.append(" Магические стрелы: доп. повреждения магией $magicType ${effect.diceCount}D${effect.diceSides}")

                    if (effect.necessarySetItemsCount > 0)
                        magicArrowsBuilder.append(" (Необходимо ${effect.necessarySetItemsCount} предмет${if (effect.necessarySetItemsCount < 5) "а" else "ов"} из набора)")
                    stringBuilderList.add(magicArrowsBuilder.toString())
                }
            }
        }
    }

    fun printWearSlots(): Array<String> {
        return if (wearSlots.isEmpty()) {
            emptyArray()
        } else {
            val stringBuilderList = mutableListOf<String>()
            for (wearSlot in wearSlots) {
                val wearLine = when (wearSlot) {
                    "ABOUT" -> "Наверное, вы сможете надеть это на плечи. ?"
                    "ARMS" -> "Наверное, вы сможете надеть это на руки. ?"
                    "BODY" -> "Наверное, вы сможете надеть это на тело."
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
                    "HOLD" -> "Наверное, вы сможете держать это в левой руке."
                    "LEGS" -> "Наверное, вы сможете надеть это на ноги. ?"
                    "NECK" -> "Наверное, вы сможете надеть это на шею."
                    "WAIST" -> "Наверное, вы сможете надеть это на талию. ?" // вокруг талии?
                    "WIELD" -> "Наверное, вы сможете держать это в правой руке."
                    "WRIST" -> "Наверное, вы сможете надеть это на запястья. ?"
                    else -> "Наверное, вы сможете надеть это в $wearSlot."
                }
                stringBuilderList.add(wearLine)
            }
            stringBuilderList.toTypedArray()
        }
    }
}