package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * A reusable premium selection item with an accent bar and gradient background.
 * Used in lists where the user needs to pick one option among many.
 * Confirms to ManaHub's premium design system.
 */
@Composable
fun MagicSelectionItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    accentColor: Color? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    
    val finalAccentColor = accentColor ?: mc.primaryAccent
    
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) finalAccentColor else mc.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.background(
                if (isSelected) {
                    Brush.verticalGradient(
                        listOf(finalAccentColor.copy(alpha = 0.08f), mc.backgroundSecondary)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(mc.surface.copy(alpha = 0.5f), mc.backgroundSecondary)
                    )
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subtle vertical accent bar
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(if (isSelected) finalAccentColor else mc.surfaceVariant.copy(alpha = 0.5f))
                )
                
                Row(
                    modifier = Modifier
                        .padding(spacing.md)
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    if (icon != null) {
                        icon()
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            style = ty.titleMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (isSelected) finalAccentColor else mc.textPrimary
                        )
                        if (description != null) {
                            Text(
                                text = description,
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
