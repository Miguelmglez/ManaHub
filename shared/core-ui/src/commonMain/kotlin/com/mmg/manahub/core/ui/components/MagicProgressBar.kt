package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

private const val SHIMMER_DURATION_MS = 1400
// Band covers 35% of total width; offset travels from -0.35 to 1.0 so the
// band is fully off-screen at both ends of the cycle.
private const val SHIMMER_BAND_FRACTION = 0.35f

/**
 * Full-width indeterminate shimmer loading bar themed for ManaHub.
 * Drop-in replacement for [androidx.compose.material3.LinearProgressIndicator]
 * when showing an indeterminate loading state.
 *
 * Renders two layers:
 *  1. A dark track (primaryAccent @ 12% alpha) clipped to a pill shape.
 *  2. A moving shimmer band (animated [Brush.linearGradient]).
 *  3. Optionally a blurred glow copy of the shimmer for depth.
 *
 * @param modifier    Standard modifier. [Modifier.fillMaxWidth] is applied internally.
 * @param trackHeight Visual height of the bar track. Default 3.dp.
 * @param showGlow    Whether to render the blur glow layer. Pass false on
 *                    pre-API-31 devices or when many bars are on screen simultaneously.
 */
@Composable
fun MagicProgressBar(
    modifier: Modifier = Modifier,
    trackHeight: Dp = 3.dp,
    showGlow: Boolean = true,
) {
    val accent = MaterialTheme.magicColors.primaryAccent
    val trackColor = accent.copy(alpha = 0.12f)
    val shape = RoundedCornerShape(50)

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -SHIMMER_BAND_FRACTION,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    // Extra vertical space lets the blur glow render without being clipped (6dp radius on each side).
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight + if (showGlow) 12.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (showGlow) {
            ShimmerBarLayer(
                shimmerOffset = shimmerOffset,
                accent        = accent,
                trackColor    = Color.Transparent,
                height        = trackHeight,
                shape         = shape,
                modifier      = Modifier
                    .fillMaxWidth()
                    .blur(radius = 6.dp),
            )
        }
        ShimmerBarLayer(
            shimmerOffset = shimmerOffset,
            accent        = accent,
            trackColor    = trackColor,
            height        = trackHeight,
            shape         = shape,
            modifier      = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ShimmerBarLayer(
    shimmerOffset: Float,
    accent: Color,
    trackColor: Color,
    height: Dp,
    shape: RoundedCornerShape,
    modifier: Modifier,
) {
    Canvas(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(trackColor),
    ) {
        val totalWidth = size.width
        val bandWidth  = totalWidth * SHIMMER_BAND_FRACTION
        val startX     = totalWidth * shimmerOffset
        val endX       = startX + bandWidth

        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.20f to accent.copy(alpha = 0.30f),
                    0.50f to accent.copy(alpha = 0.85f),
                    0.80f to accent.copy(alpha = 0.30f),
                    1.00f to Color.Transparent,
                ),
                start = Offset(startX, 0f),
                end   = Offset(endX,   0f),
            ),
        )
    }
}

/**
 * Footer composable for [androidx.compose.foundation.lazy.LazyColumn] pagination.
 * Place this as the last item in a lazy list while more pages are loading.
 *
 * Usage:
 * ```
 * item {
 *     if (uiState.isLoadingMore) MagicLoadingFooter()
 * }
 * ```
 *
 * @param modifier  Standard modifier.
 * @param label     Text shown below the bar. Pass null to hide the label entirely.
 * @param showGlow  Forwarded to the inner [MagicProgressBar].
 */
@Composable
fun MagicLoadingFooter(
    modifier: Modifier = Modifier,
    label: String? = "Loading more...",
    showGlow: Boolean = true,
) {
    val colors     = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MagicProgressBar(
            modifier    = Modifier.fillMaxWidth(),
            trackHeight = 2.dp,
            showGlow    = showGlow,
        )
        if (label != null) {
            Text(
                text  = label,
                style = typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}
