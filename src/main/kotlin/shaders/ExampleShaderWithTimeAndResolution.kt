package shaders

import androidx.compose.ui.Modifier
import org.intellij.lang.annotations.Language

/**
 * This shader uses time and resolution.
 * For time, inside composable: val time by produceDrawLoopCounter(speed = 1f)
 * Outside of composable:
 * @Composable
 * fun produceDrawLoopCounter(speed: Float = 1f): State<Float> {
 *     return produceState(0f) {
 *         while (true) {
 *             withInfiniteAnimationFrameMillis {
 *                 value = it / 1000f * speed
 *             }
 *         }
 *     }
 * }
 *
 * For resolution, inside composable:
 * var windowRealSize by remember { mutableStateOf(Pair(800.0f, 600.0f))}
 * val density = LocalDensity.current
 *
 * Also inside the composable:
 * owner.addComponentListener(object : ComponentAdapter() {
 *         override fun componentResized(e: ComponentEvent?) {
 *             // real size that depends on density
 *             windowRealSize = Pair(owner.contentPane.width * density.density, owner.contentPane.height * density.density)
 *         }
 *
 *         override fun componentMoved(e: ComponentEvent?) {
 *             windowRealSize = Pair(owner.contentPane.width * density.density, owner.contentPane.height * density.density)
 *         }
 *     })
 * Where owner is of type ComposeWindow and it's passed down to any composable from the top of the hierarchy in main.kt
 */
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