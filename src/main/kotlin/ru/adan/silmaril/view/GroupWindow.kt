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
import androidx.compose.material.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.not_same_room
import ru.adan.silmaril.generated.resources.*
import ru.adan.silmaril.generated.resources.standing
import ru.adan.silmaril.generated.resources.target
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.misc.formatMem
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.mud_messages.Position
import kotlin.math.roundToInt
import com.composables.person

@Composable
fun GroupWindow(client: MudConnection, logger: KLogger) {
    val settingsManager: SettingsManager = koinInject()
    val profileManager: ProfileManager = koinInject()
    val settings by settingsManager.settings.collectAsState()

    var internalPadding by remember { mutableStateOf(Offset.Zero) }
    val robotoFont = FontManager.getFont("RobotoClassic")
    val currentColorStyleName = settings.colorStyle
    val currentColorStyle = remember(currentColorStyleName) {StyleManager.getStyle(currentColorStyleName)}

    val groupMates by client.lastGroupMessage.collectAsState()
    val groupKnownHp by profileManager.knownGroupHPs.collectAsState()
    val groupMateMemTimers = remember { mutableStateMapOf<String, Int>() }
    val groupMateWaitTimers = remember { mutableStateMapOf<String, Double>() }

//    var groupMates by remember { mutableStateOf<List<Creature>>(emptyList()) }
//    LaunchedEffect(client) {
//        client.lastGroupMessage.collect { creatures ->
//            groupMates = creatures
//        }
//    }

    // This will tick all mem timers every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            groupMateMemTimers.keys.forEach { name ->
                groupMateMemTimers[name]?.let { currentTime ->
                    if (currentTime > 0) {
                        groupMateMemTimers[name] = currentTime - 1
                    }
                }
            }
        }
    }

    // This will tick all wait timers every 100 ms
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // More frequent delay
            groupMateWaitTimers.keys.forEach { name ->
                groupMateWaitTimers[name]?.let { currentTime ->
                    if (currentTime > 0.0) {
                        // Decrement by the delay interval (0.1 seconds)
                        groupMateWaitTimers[name] = (currentTime - 0.1).coerceAtLeast(0.0)
                    }
                }
            }
        }
    }

    // This will update mem timers if new information arrives and diverges from our local mem y more than 1 seconds
    LaunchedEffect(groupMates) {
        val updatedMemTimers = SnapshotStateMap<String, Int>()
        val updatedWaitTimers = SnapshotStateMap<String, Double>()
        groupMates.forEach { groupMate ->
            val serverMemTime = groupMate.memTime ?: 0
            val localMemTime = groupMateMemTimers[groupMate.name]
            // wait time arrives as "90" to mean "9 seconds", so always divide by 10
            val serverWaitTime = groupMate.waitState?.div(10.0) ?: 0.0
            val localWaitTime = groupMateWaitTimers[groupMate.name]

            if (localMemTime == null) {
                // If we don't have a local timer for this group mate, create one.
                updatedMemTimers[groupMate.name] = serverMemTime
            } else {
                // If a local timer exists, check if the server time is significantly different.
                // We use a threshold of 2 seconds to account for network latency and the update interval.
                if (kotlin.math.abs(serverMemTime - localMemTime) > 1 || serverMemTime == 0) {
                    updatedMemTimers[groupMate.name] = serverMemTime
                } else {
                    // Otherwise, keep the local timer value.
                    updatedMemTimers[groupMate.name] = localMemTime
                }
            }

            if (localWaitTime == null) {
                updatedWaitTimers[groupMate.name] = serverWaitTime
            } else {
                if (kotlin.math.abs(serverWaitTime - localWaitTime) > 0.5 || localWaitTime == 0.0) {
                    updatedWaitTimers[groupMate.name] = serverWaitTime
                } else {
                    updatedWaitTimers[groupMate.name] = localWaitTime
                }
            }
        }
        // Replace the old map with the updated one to remove timers for group mates who have left.
        groupMateMemTimers.clear()
        groupMateMemTimers.putAll(updatedMemTimers)

        groupMateWaitTimers.clear()
        groupMateWaitTimers.putAll(updatedWaitTimers)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentColorStyle.getUiColor(UiColor.AdditionalWindowBackground))
            .onGloballyPositioned { layoutCoordinates -> internalPadding = layoutCoordinates.positionInWindow() }
    ) {
        Column() {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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

                Box(modifier = Modifier.width(50.dp).padding(start = 10.dp), contentAlignment = Alignment.Center) {
                    Text("Статус", color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.width(60.dp).padding(start = 9.dp), contentAlignment = Alignment.Center) {
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

            groupMates.forEachIndexed  { index, groupMate ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        //.background(Color.LightGray)
                        .height(28.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (!groupMate.inSameRoom) {
                        // Icon: Not same room
                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = 3.dp)
                                .width(15.dp)
                                //.background(Color.LightGray)
                                .padding(top = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.not_same_room),
                                contentDescription = "not same room"
                            )
                        }
                    }

                    // Index box
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 16.dp)
                            .width(20.dp)
                            //.background(Color.LightGray)
                            .padding(top = 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index+1}",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor), fontSize = 15.sp, fontFamily = robotoFont)
                    }

                    // Name box
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 41.dp)
                            .width(81.dp)
                            //.background(Color.LightGray)
                            .padding(top = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(groupMate.name.capitalized(),
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
                            .absoluteOffset(x = 123.dp)
                            .width(65.dp)
                            //.background(Color.LightGray)
                            .padding(top = 0.dp, bottom = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val hpColor = when (groupMate.hitsPercent) {
                            in 70.0..100.0 -> currentColorStyle.getUiColor(UiColor.HpGood)
                            in 30.0..70.0 -> currentColorStyle.getUiColor(UiColor.HpMedium)
                            in 0.0..30.0 -> currentColorStyle.getUiColor(UiColor.HpBad)
                            else -> currentColorStyle.getUiColor(UiColor.HpExecrable)
                        }

                        Row (verticalAlignment = Alignment.Top) {
                            // if we know exact max hp, print 250/250
                            if (groupKnownHp.contains(groupMate.name)) {
                                Text("${groupKnownHp[groupMate.name]?.times(groupMate.hitsPercent)?.div(100)?.roundToInt()}",
                                    fontSize = 15.sp,
                                    fontFamily = robotoFont,
                                    color = hpColor,
                                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp)
                                    )
                                Text("/${groupKnownHp[groupMate.name]}",
                                    fontSize = 12.sp,
                                    fontFamily = robotoFont,
                                    color = currentColorStyle.getUiColor(UiColor.GroupSecondaryFontColor),
                                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp),
                                    )
                            }
                            // if we know only hp percent, print 250%
                            else {
                                Text("${groupMate.hitsPercent.roundToInt()}",
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
                        Box(modifier = Modifier.fillMaxWidth((groupMate.hitsPercent.toFloat()/100).coerceIn(0f, 1f))
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
                            .absoluteOffset(x = 197.dp)
                            .width(60.dp)
                            //.background(Color.LightGray)
                            .padding(top = 0.dp, bottom = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row (verticalAlignment = Alignment.Top) {
                            Text("${groupMate.movesPercent.roundToInt()}",
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
                        Box(modifier = Modifier.fillMaxWidth((groupMate.movesPercent.toFloat()/100).coerceIn(0f, 1f))
                            .offset(y = (-3).dp)
                            .height(2.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(2.dp))
                            .background(currentColorStyle.getUiColor(UiColor.Stamina))
                        )

                        // wait time bar
                        val displayWaitTime = groupMateWaitTimers[groupMate.name]
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

                    // Position icon
                    if (groupMate.position != Position.Standing) {
                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = 262.dp)
                                .offset(y = (-2).dp)
                                .width(22.dp)
                                .height(22.dp)
                                //.background(Color.LightGray)
                                .padding(top = 0.dp, bottom = 0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                imageVector = person,
                                contentDescription = "Favorite Icon",
                                //contentScale = ContentScale.FillHeight,
                                colorFilter = ColorFilter.tint(currentColorStyle.getUiColor(UiColor.Stamina)),
                            )
                        }
                    }

                    // Is target icon
                    if (!groupMate.isAttacked) {
                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = 284.dp)
                                .offset(y = (-2).dp)
                                .width(22.dp)
                                //.background(Color.LightGray)
                                .padding(top = 0.dp, bottom = 0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.target),
                                contentDescription = "is target"
                            )
                        }
                    }

                    // Mem box
                    val displayedMem = groupMateMemTimers[groupMate.name]
                    if (displayedMem != null && displayedMem > 0) {
                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = 311.dp)
                                .width(55.dp)
                                .background(Color.LightGray)
                                .padding(top = 0.dp, bottom = 0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                formatMem(displayedMem),
                                fontSize = 12.sp,
                                fontFamily = robotoFont,
                                color = currentColorStyle.getUiColor(UiColor.GroupPrimaryFontColor),
                                modifier = Modifier.padding(bottom = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}