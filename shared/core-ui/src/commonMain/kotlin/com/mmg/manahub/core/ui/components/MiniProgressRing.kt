package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors

/**
 * A minimal single-value progress ring sized for a tight inline slot (e.g. a [SectionHeader]'s
 * `trailing` content) — distinct from [CircularDistribution], which is a multi-segment
 * distribution CHART with a legend and a 140-180dp minimum size, not naturally a single
 * "current/ideal" progress indicator. Written new rather than adding a size tier to
 * [CircularDistribution] (Suggestions Tab UI Polish plan, W8/D6 — the plan's own documented
 * judgment call: reusing a multi-segment chart component for a single-value ring would be more
 * invasive than a small dedicated composable).
 *
 * Mirrors [HealthScoreRing]'s arc-drawing shape (track + progress arc, round caps) at a much
 * smaller footprint and with no center label — the numeric `current/ideal` readout stays a
 * sibling [androidx.compose.material3.Text] at the call site (see `CardSectionHeader`'s trailing
 * content), this component is the visual fill indicator alone.
 *
 * @param value the fill fraction in `[0,1]` — callers compute this the same way the old linear
 *   band bar did (symmetric current/ideal/max ramp, or the inverted current/max for an
 *   anti-role) so the ring always agrees with the same "healthy" shape the rest of the UI uses.
 * @param contentDescription accessibility label (CLAUDE.md) — callers should pass something like
 *   "3 of 8, healthy" rather than leaving this null; `null` renders no semantics override at all
 *   (the caller is then responsible for describing the value some other way, e.g. adjacent text).
 */
@Composable
fun MiniProgressRing(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 28.dp,
    strokeWidth: Dp = 4.dp,
    contentDescription: String? = null,
) {
    val mc = MaterialTheme.magicColors
    val fraction = value.coerceIn(0f, 1f)
    val semanticsModifier = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.size(diameter).then(semanticsModifier)) {
        val strokePx = strokeWidth.toPx()
        val inset = strokePx / 2f
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = mc.surfaceVariant,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * fraction,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}
