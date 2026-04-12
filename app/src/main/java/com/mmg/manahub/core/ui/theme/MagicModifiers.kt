package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.coloredShadow(
    color: Color,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 20.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    spread: Float = 1f,
): Modifier = this.drawBehind {
    drawIntoCanvas {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        val spreadPixel = spread.dp.toPx()
        val leftPixel   = (0f - spreadPixel) + offsetX.toPx()
        val topPixel    = (0f - spreadPixel) + offsetY.toPx()
        val rightPixel  = size.width + spreadPixel
        val bottomPixel = size.height + spreadPixel

        if (blurRadius != 0.dp) {
            frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                blurRadius.toPx(),
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }

        frameworkPaint.color = color.copy(alpha = 0f).toArgb()
        frameworkPaint.setShadowLayer(
            blurRadius.toPx(), offsetX.toPx(), offsetY.toPx(), color.toArgb(),
        )
        it.drawRoundRect(
            left    = leftPixel,
            top     = topPixel,
            right   = rightPixel,
            bottom  = bottomPixel,
            radiusX = borderRadius.toPx(),
            radiusY = borderRadius.toPx(),
            paint   = paint,
        )
    }
}
