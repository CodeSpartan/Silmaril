package com.composables

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val standing: ImageVector
    get() {
        if (_person != null) return _person!!
        
        _person = ImageVector.Builder(
            name = "person",
            defaultWidth = 185.dp,
            defaultHeight = 512.dp,
            viewportWidth = 85.75f,
            viewportHeight = 236.99f
        ).apply {
            group {
                path(
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveToRelative(69.867f, 98.335f)
                    horizontalLineToRelative(-0.076f)
                    curveToRelative(-2.975f, -0.036f, -5.108f, -0.834f, -6.877f, -1.581f)
                    curveToRelative(-1.744f, -0.77f, -3.211f, -1.602f, -4.504f, -2.336f)
                    lineToRelative(-0.523f, -0.299f)
                    curveToRelative(-2.436f, -1.389f, -4.951f, -2.828f, -7.707f, -3.91f)
                    curveToRelative(-1.179f, -0.464f, -2.214f, -1.134f, -3.101f, -1.939f)
                    curveToRelative(-0.443f, -0.021f, -0.888f, -0.052f, -1.329f, -0.052f)
                    curveToRelative(-7.561f, 0.036f, -12.541f, 2.861f, -17.813f, 5.854f)
                    curveToRelative(-0.879f, 0.498f, -1.754f, 0.993f, -2.635f, 1.474f)
                    lineToRelative(-0.76f, 0.382f)
                    curveToRelative(-2.016f, 1.019f, -4.529f, 2.285f, -8.543f, 2.403f)
                    horizontalLineToRelative(-0.033f)
                    horizontalLineToRelative(-0.113f)
                    curveToRelative(-0.009f, 0f, -0.017f, -0.002f, -0.024f, -0.002f)
                    verticalLineToRelative(20.701f)
                    lineToRelative(-8.53f, 104.591f)
                    curveToRelative(-0.555f, 6.807f, 4.514f, 12.773f, 11.318f, 13.327f)
                    curveToRelative(6.805f, 0.555f, 12.773f, -4.51f, 13.328f, -11.317f)
                    lineToRelative(7.429f, -91.083f)
                    horizontalLineToRelative(7.002f)
                    lineToRelative(7.429f, 91.083f)
                    curveToRelative(0.555f, 6.808f, 6.523f, 11.872f, 13.328f, 11.317f)
                    reflectiveCurveToRelative(11.873f, -6.52f, 11.318f, -13.327f)
                    lineToRelative(-8.53f, -104.591f)
                    verticalLineToRelative(-20.697f)
                    curveToRelative(-0.014f, 0f, -0.027f, 0.002f, -0.04f, 0.002f)
                    close()
                }
            }
            group {
                path(
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveTo(65.375f, 22.5f)
                    arcTo(22.5f, 22.5f, 0f, false, true, 42.875f, 45f)
                    arcTo(22.5f, 22.5f, 0f, false, true, 20.375f, 22.5f)
                    arcTo(22.5f, 22.5f, 0f, false, true, 65.375f, 22.5f)
                    close()
                }
            }
            group {
                path(
                    fill = SolidColor(Color(0xFF000000))
                ) {
                    moveToRelative(2.494f, 86.248f)
                    curveToRelative(1.699f, 3.462f, 4.471f, 6.723f, 8.41f, 8.708f)
                    curveToRelative(1.611f, 0.821f, 3.412f, 1.177f, 4.949f, 1.177f)
                    horizontalLineToRelative(0.082f)
                    curveToRelative(3.949f, -0.116f, 6.172f, -1.465f, 8.314f, -2.518f)
                    curveToRelative(6.193f, -3.372f, 12.021f, -7.554f, 21.502f, -7.597f)
                    curveToRelative(1.5f, 0f, 3.094f, 0.111f, 4.813f, 0.369f)
                    curveToRelative(4.369f, 0.657f, 8.443f, -2.354f, 9.1f, -6.722f)
                    curveToRelative(0.656f, -4.371f, -2.354f, -8.442f, -6.723f, -9.099f)
                    curveToRelative(-2.498f, -0.376f, -4.896f, -0.548f, -7.189f, -0.548f)
                    curveToRelative(-9.736f, -0.018f, -17.394f, 3.151f, -22.736f, 5.977f)
                    curveToRelative(-2.541f, 1.34f, -4.619f, 2.6f, -6.066f, 3.38f)
                    curveToRelative(-0.037f, -0.067f, -0.074f, -0.137f, -0.111f, -0.211f)
                    curveToRelative(-0.243f, -0.481f, -0.454f, -1.095f, -0.604f, -1.785f)
                    lineToRelative(2.019f, -1.264f)
                    curveToRelative(1.07f, -0.612f, 2.344f, -1.335f, 3.738f, -2.068f)
                    curveToRelative(7.924f, -4.193f, 15.672f, -6.23f, 23.68f, -6.23f)
                    curveToRelative(2.547f, 0f, 5.078f, 0.192f, 7.6f, 0.571f)
                    curveToRelative(2.626f, 0.395f, 4.86f, 1.772f, 6.41f, 3.693f)
                    curveToRelative(2.562f, 1.131f, 4.81f, 2.334f, 6.865f, 3.492f)
                    curveToRelative(0.33f, 0.188f, 0.646f, 0.37f, 0.953f, 0.545f)
                    lineToRelative(2.018f, 1.262f)
                    curveToRelative(-0.151f, 0.689f, -0.364f, 1.302f, -0.607f, 1.783f)
                    curveToRelative(-0.037f, 0.072f, -0.072f, 0.142f, -0.109f, 0.208f)
                    curveToRelative(-0.879f, -0.478f, -2.027f, -1.133f, -3.334f, -1.881f)
                    curveToRelative(-1.185f, -0.668f, -2.528f, -1.406f, -4.017f, -2.151f)
                    curveToRelative(0.474f, 1.456f, 0.637f, 3.036f, 0.394f, 4.65f)
                    curveToRelative(-0.728f, 4.825f, -4.861f, 8.485f, -9.7f, 8.669f)
                    curveToRelative(2.621f, 1.14f, 5.006f, 2.504f, 7.353f, 3.845f)
                    curveToRelative(1.355f, 0.767f, 2.703f, 1.529f, 4.277f, 2.223f)
                    curveToRelative(1.574f, 0.666f, 3.434f, 1.378f, 6.045f, 1.408f)
                    horizontalLineToRelative(0.064f)
                    curveToRelative(1.547f, 0f, 3.352f, -0.358f, 4.963f, -1.177f)
                    curveToRelative(3.945f, -1.993f, 6.711f, -5.251f, 8.412f, -8.71f)
                    curveToRelative(0.034f, -0.069f, 0.062f, -0.141f, 0.095f, -0.21f)
                    curveToRelative(1.641f, -3.431f, 2.392f, -7.132f, 2.399f, -10.874f)
                    curveToRelative(-0.016f, -5.621f, -1.703f, -11.512f, -5.77f, -16.366f)
                    curveToRelative(-3.771f, -4.577f, -9.897f, -7.741f, -16.903f, -8.046f)
                    curveToRelative(-0.291f, -0.033f, -0.586f, -0.054f, -0.885f, -0.054f)
                    horizontalLineToRelative(-38.637f)
                    curveToRelative(-0.299f, 0f, -0.592f, 0.021f, -0.881f, 0.054f)
                    curveToRelative(-7.006f, 0.307f, -13.135f, 3.47f, -16.903f, 8.045f)
                    curveToRelative(-4.069f, 4.855f, -5.758f, 10.749f, -5.774f, 16.368f)
                    curveToRelative(0.008f, 3.741f, 0.759f, 7.446f, 2.4f, 10.876f)
                    curveToRelative(0.033f, 0.069f, 0.06f, 0.14f, 0.094f, 0.208f)
                    close()
                }
            }
        }.build()
        
        return _person!!
    }

private var _person: ImageVector? = null

