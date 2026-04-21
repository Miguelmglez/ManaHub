package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@Composable
fun ObsidianChromeBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.03f),
) {
    Canvas(modifier = modifier) {
        val spacing = 80f
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += spacing
        }
    }
}
