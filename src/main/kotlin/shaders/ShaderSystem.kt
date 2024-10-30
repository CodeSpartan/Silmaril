package shaders

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

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