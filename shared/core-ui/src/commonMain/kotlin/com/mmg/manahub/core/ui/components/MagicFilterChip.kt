package com.mmg.manahub.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * A visually attractive global FilterChip for ManaHub.
 * Features an outlined gradient style when selected, matching MagicCtaButton.
 *
 * Guaranteed minimum height for accessible touch targets.
 */
@Composable
fun MagicFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // Micro-animation: scale down slightly when pressed
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "chip_scale")

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent.copy(alpha = 0.1f) else mc.surfaceVariant.copy(alpha = 0.25f),
        label = "chip_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent else mc.textSecondary,
        label = "chip_content"
    )

    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(mc.primaryAccent, mc.secondaryAccent)
    )

    Box(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 32.dp) // Slightly more compact but still accessible
            .clip(ChipShape)
            .background(backgroundColor)
            .then(
                if (selected) Modifier.border(1.5.dp, gradientBrush, ChipShape)
                else Modifier.border(1.dp, mc.surfaceVariant.copy(alpha = 0.4f), ChipShape)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = mc.primaryAccent),
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = spacing.md, vertical = 6.dp), // Reduced vertical padding
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.size(18.dp)) {
                    leadingIcon()
                }
                Spacer(Modifier.width(spacing.sm))
            }
            Text(
                text = label,
                style = ty.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = if (selected) 0.5.sp else 0.sp
                ),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
