package shaders

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.intellij.lang.annotations.Language

@Language("AGSL")
val EXAMPLE_SHADER_WITH_INT_ARRAY = """
    uniform shader content;
    uniform shader arrayShader; // is actually a 1d image
    uniform float4 tint;
    
    // because AGSL doesn't round
    float betterManualRound(float x) {        
        if (x >= 0.0) {
            return floor(x + 0.5);
        } else {
            return ceil(x - 0.5);
        }
    }
    
    int decodeIntFromBytes(vec4 color) {
        // Assume color channels are in the range [0, 1] representing byte values          
        int r = int(mod(betterManualRound(color.r * 255.0), 256));  
        int g = int(mod(betterManualRound(color.g * 255.0), 256));  
        int b = int(mod(betterManualRound(color.b * 255.0), 256));  
        int a = int(mod(betterManualRound(color.a * 255.0), 256));
        // If 'r' >> 128, treat as negative in two's complement
        if (r > 127) {
            // Convert unsigned to signed integer logic, accounting additional signed bit for MSB:
            r -= 256;
        }

        // Reconstruct the integer from the RGBA bytes
        // Multiply each by appropriate factor simulating bit shifting
        return r * 16777216 + g * 65536 + b * 256 + a;
    }
    
    int getArrayValue(float index) {
        return decodeIntFromBytes(arrayShader.eval(float2(index+0.5, 0.5)));
    }
    
    half4 main(vec2 fragCoord) {      
        // usage: getArrayValue(7)
        float val = 0.5;
        vec4 col = mix(content.eval(fragCoord), half4(tint), val);
        return col;
    }
""".trimIndent()

/** Accepts 4-byte ints and decomposes them into 4 bytes, where each byte goes into an rgba channel */
fun Modifier.exampleShaderWithIntArray(
    tint: Color,
    ints: IntArray,
) = this then runtimeShader(EXAMPLE_SHADER_WITH_INT_ARRAY, "content") {
    uniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    childShader("arrayShader", create1DTextureShader(ints))
}

fun create1DTextureShader(data: IntArray): Shader {
    val width = data.size
    val pixelData = ByteArray(width * 4) // Each pixel is 4 bytes

    for (i in data.indices) {
        val bytes = intToBytes(data[i])
        pixelData[i * 4 + 0] = bytes[0].toByte() // R
        pixelData[i * 4 + 1] = bytes[1].toByte() // G
        pixelData[i * 4 + 2] = bytes[2].toByte() // B
        pixelData[i * 4 + 3] = bytes[3].toByte() // A
    }

    // Create a Bitmap and install the pixel data
    val bitmap = Bitmap().apply {
        allocPixels(ImageInfo(width, 1, ColorType.RGBA_8888, ColorAlphaType.OPAQUE))
        installPixels(pixelData)
    }

    return Image.makeFromBitmap(bitmap).toComposeImageBitmap().asSkiaBitmap().makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, sampling = SamplingMode.DEFAULT)
}

fun intToBytes(intValue: Int): IntArray {
    return intArrayOf(
        (intValue shr 24) and 0xFF,  // Most significant byte // R
        (intValue shr 16) and 0xFF,                           // G
        (intValue shr 8) and 0xFF,                            // B
        intValue and 0xFF  // Least significant byte          // A
    )
}