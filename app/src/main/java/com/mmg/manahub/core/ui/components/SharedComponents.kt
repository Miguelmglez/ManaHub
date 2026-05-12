package com.mmg.manahub.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

@Composable
fun CopyBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

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

@Composable
fun RarityDot(rarity: String, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val color = when (rarity.lowercase()) {
        "mythic"   -> mc.primaryAccent
        "rare"     -> mc.goldMtg
        "uncommon" -> mc.textSecondary
        else       -> mc.textDisabled
    }
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun FoilBadge() {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.goldMtg.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text     = "foil",
            style    = MaterialTheme.typography.labelSmall,
            color    = mc.goldMtg,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun StaleBadge() {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.lifeNegative.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text     = "⚠ prices",
            style    = MaterialTheme.typography.labelSmall,
            color    = mc.lifeNegative,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun StaleWarningBanner() {
    val mc = MaterialTheme.magicColors
    Surface(color = mc.lifeNegative.copy(alpha = 0.12f)) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint               = mc.lifeNegative,
                modifier           = Modifier.size(16.dp),
            )
            Text(
                text  = "Some prices couldn't be refreshed. Showing cached data.",
                style = MaterialTheme.typography.bodySmall,
                color = mc.lifeNegative,
            )
        }
    }
}

