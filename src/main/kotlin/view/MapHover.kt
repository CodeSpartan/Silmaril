package view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import misc.FontManager
import misc.rememberFontFamily
import viewmodel.MapViewModel
import xml_schemas.Room
import xml_schemas.Zone

@Composable
fun MapHoverTooltip(room: Room, zone: Zone?, mapViewModel: MapViewModel) {
    Column(modifier = Modifier
        .padding(14.dp)
    ) {
        val robotoFont = FontManager.getFont("RobotoClassic")
        Text(room.name, color = Color.White, fontFamily = robotoFont, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        Text(room.description.replace('\n', ' '), color = Color.White, fontFamily = robotoFont, modifier = Modifier.padding(bottom = 10.dp))
        Row() {
            Text("ID комнаты: ", color = Color.White, fontFamily = robotoFont)
            Text("${room.id}", color = Color.White, fontFamily = robotoFont, fontWeight = FontWeight.Light)
        }

        val exitsTexts: MutableList<String> = mutableListOf()
        room.exitsList.forEach { exit ->
            val dirName = when(exit.direction) {
                "East" -> "Восток"
                "West" -> "Запад"
                "North" -> "Север"
                "South" -> "Юг"
                "Up" -> "Вверх"
                "Down" -> "Вниз"
                else -> exit.direction
            }
            var exitTxt = "$dirName = ${exit.roomId}"
            if (mapViewModel.getZoneByRoomId(exit.roomId) != zone) {
                exitTxt+=" (${mapViewModel.getZoneByRoomId(exit.roomId)?.name?:"не существует"})"
            }
            exitsTexts.add(exitTxt)
        }
        Text("Выходы: ${exitsTexts.joinToString(", ")}", color = Color.White)
    }
}