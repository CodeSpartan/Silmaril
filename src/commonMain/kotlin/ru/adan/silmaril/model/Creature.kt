package ru.adan.silmaril.model

import ru.adan.silmaril.misc.Position

/**
 * Represents an affect/buff/debuff on a creature
 */
data class Affect(
    val name: String = "",
    val duration: Int? = null,
    val rounds: Int? = null
)

/**
 * Represents a creature in the game (player, NPC, pet, or monster)
 */
data class Creature(
    val name: String,
    val targetName: String,
    val position: Position,
    val hitsPercent: Double,
    val movesPercent: Double,
    val isAttacked: Boolean,
    val affects: List<Affect>,
    val isGroupMate: Boolean,

    // Group-specific
    val inSameRoom: Boolean,
    // NPC specific
    val isPlayerCharacter: Boolean,
    val isBoss: Boolean,
    // Pet(groupmate)-specific properties (nullable)
    val owner: String? = null,
    // Groupmate-specific properties (nullable)
    val memTime: Int? = null,
    var waitState: Double? = null
) {
    fun getTargetingName(): String {
        return targetName.replace(' ', '.')
    }

    fun getRealWaitState(): Double {
        if (waitState == null)
            return 0.0
        return if (affects.any { effect -> effect.name == "замедление" }) waitState!! * 2
        else waitState!!
    }
}

/**
 * Extension function to get target name with index for creatures with same name
 */
fun List<Creature>.targetName(targetCreature: Creature): String {
    val creaturesWithSameName = this.filter { it.name == targetCreature.name }
    val index = creaturesWithSameName.indexOfFirst { it === targetCreature } + 1
    return if (index == 1) {
        targetCreature.getTargetingName()
    } else {
        "$index.${targetCreature.getTargetingName()}"
    }
}
