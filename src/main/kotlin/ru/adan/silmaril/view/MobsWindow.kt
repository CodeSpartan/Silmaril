package ru.adan.silmaril.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import io.github.oshai.kotlinlogging.KLogger
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.visual_styles.StyleManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.*
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.mud_messages.Position
import ru.adan.silmaril.view.hovertooltips.EffectTooltip
import ru.adan.silmaril.view.hovertooltips.LocalHoverManager
import java.util.Objects
import kotlin.math.roundToInt

@Composable
fun MobsWindow(client: MudConnection, logger: KLogger) {
    val settingsManager: SettingsManager = koinInject()
    val profileManager: ProfileManager = koinInject()
    val settings by settingsManager.settings.collectAsState()

    val hoverManager = LocalHoverManager.current
    val ownerWindow = OwnerWindow.current
    var internalPadding by remember { mutableStateOf(Offset.Zero) }
    var tooltipOffset by remember { mutableStateOf(Offset.Zero) }
    val dpi = LocalDensity.current.density

    val robotoFont = FontManager.getFont("RobotoClassic")
    val currentColorStyleName = settings.colorStyle
    val currentColorStyle = remember(currentColorStyleName) {StyleManager.getStyle(currentColorStyleName)}

    val roomMobs by client.lastMonstersMessage.collectAsState()
    val creatures = roomMobs.mobs
    val creatureKnownHPs by profileManager.knownMobsHPs.collectAsState()
    //val creatureMemTimers = remember { mutableStateMapOf<String, Int>() }
    //val creatureWaitTimers = remember { mutableStateMapOf<String, Double>() }
    val creatureEffects = remember { mutableStateMapOf<Int, List<CreatureEffect>>()} // key is the order of creatures in the message

    // This will tick effects every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            creatureEffects.keys.forEach { index ->
                creatureEffects[index]?.let { effects ->
                    val updatedListOfEffects = effects.map { effect ->
                        effect.copy(duration = effect.duration?.minus(1))
                    }
                    creatureEffects[index] = updatedListOfEffects
                }
            }
        }
    }

    // This will update effect timers if new information arrives and diverges from our local timer by more than 1 minute
    LaunchedEffect(creatures) {
        val updatedEffects = SnapshotStateMap<Int, List<CreatureEffect>>()

        // update effect timers
        creatures.forEachIndexed { index, creature ->
            updatedEffects[index] = creature.affects.mapNotNull { affect ->
                CreatureEffect.fromAffect(affect)
            }
            // because MUD only updates effects duration once per minute, but we're counting them down,
            // we have to make them survive between messages that MUD sends, ignoring MUD's values
            // if the value is just like the old one
            for (newEffectInstance in updatedEffects[index]!!) {
                val sameEffectBeforeUpdate = creatureEffects[index]?.firstOrNull { it.name == newEffectInstance.name }
                if (newEffectInstance.lastServerDuration == sameEffectBeforeUpdate?.lastServerDuration) {
                    // keep the duration that we're counting down
                    newEffectInstance.duration = sameEffectBeforeUpdate?.duration
                }
            }
        }
        creatureEffects.clear()
        creatureEffects.putAll(updatedEffects)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentColorStyle.getUiColor(UiColor.AdditionalWindowBackground))
            .border(1.dp, color = if (currentColorStyle.borderAroundFloatWidgets()) JewelTheme.globalColors.borders.normal else Color.Unspecified)
            .onGloballyPositioned { layoutCoordinates -> internalPadding = layoutCoordinates.positionInWindow() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        when (e.type) {
                            PointerEventType.Press -> if (e.buttons.isSecondaryPressed) {
                                profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit)
                                e.changes.forEach { it.consume() }
                            }
                            PointerEventType.Release -> {
                                profileManager.currentMainViewModel.value.focusTarget.tryEmit(Unit)
                            }
                        }
                    }
                }
            }
    ) {
        Column() {
            // Title row
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.width(183.dp).padding(start = 40.dp)) {
                    Text(text="Существо", color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.width(65.dp), contentAlignment = Alignment.Center) {
                    Text("HP", color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.width(66.dp).padding(start = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Стамина", color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.width(52.dp).padding(start = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Статус", color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.weight(1f).padding(start = 9.dp), contentAlignment = Alignment.Center) {
                    Text("Эффекты", textAlign = TextAlign.Center, color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }
            }

            // separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 21.dp, end = 10.dp, top = 2.dp, bottom = 7.dp)
                    .height(1.dp)
                    .background(color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor))
            )

            creatures.forEachIndexed { index, creature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        //.background(Color.LightGray)
                        .height(28.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Icon: is player character
                    Box(
                        modifier = Modifier
                            .width(21.dp)
                            .height(21.dp)
                            //.background(Color.LightGray)
                            .padding(top = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (creature.isPlayerCharacter) {
                            Image(
                                painter = painterResource(Res.drawable.player_character),
                                colorFilter = ColorFilter.tint(currentColorStyle.getUiColor(UiColor.Stamina)),
                                contentDescription = "is player character",
                                modifier = Modifier.size(11.dp),
                            )
                        }
                        if (creature.isBoss) {
                            Image(
                                painter = painterResource(Res.drawable.skull),
                                contentDescription = "is boss",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    // Index box
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .offset(x = (-5).dp)
                            //.background(Color.LightGray)
                            .padding(top = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index+1}",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 15.sp, fontFamily = robotoFont)
                    }

                    // Name box
                    Box(
                        modifier = Modifier
                            .width(142.dp)
                            //.background(Color.LightGray)
                            .padding(top = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(creature.name.capitalized(),
                            modifier = Modifier.align(Alignment.CenterStart),
                            color = currentColorStyle.getUiColor(UiColor.GroupPrimaryFontColor),
                            fontSize = 15.sp,
                            fontFamily = robotoFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // HP Box
                    Box(
                        modifier = Modifier
                            .width(65.dp)
                            //.background(Color.LightGray)
                            .padding(top = 3.dp, bottom = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val hpColor = when (creature.hitsPercent) {
                            in 70.0..100.0 -> currentColorStyle.getUiColor(UiColor.HpGood)
                            in 30.0..70.0 -> currentColorStyle.getUiColor(UiColor.HpMedium)
                            in 0.0..30.0 -> currentColorStyle.getUiColor(UiColor.HpBad)
                            else -> currentColorStyle.getUiColor(UiColor.HpExecrable)
                        }

                        Row (verticalAlignment = Alignment.Top) {
                            // if we know exact max hp, print 250/250
                            if (creatureKnownHPs.contains(creature.name)) {
                                Text("${creatureKnownHPs[creature.name]?.times(creature.hitsPercent)?.div(100)?.roundToInt()}",
                                    fontSize = 15.sp,
                                    fontFamily = robotoFont,
                                    color = hpColor,
                                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp)
                                )
                                Text("/${creatureKnownHPs[creature.name]}",
                                    fontSize = 12.sp,
                                    fontFamily = robotoFont,
                                    color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor),
                                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp),
                                )
                            }
                            // if we know only hp percent, print 250%
                            else {
                                Text("${creature.hitsPercent.roundToInt()}",
                                    fontSize = 15.sp,
                                    fontFamily = robotoFont,
                                    color = hpColor,
                                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp)
                                )
                                Text("%",
                                    fontSize = 12.sp,
                                    fontFamily = robotoFont,
                                    color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor),
                                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp),
                                )
                            }
                        }

                        // background hp bar
                        Box(modifier = Modifier.fillMaxWidth()
                            .offset(y = (-3).dp)
                            .height(2.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(2.dp))
                            .background(currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor))
                        )

                        // foreground hp bar
                        Box(modifier = Modifier.fillMaxWidth((creature.hitsPercent.toFloat()/100).coerceIn(0f, 1f))
                            .offset(y = (-3).dp)
                            .height(2.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(2.dp))
                            .background(hpColor)
                        )
                    }

                    // Stamina box
                    Box(
                        modifier = Modifier
                            .width(69.dp)
                            //.background(Color.LightGray)
                            .padding(top = 3.dp, bottom = 0.dp, start = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row (verticalAlignment = Alignment.Top) {
                            Text("${creature.movesPercent.roundToInt()}",
                                fontSize = 15.sp,
                                fontFamily = robotoFont,
                                color = currentColorStyle.getUiColor(UiColor.Stamina),
                                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp)
                            )
                            Text("%",
                                fontSize = 12.sp,
                                fontFamily = robotoFont,
                                color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor),
                                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp),
                            )
                        }

                        // background stamina bar
                        Box(modifier = Modifier.fillMaxWidth()
                            .offset(y = (-3).dp)
                            .height(2.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(2.dp))
                            .background(currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor))
                        )

                        // foreground stamina bar
                        Box(modifier = Modifier.fillMaxWidth((creature.movesPercent.toFloat()/100).coerceIn(0f, 1f))
                            .offset(y = (-3).dp)
                            .height(2.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(2.dp))
                            .background(currentColorStyle.getUiColor(UiColor.Stamina))
                        )
                    }

                    // Position icon & Target icon
                    Box(
                        modifier = Modifier
                            .offset(y = (-2).dp)
                            .width(49.dp)
                            .height(22.dp)
                            .padding(start = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row {
                            if (creature.position != Position.Standing)
                                Image(
                                    painter = painterResource(
                                        when (creature.position) {
                                            Position.Dying -> Res.drawable.rip
                                            Position.Sleeping -> Res.drawable.sleeping
                                            Position.Resting -> Res.drawable.resting
                                            Position.Sitting -> Res.drawable.sitting
                                            Position.Fighting -> Res.drawable.fighting
                                            //Position.Standing -> Res.drawable.standing
                                            Position.Riding -> Res.drawable.riding
                                            else -> Res.drawable.standing
                                        }
                                    ),
                                    modifier = Modifier.width(22.dp).height(22.dp),
                                    contentDescription = "Position",
                                    colorFilter = when (creature.position) {
                                        Position.Dying -> null
                                        Position.Fighting -> null
                                        Position.Sitting -> ColorFilter.tint(currentColorStyle.getUiColor(UiColor.HpBad))
                                        else -> ColorFilter.tint(currentColorStyle.getUiColor(UiColor.Stamina))
                                    },
                                )
                            if (creature.isAttacked)
                                Image(
                                    painter = painterResource(Res.drawable.target),
                                    modifier = Modifier.width(22.dp),
                                    contentDescription = "Is target",
                                )
                        }
                    }

                    // Effects
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(top = 0.dp, start = 8.dp, bottom = 0.dp, end = 10.dp),
                        //.background(Color.LightGray)
                        verticalAlignment = Alignment.Top
                    ) {
                        creatureEffects[index]?.forEachIndexed { effectIndex, effect ->
                            Effect(currentColorStyle, robotoFont, effect, onEffectHover = { show, mousePos, effectPosInWindow ->
                                tooltipOffset = (internalPadding + effectPosInWindow + Offset(33f, 14f) * dpi ) / dpi
                                if (show) {
                                    hoverManager.show(
                                        ownerWindow,
                                        relativePosition = tooltipOffset,
                                        width = 250,
                                        assumedHeight = 68,
                                        Objects.hash(index, effectIndex, effect.name.hashCode()),
                                    ) {
                                        EffectTooltip(effect, robotoFont, currentColorStyle)
                                    }
                                } else {
                                    hoverManager.hide()
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}