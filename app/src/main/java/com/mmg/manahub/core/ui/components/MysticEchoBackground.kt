package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun MysticEchoBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE040FB).copy(alpha = 0.05f),
) {
    Canvas(modifier = modifier) {
        val spacing = 150f
        val cols = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 2

        for (row in 0..rows) {
            for (col in 0..cols) {
                val x = col * spacing + if (row % 2 != 0) spacing / 2f else 0f
                val y = row * spacing
                drawEchoCircle(
                    center = Offset(x, y),
                    maxRadius = 60f,
                    color = color
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEchoCircle(
    center: Offset,
    maxRadius: Float,
    color: Color
) {
    for (i in 1..3) {
        drawCircle(
            color = color.copy(alpha = color.alpha / i),
            radius = maxRadius * (i / 3f),
            center = center,
            style = Stroke(width = 0.8f)
        )
    }
}
