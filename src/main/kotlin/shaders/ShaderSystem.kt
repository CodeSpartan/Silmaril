package shaders

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import java.io.InputStream

// useful info https://github.com/drinkthestars/shady

interface ShaderUniformProvider {
    fun updateResolution(size: Size)
    fun uniform(name: String, value: Int)
    fun uniform(name: String, value: Float)
    fun uniform(name: String, value1: Float, value2: Float)
    fun uniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)
}


// this can be overridden based on platform, see https://medium.com/@mmartosdev/pushing-the-boundaries-of-compose-multiplatform-with-agsl-shaders-d6d47380ba8a
fun Modifier.shader(
    shader: String,
    uniformsBlock: (ShaderUniformProvider.() -> Unit)?,
): Modifier = this then composed {
    val runtimeShaderBuilder = remember {
        RuntimeShaderBuilder(
            effect = RuntimeEffect.makeForShader(shader),
        )
    }
    val shaderUniformProvider = remember {
        ShaderUniformProviderImpl(runtimeShaderBuilder)
    }
    graphicsLayer {
        clip = true
        renderEffect = ImageFilter.makeShader(
            shader = runtimeShaderBuilder.apply {
                uniformsBlock?.invoke(shaderUniformProvider)
            }.makeShader(),
            crop = null,
        ).asComposeRenderEffect()
    }
}

// this can be overridden based on platform, see https://medium.com/@mmartosdev/pushing-the-boundaries-of-compose-multiplatform-with-agsl-shaders-d6d47380ba8a
fun Modifier.runtimeShader(
    shader: String,
    uniformNames: Array<String>,
    imageFilters: Array<ImageFilter?>,
    uniformsBlock: (ShaderUniformProvider.() -> Unit)?,
): Modifier = this then composed {
    val runtimeShaderBuilder = remember {
        RuntimeShaderBuilder(
            effect = RuntimeEffect.makeForShader(shader),
        )
    }
    val shaderUniformProvider = remember {
        ShaderUniformProviderImpl(runtimeShaderBuilder)
    }
    graphicsLayer {
        clip = true
        renderEffect = ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = runtimeShaderBuilder.apply {
                uniformsBlock?.invoke(shaderUniformProvider)
            },
            shaderNames = arrayOf("content") + uniformNames,
            inputs = arrayOf<ImageFilter?>(null) + imageFilters,
        ).asComposeRenderEffect()
    }
}

// this can be overridden based on platform, see https://medium.com/@mmartosdev/pushing-the-boundaries-of-compose-multiplatform-with-agsl-shaders-d6d47380ba8a
fun Modifier.runtimeShader(
    shader: String,
    uniformName: String,
    uniformsBlock: (ShaderUniformProvider.() -> Unit)?,
): Modifier = this then composed {
    val runtimeShaderBuilder = remember {
        RuntimeShaderBuilder(
            effect = RuntimeEffect.makeForShader(shader),
        )
    }
    val shaderUniformProvider = remember {
        ShaderUniformProviderImpl(runtimeShaderBuilder)
    }
    graphicsLayer {
        clip = true
        renderEffect = ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = runtimeShaderBuilder.apply {
                uniformsBlock?.invoke(shaderUniformProvider)
            },
            shaderName = uniformName,
            input = null,
        ).asComposeRenderEffect()
    }
}

private class ShaderUniformProviderImpl(
    private val runtimeShaderBuilder: RuntimeShaderBuilder,
) : ShaderUniformProvider {

    override fun updateResolution(size: Size) {
        uniform("resolution", size.width, size.height)
    }

    override fun uniform(name: String, value: Int) {
        runtimeShaderBuilder.uniform(name, value)
    }

    override fun uniform(name: String, value: Float) {
        runtimeShaderBuilder.uniform(name, value)
    }

    override fun uniform(name: String, value1: Float, value2: Float) {
        runtimeShaderBuilder.uniform(name, value1, value2)
    }

    override fun uniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        runtimeShaderBuilder.uniform(name, value1, value2, value3, value4)
    }
}

// Path is relative to /src/main/resources/
// SkiaImages can be fed into shaders as uniforms
fun loadSkiaImage(resourcePath: String): Image {
    val inputStream: InputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
        ?: throw IllegalArgumentException("Resource not found: $resourcePath")
    val imageBytes = inputStream.readBytes()
    return Image.makeFromEncoded(imageBytes)
}

// Could be useful to test the loaded skia image
fun skiaImageToImageBitmap(skiaImage: Image): ImageBitmap {
    return skiaImage.toComposeImageBitmap()
}