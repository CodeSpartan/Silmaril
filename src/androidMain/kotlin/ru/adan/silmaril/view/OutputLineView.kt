package ru.adan.silmaril.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.misc.ColorfulTextMessage
import ru.adan.silmaril.misc.OutputItem
import ru.adan.silmaril.misc.TextMessageChunk
import ru.adan.silmaril.misc.TextSize

/**
 * Displays a single output line with stable recomposition.
 * Uses OutputItem's id as part of the remember key to avoid rebuilding
 * the annotated string when other messages arrive.
 */
@Composable
fun OutputLineView(item: OutputItem, baseTextSize: Float = 12f) {
    val message = item.message
    var showLoreDialog by remember { mutableStateOf(false) }
    val baseSizeSp = baseTextSize.sp

    if (message.loreItem != null) {
        // Memoize annotated string based on item id and text size
        val annotatedString = remember(item.id, baseTextSize) {
            buildLoreAnnotatedString(message, baseTextSize, baseSizeSp)
        }

        ClickableText(
            text = annotatedString,
            modifier = Modifier.fillMaxWidth(),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = baseSizeSp
            ),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "LORE", start = offset, end = offset)
                    .firstOrNull()?.let {
                        showLoreDialog = true
                    }
            }
        )

        if (showLoreDialog) {
            LoreDialog(
                itemName = message.loreItem,
                onDismiss = { showLoreDialog = false }
            )
        }
    } else {
        // Memoize annotated string based on item id and text size
        val annotatedString = remember(item.id, baseTextSize) {
            buildRegularAnnotatedString(message, baseTextSize)
        }

        Text(
            text = annotatedString,
            modifier = Modifier.fillMaxWidth(),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = baseSizeSp
            )
        )
    }
}

/**
 * Build annotated string for messages with lore links.
 * Pure function - no Compose state/capturing.
 */
private fun buildLoreAnnotatedString(
    message: ColorfulTextMessage,
    baseTextSize: Float,
    baseSizeSp: TextUnit
): AnnotatedString = buildAnnotatedString {
    // Display text before the item link (if exists)
    if (message.chunks.isNotEmpty()) {
        val firstChunk = message.chunks[0]
        withStyle(
            style = SpanStyle(
                color = getChunkColorPure(firstChunk),
                fontSize = getChunkFontSizePure(firstChunk, baseTextSize),
                background = getChunkBackgroundPure(firstChunk),
            )
        ) {
            append(firstChunk.text)
        }
    }

    // Add clickable lore item
    pushStringAnnotation(tag = "LORE", annotation = message.loreItem!!)
    withStyle(
        style = SpanStyle(
            color = Color(0xFF54B4CC), // Link color from ModernBlack
            textDecoration = TextDecoration.Underline,
            fontSize = baseSizeSp
        )
    ) {
        append(message.loreItem)
    }
    pop()

    // Display text after the item link (if exists)
    if (message.chunks.size >= 2) {
        for (i in 1 until message.chunks.size) {
            val chunk = message.chunks[i]
            withStyle(
                style = SpanStyle(
                    color = getChunkColorPure(chunk),
                    fontSize = getChunkFontSizePure(chunk, baseTextSize),
                    background = getChunkBackgroundPure(chunk),
                )
            ) {
                append(chunk.text)
            }
        }
    }
}

/**
 * Build annotated string for regular messages (no lore).
 * Pure function - no Compose state/capturing.
 */
private fun buildRegularAnnotatedString(
    message: ColorfulTextMessage,
    baseTextSize: Float
): AnnotatedString = buildAnnotatedString {
    message.chunks.forEach { chunk ->
        withStyle(
            style = SpanStyle(
                color = getChunkColorPure(chunk),
                fontSize = getChunkFontSizePure(chunk, baseTextSize),
                background = getChunkBackgroundPure(chunk),
            )
        ) {
            append(chunk.text)
        }
    }
}

/**
 * Pure function for chunk color (no @Composable).
 */
private fun getChunkColorPure(chunk: TextMessageChunk): Color {
    return if (chunk.fg.isLiteral) {
        chunk.fg.color!!
    } else {
        ansiToComposeColorPure(chunk.fg.ansi, chunk.fg.isBright)
    }
}

/**
 * Pure function for chunk font size (no @Composable).
 */
private fun getChunkFontSizePure(chunk: TextMessageChunk, baseSize: Float): TextUnit {
    return when (chunk.textSize) {
        TextSize.Small -> (baseSize * 0.67f).sp  // ~2/3 of base
        TextSize.Normal -> baseSize.sp
        TextSize.Large -> (baseSize * 1.33f).sp  // ~4/3 of base
    }
}

/**
 * Pure function for chunk background (no @Composable).
 */
private fun getChunkBackgroundPure(chunk: TextMessageChunk): Color {
    return when {
        chunk.bg.isLiteral -> chunk.bg.color!!
        chunk.bg.ansi != AnsiColor.None -> ansiToComposeColorPure(chunk.bg.ansi, chunk.bg.isBright)
        else -> Color.Unspecified
    }
}

/**
 * Pure function version of ansiToComposeColor.
 * Uses ModernBlack color scheme from desktop.
 */
private fun ansiToComposeColorPure(ansi: AnsiColor, isBright: Boolean): Color {
    return if (isBright) {
        when (ansi) {
            AnsiColor.Black -> Color(0xFF616161)  // grey ray in the "prismatic rays" spell + OOC channel
            AnsiColor.Red -> Color(0xFFFF2B2B)
            AnsiColor.Green -> Color(0xFF2BFF79)
            AnsiColor.Yellow -> Color(0xFFFFF42B)
            AnsiColor.Blue -> Color(0xFF3B82F6)   // blue ray in the "prismatic rays" spell
            AnsiColor.Magenta -> Color(0xFFA855F7)
            AnsiColor.Cyan -> Color(0xFF67E8F9)
            AnsiColor.White -> Color(0xFFFAFAFA)
            AnsiColor.None -> Color(0xFFCCCCCC)
        }
    } else {
        when (ansi) {
            AnsiColor.Black -> Color(0xFF595959)
            AnsiColor.Red -> Color(0xFF991B1B)
            AnsiColor.Green -> Color(0xFF15803D)
            AnsiColor.Yellow -> Color(0xFFC1944E)
            AnsiColor.Blue -> Color(0xFF1D4ED8)
            AnsiColor.Magenta -> Color(0xFF7E22CE)
            AnsiColor.Cyan -> Color(0xFF067C99)
            AnsiColor.White -> Color(0xFF808080)
            AnsiColor.None -> Color(0xFFCCCCCC)
        }
    }
}
