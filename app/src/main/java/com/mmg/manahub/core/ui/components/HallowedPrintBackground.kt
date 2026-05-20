package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Decorative background for [AppTheme.HallowedPrint] — the light theme.
 *
 * Faint horizontal ruling, like a notebook or codex page. Uses a low-alpha
 * sepia ink so it reads as paper texture, not lines.
 */
@Composable
fun HallowedPrintBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF5A5247).copy(alpha = 0.04f),
) {
    Canvas(modifier = modifier) {
        val spacingY = 28f
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = color,
                start = Offset(0f, y),
                end   = Offset(size.width, y),
                strokeWidth = 0.6f,
            )
            y += spacingY
        }
        // Tiny ink-dot fleck pattern to suggest paper grain
        val grainColor = Color(0xFF5A5247).copy(alpha = 0.025f)
        val grainStep  = 64f
        val cols = (size.width / grainStep).toInt() + 1
        val rows = (size.height / grainStep).toInt() + 1
        for (row in 0..rows) {
            for (col in 0..cols) {
                val x = col * grainStep + (if (row % 2 == 0) 18f else 42f)
                val cy = row * grainStep + 14f
                drawCircle(grainColor, radius = 1f, center = Offset(x, cy))
            }
        }
    }
}
