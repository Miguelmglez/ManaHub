package com.mmg.magicfolder.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun HexGridBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.04f),
    hexSize: Float = 28f,
) {
    Canvas(modifier = modifier) {
        drawHexGrid(color = color, hexSize = hexSize)
    }
}

fun DrawScope.drawHexGrid(color: Color, alpha: Float = 1f, hexSize: Float = 28f) {
    val w = hexSize * 2f
    val h = sqrt(3f) * hexSize
    val cols = (size.width / (w * 0.75f)).toInt() + 2
    val rows = (size.height / h).toInt() + 2

    for (row in -1..rows) {
        for (col in -1..cols) {
            val x = col * w * 0.75f
            val y = row * h + if (col % 2 != 0) h / 2f else 0f
            drawHexOutline(
                center = Offset(x, y),
                size   = hexSize,
                color  = color.copy(alpha = color.alpha * alpha),
            )
        }
    }
}

private fun DrawScope.drawHexOutline(
    center: Offset,
    size: Float,
    color: Color,
) {
    val path = Path()
    for (i in 0..5) {
        val angle = Math.PI / 180.0 * (60 * i - 30)
        val x = center.x + size * cos(angle).toFloat()
        val y = center.y + size * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color, style = Stroke(width = 0.8f))
}
