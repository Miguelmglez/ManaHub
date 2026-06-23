package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun ForestMurmurBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00C853).copy(alpha = 0.05f),
) {
    Canvas(modifier = modifier) {
        val spacingX = 120f
        val spacingY = 160f
        val cols = (size.width / spacingX).toInt() + 2
        val rows = (size.height / spacingY).toInt() + 2

        for (row in 0..rows) {
            for (col in 0..cols) {
                val x = col * spacingX + if (row % 2 != 0) spacingX / 2f else 0f
                val y = row * spacingY
                drawLeaf(
                    center = Offset(x, y),
                    size = 40f,
                    color = color,
                    rotation = (row * 30 + col * 45) % 360f
                )
            }
        }
    }
}

private fun DrawScope.drawLeaf(
    center: Offset,
    size: Float,
    color: Color,
    rotation: Float
) {
    val path = Path().apply {
        moveTo(0f, -size)
        quadraticTo(size / 2, -size / 2, 0f, size)
        quadraticTo(-size / 2, -size / 2, 0f, -size)
        close()
        // Midrib
        moveTo(0f, -size)
        lineTo(0f, size)
    }

    drawContext.canvas.save()
    drawContext.canvas.translate(center.x, center.y)
    drawContext.canvas.rotate(rotation)
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 1f)
    )
    drawContext.canvas.restore()
}
