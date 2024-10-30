package shaders

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.intellij.lang.annotations.Language
import org.jetbrains.skia.ImageFilter

@Language("AGSL")
private val CRT_SHADER = """
    uniform shader content;
    uniform float2 iResolution;
    
    // Will return a value of 1 if the 'x' is < 'value'
    float Less(float x, float value)
    {
        return 1.0 - step(value, x);
    }
    
    // Will return a value of 1 if the 'x' is >= 'lower' && < 'upper'
    float Between(float x, float  lower, float upper)
    {
        return step(lower, x) * (1.0 - step(upper, x));
    }
    
    //	Will return a value of 1 if 'x' is >= value
    float GEqual(float x, float value)
    {
        return step(value, x);
    }
    
    half4 main( vec2 fragCoord )
    {
        float brightness = 1.25;
        vec2 uv = fragCoord.xy / iResolution.xy;
        uv.y = -uv.y;
        
        vec2 uvStep;
        uvStep.x = uv.x / (1.0 / iResolution.x);
        uvStep.x = mod(uvStep.x, 3.0);
        uvStep.y = uv.y / (1.0 / iResolution.y);
        uvStep.y = mod(uvStep.y, 3.0);
        
        // texture(image, uv)
        vec4 newColour = content.eval(fragCoord);
        
        newColour.r = newColour.r * step(1.0, (Less(uvStep.x, 1.0) + Less(uvStep.y, 1.0)));
        newColour.g = newColour.g * step(1.0, (Between(uvStep.x, 1.0, 2.0) + Between(uvStep.y, 1.0, 2.0)));
        newColour.b = newColour.b * step(1.0, (GEqual(uvStep.x, 2.0) + GEqual(uvStep.y, 2.0)));
    
        return newColour * brightness;
    }
""".trimIndent()

fun Modifier.crtShader(
    width: Float,
    height: Float,
) = this then runtimeShader(CRT_SHADER, "content") {
    uniform("iResolution", width, height)
}
