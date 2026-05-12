package com.mmg.manahub.feature.decks.components

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

// Height budget constants (all in dp, converted in Canvas scope)
private val CANVAS_HEIGHT        = 96.dp
private val AXIS_LABEL_ZONE_DP   = 20.dp  // reserved at bottom for "0 1 2 … 7+"
private val COUNT_LABEL_ZONE_DP  = 14.dp  // reserved at top for card counts
private val BAR_SPACING_DP       = 5.dp
private val BAR_CORNER_DP        = 3.dp
private val BASELINE_STROKE_DP   = 0.5.dp

@Composable
fun ManaCurveChart(
    cards: List<DeckCard>,
    modifier: Modifier = Modifier,
    showIdealCurve: Boolean = true,
) {
    val mc = MaterialTheme.magicColors

    // --- Data buckets ---
    val buckets = remember(cards) {
        IntArray(8).also { b ->
            cards.forEach { dc ->
                b[dc.card.cmc.toInt().coerceIn(0, 7)] += dc.quantity
            }
        }
    }
    val maxCount = remember(buckets) { buckets.max().coerceAtLeast(1) }

    // Ideal curve for a standard 60-card deck
    val idealCurve = remember { floatArrayOf(0f, 6f, 8f, 7f, 5f, 4f, 3f, 3f) }
    val idealMax   = remember { idealCurve.max().coerceAtLeast(1f) }

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
    val barColorTop    = mc.primaryAccent
    val barColorBottom = mc.primaryAccent.copy(alpha = 0.4f)
    val idealColor     = Color.White.copy(alpha = 0.35f)
    val baselineColor  = Color.White.copy(alpha = 0.12f)
    val countLabelArgb = Color.White.copy(alpha = 0.70f).toArgb()
    val axisLabelArgb  = Color.White.copy(alpha = 0.30f).toArgb()

    val axisLabels = remember { listOf("0", "1", "2", "3", "4", "5", "6", "7+") }

    Column(modifier = modifier) {
        // --- Header row ---
        Row(
            modifier              = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = stringResource(R.string.deckbuilder_mana_curve_title),
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textSecondary,
            )
            if (showIdealCurve) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Canvas(modifier = Modifier.width(20.dp).height(2.dp)) {
                        drawLine(
                            color       = idealColor,
                            start       = Offset(0f, size.height / 2),
                            end         = Offset(size.width, size.height / 2),
                            strokeWidth = 2f,
                            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                        )
                    }
                    Text(
                        text  = stringResource(R.string.deckbuilder_ideal_curve),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textDisabled,
                    )
                }
            }
        }

        // --- Canvas ---
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CANVAS_HEIGHT),
        ) {
            val barCount = 8
            val spacing       = BAR_SPACING_DP.toPx()
            val axisZone      = AXIS_LABEL_ZONE_DP.toPx()
            val countZone     = COUNT_LABEL_ZONE_DP.toPx()
            val barWidth      = (size.width - spacing * (barCount - 1)) / barCount
            
            // Drawing zones: 
            // barZoneTop/Bottom are strictly to define the HEIGHT of the bars 
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
                    val barH    = ratio * barZoneHeight
                    val barX    = i * (barWidth + spacing)
                    val barTop  = barZoneBottom - barH

                    // Draw a single bar with a vertical gradient
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(barColorTop, barColorBottom),
                            startY = barTop,
                            endY   = barZoneBottom
                        ),
                        topLeft      = Offset(barX, barTop),
                        size         = Size(barWidth, barH),
                        cornerRadius = CornerRadius(BAR_CORNER_DP.toPx()),
                    )
                }
            }

            // --- Ideal curve line ---
            if (showIdealCurve) {
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

            // --- Count labels (ABOVE) ---
            val countPaint = Paint().apply {
                isAntiAlias = true
                textAlign   = Paint.Align.CENTER
                textSize    = 10.sp.toPx()
                color       = countLabelArgb
                typeface    = Typeface.DEFAULT
            }
            buckets.forEachIndexed { i, count ->
                if (count > 0) {
                    val barX    = i * (barWidth + spacing)
                    val centerX = barX + barWidth / 2f
                    val ratio   = animatedRatios[i].value
                    val barH    = ratio * barZoneHeight
                    val labelY  = barZoneBottom - barH - 3.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(
                        count.toString(),
                        centerX,
                        labelY,
                        countPaint,
                    )
                }
            }

            // --- Axis labels (BELOW) ---
            val axisPaint = Paint().apply {
                isAntiAlias = true
                textAlign   = Paint.Align.CENTER
                textSize    = 11.sp.toPx()
                color       = axisLabelArgb
                typeface    = Typeface.DEFAULT
            }
            val labelY = barZoneBottom + axisZone / 2f + axisPaint.textSize / 3f
            axisLabels.forEachIndexed { i, label ->
                val barX    = i * (barWidth + spacing)
                val centerX = barX + barWidth / 2f
                drawContext.canvas.nativeCanvas.drawText(label, centerX, labelY, axisPaint)
            }
        }
    }
}
