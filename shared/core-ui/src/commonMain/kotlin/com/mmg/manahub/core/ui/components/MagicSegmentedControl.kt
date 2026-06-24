package com.mmg.manahub.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A custom segmented control styled with MagicTheme tokens.
 *
 * Renders a row of text options with an animated background indicator
 * that slides to the selected segment.
 *
 * @param options          Labels for each segment.
 * @param selectedIndex    Index of the currently selected segment.
 * @param onOptionSelected Called when the user taps a segment.
 */
@Composable
fun MagicSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, colors.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            .padding(4.dp)
    ) {
        val maxWidth = maxWidth
        val itemWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            label = "indicatorOffset"
        )

        // Animated background indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.primaryAccent.copy(alpha = 0.15f))
                .border(1.dp, colors.primaryAccent.copy(alpha = 0.25f), CircleShape)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, text ->
                val selected = index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = if (selected) colors.primaryAccent else colors.textDisabled,
                    label = "contentColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOptionSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        style = typography.labelMedium,
                        color = contentColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
