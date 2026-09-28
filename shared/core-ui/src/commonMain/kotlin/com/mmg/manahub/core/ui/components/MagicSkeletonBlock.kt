package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors

private const val SKELETON_STATIC_ALPHA = 0.18f

/**
 * The pulse shared by every skeleton block on one screen, providing static fill alpha
 * without running infinite transition loops.
 *
 * [alpha] must only be read from a draw-phase lambda: the pulse then invalidates drawing, never
 * composition or layout.
 */
@Stable
class MagicSkeletonPulse internal constructor(
    private val animatedAlpha: State<Float>?,
) {
    /** The current fill alpha. */
    val alpha: Float get() = animatedAlpha?.value ?: SKELETON_STATIC_ALPHA
}

/**
 * Creates the screen-level skeleton pulse. Returns statically without infinite transitions
 * to prevent continuous repainting/recomposition loops.
 */
@Composable
fun rememberMagicSkeletonPulse(reducedMotion: Boolean = false): MagicSkeletonPulse {
    return remember { MagicSkeletonPulse(animatedAlpha = null) }
}

/**
 * A token-based loading placeholder block. Size it with [modifier]; it has no intrinsic size.
 *
 * The fill is `textDisabled` at a low alpha rather than `surfaceVariant`, which is nearly invisible
 * on the light HallowedPrint palette. The block is decorative and exposes no semantics.
 *
 * @param pulse the screen-level pulse from [rememberMagicSkeletonPulse].
 * @param shape the block's corner shape.
 */
@Composable
fun MagicSkeletonBlock(
    pulse: MagicSkeletonPulse,
    modifier: Modifier = Modifier,
    shape: Shape = SmallCardShape,
) {
    val base = MaterialTheme.magicColors.textDisabled
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind { drawRect(base.copy(alpha = pulse.alpha)) }
            .clearAndSetSemantics {},
    )
}
