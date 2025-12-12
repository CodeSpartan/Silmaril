package ru.adan.silmaril.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.adan.silmaril.model.Creature

/**
 * Shared state for group information
 */
data class GroupState(
    val members: List<Creature> = emptyList(),
    val myName: String = "",
    val isLeader: Boolean? = null
)

/**
 * Shared ViewModel for group status management
 */
open class GroupViewModel {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _groupState = MutableStateFlow(GroupState())
    val groupState: StateFlow<GroupState> = _groupState.asStateFlow()

    fun updateGroupMembers(members: List<Creature>) {
        _groupState.value = _groupState.value.copy(members = members)
    }

    fun updateMyName(name: String) {
        _groupState.value = _groupState.value.copy(myName = name)
        updateLeaderStatus()
    }

    private fun updateLeaderStatus() {
        val state = _groupState.value
        if (state.members.isEmpty() || state.myName.isEmpty()) {
            _groupState.value = state.copy(isLeader = null)
        } else {
            val isLeader = state.members.firstOrNull()?.name == state.myName
            _groupState.value = state.copy(isLeader = isLeader)
        }
    }

    fun getMyStamina(): Int? {
        val state = _groupState.value
        if (state.members.isEmpty() || state.myName.isEmpty()) return null
        return state.members.find { it.name == state.myName }?.movesPercent?.toInt()
    }

    fun clearGroup() {
        _groupState.value = GroupState()
    }
}
