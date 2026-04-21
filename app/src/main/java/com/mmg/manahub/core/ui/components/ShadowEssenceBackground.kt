package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun ShadowEssenceBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF9C27B0).copy(alpha = 0.05f),
) {
    Canvas(modifier = modifier) {
        val spacing = 100f
        val cols = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 2

        for (row in 0..rows) {
            for (col in 0..cols) {
                val x = col * spacing + if (row % 2 != 0) spacing / 2f else 0f
                val y = row * spacing
                drawCircle(
                    color = color,
                    radius = 4f,
                    center = Offset(x, y),
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}
