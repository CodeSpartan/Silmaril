package ru.adan.silmaril.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.koin.java.KoinJavaComponent.get
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.TextSize
import ru.adan.silmaril.model.LoreManager

@Composable
fun LoreDialog(itemName: String, onDismiss: () -> Unit) {
    val loreManager = remember { get<LoreManager>(LoreManager::class.java) }
    var loreLines by remember { mutableStateOf<List<String>?>(null) }
    var found by remember { mutableStateOf(false) }

    LaunchedEffect(itemName) {
        val (isFound, loreList) = loreManager.findExactMatchOrAlmost(itemName)
        found = isFound
        loreLines = loreList
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF2B2B2B),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = itemName,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF404040)
                        ),
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("X", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content
                if (loreLines == null) {
                    Text(
                        text = "Ищу...",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (!found) {
                    Text(
                        text = "Не найдено.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(loreLines!!.size) { index ->
                            val loreLine = loreLines!![index]
                            val chunks = ColorfulTextMessage.makeColoredChunksFromTaggedText(loreLine, false)
                            val annotatedString = buildAnnotatedString {
                                chunks.forEach { chunk ->
                                    withStyle(
                                        style = SpanStyle(
                                            color = if (chunk.fg.isLiteral) chunk.fg.color!! else
                                                if (chunk.fg.ansi == AnsiColor.None) Color(0xFFE4E4E4)
                                                else ansiToComposeColor(chunk.fg.ansi, chunk.fg.isBright),
                                            fontSize = when (chunk.textSize) {
                                                TextSize.Small -> 10.sp
                                                TextSize.Normal -> 14.sp
                                                TextSize.Large -> 18.sp
                                            },
                                            background = if (chunk.bg.isLiteral) chunk.bg.color!!
                                            else if (chunk.bg.ansi != AnsiColor.None) ansiToComposeColor(chunk.bg.ansi, chunk.bg.isBright)
                                            else Color.Unspecified
                                        )
                                    ) {
                                        append(chunk.text)
                                    }
                                }
                            }

                            Text(
                                text = annotatedString,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
