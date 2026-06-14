package com.mmg.manahub.feature.decks.presentation.improvement.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.RoleCoverage

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Doctor — Health view
//
//  Stateless, theme-token-only composables for the read-only Health evaluation:
//  a score ring, role-coverage track bars, a mana-curve mini chart and warning chips.
//  All colors derive from MagicColors (good → mid → low = lifePositive → goldMtg →
//  lifeNegative); none are hardcoded.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Resolves a 0..1 quality fraction to a theme color on the good → mid → low ramp.
 * 1.0 = healthy (`lifePositive`), ~0.5 = caution (`goldMtg`), 0.0 = alert (`lifeNegative`).
 */
private fun MagicColors.qualityColor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return if (f >= 0.5f) {
        lerp(goldMtg, lifePositive, (f - 0.5f) / 0.5f)
    } else {
        lerp(lifeNegative, goldMtg, f / 0.5f)
    }
}

/**
 * Circular 0–100 health score with a short verdict in the center.
 *
 * @param score deck health score in [0,100].
 */
@Composable
fun HealthScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val fraction = (score / 100f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = fraction, label = "healthScore")
    val arcColor = mc.qualityColor(fraction)

    val verdict = when {
        score >= 80 -> stringResource(R.string.deck_health_verdict_excellent)
        score >= 60 -> stringResource(R.string.deck_health_verdict_solid)
        score >= 40 -> stringResource(R.string.deck_health_verdict_needs_work)
        else -> stringResource(R.string.deck_health_verdict_rough)
    }
    val ringDescription = stringResource(R.string.deck_health_score_cd, score)

    Box(
        modifier = modifier
            .size(168.dp)
            .clearAndSetSemantics { contentDescription = ringDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(168.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Track
            drawArc(
                color = mc.surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = ty.displayLarge,
                color = mc.textPrimary,
            )
            Text(
                text = verdict,
                style = ty.labelMedium,
                color = arcColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * A single role-coverage row: role name on the left, a track bar with a current fill and an
 * "ideal" marker, and the `current/ideal` value on the right. Color reflects how close the
 * current count is to the ideal; far-below-ideal functional roles flip to the alert color.
 */
@Composable
fun RoleCoverageRow(
    coverage: RoleCoverage,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // ratio is current/ideal clamped to [0,2]; quality is how close to (or above) ideal we are.
    val ratio = coverage.ratio
    val quality = ratio.coerceAtMost(1f)
    val barColor = mc.qualityColor(quality)
    // Fill fraction of the whole track; ideal sits at the 50% mark so over-coverage is visible.
    val fillFraction = (ratio / 2f).coerceIn(0f, 1f)
    val roleName = coverage.role.label()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = roleName,
            style = ty.bodyMedium,
            color = mc.textPrimary,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.md))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(mc.surfaceVariant),
        ) {
            // Current fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor),
            )
            // Ideal marker at 50% of the track
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(10.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(10.dp)
                        .background(mc.textSecondary),
                )
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacing.md))
        Text(
            text = "${coverage.current}/${coverage.ideal}",
            style = ty.labelMedium,
            color = barColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp),
        )
    }
}

/**
 * Mana-curve mini bar chart. Lands are excluded (the engine's [DeckEvaluation.curveHistogram] only
 * counts non-lands). Buckets run 1..7, where bucket 7 is "7+".
 */
@Composable
fun ManaCurveChart(
    histogram: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val buckets = (1..7).toList()
    val maxCount = (histogram.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val chartDescription = stringResource(R.string.deck_health_curve_cd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clearAndSetSemantics { contentDescription = chartDescription },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { bucket ->
            val count = histogram[bucket] ?: 0
            val heightFraction = count.toFloat() / maxCount
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = count.toString(),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
                Spacer(Modifier.height(MaterialTheme.spacing.xxs))
                // Bar grows from the bottom; min visible sliver even when empty.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .barHeight(heightFraction, maxHeight = 72.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (count == 0) mc.surfaceVariant else mc.primaryAccent),
                )
                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                Text(
                    text = if (bucket == 7) "7+" else bucket.toString(),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            }
        }
    }
}

/** Lays a curve bar out at [fraction] of [maxHeight], with a small floor so empty bars stay visible. */
private fun Modifier.barHeight(fraction: Float, maxHeight: androidx.compose.ui.unit.Dp): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val maxPx = maxHeight.roundToPx()
            val minPx = (maxPx * 0.04f).toInt().coerceAtLeast(2)
            val h = (maxPx * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(minPx)
            val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
    )

/** A warning chip rendered in the alert color. */
@Composable
fun WarningChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.lifeNegative.copy(alpha = 0.16f),
        shape = ChipShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.lifeNegative),
            )
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Text(text = text, style = ty.bodyMedium, color = mc.textPrimary)
        }
    }
}
