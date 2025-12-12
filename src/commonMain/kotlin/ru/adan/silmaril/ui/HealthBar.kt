package ru.adan.silmaril.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A health/status bar component showing percentage
 */
@Composable
fun HealthBar(
    percentage: Float,
    label: String = "",
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true,
    backgroundColor: Color = Color(0xFF3C3C3C),
    height: Int = 20
) {
    val colors = SilmarilTheme.colors

    val fillColor = when {
        percentage > 75 -> colors.hpGood
        percentage > 50 -> colors.hpMedium
        percentage > 25 -> colors.hpBad
        else -> colors.hpCritical
    }

    val clampedPercentage = percentage.coerceIn(0f, 100f)

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clampedPercentage / 100f)
                    .background(fillColor)
            )

            if (showPercentage) {
                Text(
                    text = "${clampedPercentage.toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Compact health bar for creature lists
 */
@Composable
fun CompactHealthBar(
    percentage: Float,
    modifier: Modifier = Modifier,
    height: Int = 8
) {
    val colors = SilmarilTheme.colors

    val fillColor = when {
        percentage > 75 -> colors.hpGood
        percentage > 50 -> colors.hpMedium
        percentage > 25 -> colors.hpBad
        else -> colors.hpCritical
    }

    Box(
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF3C3C3C))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(percentage.coerceIn(0f, 100f) / 100f)
                .background(fillColor)
        )
    }
}
