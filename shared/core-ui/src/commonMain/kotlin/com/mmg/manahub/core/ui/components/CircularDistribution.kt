package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A reusable circular distribution (ring) chart with a legend.
 * Supports both a full version (for stats screens) and a compact version (for home widgets).
 *
 * When [isColor] is true the legend uses mana-symbol icons for the standard MTG color labels
 * ("White", "Blue", "Black", "Red", "Green", "Colorless"). Callers that use color keys are
 * expected to pass those exact English label strings as map keys (the app is English-only).
 *
 * @param data         Map of label → count. Labels are arbitrary strings; for MTG colors use
 *                     the canonical English names ("White", "Blue", etc.).
 * @param colorMapper  Maps a label string to its display [Color].
 * @param modifier     Optional [Modifier].
 * @param isColor      If true, renders mana-symbol icons for known MTG color labels in the legend.
 * @param isCompact    If true, renders a smaller ring suitable for widget tiles.
 */
@Composable
fun CircularDistribution(
    data: Map<String, Int>,
    colorMapper: (String) -> Color,
    modifier: Modifier = Modifier,
    isColor: Boolean = false,
    isCompact: Boolean = false,
) {
    if (data.isEmpty()) return
    val total = data.values.sum().toFloat().coerceAtLeast(1f)
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    // Dimension configuration based on mode
    val ringSize = if (isCompact) 140.dp else 240.dp
    val strokeWidth = if (isCompact) 18.dp else 32.dp
    val legendSpacing = if (isCompact) MaterialTheme.spacing.xxs else MaterialTheme.spacing.sm
    val symbolSize = if (isCompact) 14.dp else 18.dp
    val outerStrokeWidth = if (isCompact) 0.5.dp else 1.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // ── Ring chart ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = strokeWidth.toPx()
                val ringDiameter = size.minDimension - strokePx
                val topLeftOffset = androidx.compose.ui.geometry.Offset(
                    (size.width - ringDiameter) / 2,
                    (size.height - ringDiameter) / 2
                )
                val arcSize = androidx.compose.ui.geometry.Size(ringDiameter, ringDiameter)
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)

                var startAngle = -90f
                val sortedData = data.entries.sortedByDescending { it.value }

                // Draw background segments (alpha fill)
                sortedData.forEach { (label, count) ->
                    val sweepAngle = (count / total) * 360f * animationProgress.value
                    if (sweepAngle <= 0f) return@forEach
                    val baseColor = colorMapper(label)
                    drawArc(
                        color = baseColor.copy(alpha = 0.75f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeftOffset,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )
                    startAngle += sweepAngle
                }

                // Draw strokes and dividers (solid)
                startAngle = -90f
                sortedData.forEach { (label, count) ->
                    val sweepAngle = (count / total) * 360f * animationProgress.value
                    if (sweepAngle <= 0f) return@forEach
                    val baseColor = colorMapper(label)

                    // Outer stroke
                    val outerDiameter = ringDiameter + strokePx
                    drawArc(
                        color = baseColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            (center.x - (outerDiameter / 2)),
                            (center.y - (outerDiameter / 2))
                        ),
                        size = androidx.compose.ui.geometry.Size(outerDiameter, outerDiameter),
                        style = Stroke(width = outerStrokeWidth.toPx())
                    )

                    // Inner stroke
                    val innerDiameter = ringDiameter - strokePx
                    drawArc(
                        color = baseColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            (center.x - (innerDiameter / 2)),
                            (center.y - (innerDiameter / 2))
                        ),
                        size = androidx.compose.ui.geometry.Size(innerDiameter, innerDiameter),
                        style = Stroke(width = outerStrokeWidth.toPx())
                    )

                    // Radial separator line
                    val startAngleRad = (startAngle * PI / 180f)
                    val innerR = innerDiameter / 2
                    val outerR = outerDiameter / 2

                    drawLine(
                        color = mc.surface,
                        start = androidx.compose.ui.geometry.Offset(
                            center.x + innerR * cos(startAngleRad).toFloat(),
                            center.y + innerR * sin(startAngleRad).toFloat()
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            center.x + outerR * cos(startAngleRad).toFloat(),
                            center.y + outerR * sin(startAngleRad).toFloat()
                        ),
                        strokeWidth = outerStrokeWidth.toPx()
                    )

                    startAngle += sweepAngle
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total.toInt().toString(),
                    style = if (isCompact) ty.labelLarge.copy(fontWeight = FontWeight.Bold)
                            else ty.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp),
                    color = mc.textPrimary
                )
                if (!isCompact) {
                    Text(
                        text = "Total",
                        style = ty.labelSmall,
                        color = mc.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.width(if (isCompact) MaterialTheme.spacing.md else MaterialTheme.spacing.xl))

        // ── Legend ───────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(legendSpacing)
        ) {
            // Canonical English color names used to identify MTG mana colors in the legend.
            // These must match the keys that callers place in [data] when [isColor] = true.
            val whiteName = "White"
            val blueName = "Blue"
            val blackName = "Black"
            val redName = "Red"
            val greenName = "Green"
            val colorlessName = "Colorless"

            val items = if (isCompact) data.entries.sortedByDescending { it.value }.take(6)
                        else data.entries.sortedByDescending { it.value }

            items.forEach { (label, count) ->
                val percentage = (count / total * 100).toInt()
                val colorCode = if (isColor) {
                    when (label) {
                        whiteName     -> "W"
                        blueName      -> "U"
                        blackName     -> "B"
                        redName       -> "R"
                        greenName     -> "G"
                        colorlessName -> "C"
                        else          -> null
                    }
                } else null

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                ) {
                    if (colorCode != null) {
                        ManaSymbolImage(token = colorCode, size = symbolSize)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(if (isCompact) 8.dp else 10.dp)
                                .clip(CircleShape)
                                .background(colorMapper(label))
                        )
                    }

                    Text(
                        text = label,
                        style = if (isCompact) ty.labelSmall else ty.labelMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$percentage%",
                        style = (if (isCompact) ty.labelSmall else ty.labelMedium).copy(fontWeight = FontWeight.Bold),
                        color = mc.textPrimary,
                        modifier = Modifier.widthIn(min = 42.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
