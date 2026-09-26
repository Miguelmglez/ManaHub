package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    XLarge(100.dp),
}

/**
 * Animated native hexagon spinner used for all loading states in ManaHub.
 *
 * Uses Compose Canvas with two counter-rotating hexagons, dynamic path trimming via [PathMeasure],
 * and sweep gradients featuring [com.mmg.manahub.core.ui.theme.MagicColors.primaryAccent] and
 * [com.mmg.manahub.core.ui.theme.MagicColors.secondaryAccent].
 *
 * Use the [size] parameter to select one of the standardized [MagicLoadingSize]
 * variants. For custom sizing that doesn't fit the standard grid, pass a [modifier].
 */
@Composable
fun MagicLoadingSpinner(
    modifier: Modifier = Modifier,
    size: MagicLoadingSize = MagicLoadingSize.Medium,
) {
    val primaryAccent = MaterialTheme.magicColors.primaryAccent
    val secondaryAccent = MaterialTheme.magicColors.secondaryAccent

    val infiniteTransition = rememberInfiniteTransition(label = "MagicLoadingSpinnerTransition")

    // Outer hexagon continuous rotation (clockwise)
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outerRotation",
    )

    // Outer path trim progress (0f to 1f)
    val trimProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "trimProgress",
    )

    // Inner hexagon counter-rotation (counter-clockwise)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "innerRotation",
    )

    val pathMeasure = remember { PathMeasure() }

    Canvas(modifier = modifier.size(size.dp)) {
        val canvasWidth = this.size.width
        val canvasHeight = this.size.height
        val minDim = minOf(canvasWidth, canvasHeight)
        if (minDim <= 0f) return@Canvas

        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)

        // Dynamic stroke widths scaled to canvas size
        val strokeWidthOuter = (minDim * 0.08f).coerceAtLeast(1.5.dp.toPx())
        val strokeWidthInner = (minDim * 0.06f).coerceAtLeast(1.dp.toPx())

        val outerRadius = ((minDim - strokeWidthOuter) / 2f) * 0.92f
        val innerRadius = outerRadius * 0.52f

        if (outerRadius <= 0f) return@Canvas

        // 1. Create full outer hexagon path
        val outerHexagonPath = createHexagonPath(center, outerRadius)

        // 2. Draw subtle outer background track
        drawPath(
            path = outerHexagonPath,
            color = primaryAccent.copy(alpha = 0.15f),
            style = Stroke(
                width = strokeWidthOuter,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // 3. Trim outer hexagon path using PathMeasure
        pathMeasure.setPath(outerHexagonPath, forceClosed = true)
        val outerTotalLength = pathMeasure.length
        if (outerTotalLength > 0f) {
            val minTrimFraction = 0.20f
            val maxTrimFraction = 0.70f
            val trimLengthFraction = minTrimFraction + ((maxTrimFraction - minTrimFraction) *
                (0.5f + (0.5f * sin(trimProgress * 2f * PI.toFloat()))))
            val trimLength = outerTotalLength * trimLengthFraction

            val startDistance = (trimProgress * outerTotalLength) % outerTotalLength
            val endDistance = startDistance + trimLength

            val outerSegmentPath = Path()
            if (endDistance <= outerTotalLength) {
                pathMeasure.getSegment(startDistance, endDistance, outerSegmentPath, startWithMoveTo = true)
            } else {
                pathMeasure.getSegment(startDistance, outerTotalLength, outerSegmentPath, startWithMoveTo = true)
                pathMeasure.getSegment(0f, endDistance - outerTotalLength, outerSegmentPath, startWithMoveTo = true)
            }

            val outerSweepBrush = Brush.sweepGradient(
                colors = listOf(primaryAccent, secondaryAccent, primaryAccent),
                center = center,
            )

            rotate(degrees = outerRotation, pivot = center) {
                drawPath(
                    path = outerSegmentPath,
                    brush = outerSweepBrush,
                    style = Stroke(
                        width = strokeWidthOuter,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        // 4. Inner counter-rotating hexagon
        if (innerRadius > 0f) {
            val innerHexagonPath = createHexagonPath(center, innerRadius)

            pathMeasure.setPath(innerHexagonPath, forceClosed = true)
            val innerTotalLength = pathMeasure.length
            if (innerTotalLength > 0f) {
                val innerSegmentLength = innerTotalLength * 0.65f
                val innerStartDist = ((1f - trimProgress) * innerTotalLength) % innerTotalLength
                val innerEndDist = innerStartDist + innerSegmentLength

                val innerSegmentPath = Path()
                if (innerEndDist <= innerTotalLength) {
                    pathMeasure.getSegment(innerStartDist, innerEndDist, innerSegmentPath, startWithMoveTo = true)
                } else {
                    pathMeasure.getSegment(innerStartDist, innerTotalLength, innerSegmentPath, startWithMoveTo = true)
                    pathMeasure.getSegment(0f, innerEndDist - innerTotalLength, innerSegmentPath, startWithMoveTo = true)
                }

                val innerSweepBrush = Brush.sweepGradient(
                    colors = listOf(secondaryAccent, primaryAccent, secondaryAccent),
                    center = center,
                )

                rotate(degrees = innerRotation, pivot = center) {
                    drawPath(
                        path = innerSegmentPath,
                        brush = innerSweepBrush,
                        style = Stroke(
                            width = strokeWidthInner,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Creates a regular 6-sided hexagon [Path] centered at [center] with radius [radius].
 */
private fun createHexagonPath(
    center: Offset,
    radius: Float,
): Path {
    val path = Path()
    for (i in 0 until 6) {
        val angleRad = ((i * 60f) - 90f) * (PI.toFloat() / 180f)
        val x = center.x + (radius * cos(angleRad))
        val y = center.y + (radius * sin(angleRad))
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}
