package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * wasmJs actual: shadows are not supported on the web target in this phase.
 * Returns the receiver [Modifier] unchanged.
 */
actual fun Modifier.coloredShadow(
    color: Color,
    borderRadius: Dp,
    blurRadius: Dp,
    offsetX: Dp,
    offsetY: Dp,
    spread: Float,
): Modifier = this
