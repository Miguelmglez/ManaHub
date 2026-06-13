package com.mmg.manahub.feature.profile.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mmg.manahub.core.gamification.domain.catalog.RenderSpec
import com.mmg.manahub.core.gamification.domain.catalog.RingStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.gamification.presentation.rememberRingStyleBrush

/**
 * A small, read-only XP progress ring with a level number chip, sized for overlay on the
 * Profile hero (ADR-002 §8, Phase 0). Stateless: the caller supplies the level and the
 * already-computed [progress] fraction.
 *
 * The arc sweep animates whenever [progress] changes. The whole badge exposes a single,
 * meaningful accessibility label ("Level N, X of Y XP"); its sub-elements are decorative.
 *
 * @param level the player's current level, drawn in the centre chip.
 * @param progress fraction of the current level completed, in `0f..1f` (clamped defensively).
 * @param contentDescription the merged a11y label for the whole badge.
 * @param ringStyle optional equipped level-ring style (Phase 3). When null or
 *   [RingStyle.SOLID] the progress arc keeps its original flat `primaryAccent` look (the read-only
 *   Phase-0 ring is unchanged when nothing is equipped); other styles restyle the arc with a brush.
 * @param ringRenderSpec the equipped ring cosmetic's render spec (tokens for the brush); ignored when
 *   [ringStyle] is null/SOLID.
 * @param diameter the outer diameter of the ring badge.
 * @param strokeWidth the thickness of the progress ring.
 */
@Composable
fun ProfileLevelRing(
    level: Int,
    progress: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    ringStyle: RingStyle? = null,
    ringRenderSpec: RenderSpec? = null,
    diameter: Dp = 56.dp,
    strokeWidth: Dp = 5.dp,
) {
    val mc = MaterialTheme.magicColors
    val target = progress.coerceIn(0f, 1f)
    // Animate the arc sweep on progress change (small state change → ~400ms emphasized-ish).
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "xpRingSweep",
    )

    val trackColor = mc.surfaceVariant
    val progressColor = mc.primaryAccent
    // Equipped ring-style brush (Phase 3). Null → fall back to the flat primaryAccent arc (unchanged).
    val styledBrush = rememberRingStyleBrush(ringStyle, ringRenderSpec)

    Box(
        modifier = modifier
            .size(diameter)
            // Merge into one semantic node: the ring + chip describe a single concept.
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Full track ring (faint).
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress arc, starting at 12 o'clock and sweeping clockwise. Uses the equipped ring
            // brush when one is set; otherwise the flat primaryAccent color (original behavior).
            if (animatedProgress > 0f) {
                if (styledBrush != null) {
                    drawArc(
                        brush = styledBrush,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                } else {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }

        // Centre level chip.
        Box(
            modifier = Modifier
                .size(diameter - strokeWidth * 3)
                .clip(CircleShape)
                .background(mc.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = level.toString(),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.primaryAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
