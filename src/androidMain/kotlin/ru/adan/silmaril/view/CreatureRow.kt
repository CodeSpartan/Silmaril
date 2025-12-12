package ru.adan.silmaril.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.adan.silmaril.misc.Position
import ru.adan.silmaril.model.Affect
import ru.adan.silmaril.model.Creature
import ru.adan.silmaril.ui.SilmarilTheme

// Colors matching desktop's ModernBlack style
private val HpGood = Color(0xFF91e966)
private val HpMedium = Color(0xFFe9d866)
private val HpBad = Color(0xFFe94747)
private val HpExecrable = Color(0xFFc91c1c)
private val Stamina = Color(0xFFe7dfd5)
private val WaitTime = Color(0xFFe98447)
private val CombatColor = Color(0xFFFF5252)
private val SittingColor = Color(0xFFe94747) // Same as HpBad on desktop
private val BarBackground = Color(0xFF404040)
private val MemColor = Color(0xFFAAAAAA)

/**
 * Compact creature row for group/mobs display
 * Line 1: Name (truncated) | Mem timer | Stacked bars
 * Line 2: Position + Status + Buffs (single text, wraps naturally)
 */
@Composable
fun CreatureRow(
    creature: Creature,
    memTime: Int? = null,
    waitState: Double? = null,
    onClick: () -> Unit = {}
) {
    val colors = SilmarilTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        // Line 1: Name on left, mem timer, stacked bars on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Name (limited width, truncated with ...)
            Text(
                text = creature.name.replaceFirstChar { it.uppercase() },
                color = if (creature.isAttacked) colors.error else colors.textPrimary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Mem timer (only for groupmates with mem time > 0)
            val displayMem = memTime ?: creature.memTime
            if (displayMem != null && displayMem > 0) {
                Text(
                    text = formatMemTime(displayMem),
                    color = MemColor,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Stacked bars (HP, Stamina, Lag) on the right
            Column(
                modifier = Modifier.width(60.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // HP bar
                ProgressBar(
                    value = (creature.hitsPercent / 100f).toFloat().coerceIn(0f, 1f),
                    color = getHpColor(creature.hitsPercent),
                    modifier = Modifier.fillMaxWidth()
                )

                // Stamina bar
                ProgressBar(
                    value = (creature.movesPercent / 100f).toFloat().coerceIn(0f, 1f),
                    color = Stamina,
                    modifier = Modifier.fillMaxWidth()
                )

                // Lag/wait bar (waitState comes as "90" meaning "9 seconds", so divide by 10)
                val rawWait = waitState ?: creature.waitState ?: 0.0
                val displayWait = rawWait / 10.0
                ProgressBar(
                    value = (displayWait.coerceIn(0.0, 8.0) / 8.0).toFloat(),
                    color = WaitTime,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Line 2: Position + Status + Buffs with individual coloring
        val statusText = buildStatusAnnotatedString(creature, colors.textSecondary)
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                fontSize = 7.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Build the status line with proper coloring for each part
 */
private fun buildStatusAnnotatedString(creature: Creature, defaultColor: Color) = buildAnnotatedString {
    val isInCombat = creature.position == Position.Fighting || creature.isAttacked
    val isSitting = creature.position == Position.Sitting

    // Position - colored based on state
    val positionColor = when {
        isInCombat -> CombatColor
        isSitting -> SittingColor
        else -> defaultColor
    }
    withStyle(SpanStyle(color = positionColor)) {
        append(getPositionText(creature.position))
    }

    // Attacked indicator
    if (creature.isAttacked) {
        withStyle(SpanStyle(color = CombatColor)) {
            append(" Цель")
        }
    }

    // Not in same room indicator
    if (!creature.inSameRoom && creature.isGroupMate) {
        withStyle(SpanStyle(color = defaultColor)) {
            append(" ◈")
        }
    }

    // Buffs - always default color
    val buffText = formatBuffs(creature.affects)
    if (buffText.isNotEmpty()) {
        withStyle(SpanStyle(color = defaultColor)) {
            append(" $buffText")
        }
    }
}

@Composable
private fun ProgressBar(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(BarBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = value)
                .background(color)
        )
    }
}

private fun getHpColor(hitsPercent: Double): Color {
    return when {
        hitsPercent >= 70 -> HpGood
        hitsPercent >= 30 -> HpMedium
        hitsPercent >= 0 -> HpBad
        else -> HpExecrable
    }
}

private fun getPositionText(position: Position): String {
    return when (position) {
        Position.Standing -> "Ст"
        Position.Sitting -> "Сид"
        Position.Resting -> "Отд"
        Position.Sleeping -> "Сп"
        Position.Fighting -> "Бой"
        Position.Dying -> "Ум"
        Position.Riding -> "Вер"
    }
}

/**
 * Format mem time as MM:SS or just seconds
 */
private fun formatMemTime(seconds: Int): String {
    return if (seconds >= 60) {
        val mins = seconds / 60
        val secs = seconds % 60
        String.format("%d:%02d", mins, secs)
    } else {
        "${seconds}с"
    }
}

/**
 * Buff display info: abbreviation and whether it's round-based
 */
private data class BuffInfo(val abbr: String, val isRoundBased: Boolean = false)

/**
 * Map of effect names to display info.
 * Only effects in this map will be displayed - easy to curate.
 * isRoundBased = true means duration is shown in rounds (р), false means seconds (с)
 */
private val buffAbbreviations = mapOf(
    // Basic needs
    "голод" to BuffInfo("Гол"),
    "жажда" to BuffInfo("Жаж"),
    // Movement
    "полет" to BuffInfo("Пол"),
    "ускорение" to BuffInfo("Уск", isRoundBased = true),
    // Blessings
    "благословение" to BuffInfo("Блг"),
    "точность" to BuffInfo("Точ"),
    // Debuffs
    "слепота" to BuffInfo("Слп", isRoundBased = true),
    "проклятие" to BuffInfo("Прк"),
    // Cleric heals
    "легкое заживление" to BuffInfo("ЛЗж", isRoundBased = true),
    "серьезное заживление" to BuffInfo("СЗж", isRoundBased = true),
    "критическое заживление" to BuffInfo("КЗж", isRoundBased = true),
    "заживление" to BuffInfo("Зжв", isRoundBased = true),
    // Druid heals
    "легкое обновление" to BuffInfo("ЛОб", isRoundBased = true),
    "серьезное обновление" to BuffInfo("СОб", isRoundBased = true),
    "критическое обновление" to BuffInfo("КОб", isRoundBased = true),
    // Holds
    "портал" to BuffInfo("Прт", isRoundBased = true),
    "придержать персону" to BuffInfo("Хлд", isRoundBased = true),
    "придержать любого" to BuffInfo("Хлд", isRoundBased = true),
    // Other
    "невидимость" to BuffInfo("Нвд"),
    "каменное проклятие" to BuffInfo("Кам-прок"),
    "яд" to BuffInfo("Яд", isRoundBased = true),
    "ядовитый выстрел" to BuffInfo("Яд", isRoundBased = true),
    "защита" to BuffInfo("Защ"),
    "замедление" to BuffInfo("Медл", isRoundBased = true),
    "сила энтов" to BuffInfo("Энт", isRoundBased = true),
    "паралич" to BuffInfo("Пар", isRoundBased = true),
    "шок" to BuffInfo("Шок", isRoundBased = true),
    "молчание" to BuffInfo("Млч", isRoundBased = true),
    "оглушение" to BuffInfo("Глуш", isRoundBased = true),
    "иммуность к оглушению" to BuffInfo("Глуш-имм", isRoundBased = true),
    "торнадо" to BuffInfo("Трн", isRoundBased = true),
    "слабость" to BuffInfo("Слб"),
    "болезнь" to BuffInfo("Блз"),
    "проклятие защиты" to BuffInfo("ПЗ"),
    "страх" to BuffInfo("Страх", isRoundBased = true),
    "восстановление" to BuffInfo("Вст"),
    "сила" to BuffInfo("Сил"),
    "защита от света" to BuffInfo("ЗСв", isRoundBased = true),
    "защита от тьмы" to BuffInfo("ЗТм", isRoundBased = true),
)

/**
 * Format buffs as abbreviated names with duration.
 * Only buffs present in buffAbbreviations map are displayed.
 * Duration >= 60 shown as minutes (no ticking), < 60 shown as seconds (ticking).
 */
private fun formatBuffs(affects: List<Affect>): String {
    return affects
        .mapNotNull { affect ->
            val info = buffAbbreviations[affect.name] ?: return@mapNotNull null
            when {
                // Round-based with rounds available
                info.isRoundBased && affect.rounds != null && affect.rounds > 0 ->
                    "${info.abbr}(${affect.rounds}р)"
                // Time-based with duration >= 60 seconds - show in minutes rounded up (no visual ticking)
                !info.isRoundBased && affect.duration != null && affect.duration >= 60 ->
                    "${info.abbr}(${(affect.duration + 59) / 60}м)"
                // Time-based with duration < 60 seconds - show seconds (ticking)
                !info.isRoundBased && affect.duration != null && affect.duration > 0 ->
                    "${info.abbr}(${affect.duration}с)"
                // No time info (indefinite)
                else -> info.abbr
            }
        }
        .joinToString(" ")
}
