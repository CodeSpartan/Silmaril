package shaders

import androidx.compose.ui.Modifier
import org.intellij.lang.annotations.Language

@Language("AGSL")
private val TEMPLATE_SHADER = """
    uniform shader content;
    
    half4 main( vec2 fragCoord )
    {
        vec4 newColour = content.eval(fragCoord);
        return newColour;
    }
""".trimIndent()

fun Modifier.templateShader(
//    width: Float,
//    height: Float,
) = this then runtimeShader(TEMPLATE_SHADER, "content") {
    //uniform("iResolution", width, height)
}

