package ru.adan.silmaril.view

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import ru.adan.silmaril.model.Affect
import ru.adan.silmaril.model.Creature
import kotlin.math.abs

// Height for one creature row (2 lines: name+bars, status+buffs, plus 4dp vertical padding)
private val CreatureRowHeight = 30.dp
// Max visible creatures before scrolling
private const val MaxVisibleCreatures = 4

/**
 * Check if an affect is round-based (matches desktop's Effect.kt logic)
 */
private fun isRoundBasedAffect(name: String): Boolean = when (name) {
    "яд", "ядовитый выстрел", "ускорение", "слепота",
    "легкое заживление", "серьезное заживление", "критическое заживление", "заживление",
    "легкое обновление", "серьезное обновление", "критическое обновление",
    "страх", "паралич", "шок", "молчание", "замедление", "сила энтов",
    "оглушение", "иммуность к оглушению", "торнадо",
    "придержать персону", "придержать любого", "портал",
    "защита от света", "защита от тьмы" -> true
    else -> false
}

/**
 * Data class to hold locally managed affect state with both server and local duration
 */
private data class LocalAffect(
    val name: String,
    val lastServerDuration: Int?, // What server originally sent (for comparison)
    val localDuration: Int?,      // Our locally ticking countdown
    val rounds: Int?,             // Round-based - not ticked locally
    val isRoundBased: Boolean
)

/**
 * Data class to hold locally managed timer state for a creature
 */
private data class CreatureTimerState(
    val memTime: Int = 0,
    val waitTime: Double = 0.0,
    val affects: List<LocalAffect> = emptyList()
)

/**
 * Group and Mobs widgets displayed side-by-side at 50% width each.
 * Shows at the top of the main view.
 * Manages local timers for mem, wait, and buff durations like desktop does.
 */
@Composable
fun GroupMobsWidgets(
    groupStatus: List<Creature>,
    mobs: List<Creature>,
    modifier: Modifier = Modifier,
    onGroupCommand: (creatureIndex: Int, commandType: String) -> Unit = { _, _ -> },
    onMobCommand: (creatureIndex: Int, commandType: String) -> Unit = { _, _ -> }
) {
    // State for showing action menu
    var showGroupMenu by remember { mutableStateOf(false) }
    var showMobMenu by remember { mutableStateOf(false) }
    var selectedGroupIndex by remember { mutableStateOf(-1) }
    var selectedMobIndex by remember { mutableStateOf(-1) }
    // Local timer state for group members (keyed by creature name)
    val groupTimers = remember { mutableStateMapOf<String, CreatureTimerState>() }
    // Local timer state for mobs (keyed by creature name)
    val mobTimers = remember { mutableStateMapOf<String, CreatureTimerState>() }

    // Tick mem timers and buff durations every 1 second
    // Only tick duration-based affects (not round-based), and only if localDuration > 0
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            // Tick group timers
            groupTimers.keys.toList().forEach { name ->
                groupTimers[name]?.let { state ->
                    groupTimers[name] = state.copy(
                        memTime = (state.memTime - 1).coerceAtLeast(0),
                        affects = state.affects.map { affect ->
                            // Only tick duration-based affects (NOT round-based)
                            if (!affect.isRoundBased && affect.localDuration != null && affect.localDuration > 0) {
                                affect.copy(localDuration = affect.localDuration - 1)
                            } else {
                                affect
                            }
                        }
                    )
                }
            }
            // Tick mob timers
            mobTimers.keys.toList().forEach { name ->
                mobTimers[name]?.let { state ->
                    mobTimers[name] = state.copy(
                        memTime = (state.memTime - 1).coerceAtLeast(0),
                        affects = state.affects.map { affect ->
                            if (!affect.isRoundBased && affect.localDuration != null && affect.localDuration > 0) {
                                affect.copy(localDuration = affect.localDuration - 1)
                            } else {
                                affect
                            }
                        }
                    )
                }
            }
        }
    }

    // Tick wait timers every 100ms for smooth animation
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            groupTimers.keys.toList().forEach { name ->
                groupTimers[name]?.let { state ->
                    if (state.waitTime > 0.0) {
                        groupTimers[name] = state.copy(
                            waitTime = (state.waitTime - 0.1).coerceAtLeast(0.0)
                        )
                    }
                }
            }
            mobTimers.keys.toList().forEach { name ->
                mobTimers[name]?.let { state ->
                    if (state.waitTime > 0.0) {
                        mobTimers[name] = state.copy(
                            waitTime = (state.waitTime - 0.1).coerceAtLeast(0.0)
                        )
                    }
                }
            }
        }
    }

    // Update local timers when server data arrives
    LaunchedEffect(groupStatus) {
        val newTimers = mutableMapOf<String, CreatureTimerState>()
        groupStatus.forEach { creature ->
            val serverMemTime = creature.memTime ?: 0
            val serverWaitTime = (creature.waitState ?: 0.0) / 10.0 // Server sends as "90" meaning "9 seconds"
            val localState = groupTimers[creature.name]

            val newMemTime = if (localState == null) {
                serverMemTime
            } else {
                // Only update if server value differs significantly or is 0
                if (abs(serverMemTime - localState.memTime) > 1 || serverMemTime == 0) {
                    serverMemTime
                } else {
                    localState.memTime
                }
            }

            val newWaitTime = if (localState == null) {
                serverWaitTime
            } else {
                // Only update if server value differs significantly or local is 0
                if (abs(serverWaitTime - localState.waitTime) > 0.5 || localState.waitTime == 0.0) {
                    serverWaitTime
                } else {
                    localState.waitTime
                }
            }

            // For affects, compare lastServerDuration to decide whether to keep local countdown
            // MUD only updates duration with precision of 60 seconds, so we cache our countdown
            val newAffects = creature.affects.map { serverAffect ->
                val isRoundBased = isRoundBasedAffect(serverAffect.name)
                val localAffect = localState?.affects?.find { it.name == serverAffect.name }

                if (localAffect != null && serverAffect.duration == localAffect.lastServerDuration) {
                    // Server duration unchanged - keep our local countdown
                    localAffect
                } else {
                    // Server duration changed (buff renewed or new buff) - use server value
                    LocalAffect(
                        name = serverAffect.name,
                        lastServerDuration = serverAffect.duration,
                        localDuration = serverAffect.duration,
                        rounds = serverAffect.rounds,
                        isRoundBased = isRoundBased
                    )
                }
            }

            newTimers[creature.name] = CreatureTimerState(
                memTime = newMemTime,
                waitTime = newWaitTime,
                affects = newAffects
            )
        }
        groupTimers.clear()
        groupTimers.putAll(newTimers)
    }

    // Update mob timers when server data arrives
    LaunchedEffect(mobs) {
        val newTimers = mutableMapOf<String, CreatureTimerState>()
        mobs.forEach { creature ->
            val serverMemTime = creature.memTime ?: 0
            val serverWaitTime = (creature.waitState ?: 0.0) / 10.0
            val localState = mobTimers[creature.name]

            val newMemTime = if (localState == null) {
                serverMemTime
            } else {
                if (abs(serverMemTime - localState.memTime) > 1 || serverMemTime == 0) {
                    serverMemTime
                } else {
                    localState.memTime
                }
            }

            val newWaitTime = if (localState == null) {
                serverWaitTime
            } else {
                if (abs(serverWaitTime - localState.waitTime) > 0.5 || localState.waitTime == 0.0) {
                    serverWaitTime
                } else {
                    localState.waitTime
                }
            }

            val newAffects = creature.affects.map { serverAffect ->
                val isRoundBased = isRoundBasedAffect(serverAffect.name)
                val localAffect = localState?.affects?.find { it.name == serverAffect.name }

                if (localAffect != null && serverAffect.duration == localAffect.lastServerDuration) {
                    localAffect
                } else {
                    LocalAffect(
                        name = serverAffect.name,
                        lastServerDuration = serverAffect.duration,
                        localDuration = serverAffect.duration,
                        rounds = serverAffect.rounds,
                        isRoundBased = isRoundBased
                    )
                }
            }

            newTimers[creature.name] = CreatureTimerState(
                memTime = newMemTime,
                waitTime = newWaitTime,
                affects = newAffects
            )
        }
        mobTimers.clear()
        mobTimers.putAll(newTimers)
    }

    // Calculate height based on max creatures, capped at 4
    val groupCount = groupStatus.size
    val mobsCount = mobs.size
    val maxCount = maxOf(groupCount, mobsCount).coerceIn(1, MaxVisibleCreatures)
    val widgetHeight = (maxCount * CreatureRowHeight.value).dp + 8.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(widgetHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Group widget - always 50% width
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF252525))
                .padding(4.dp)
        ) {
            if (groupStatus.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(groupStatus) { index, creature ->
                        val timerState = groupTimers[creature.name]
                        // Convert LocalAffects back to Affects with local durations for display
                        val displayAffects = timerState?.affects?.map { localAffect ->
                            Affect(
                                name = localAffect.name,
                                duration = localAffect.localDuration,
                                rounds = localAffect.rounds
                            )
                        } ?: creature.affects
                        val displayCreature = creature.copy(affects = displayAffects)
                        CreatureRow(
                            creature = displayCreature,
                            memTime = timerState?.memTime,
                            waitState = timerState?.waitTime?.times(10.0), // Convert back to server format for CreatureRow
                            onClick = {
                                selectedGroupIndex = index
                                showGroupMenu = true
                            }
                        )
                    }
                }
            }

            // Group action menu popup
            if (showGroupMenu && selectedGroupIndex >= 0) {
                Popup(
                    alignment = Alignment.Center,
                    onDismissRequest = { showGroupMenu = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    Surface(
                        modifier = Modifier.width(150.dp),
                        color = Color(0xFF2a2a2a),
                        elevation = 8.dp,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                            items(5) { cmdNum ->
                                val cmdName = "gcmd${cmdNum + 1}"
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures {
                                                    onGroupCommand(selectedGroupIndex, cmdName)
                                                    showGroupMenu = false
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = cmdName,
                                            color = Color(0xFFe8e8e8),
                                            fontSize = 14.sp
                                        )
                                    }
                                    if (cmdNum < 4) {
                                        Divider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            color = Color(0xFF3d3d3d),
                                            thickness = 1.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mobs widget - always 50% width
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF252525))
                .padding(4.dp)
        ) {
            if (mobs.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(mobs) { index, creature ->
                        val timerState = mobTimers[creature.name]
                        val displayAffects = timerState?.affects?.map { localAffect ->
                            Affect(
                                name = localAffect.name,
                                duration = localAffect.localDuration,
                                rounds = localAffect.rounds
                            )
                        } ?: creature.affects
                        val displayCreature = creature.copy(affects = displayAffects)
                        CreatureRow(
                            creature = displayCreature,
                            memTime = timerState?.memTime,
                            waitState = timerState?.waitTime?.times(10.0),
                            onClick = {
                                selectedMobIndex = index
                                showMobMenu = true
                            }
                        )
                    }
                }
            }

            // Mob action menu popup
            if (showMobMenu && selectedMobIndex >= 0) {
                Popup(
                    alignment = Alignment.Center,
                    onDismissRequest = { showMobMenu = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    Surface(
                        modifier = Modifier.width(150.dp),
                        color = Color(0xFF2a2a2a),
                        elevation = 8.dp,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                            items(5) { cmdNum ->
                                val cmdName = "mcmd${cmdNum + 1}"
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures {
                                                    onMobCommand(selectedMobIndex, cmdName)
                                                    showMobMenu = false
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = cmdName,
                                            color = Color(0xFFe8e8e8),
                                            fontSize = 14.sp
                                        )
                                    }
                                    if (cmdNum < 4) {
                                        Divider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            color = Color(0xFF3d3d3d),
                                            thickness = 1.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
