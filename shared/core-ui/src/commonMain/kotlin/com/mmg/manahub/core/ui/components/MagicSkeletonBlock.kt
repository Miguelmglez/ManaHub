package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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

private const val SKELETON_MIN_ALPHA = 0.12f
private const val SKELETON_MAX_ALPHA = 0.26f
private const val SKELETON_STATIC_ALPHA = 0.18f
private const val SKELETON_PULSE_MS = 900

/**
 * The pulse shared by every skeleton block on one screen, so a board of placeholders runs a single
 * infinite transition instead of one per block.
 *
 * [alpha] must only be read from a draw-phase lambda: the pulse then invalidates drawing, never
 * composition or layout.
 */
@Stable
class MagicSkeletonPulse internal constructor(
    private val animatedAlpha: State<Float>?,
) {
    /** The current fill alpha (static when reduced motion is on). */
    val alpha: Float get() = animatedAlpha?.value ?: SKELETON_STATIC_ALPHA
}

/**
 * Creates the screen-level skeleton pulse. With [reducedMotion] the blocks render a static mid tone;
 * the platform decides the flag (the Android caller reads the system animator scale).
 */
@Composable
fun rememberMagicSkeletonPulse(reducedMotion: Boolean): MagicSkeletonPulse {
    if (reducedMotion) return remember { MagicSkeletonPulse(animatedAlpha = null) }
    val transition = rememberInfiniteTransition(label = "skeleton-pulse")
    val alpha = transition.animateFloat(
        initialValue = SKELETON_MIN_ALPHA,
        targetValue = SKELETON_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    return remember(alpha) { MagicSkeletonPulse(animatedAlpha = alpha) }
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
