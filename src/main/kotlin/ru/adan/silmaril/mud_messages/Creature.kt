package ru.adan.silmaril.mud_messages

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
    val waitState: Double? = null
)

fun GroupMate.toCreature(): Creature {
    return Creature(
        name = this.name,
        targetName = this.targetName,
        position = this.position,
        hitsPercent = this.hitsPercent,
        movesPercent = this.movesPercent,
        isAttacked = this.isAttacked,
        affects = this.affects,
        inSameRoom = this.inSameRoom,
        isGroupMate = true,

        // Logic specific to GroupMate
        isPlayerCharacter = true, // A GroupMate is always a player character
        isBoss = false,           // A GroupMate is never a boss

        // GroupMate-specific fields
        memTime = this.memTime,
        waitState = this.waitState,
    )
}

fun Pet.toCreature(): Creature {
    return Creature(
        name = this.name,
        targetName = this.targetName,
        position = this.position,
        hitsPercent = this.hitsPercent,
        movesPercent = this.movesPercent,
        isAttacked = this.isAttacked,
        affects = this.affects,
        inSameRoom = this.inSameRoom,
        isGroupMate = true,

        // Logic specific to Pet
        isPlayerCharacter = false, // A Pet is never a player character
        isBoss = false,            // A Pet is never a boss
        owner = this.owner
    )
}

fun Monster.toCreature(): Creature {
    return Creature(
        name = this.name,
        targetName = this.targetName,
        position = this.position,
        hitsPercent = this.hitsPercent,
        movesPercent = this.movesPercent,
        isAttacked = this.isAttacked,
        affects = this.affects,
        isGroupMate = false,

        // Logic specific to Monster
        isPlayerCharacter = this.isPlayerCharacter, // This is variable for a Monster
        inSameRoom = true, // a Monster is always in the same room
        isBoss = this.isBoss
    )
}