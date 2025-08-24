package ru.adan.silmaril.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.visual_styles.ColorStyle

@Composable
fun EffectTooltip(effect: CreatureEffect, fontFamily: FontFamily, style: ColorStyle) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Transparent)
    ) {
        Column (modifier = Modifier
            .background(style.getUiColor(UiColor.HoverBackground))
            .height(68.dp)
            .padding(10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row (verticalAlignment = Alignment.CenterVertically)
            {
                Image(
                    painter = painterResource(effect.icon),
                    modifier = Modifier.size(48.dp),
                    contentDescription = "Effect",
                )
                Column (modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        effect.name.capitalized(),
                        fontFamily = fontFamily,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val durationSecs = effect.duration
                    if (!effect.isRoundBased && durationSecs != null && durationSecs > 0) {
                        Text(
                            text = "${(durationSecs / 60) + 1} мин",
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Light,
                            color = Color.White
                        )
                    }
                    val durationRounds = effect.rounds
                    if (effect.isRoundBased && durationRounds != null && durationRounds > 0) {
                        Text(
                            text = "$durationRounds рд",
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Light,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}