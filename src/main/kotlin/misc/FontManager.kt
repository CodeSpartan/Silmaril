package misc

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

object FontManager {

    val consolas = FontFamily(
        Font("fonts/Consolas/consola.ttf", FontWeight.Normal),
        Font("fonts/Consolas/consola-bold.ttf", FontWeight.Bold),
        Font("fonts/Consolas/consola-italic.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/Consolas/consola-bolditalic.ttf", FontWeight.Bold, style = FontStyle.Italic)
    )

    val cousine = FontFamily(
        Font("fonts/Cousine/Cousine-Regular.ttf", FontWeight.Normal),
        Font("fonts/Cousine/Cousine-Bold.ttf", FontWeight.Bold),
        Font("fonts/Cousine/Cousine-Italic.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/Cousine/Cousine-BoldItalic.ttf", FontWeight.Bold, style = FontStyle.Italic)
    )

    val fira = FontFamily(
        Font("fonts/Fira_Mono/FiraMono-Regular.ttf", FontWeight.Normal),
        Font("fonts/Fira_Mono/FiraMono-Medium.ttf", FontWeight.Medium),
        Font("fonts/Fira_Mono/FiraMono-Bold.ttf", FontWeight.Bold),
    )

    val fixedsys = FontFamily(
        Font("fonts/Fixedsys_Excelsior/FSEX302.ttf", FontWeight.Normal)
    )

    val jetbrains = FontFamily(
        Font("fonts/JetBrains_Mono/JetBrainsMono-VariableFont_wght.ttf", FontWeight.Thin),
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

    val roboto = FontFamily(
        Font("fonts/Roboto_Mono/RobotoMono-VariableFont_wght.ttf", FontWeight.Thin),
        Font("fonts/Roboto_Mono/RobotoMono-VariableFont_wght.ttf", FontWeight.Light),
        Font("fonts/Roboto_Mono/RobotoMono-VariableFont_wght.ttf", FontWeight.Normal),
        Font("fonts/Roboto_Mono/RobotoMono-VariableFont_wght.ttf", FontWeight.Medium),
        Font("fonts/Roboto_Mono/RobotoMono-VariableFont_wght.ttf", FontWeight.Bold),
        Font("fonts/Roboto_Mono/RobotoMono-Italic-VariableFont_wght.ttf", FontWeight.Thin, style = FontStyle.Italic),
        Font("fonts/Roboto_Mono/RobotoMono-Italic-VariableFont_wght.ttf", FontWeight.Light, style = FontStyle.Italic),
        Font("fonts/Roboto_Mono/RobotoMono-Italic-VariableFont_wght.ttf", FontWeight.Normal, style = FontStyle.Italic),
        Font("fonts/Roboto_Mono/RobotoMono-Italic-VariableFont_wght.ttf", FontWeight.Medium, style = FontStyle.Italic),
        Font("fonts/Roboto_Mono/RobotoMono-Italic-VariableFont_wght.ttf", FontWeight.Bold, style = FontStyle.Italic),
    )

    val sourceCodePro = FontFamily(
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
}
