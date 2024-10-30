package shaders

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.intellij.lang.annotations.Language
import org.jetbrains.skia.ImageFilter

@Language("AGSL")
private val TINT_SHADER = """
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

fun Modifier.tintShader(
    uniformNames: Array<String>,
    imageFilters: Array<ImageFilter?>,
    tint: Color,
    strength: Float,
) = this then runtimeShader(TINT_SHADER, uniformNames, imageFilters) {
    uniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    uniform("strength", strength)
}

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

@Language("AGSL")
private val SNOW_SHADER = """
    uniform shader content;
    uniform float2 iResolution;
    uniform float iTime;
        
    vec4 rgba_noise(vec2 fragCoord)
    {
        vec2 uv = fragCoord.xy;
    
        uv -= floor(uv / 289.0) * 289.0;
        uv += vec2(223.35734, 550.56781);
        uv *= uv;
        
        float xy = uv.x * uv.y;
        
        return vec4(fract(xy * 0.00000012),
                         fract(xy * 0.00000543),
                         fract(xy * 0.00000192),
                         fract(xy * 0.00000423));
    }
        
    float intensity = 1.12;
    float rot = -15.0;
    
    half4 main(vec2 fragCoord)
    {
    	vec2 uv = (fragCoord.xy / (iResolution.xy * 0.01));        
        vec3 col=content.eval(fragCoord).rgb;
        
        col = rgba_noise(fragCoord).rgb;
        
        // snow direction
        float c=cos(rot*0.01),si=sin(rot*0.01);
        uv=(uv-0.5)*mat2(c,si,-si,c);	
        
        // snowflakes
        float s=rgba_noise(fragCoord * 1.01 +vec2(iTime)*vec2(0.02,0.501)).r;
        col=mix(col,vec3(1.0),smoothstep(0.9,1.0, s * .9 * intensity));
            
    	return vec4(col,1.0);
    }
""".trimIndent()

fun Modifier.snowShader(
    width: Float,
    height: Float,
    time: Float,
) = this then runtimeShader(SNOW_SHADER, "content") {
    uniform("iResolution", width, height)
    uniform("iTime", time)
}