package ru.adan.silmaril.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.*
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.misc.formatMem
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.mud_messages.Position
import ru.adan.silmaril.view.hovertooltips.EffectTooltip
import ru.adan.silmaril.view.hovertooltips.LocalHoverManager
import java.util.Objects
import kotlin.math.roundToInt

@Composable
fun GroupWindow(client: MudConnection, logger: KLogger) {
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

    val creatures by client.lastGroupMessage.collectAsState()
    val creatureKnownHPs by profileManager.knownGroupHPs.collectAsState()
    val creatureMemTimers = remember { mutableStateMapOf<String, Int>() }
    val creatureWaitTimers = remember { mutableStateMapOf<String, Double>() }
    val creatureEffects = remember { mutableStateMapOf<Int, List<CreatureEffect>>()} // key is the order of creatures in the message

    // This will tick all mem timers every second
    // And also tick all effects by 1 sec
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            creatureMemTimers.keys.forEach { name ->
                creatureMemTimers[name]?.let { currentTime ->
                    if (currentTime > 0) {
                        creatureMemTimers[name] = currentTime - 1
                    }
                }
            }
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

    // This will tick all wait timers every 100 ms
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // More frequent delay
            creatureWaitTimers.keys.forEach { name ->
                creatureWaitTimers[name]?.let { currentTime ->
                    if (currentTime > 0.0) {
                        // Decrement by the delay interval (0.1 seconds)
                        creatureWaitTimers[name] = (currentTime - 0.1).coerceAtLeast(0.0)
                    }
                }
            }
        }
    }

    // This will update mem timers if new information arrives and diverges from our local mem y more than 1 seconds
    LaunchedEffect(creatures) {
        val updatedMemTimers = SnapshotStateMap<String, Int>()
        val updatedWaitTimers = SnapshotStateMap<String, Double>()
        val updatedEffects = SnapshotStateMap<Int, List<CreatureEffect>>()

        // update mem timers and wait timers
        creatures.forEachIndexed { index, creature ->
            val serverMemTime = creature.memTime ?: 0
            val localMemTime = creatureMemTimers[creature.name]
            // wait time arrives as "90" to mean "9 seconds", so always divide by 10
            val serverWaitTime = creature.waitState?.div(10.0) ?: 0.0
            val localWaitTime = creatureWaitTimers[creature.name]

            if (localMemTime == null) {
                // If we don't have a local timer for this group mate, create one.
                updatedMemTimers[creature.name] = serverMemTime
            } else {
                // If a local timer exists, check if the server time is significantly different.
                // We use a threshold of 2 seconds to account for network latency and the update interval.
                if (kotlin.math.abs(serverMemTime - localMemTime) > 1 || serverMemTime == 0) {
                    updatedMemTimers[creature.name] = serverMemTime
                } else {
                    // Otherwise, keep the local timer value.
                    updatedMemTimers[creature.name] = localMemTime
                }
            }

            if (localWaitTime == null) {
                updatedWaitTimers[creature.name] = serverWaitTime
            } else {
                if (kotlin.math.abs(serverWaitTime - localWaitTime) > 0.5 || localWaitTime == 0.0) {
                    updatedWaitTimers[creature.name] = serverWaitTime
                } else {
                    updatedWaitTimers[creature.name] = localWaitTime
                }
            }

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
        // Replace the old map with the updated one to remove timers for group mates who have left.
        creatureMemTimers.clear()
        creatureMemTimers.putAll(updatedMemTimers)

        creatureWaitTimers.clear()
        creatureWaitTimers.putAll(updatedWaitTimers)

        creatureEffects.clear()
        creatureEffects.putAll(updatedEffects)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentColorStyle.getUiColor(UiColor.AdditionalWindowBackground))
            .onGloballyPositioned { layoutCoordinates -> internalPadding = layoutCoordinates.positionInWindow() }
    ) {
        Column() {
            // Title row
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.width(123.dp).padding(start = 40.dp)) {
                    Text(text="Согрупник", color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 12.sp, fontFamily = robotoFont)
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

                Box(modifier = Modifier.width(60.dp).padding(start = 0.dp), contentAlignment = Alignment.Center) {
                    Text("Мем", color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                    // Icon: not same room
                    Box(
                        modifier = Modifier
                            .width(21.dp)
                            .height(21.dp)
                            //.background(Color.LightGray)
                            .padding(top = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!creature.inSameRoom) {
                            Image(
                                painter = painterResource(Res.drawable.not_same_room),
                                contentDescription = "not same room",
                                modifier = Modifier.offset(x = (-2).dp),
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
                            .width(82.dp)
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

                        // wait time bar
                        val displayWaitTime = creatureWaitTimers[creature.name]
                        if (displayWaitTime != null && displayWaitTime > 0.0) {
                            Box(
                                modifier = Modifier.fillMaxWidth((displayWaitTime.coerceIn(0.0, 8.0) / 8.0).toFloat())
                                    //.offset(y = (-1).dp)
                                    .height(2.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(currentColorStyle.getUiColor(UiColor.WaitTime))
                            )
                        }
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


                    // Mem box
                    val displayedMem = creatureMemTimers[creature.name]
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            //.background(Color.DarkGray)
                            .padding(top = 5.dp, bottom = 0.dp, start = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (displayedMem != null && displayedMem > 0) {
                            Text(
                                formatMem(displayedMem),
                                fontSize = 12.sp,
                                fontFamily = robotoFont,
                                color = currentColorStyle.getUiColor(UiColor.GroupPrimaryFontColor),
                                modifier = Modifier.padding(bottom = 1.dp),
                            )
                        }
                    }

                    // Effects
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(top = 0.dp, bottom = 0.dp, end = 10.dp),
                            //.background(Color.LightGray)
                        verticalAlignment = Alignment.Top
                    ) {
                        creatureEffects[index]?.forEachIndexed { effectIndex, effect ->
                            Effect(currentColorStyle, robotoFont, effect, onEffectHover = { show, mousePos, effectPosInWindow ->
                                tooltipOffset =  (internalPadding + effectPosInWindow + Offset(50f, 35f)) / dpi
                                if (show) {
                                    hoverManager.show(
                                        ownerWindow,
                                        tooltipOffset,
                                        250,
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