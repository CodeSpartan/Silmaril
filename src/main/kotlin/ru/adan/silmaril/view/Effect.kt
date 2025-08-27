package ru.adan.silmaril.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.*
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.mud_messages.Affect
import ru.adan.silmaril.visual_styles.ColorStyle

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Effect(
    colorStyle: ColorStyle,
    font: FontFamily,
    effect: CreatureEffect,
    onEffectHover: (show: Boolean, mousePos: Offset, effectPos: Offset) -> Unit
    ) {

    var effectPosInWindow by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(Unit) {
        onDispose {
            onEffectHover(false, Offset.Zero, Offset.Zero)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(32.dp)
            .onGloballyPositioned { layoutCoordinates ->
                effectPosInWindow = layoutCoordinates.positionInWindow()
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                onEffectHover(true, event.changes.first().position, effectPosInWindow)
            }
            .onPointerEvent(PointerEventType.Exit) { event ->
                onEffectHover(false, Offset.Zero, Offset.Zero)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Image(
            painter = painterResource(effect.icon),
            modifier = Modifier.width(24.dp),
            contentDescription = "Effect",
        )

        // Round counting dots
        Box(
            modifier = Modifier
                //.offset(x = 24.dp)
                .width(8.dp)
                .fillMaxHeight()
                .align(Alignment.BottomEnd)
                //.background(Color.DarkGray)
                .padding(end = 4.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            if (effect.isRoundBased && effect.rounds != null && effect.rounds > 0) {
                for (i in 0..effect.rounds.coerceAtMost(6) - 1) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-1 - (4 * i)).dp)
                            .height(2.dp)
                            .width(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorStyle.getUiColor(UiColor.HpMedium))
                    )
                }
            }
        }

        val currentDuration = effect.duration
        if (!effect.isRoundBased && currentDuration != null && currentDuration < 60) {
            Text(
                text = "${currentDuration.coerceAtLeast(0)}",
                color = colorStyle.getUiColor(UiColor.GroupPrimaryFontColor),
                fontSize = 8.sp,
                fontFamily = font,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp)
            )
        }
    }
}

data class CreatureEffect(
    val name: String,
    val icon: DrawableResource,
    val isRoundBased: Boolean,
    var duration: Int?,
    val lastServerDuration: Int?,
    val rounds: Int?
) {
    companion object {
        fun fromAffect(a: Affect): CreatureEffect? {

            val resource = when (a.name) {
                "голод" -> Res.drawable.hunger
                "жажда" -> Res.drawable.thirst
                "полет" -> Res.drawable.hermes
                "ускорение" -> Res.drawable.accelerate
                // blesses
                "благословение" -> Res.drawable.bless
                "точность" -> Res.drawable.bless
                "слепота" -> Res.drawable.blind
                "проклятие" -> Res.drawable.curse
                // cleric heals
                "легкое заживление" -> Res.drawable.heal_cleric
                "серьезное заживление" -> Res.drawable.heal_cleric
                "критическое заживление" -> Res.drawable.heal_cleric
                "заживление" -> Res.drawable.heal_cleric
                // druid heals
                "легкое обновление" -> Res.drawable.heal_druid
                "серьезное обновление" -> Res.drawable.heal_druid
                "критическое обновление" -> Res.drawable.heal_druid
                // holds
                "придержать персону" -> Res.drawable.hold_person
                "придержать любого" -> Res.drawable.hold_person
                "невидимость" -> Res.drawable.invisibility
                "каменное проклятие" -> Res.drawable.petrification
                // poisons
                "яд" -> Res.drawable.poison
                "ядовитый выстрел" -> Res.drawable.poison
                "защита" -> Res.drawable.shield1
                "замедление" -> Res.drawable.slow
                "сила энтов" -> Res.drawable.ents
                "паралич" -> Res.drawable.paralysis
                "шок" -> Res.drawable.shock
                "молчание" -> Res.drawable.silence
                "оглушение" -> Res.drawable.stunned
                "иммуность к оглушению" -> Res.drawable.stun_immune
                "торнадо" -> Res.drawable.tornado
                "слабость" -> Res.drawable.weak
                "болезнь" -> Res.drawable.disease
                "проклятие защиты" -> Res.drawable.armor_curse
                "страх" -> Res.drawable.fear
                "восстановление" -> Res.drawable.fresh
                "сила" -> Res.drawable.strength

                //@TODO use Res.drawable.shield2 for something
                else -> null
            }

            val isRoundBased = when (a.name) {
                "яд" -> true
                "ядовитый выстрел" -> true
                "ускорение" -> true
                "слепота" -> true
                "легкое заживление" -> true
                "серьезное заживление" -> true
                "критическое заживление" -> true
                "заживление" -> true
                "легкое обновление" -> true
                "серьезное обновление" -> true
                "критическое обновление" -> true
                "страх" -> true
                "паралич" -> true
                "шок" -> true
                "молчание" -> true
                "замедление" -> true
                "сила энтов" -> true
                "оглушение" -> true
                "иммуность к оглушению" -> true
                "торнадо" -> true
                "придержать персону" -> true
                "придержать любого" -> true
                else -> false
            }

            if (resource != null) {
                return CreatureEffect(
                    name = a.name,
                    duration = a.duration,
                    lastServerDuration = a.duration,
                    rounds = a.rounds,
                    icon = resource,
                    isRoundBased = isRoundBased,
                )
            }
            return null
        }
    }
}