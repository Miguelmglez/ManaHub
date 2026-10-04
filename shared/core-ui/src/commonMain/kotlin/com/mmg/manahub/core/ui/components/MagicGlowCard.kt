package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors

/**
 * Applies the GET_STARTED widget container style to a Modifier:
 * - Horizontal gradient border with [magicColors.primaryAccent] (50% -> 15% -> 50% alpha)
 * - Colored shadow using [coloredShadow] with [magicColors.primaryAccent]
 * - Clipped to [shape]
 * - Vertical gradient background from [magicColors.surfaceVariant] (95% alpha) to [magicColors.surface] (98% alpha)
 */
@Composable
fun Modifier.getStartedContainer(
    shape: Shape = CardShape,
): Modifier {
    val mc = MaterialTheme.magicColors
    return this
        .coloredShadow(
            color = mc.primaryAccent.copy(alpha = 0.18f),
            borderRadius = 18.dp,
            blurRadius = 24.dp,
        )
        .border(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    mc.primaryAccent.copy(alpha = 0.5f),
                    mc.primaryAccent.copy(alpha = 0.15f),
                    mc.primaryAccent.copy(alpha = 0.5f),
                ),
            ),
            shape = shape,
        )
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    mc.surfaceVariant.copy(alpha = 0.95f),
                    mc.surface.copy(alpha = 0.98f),
                ),
            ),
        )
}

/**
 * A container card representing the GET_STARTED widget style.
 *
 * Employs a subtle primaryAccent colored shadow, horizontal gradient border, and vertical surface gradient background.
 */
@Composable
fun MagicGlowCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val containerModifier = modifier
        .getStartedContainer(shape = shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    onClick = onClick,
                )
            } else {
                Modifier
            }
        )

    Box(
        modifier = containerModifier,
        content = content,
    )
}
