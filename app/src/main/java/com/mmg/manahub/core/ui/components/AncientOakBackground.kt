package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun AncientOakBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF8D6E63).copy(alpha = 0.04f),
) {
    Canvas(modifier = modifier) {
        val spacingY = 40f
        var y = 0f
        while (y < size.height) {
            val thickness = (2..6).random().toFloat()
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = thickness * 0.2f
            )
            y += spacingY + (0..20).random()
        }
    }
}
