package ru.adan.silmaril.view.hovertooltips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.TextSize
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.visual_styles.ColorStyle

@Composable
fun LoreTooltip(itemName: String, currentColorStyle: ColorStyle,) {

    val font = remember { FontManager.getFont("RobotoMono") }
    val loreManager : LoreManager = koinInject()
    var lore by remember { mutableStateOf<List<String>?>(null) }
    var found by remember { mutableStateOf(false) }
    var currentFontSize by remember { mutableStateOf(14) }

    LaunchedEffect(Unit) {
        val (isFound, loreList) = loreManager.findExactMatchOrAlmost(itemName)
        found = isFound
        lore = loreList
    }

    if (lore != null) {
        if (found) {
            Box(
                modifier = Modifier.width(600.dp).padding(10.dp),
            ) {
                Column {
                    lore?.forEach { loreLine ->
                        val gluedLine = ColorfulTextMessage.makeColoredChunksFromTaggedText(loreLine, false)
                        val gluedString = gluedLine.joinToString(separator = "", transform = { chunk -> chunk.text})
                        val colorfulMsg = ColorfulTextMessage(gluedLine)
                        val annotatedString = buildAnnotatedString {
                            colorfulMsg.chunks.forEach { chunk ->
                                withStyle(
                                    style = SpanStyle(
                                        color = if (chunk.fg.isLiteral) chunk.fg.color!! else
                                            if (chunk.fg.ansi == AnsiColor.None) Color(0xffe4e4e4)
                                            else currentColorStyle.getAnsiColor(
                                                chunk.fg.ansi,
                                                chunk.fg.isBright
                                            ),
                                        fontSize = when (chunk.textSize) {
                                            TextSize.Small -> (currentFontSize - 4).sp
                                            TextSize.Normal -> currentFontSize.sp
                                            TextSize.Large -> (currentFontSize + 4).sp
                                        },
                                        background = if (chunk.bg.isLiteral)
                                            chunk.bg.color!!
                                        else if (chunk.bg.ansi != AnsiColor.None) currentColorStyle.getAnsiColor(
                                            chunk.bg.ansi,
                                            chunk.bg.isBright
                                        ) else Color.Unspecified,
                                        // You can add other styles like fontWeight here if needed
                                    )
                                ) {
                                    append(chunk.text)
                                }
                            }
                            append("\r") // helps format the copied text, otherwise it has no newlines
                        }
                        androidx.compose.material.Text(
                            text = annotatedString,
                            fontSize = currentFontSize.sp,
                            fontFamily = font
                        )
                    }
                }
            }
        }
        else {
            Text(
                "Не найдено.",
                fontSize = currentFontSize.sp,
                fontFamily = font
            )
        }
    } else {
        Text("Ищу...",
            fontSize = currentFontSize.sp,
            fontFamily = font
        )
    }
}