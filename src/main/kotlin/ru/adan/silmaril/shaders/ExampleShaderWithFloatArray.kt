package ru.adan.silmaril.shaders

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.intellij.lang.annotations.Language
import org.jetbrains.skia.*

@Language("AGSL")
val EXAMPLE_SHADER_WITH_FLOAT_ARRAY = """
    uniform shader content;
    uniform shader arrayShader; // is actually a 1d image
    uniform float4 tint;
    
    float getArrayValue(float index) {
        return arrayShader.eval(float2(index+0.5, 0.5)).g;
    }
    
    half4 ru.adan.silmaril.main(vec2 fragCoord) {        
        float val = getArrayValue(1);
        vec4 col = mix(content.eval(fragCoord), half4(tint), val);
        return col;
    }
""".trimIndent()

/** Accepts float values from 0 to 1 currently, and precision is one byte (0-255) */
fun Modifier.exampleShaderWithFloatArray(
    tint: Color,
    floats: FloatArray,
) = this then runtimeShader(EXAMPLE_SHADER_WITH_FLOAT_ARRAY, "content") {
    uniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
    childShader("arrayShader", create1DTextureShader(floats))
}

// Creates a 2d texture the width of data.size and the height of 1
// Each pixel (pixel[i]) channel carries the same info in its 3 color channels - data[i]
// Each pixel is 255 on alpha channel
fun create1DTextureShader(data: FloatArray): Shader {
    val width = data.size
    val pixelData = ByteArray(width * 4) // Each pixel is 4 bytes (A, R, G, B)

    // Populate the ByteArray with ARGB values for each float in the data array
    for (i in data.indices) {
        val value = (data[i].coerceIn(0f, 1f) * 255).toInt().toByte()
        pixelData[i * 4] = (-1).toByte()       // Alpha channel (0xFF) //@TODO: the order here is actually RGBA, no?
        pixelData[i * 4 + 1] = value           // Red channel
        pixelData[i * 4 + 2] = value           // Green channel
        pixelData[i * 4 + 3] = value           // Blue channel
    }

    // Create a Bitmap and install the pixel data
    val bitmap = Bitmap().apply {
        allocPixels(ImageInfo(width, 1, ColorType.RGBA_8888, ColorAlphaType.OPAQUE))
        installPixels(pixelData)
    }

    //@TODO: change sampling to DEFAULT? because it works in Int, and Catmull doesn't work there, and I tested Int more thoroughly
    return Image.makeFromBitmap(bitmap).toComposeImageBitmap().asSkiaBitmap().makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, sampling = SamplingMode.CATMULL_ROM)
}