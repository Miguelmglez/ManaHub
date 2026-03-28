package com.mmg.magicfolder.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@Composable
fun MedievalGrimoireBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFC9A84C).copy(alpha = 0.04f),
) {
    Canvas(modifier = modifier) {
        // Horizontal lines — aged parchment / ruled manuscript style
        val lineSpacing = 28f
        var y = 0f
        while (y < size.height) {
            drawLine(
                color       = color,
                start       = Offset(0f, y),
                end         = Offset(size.width, y),
                strokeWidth = 0.6f,
            )
            y += lineSpacing
        }
        // Vertical margin line — left edge of a manuscript page
        drawLine(
            color       = color.copy(alpha = color.alpha * 2),
            start       = Offset(32f, 0f),
            end         = Offset(32f, size.height),
            strokeWidth = 1f,
        )
    }
}
