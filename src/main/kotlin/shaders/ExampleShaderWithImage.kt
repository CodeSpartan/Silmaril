package shaders

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.intellij.lang.annotations.Language
import org.jetbrains.skia.ImageFilter

/**
 * An example shader that accepts an image as a uniform and overlays it on top of a composable
 * To call this shader: .exampleShaderWithImage(uniformNames=arrayOf("image"), imageFilters=arrayOf(filter), Color.Red, 0.25f)
 * where filter is: val filter = ImageFilter.makeImage(image = loadSkiaImage("icon.png"))
 * where "icon.png" is relative to /src/main/resources/
 * Important point:- the uniform image must already exist in the shader for the program not to crash
 * */
@Language("AGSL")
val EXAMPLE_SHADER_WITH_IMAGE = """
    uniform shader content;
    uniform shader image;
    uniform float4 tint;
    uniform float strength;
    half4 main(vec2 fragCoord) {
        vec4 col = mix(content.eval(fragCoord), half4(tint), strength);
        vec4 overlayColor = image.eval(fragCoord);
        col = mix(col, overlayColor, overlayColor.a);
        return col;
    }
""".trimIndent()

fun Modifier.exampleShaderWithImage(
    uniformNames: Array<String>,
    imageFilters: Array<ImageFilter?>,
    tint: Color,
    strength: Float,
) = this then runtimeShader(EXAMPLE_SHADER_WITH_IMAGE, uniformNames, imageFilters) {
    uniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    uniform("strength", strength)
}