package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Standard sizes for the [MagicLoadingSpinner] to ensure visual consistency
 * across the app and prevent layout shifts.
 */
enum class MagicLoadingSize(val dp: Dp) {
    /** 16dp — Inline within small buttons or text rows. */
    XSmall(16.dp),
    /** 28dp — Inside widget headers or small card previews. */
    Small(28.dp),
    /** 48dp — The standard default for most full-screen or section loading. */
    Medium(48.dp),
    /** 64dp — Featured loading states in large hero cards. */
    Large(64.dp),
    /** 100dp — Legacy default, for splash screens or massive empty states. */
    XLarge(100.dp)
}

/**
 * Animated hexagon spinner used for all loading states in ManaHub.
 *
 * Use the [size] parameter to select one of the standardized [MagicLoadingSize]
 * variants. For custom sizing that doesn't fit the standard grid, pass a [modifier].
 */
@Composable
fun MagicLoadingSpinner(
    modifier: Modifier = Modifier,
    size: MagicLoadingSize = MagicLoadingSize.Medium
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_loading_hexagon)
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    LottieAnimation(
        composition = composition,
        modifier = modifier.size(size.dp),
        progress = { progress }
    )
}
