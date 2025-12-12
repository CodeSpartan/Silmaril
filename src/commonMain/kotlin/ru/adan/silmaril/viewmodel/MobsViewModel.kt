package ru.adan.silmaril.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.adan.silmaril.model.Creature

/**
 * State for room monsters/NPCs
 */
data class MobsState(
    val mobs: List<Creature> = emptyList(),
    val isNewRound: Boolean = false,
    val targetedMob: Creature? = null
)

/**
 * Shared ViewModel for room mobs management
 */
open class MobsViewModelBase {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _mobsState = MutableStateFlow(MobsState())
    val mobsState: StateFlow<MobsState> = _mobsState.asStateFlow()

    fun updateMobs(mobs: List<Creature>, isNewRound: Boolean = false) {
        _mobsState.value = _mobsState.value.copy(
            mobs = mobs,
            isNewRound = isNewRound
        )
    }

    fun setTargetedMob(mob: Creature?) {
        _mobsState.value = _mobsState.value.copy(targetedMob = mob)
    }

    fun clearMobs() {
        _mobsState.value = MobsState()
    }

    /**
     * Get mobs sorted by various criteria
     */
    fun getMobsSortedByHealth(): List<Creature> {
        return _mobsState.value.mobs.sortedBy { it.hitsPercent }
    }

    fun getMobsSortedByName(): List<Creature> {
        return _mobsState.value.mobs.sortedBy { it.name }
    }

    fun getAttackedMobs(): List<Creature> {
        return _mobsState.value.mobs.filter { it.isAttacked }
    }

    fun getBossMobs(): List<Creature> {
        return _mobsState.value.mobs.filter { it.isBoss }
    }
}
