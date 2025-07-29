package visual_styles

import androidx.compose.material.darkColors
import androidx.compose.ui.graphics.Color

object AppColors {
    val DarkColorPalette = darkColors(
        primary = Color(0xFF7289DA),
        primaryVariant = Color(0xFF5B6EAE),
        secondary = Color(0xFF7289DA),
        background = Color(0xFF2C2F33),
        surface = Color(0xFF36373e),
        error = Color(0xFFfa928d), // delete button
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )

    object Button {
        val deleteBackground = Color(0xFF3e3f45)
        val disabledDeleteBackground = Color(0xFF4e4f55)
        val disabledDeleteContent = Color(0xFF919296)
    }

    object TextField {
        val background = Color(0xFF2b2c32)
        val focusedBorder = Color(0xFF83adf6)
        val unfocusedBorder = Color(0xFF414148)
    }

    object List {
        val background = Color(0xFF2F3036)
    }

    val closeButton = Color(0xFFc4c5c9)
}