package ru.adan.silmaril.view.hovertooltips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.visual_styles.ColorStyle
import ru.adan.silmaril.xml_schemas.Room
import ru.adan.silmaril.xml_schemas.Zone

@Composable
fun MapHoverTooltip(room: Room, zone: Zone?, mapModel: MapModel, style: ColorStyle) {
    val roomDataManager: RoomDataManager = koinInject()
    val robotoFont = remember { FontManager.getFont("RobotoClassic") }
    val roomComment = roomDataManager.getRoomComment(room.id)

    Column(modifier = Modifier
        .padding(all = 0.dp)
        //.shadow(elevation = 8.dp, shape = RoundedCornerShape(5.dp)) // flickers with white background, try in later versions
        .clip(RoundedCornerShape(5.dp))
        .background(Color.Transparent)
    ) {
        Column(modifier = Modifier
            .background(style.getUiColor(UiColor.HoverBackground))
            .padding(14.dp)
        ) {
            Text(
                room.name,
                color = Color.White,
                fontFamily = robotoFont,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                room.description.replace('\n', ' '),
                color = Color.White,
                fontFamily = robotoFont,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Divider(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                thickness = 1.dp,
                color = style.getUiColor(UiColor.HoverSeparator)
            )

            if (roomComment != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color.White, fontFamily = robotoFont)) {
                            append("Запись: ")
                        }
                        withStyle(style = SpanStyle(color = Color.White, fontFamily = robotoFont, fontWeight = FontWeight.Light)) {
                            append(roomComment)
                        }
                    }
                )

                Divider(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    thickness = 1.dp,
                    color = style.getUiColor(UiColor.HoverSeparator)
                )
            }

            Row (modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("ID комнаты: ", color = Color.White, fontFamily = robotoFont)
                Text("${room.id}", color = Color.White, fontFamily = robotoFont, fontWeight = FontWeight.Light)
            }
//            Text("Координаты: ${room.x}:${room.y}:${room.z}", color = Color.White, fontFamily = robotoFont)
//            Text("Оригинальные координаты: ${room.originalX}:${room.originalY}:${room.originalZ}", color = Color.White, fontFamily = robotoFont)
            Text("Выходы", color = Color.White, fontFamily = robotoFont)

            room.exitsList.forEach { exit ->
                val dirName = when (exit.direction) {
                    "East" -> "Восток"
                    "West" -> "Запад"
                    "North" -> "Север"
                    "South" -> "Юг"
                    "Up" -> "Вверх"
                    "Down" -> "Вниз"
                    else -> exit.direction
                }
                Row (verticalAlignment = Alignment.CenterVertically) {
                    Text(dirName, color = Color.White, fontFamily = robotoFont, modifier = Modifier.width(60.dp).alignByBaseline())
                    Text("=", color = Color.White, fontFamily = robotoFont, fontWeight = FontWeight.Light, modifier = Modifier.width(20.dp).alignByBaseline())
                    Text("${exit.roomId}", color = Color.White, fontFamily = robotoFont, fontWeight = FontWeight.Light, modifier = Modifier.width(45.dp).alignByBaseline())
                    if (mapModel.getZoneByRoomId(exit.roomId) != zone) {
                        Text(" →  ", color = Color.White, fontFamily = robotoFont, modifier = Modifier.offset(y = (-2).dp).alignByBaseline().height(16.dp)) // fix for weird behavior
                        Text(
                            mapModel.getZoneByRoomId(exit.roomId)?.fullName ?: "(не существует)",
                            color = if (mapModel.getZoneByRoomId(exit.roomId) != null) Color.White else Color.Gray,
                            fontFamily = robotoFont,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
            }
        }
    }
}