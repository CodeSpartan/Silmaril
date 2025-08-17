package ru.adan.silmaril.mud_messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.adan.silmaril.misc.formatDuration
import ru.adan.silmaril.misc.joinOrNone
import ru.adan.silmaril.misc.minutesToDaysFormatted
import ru.adan.silmaril.misc.toSmartString
import ru.adan.silmaril.xml_schemas.Affects
import ru.adan.silmaril.xml_schemas.AppliedAffects
import ru.adan.silmaril.xml_schemas.ArmorStats
import ru.adan.silmaril.xml_schemas.Enhance
import ru.adan.silmaril.xml_schemas.Envenom
import ru.adan.silmaril.xml_schemas.Flags
import ru.adan.silmaril.xml_schemas.Ingredient
import ru.adan.silmaril.xml_schemas.ItemSetAffects
import ru.adan.silmaril.xml_schemas.MagicArrows
import ru.adan.silmaril.xml_schemas.NoFlags
import ru.adan.silmaril.xml_schemas.ObjectAffects
import ru.adan.silmaril.xml_schemas.Recipe
import ru.adan.silmaril.xml_schemas.RestrictionFlags
import ru.adan.silmaril.xml_schemas.ScrollOrPotionSpells
import ru.adan.silmaril.xml_schemas.SkillEnhance
import ru.adan.silmaril.xml_schemas.SkillResist
import ru.adan.silmaril.xml_schemas.SpellBook
import ru.adan.silmaril.xml_schemas.WandOrStaffSpell
import ru.adan.silmaril.xml_schemas.WeaponStats
import ru.adan.silmaril.xml_schemas.Wear
import ru.adan.silmaril.xml_schemas.WearingAffect
import kotlin.collections.mutableListOf

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LoreMessage(
    // --- Attributes ---
    @field:JacksonXmlProperty(isAttribute = true, localName = "Name")
    var name: String = "",

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

    // Note the get instead of field. For a boolean property, the standard Java getter method is named isPropertyName(), which caused problems with field.
    // Jackson's core was designed for Java
    @get:JacksonXmlProperty(isAttribute = true, localName = "IsFull")
    val isFull: Boolean = false,

    // --- Child Elements ---
    @field:JacksonXmlProperty(localName = "Wear")
    val wear: Wear? = null,

    @field:JacksonXmlProperty(localName = "ObjectAffects")
    val objectAffects: ObjectAffects? = null,

    @field:JacksonXmlProperty(localName = "Flags")
    val flags: Flags? = null,

    @field:JacksonXmlProperty(localName = "RestrictionFlags")
    val restrictionFlags: RestrictionFlags? = null,

    @field:JacksonXmlProperty(localName = "NoFlags")
    val noFlags: NoFlags? = null,

    @field:JacksonXmlProperty(localName = "Affects")
    val affects: Affects? = null,

    @field:JacksonXmlProperty(localName = "ScrollOrPotionSpells")
    val scrollOrPotionSpells: ScrollOrPotionSpells? = null,

    @field:JacksonXmlProperty(localName = "WearingAffect")
    val wearingAffect: WearingAffect? = null,

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
    var comment: String? = null
) {

    companion object {
        private val logger = KotlinLogging.logger {}
        val EMPTY = LoreMessage() // You might need a default constructor or update this

        fun fromXml(xml: String): LoreMessage? {
            logger.info { "LoreMessage.fromXml(): $xml" }
            val xmlMapper = XmlMapper().registerKotlinModule()
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
                "Объект '<color=cyan>$name</color>', Тип предмета: $type",
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
                "Объект '<color=cyan>$name</color>', Тип предмета: $type",
                *printWearSlots(),
                "Флаги предмета        : <color=dark-cyan>${flags?.flags?.joinOrNone()}</color>",
                "Флаги запрета         : <color=dark-cyan>${restrictionFlags?.restrictionFlags?.joinOrNone()}</color>",
                "Флаги неудобств       : <color=dark-cyan>${noFlags?.noFlags?.joinOrNone()}</color>",
                if ((minLevel ?: 0) > 1) "Требуемый уровень     : <color=dark-cyan>${minLevel}</color>" else null,
                "Аффекты               : <color=dark-cyan>${affects?.affects?.joinOrNone()}</color>",
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
        return if (scrollOrPotionSpells != null && scrollOrPotionSpells.spells.isNotEmpty()) {
            "Заклинания: ${scrollOrPotionSpells.spells.joinToString { "<color=dark-green>"+it.name+"</color>" }}"
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
        return if (appliedEffects == null || appliedEffects.affects.isEmpty()) {
            emptyArray()
        } else {
            val stringBuilderList = mutableListOf<String>()

            stringBuilderList.add("Эффекты на вас:")

            val sortedBySetItems = appliedEffects.affects.sortedBy { it.necessarySetItemsCount }
            printEffectsAsStrings(stringBuilderList, sortedBySetItems)

            stringBuilderList.toTypedArray()
        }
    }

    fun printItemSetEffects(): Array<String> {
        // appliedEffects is a polymorphic list of multiple types of effects
        return if (itemSetAffects == null || itemSetAffects.affects.isEmpty()) {
            emptyArray()
        } else {
            val stringBuilderList = mutableListOf<String>()

            stringBuilderList.add("Аффекты набора ${itemSetAffects.name}:")

            val sortedBySetItems = itemSetAffects.affects.sortedBy { it.necessarySetItemsCount }
            printEffectsAsStrings(stringBuilderList, sortedBySetItems)

            stringBuilderList.toTypedArray()
        }
    }

    fun printEffectsAsStrings(stringBuilderList : MutableList<String>, sortedBySetItems: List<ru.adan.silmaril.xml_schemas.Affect>) {
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
        return if (wear == null || wear.wearSlots.isEmpty()) {
            emptyArray()
        } else {
            val stringBuilderList = mutableListOf<String>()
            for (wearSlot in wear.wearSlots) {
                val wearLine = when (wearSlot) {
                    "ABOUT" -> "Наверное, вы сможете набросить это на плечи."
                    "ARMS" -> "Наверное, вы сможете надеть это на руки."
                    "BODY" -> "Наверное, вы сможете надеть это на тело."
                    "DEF1" -> "Наверное, вы сможете надеть это в дополнительный слот 1."
                    "DEF2" -> "Наверное, вы сможете надеть это в дополнительный слот 2."
                    "DEF3" -> "Наверное, вы сможете надеть это в дополнительный слот 3."
                    "DWIELD" -> "Наверное, вы сможете держать это в обеих руках."
                    "EARS" -> "Наверное, вы сможете вставить это в уши." //@TODO: какой стринг приходит в жабу?
                    "EYES" -> "Наверное, вы сможете надвинуть это на глаза." //@TODO: какой стринг приходит в жабу?
                    "FEET" -> "Наверное, вы сможете в это обуться."
                    "FINGER" -> "Наверное, вы сможете надеть это на палец."
                    "HANDS" -> "Наверное, вы сможете надеть это на кисти рук."
                    "HEAD" -> "Наверное, вы сможете надеть это на голову."
                    "HOLD" -> "Наверное, вы сможете держать это в левой руке."
                    "LEGS" -> "Наверное, вы сможете надеть это на ноги."
                    "NECK" -> "Наверное, вы сможете надеть это на шею."
                    "WAIST" -> "Наверное, вы сможете надеть это вокруг талии."
                    "WIELD" -> "Наверное, вы сможете держать это в правой руке."
                    "WRIST" -> "Наверное, вы сможете надеть это на запястья."
                    else -> "Наверное, вы сможете надеть это в $wearSlot."
                }
                stringBuilderList.add(wearLine)
            }
            stringBuilderList.toTypedArray()
        }
    }
}