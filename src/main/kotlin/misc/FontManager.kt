package misc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import org.jetbrains.compose.resources.Font
import ru.adan.silmaril.generated.resources.*
import androidx.compose.ui.text.font.Font


object FontManager {
    // If you add more fonts, make sure to add a value to the fontFamilies map at the end of this object

    private val consolas = FontFamily(
        Font("fonts/Consolas/consola.ttf", FontWeight.Normal),
        Font("fonts/Consolas/consola-bold.ttf", FontWeight.Bold),
        Font("fonts/Consolas/consola-italic.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/Consolas/consola-bolditalic.ttf", FontWeight.Bold, style = FontStyle.Italic)
    )

    private val cousine = FontFamily(
        Font("fonts/Cousine/Cousine-Regular.ttf", FontWeight.Normal),
        Font("fonts/Cousine/Cousine-Bold.ttf", FontWeight.Bold),
        Font("fonts/Cousine/Cousine-Italic.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/Cousine/Cousine-BoldItalic.ttf", FontWeight.Bold, style = FontStyle.Italic)
    )

    private val firaMono = FontFamily(
        Font("fonts/Fira_Mono/FiraMono-Regular.ttf", FontWeight.Normal),
        Font("fonts/Fira_Mono/FiraMono-Medium.ttf", FontWeight.Medium),
        Font("fonts/Fira_Mono/FiraMono-Bold.ttf", FontWeight.Bold),
    )

    // doesn't look good
//    private val fixedsys = FontFamily(
//        Font("fonts/Fixedsys_Excelsior/FSEX302.ttf", FontWeight.Normal)
//    )

    private val jetbrains = FontFamily(
        Font("fonts/JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf", FontWeight.ExtraLight),
        Font("fonts/JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf", FontWeight.Light),
        Font("fonts/JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf", FontWeight.Normal),
        Font("fonts/JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf", FontWeight.Medium),
        Font("fonts/JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf", FontWeight.Bold),
        Font("fonts/JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf", FontWeight.ExtraBold),
        Font("fonts/JetBrains_Mono/JetBrainsMono-Italic-VariableFont_wght.ttf", FontWeight.Thin, style = FontStyle.Italic),
        Font("fonts/JetBrains_Mono/JetBrainsMono-Italic-VariableFont_wght.ttf", FontWeight.ExtraLight, style = FontStyle.Italic),
        Font("fonts/JetBrains_Mono/JetBrainsMono-Italic-VariableFont_wght.ttf", FontWeight.Light, style = FontStyle.Italic),
        Font("fonts/JetBrains_Mono/JetBrainsMono-Italic-VariableFont_wght.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/JetBrains_Mono/JetBrainsMono-Italic-VariableFont_wght.ttf", FontWeight.Medium, style = FontStyle.Italic),
        Font("fonts/JetBrains_Mono/JetBrainsMono-Italic-VariableFont_wght.ttf", FontWeight.Bold, style = FontStyle.Italic),
        Font("fonts/JetBrains_Mono/JetBrainsMono-Italic-VariableFont_wght.ttf", FontWeight.ExtraBold, style = FontStyle.Italic),
    )

    private val robotoClassic = FontFamily(
        Font("fonts/Roboto_Classic/Roboto-Thin.ttf", FontWeight.Thin),
        Font("fonts/Roboto_Classic/Roboto-Light.ttf", FontWeight.Light),
        Font("fonts/Roboto_Classic/Roboto-Regular.ttf", FontWeight.Normal),
        Font("fonts/Roboto_Classic/Roboto-Medium.ttf", FontWeight.Medium),
        Font("fonts/Roboto_Classic/Roboto-Bold.ttf", FontWeight.Bold),
        Font("fonts/Roboto_Classic/Roboto-ThinItalic.ttf", FontWeight.Thin, style = FontStyle.Italic),
        Font("fonts/Roboto_Classic/Roboto-LightItalic.ttf", FontWeight.Light, style = FontStyle.Italic),
        Font("fonts/Roboto_Classic/Roboto-Italic.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/Roboto_Classic/Roboto-MediumItalic.ttf", FontWeight.Medium, style = FontStyle.Italic),
        Font("fonts/Roboto_Classic/Roboto-BoldItalic.ttf", FontWeight.Bold, style = FontStyle.Italic),
    )

    private val robotoMono = FontFamily(
        Font("fonts/Roboto_Mono/RobotoMono-Thin.ttf", FontWeight.Thin),
        Font("fonts/Roboto_Mono/RobotoMono-Light.ttf", FontWeight.Light),
        Font("fonts/Roboto_Mono/RobotoMono-Regular.ttf", FontWeight.Normal),
        Font("fonts/Roboto_Mono/RobotoMono-Medium.ttf", FontWeight.Medium),
        Font("fonts/Roboto_Mono/RobotoMono-Bold.ttf", FontWeight.Bold),
        Font("fonts/Roboto_Mono/RobotoMono-ThinItalic.ttf", FontWeight.Thin, style = FontStyle.Italic),
        Font("fonts/Roboto_Mono/RobotoMono-LightItalic.ttf", FontWeight.Light, style = FontStyle.Italic),
        Font("fonts/Roboto_Mono/RobotoMono-Italic.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/Roboto_Mono/RobotoMono-MediumItalic.ttf", FontWeight.Medium, style = FontStyle.Italic),
        Font("fonts/Roboto_Mono/RobotoMono-BoldItalic.ttf", FontWeight.Bold, style = FontStyle.Italic),
    )

    private val sourceCodePro = FontFamily(
        Font("fonts/Source_Code_Pro/SourceCodePro-VariableFont_wght.ttf", FontWeight.ExtraLight),
        Font("fonts/Source_Code_Pro/SourceCodePro-VariableFont_wght.ttf", FontWeight.Light),
        Font("fonts/Source_Code_Pro/SourceCodePro-VariableFont_wght.ttf", FontWeight.Normal),
        Font("fonts/Source_Code_Pro/SourceCodePro-VariableFont_wght.ttf", FontWeight.Medium),
        Font("fonts/Source_Code_Pro/SourceCodePro-VariableFont_wght.ttf", FontWeight.SemiBold),
        Font("fonts/Source_Code_Pro/SourceCodePro-VariableFont_wght.ttf", FontWeight.Bold),
        Font("fonts/Source_Code_Pro/SourceCodePro-VariableFont_wght.ttf", FontWeight.ExtraBold),
        Font("fonts/Source_Code_Pro/SourceCodePro-VariableFont_wght.ttf", FontWeight.Black),
        Font("fonts/Source_Code_Pro/SourceCodePro-Italic-VariableFont_wght.ttf", FontWeight.ExtraLight, style = FontStyle.Italic),
        Font("fonts/Source_Code_Pro/SourceCodePro-Italic-VariableFont_wght.ttf", FontWeight.Light, style = FontStyle.Italic),
        Font("fonts/Source_Code_Pro/SourceCodePro-Italic-VariableFont_wght.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/Source_Code_Pro/SourceCodePro-Italic-VariableFont_wght.ttf", FontWeight.Medium, style = FontStyle.Italic),
        Font("fonts/Source_Code_Pro/SourceCodePro-Italic-VariableFont_wght.ttf", FontWeight.SemiBold, style = FontStyle.Italic),
        Font("fonts/Source_Code_Pro/SourceCodePro-Italic-VariableFont_wght.ttf", FontWeight.Bold, style = FontStyle.Italic),
        Font("fonts/Source_Code_Pro/SourceCodePro-Italic-VariableFont_wght.ttf", FontWeight.ExtraBold, style = FontStyle.Italic),
        Font("fonts/Source_Code_Pro/SourceCodePro-Italic-VariableFont_wght.ttf", FontWeight.Black, style = FontStyle.Italic),
    )

    val fontFamilies = mapOf(
        "Consolas" to consolas,
        "Cousine" to cousine,
        "FiraMono" to firaMono,
        "Jetbrains" to jetbrains,
        "RobotoClassic" to robotoClassic,
        "RobotoMono" to robotoMono,
        "SourceCodePro" to sourceCodePro,
    )

    fun getFont(s: String): FontFamily {
        return fontFamilies[s]!!
    }
}

// This may be the new way of adding fonts when they teach Windows to read variable fonts
// Leaving it as a reminder to self on how to use it
@Composable
fun rememberFontFamily(name: String): FontFamily {

    val robotoClassicThin = Font(Res.font.Roboto_Thin, FontWeight.Thin)
    val robotoClassicNormal = Font(Res.font.Roboto_Regular, FontWeight.Normal)

    val robotoClassicFamily = remember(robotoClassicThin, robotoClassicNormal) {
        FontFamily(robotoClassicThin, robotoClassicNormal)
    }

    // 3. Finally, remember the map itself to avoid recreating it on every call.
    val fontFamilies = remember(robotoClassicFamily) {
        mapOf(
            "RobotoClassic" to robotoClassicFamily
            // ... add other families here
        )
    }

    // Return the requested font, with a safe fallback.
    return fontFamilies[name] ?: robotoClassicFamily
}