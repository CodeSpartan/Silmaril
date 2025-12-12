package ru.adan.silmaril.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Shared theme colors for Silmaril app
 */
data class SilmarilColors(
    val background: Color = Color(0xFF1E1E1E),
    val surface: Color = Color(0xFF2D2D2D),
    val primary: Color = Color(0xFF4A90D9),
    val secondary: Color = Color(0xFF6C757D),
    val textPrimary: Color = Color(0xFFE0E0E0),
    val textSecondary: Color = Color(0xFFA0A0A0),
    val inputBackground: Color = Color(0xFF3C3C3C),
    val inputText: Color = Color(0xFFFFFFFF),
    val error: Color = Color(0xFFFF6B6B),
    val success: Color = Color(0xFF51CF66),
    val warning: Color = Color(0xFFFFD93D),

    // HP colors
    val hpGood: Color = Color(0xFF51CF66),
    val hpMedium: Color = Color(0xFFFFD93D),
    val hpBad: Color = Color(0xFFFF922B),
    val hpCritical: Color = Color(0xFFFF6B6B),

    // Connection status
    val connected: Color = Color(0xFF51CF66),
    val disconnected: Color = Color(0xFFFF6B6B),
    val connecting: Color = Color(0xFFFFD93D),
)

val LocalSilmarilColors = staticCompositionLocalOf { SilmarilColors() }

@Composable
fun SilmarilTheme(
    colors: SilmarilColors = SilmarilColors(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSilmarilColors provides colors
    ) {
        content()
    }
}

object SilmarilTheme {
    val colors: SilmarilColors
        @Composable
        get() = LocalSilmarilColors.current
}
