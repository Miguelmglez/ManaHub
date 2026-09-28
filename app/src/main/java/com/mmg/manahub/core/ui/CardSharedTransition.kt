package com.mmg.manahub.core.ui

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/** Duration of the card-image shared-bounds transition between a card thumbnail and Card Detail. */
const val CARD_SHARED_BOUNDS_MS = 380

/**
 * The one bounds curve for card-image shared transitions. Both ends must use it: the entering side
 * drives the curve, so a different spec per screen made the return path run a different motion.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val CardSharedBoundsTransform: BoundsTransform = BoundsTransform { _, _ ->
    tween(durationMillis = CARD_SHARED_BOUNDS_MS, easing = LinearOutSlowInEasing)
}
