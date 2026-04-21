package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun GildedSilverBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFD700).copy(alpha = 0.05f),
) {
    Canvas(modifier = modifier) {
        val size = 60f
        val spacing = 120f
        val cols = (this.size.width / spacing).toInt() + 2
        val rows = (this.size.height / spacing).toInt() + 2

        for (row in 0..rows) {
            for (col in 0..cols) {
                val x = col * spacing + if (row % 2 != 0) spacing / 2f else 0f
                val y = row * spacing
                drawDiamond(
                    center = Offset(x, y),
                    size = size,
                    color = color
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiamond(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y - size / 2)
        lineTo(center.x + size / 2, center.y)
        lineTo(center.x, center.y + size / 2)
        lineTo(center.x - size / 2, center.y)
        close()
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 0.8f)
    )
}
