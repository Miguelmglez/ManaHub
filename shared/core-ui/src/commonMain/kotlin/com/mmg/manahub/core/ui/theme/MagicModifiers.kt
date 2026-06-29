package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a colored box-shadow behind the composable using platform-specific blur primitives.
 *
 * On Android the shadow is rendered via [android.graphics.BlurMaskFilter] and
 * [android.graphics.Paint.setShadowLayer]. On wasmJs the modifier is a no-op (shadows are
 * not supported on the web target in this phase).
 *
 * @param color       Shadow color (alpha is ignored; the shadow layer sets its own opacity).
 * @param borderRadius Corner radius to match the composable's shape.
 * @param blurRadius  Gaussian blur radius of the shadow.
 * @param offsetX     Horizontal offset of the shadow relative to the composable.
 * @param offsetY     Vertical offset of the shadow relative to the composable.
 * @param spread      Uniform expansion of the shadow bounding box in dp.
 */
expect fun Modifier.coloredShadow(
    color: Color,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 20.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    spread: Float = 1f,
): Modifier
