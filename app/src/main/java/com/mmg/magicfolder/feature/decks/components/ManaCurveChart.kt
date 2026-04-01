package com.mmg.magicfolder.feature.decks.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.DeckCard
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

@Composable
fun ManaCurveChart(
    cards: List<DeckCard>,
    modifier: Modifier = Modifier,
    showIdealCurve: Boolean = true,
) {
    val mc = MaterialTheme.magicColors

    // Bucket cards by CMC: 0..6 and 7+
    val buckets = IntArray(8)
    cards.forEach { dc ->
        val cmc = dc.card.cmc.toInt().coerceIn(0, 7)
        buckets[cmc] += dc.quantity
    }
    val maxCount = buckets.max().coerceAtLeast(1)

    // Simplified ideal curve (non-land cards, standard 60-card deck)
    val idealCurve = floatArrayOf(0f, 6f, 8f, 7f, 5f, 4f, 3f, 3f)
    val idealMax   = idealCurve.max().coerceAtLeast(1f)

    Column(modifier = modifier) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
                    val lineColor = Color.White.copy(alpha = 0.4f)
                    Canvas(modifier = Modifier.width(20.dp).height(2.dp)) {
                        drawLine(
                            color       = lineColor,
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

        val barColor   = mc.primaryAccent.copy(alpha = 0.80f)
        val idealColor = Color.White.copy(alpha = 0.35f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
        ) {
            val barCount = 8
            val spacing  = 4.dp.toPx()
            val barWidth = (size.width - spacing * (barCount - 1)) / barCount
            val chartH   = size.height

            buckets.forEachIndexed { i, count ->
                if (count > 0) {
                    val barH = (count.toFloat() / maxCount) * chartH
                    val x    = i * (barWidth + spacing)
                    drawRoundRect(
                        color        = barColor,
                        topLeft      = Offset(x, chartH - barH),
                        size         = Size(barWidth, barH),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }

            if (showIdealCurve) {
                val pts = idealCurve.mapIndexed { i, ideal ->
                    Offset(
                        x = i * (barWidth + spacing) + barWidth / 2f,
                        y = chartH - (ideal / idealMax) * chartH,
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
        }

        // CMC axis labels
        Row(
            modifier              = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("0", "1", "2", "3", "4", "5", "6", "7+").forEach { label ->
                Text(
                    text  = label,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
            }
        }
    }
}
