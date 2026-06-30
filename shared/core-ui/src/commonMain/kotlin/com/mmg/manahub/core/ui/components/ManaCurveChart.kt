package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

// Height budget constants (all in dp, converted in Canvas scope)
private val CANVAS_HEIGHT        = 96.dp
private val AXIS_LABEL_ZONE_DP   = 20.dp  // reserved at bottom for "0 1 2 … 7+"
private val COUNT_LABEL_ZONE_DP  = 14.dp  // reserved at top for card counts
private val BAR_SPACING_DP       = 5.dp
private val BAR_CORNER_DP        = 3.dp
private val BASELINE_STROKE_DP   = 0.5.dp

/**
 * Bar chart visualising the mana-curve distribution of a deck.
 *
 * @param cmcDistribution Map from converted mana cost (0–7+) to card count.
 * @param modifier        Layout modifier.
 * @param title           Optional header label (displayed left of the legend).
 * @param showIdealCurve  When `true`, overlays the [idealCurve] as a dashed line.
 * @param idealCurve      Per-CMC ideal ratios (length must be ≥ 8 when [showIdealCurve] is true).
 * @param legendLabel     Label for the ideal-curve legend swatch. Defaults to "Ideal curve".
 *                        Pass the app's localised string if required; the default is English-only
 *                        (acceptable — the app is English-only per CLAUDE.md).
 */
@Composable
fun ManaCurveChart(
    cmcDistribution: Map<Int, Int>,
    modifier: Modifier = Modifier,
    title: String? = null,
    showIdealCurve: Boolean = false,
    idealCurve: FloatArray? = null,
    legendLabel: String = "Ideal curve",
) {
    val mc = MaterialTheme.magicColors
    val textMeasurer = rememberTextMeasurer()

    // --- Data buckets ---
    val buckets = remember(cmcDistribution) {
        IntArray(8).also { b ->
            cmcDistribution.forEach { (cmc, count) ->
                b[cmc.coerceIn(0, 7)] += count
            }
        }
    }
    val maxCount = remember(buckets) { buckets.max().coerceAtLeast(1) }

    // Animate bar proportions
    val animatedRatios = buckets.mapIndexed { i, count ->
        val target = count.toFloat() / maxCount
        animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = 400,
                delayMillis    = i * 25,
                easing         = FastOutSlowInEasing,
            ),
            label = "bar_ratio_$i",
        )
    }

    // --- Color tokens ---
    val barColorTop      = mc.primaryAccent
    val barColorBottom   = mc.primaryAccent.copy(alpha = 0.4f)
    val idealColor       = Color.White.copy(alpha = 0.35f)
    val baselineColor    = Color.White.copy(alpha = 0.12f)
    val countLabelColor  = Color.White.copy(alpha = 0.70f)
    val axisLabelColor   = Color.White.copy(alpha = 0.30f)

    val axisLabels = remember { listOf("0", "1", "2", "3", "4", "5", "6", "7+") }

    // --- Pre-measure text (stable across animation; re-measure only when buckets/labels change) ---
    val countLabelStyle = remember { TextStyle(fontSize = 10.sp) }
    val axisLabelStyle  = remember { TextStyle(fontSize = 11.sp) }

    val measuredCounts = remember(buckets, textMeasurer) {
        buckets.map { count ->
            if (count > 0) textMeasurer.measure(count.toString(), countLabelStyle) else null
        }
    }
    val measuredAxisLabels = remember(textMeasurer) {
        axisLabels.map { label -> textMeasurer.measure(label, axisLabelStyle) }
    }

    Column(modifier = modifier) {
        // --- Header row ---
        if (title != null || showIdealCurve) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.textSecondary,
                    )
                }
                if (showIdealCurve) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp),
                        ) {
                            drawLine(
                                color = idealColor,
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                            )
                        }
                        Text(
                            text = legendLabel,
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textDisabled,
                        )
                    }
                }
            }
        }

        // --- Canvas ---
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CANVAS_HEIGHT),
        ) {
            val barCount      = 8
            val spacing       = BAR_SPACING_DP.toPx()
            val axisZone      = AXIS_LABEL_ZONE_DP.toPx()
            val countZone     = COUNT_LABEL_ZONE_DP.toPx()
            val barWidth      = (size.width - spacing * (barCount - 1)) / barCount

            // Drawing zones:
            // barZoneTop/Bottom strictly define the HEIGHT of the bars
            // so they don't overlap with labels above or below.
            val barZoneBottom = size.height - axisZone
            val barZoneHeight = barZoneBottom - countZone

            // --- Baseline ---
            drawLine(
                color       = baselineColor,
                start       = Offset(0f, barZoneBottom),
                end         = Offset(size.width, barZoneBottom),
                strokeWidth = BASELINE_STROKE_DP.toPx(),
            )

            // --- Bars ---
            animatedRatios.forEachIndexed { i, ratioState ->
                val ratio = ratioState.value
                if (ratio > 0f) {
                    val barH   = ratio * barZoneHeight
                    val barX   = i * (barWidth + spacing)
                    val barTop = barZoneBottom - barH

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(barColorTop, barColorBottom),
                            startY = barTop,
                            endY   = barZoneBottom,
                        ),
                        topLeft      = Offset(barX, barTop),
                        size         = Size(barWidth, barH),
                        cornerRadius = CornerRadius(BAR_CORNER_DP.toPx()),
                    )
                }
            }

            // --- Ideal curve line ---
            if (showIdealCurve && idealCurve != null) {
                val idealMax = idealCurve.max().coerceAtLeast(1f)
                val pts = idealCurve.mapIndexed { i, ideal ->
                    Offset(
                        x = i * (barWidth + spacing) + barWidth / 2f,
                        y = barZoneBottom - (ideal / idealMax) * barZoneHeight,
                    )
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                for (j in 0 until pts.size - 1) {
                    drawLine(
                        color       = idealColor,
                        start       = pts[j],
                        end         = pts[j + 1],
                        strokeWidth = 1.5f,
                        pathEffect  = dash,
                    )
                }
            }

            // --- Count labels (ABOVE bars) ---
            // drawText positions at topLeft; we want the label bottom at (barTop - 3dp).
            buckets.forEachIndexed { i, count ->
                if (count > 0) {
                    val measured = measuredCounts[i] ?: return@forEachIndexed
                    val barX      = i * (barWidth + spacing)
                    val centerX   = barX + barWidth / 2f
                    val ratio     = animatedRatios[i].value
                    val barH      = ratio * barZoneHeight
                    val labelBottomY = barZoneBottom - barH - 3.dp.toPx()
                    drawText(
                        textLayoutResult = measured,
                        color   = countLabelColor,
                        topLeft = Offset(
                            x = centerX - measured.size.width / 2f,
                            y = labelBottomY - measured.size.height,
                        ),
                    )
                }
            }

            // --- Axis labels (BELOW baseline) ---
            // Centre each label vertically in the axis zone.
            val axisCenterY = barZoneBottom + axisZone / 2f
            axisLabels.forEachIndexed { i, _ ->
                val measured = measuredAxisLabels[i]
                val barX     = i * (barWidth + spacing)
                val centerX  = barX + barWidth / 2f
                drawText(
                    textLayoutResult = measured,
                    color   = axisLabelColor,
                    topLeft = Offset(
                        x = centerX - measured.size.width / 2f,
                        y = axisCenterY - measured.size.height / 2f,
                    ),
                )
            }
        }
    }
}
