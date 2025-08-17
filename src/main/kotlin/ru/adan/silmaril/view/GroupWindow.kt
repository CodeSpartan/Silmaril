package ru.adan.silmaril.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ru.adan.silmaril.model.GroupModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.visual_styles.StyleManager
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.mud_messages.Creature

@Composable
fun GroupWindow(client: MudConnection, logger: KLogger) {
    val groupModel: GroupModel = koinInject()
    val settingsManager: SettingsManager = koinInject()
    val settings by settingsManager.settings.collectAsState()

    var internalPadding by remember { mutableStateOf(Offset.Zero) }
    val robotoFont = FontManager.getFont("RobotoClassic")
    val currentColorStyleName = settings.colorStyle
    val currentColorStyle = remember(currentColorStyleName) {StyleManager.getStyle(currentColorStyleName)}

    val groupMates by client.lastGroupMessage.collectAsState()
//    var groupMates by remember { mutableStateOf<List<Creature>>(emptyList()) }
//    LaunchedEffect(client) {
//        client.lastGroupMessage.collect { creatures ->
//            groupMates = creatures
//        }
//    }

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
                Box(modifier = Modifier.width(120.dp).padding(start = 37.dp)) {
                    Text(text="Согрупник", color = currentColorStyle.getUiColor(UiColor.GroupTitleFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.width(65.dp), contentAlignment = Alignment.Center) {
                    Text("HP", color = currentColorStyle.getUiColor(UiColor.GroupTitleFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.width(60.dp).padding(start = 9.dp), contentAlignment = Alignment.Center) {
                    Text("Стамина", color = currentColorStyle.getUiColor(UiColor.GroupTitleFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
                    Text("Мем", color = currentColorStyle.getUiColor(UiColor.GroupTitleFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Эффекты", textAlign = TextAlign.Center, color = currentColorStyle.getUiColor(UiColor.GroupTitleFontColor), fontSize = 12.sp, fontFamily = robotoFont)
                }
            }

            // separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 21.dp, end = 10.dp, top = 2.dp)
                    .height(1.dp)
                    .background(color = currentColorStyle.getUiColor(UiColor.GroupTitleFontColor))
            )

            groupMates.forEachIndexed  { index, groupMate ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(25.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 13.dp)
                            .width(20.dp)
                            //.background(Color.LightGray)
                            .padding(top = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index+1}",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            color = currentColorStyle.getUiColor(UiColor.GroupTitleFontColor), fontSize = 15.sp, fontFamily = robotoFont)
                    }
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 38.dp)
                            .width(81.dp)
                            //.background(Color.LightGray)
                            .padding(top = 3.dp),
                        contentAlignment = Alignment.Center
                    )
                    {
                        Text(groupMate.name.capitalized(),
                            modifier = Modifier.align(Alignment.CenterStart),
                            color = currentColorStyle.getUiColor(UiColor.GroupNameFontColor),
                            fontSize = 15.sp,
                            fontFamily = robotoFont,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            )
                    }

                    // HP Box
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 120.dp)
                            .width(65.dp)
                            .background(Color.LightGray)
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // if know exact max hp
                        // if know only hp percent
                        Row {
                            Text("${groupMate.hitsPercent}")
                            Text("/${groupMate.hitsPercent}")
                        }
                    }
                }
            }


        }
    }
}