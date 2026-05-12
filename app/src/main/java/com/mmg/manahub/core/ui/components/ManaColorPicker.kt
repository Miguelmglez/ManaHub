package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors

/**
 * A horizontal row of mana color symbols that allows for selection.
 * Used in search filters, profile customization, etc.
 */
@Composable
fun ManaColorPicker(
    selectedColors: Set<String>,
    onToggleColor: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemSize: Dp = 44.dp,
    symbolSize: Dp = 32.dp,
    spacing: Dp = 8.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(spacing),
    colors: List<String> = listOf("All", "W", "U", "B", "R", "G", "C")
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { color ->
            ManaColorItem(
                color = color,
                isSelected = if (color == "All") selectedColors.isEmpty() else selectedColors.contains(color),
                onClick = { onToggleColor(color) },
                itemSize = itemSize,
                symbolSize = symbolSize,
            )
        }
    }
}

/**
 * A single mana symbol circle with selection state styling.
 */
@Composable
fun ManaColorItem(
    color: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemSize: Dp = 44.dp,
    symbolSize: Dp = 32.dp,
) {
    val mc = MaterialTheme.magicColors
    val manaColor = if (color == "All") mc.goldMtg else manaColorFor(color, mc)
    
    // For Black ("B"), use a light gray selection color to improve visibility on dark backgrounds
    val selectionColor = if (color == "B") Color.LightGray else manaColor
    val selectionAlpha = if (color == "B") 0.4f else 0.2f

    Box(
        modifier = modifier
            .size(itemSize)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier
                        .background(selectionColor.copy(alpha = selectionAlpha))
                        .border(2.dp, selectionColor, CircleShape)
                } else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (color == "All") {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.mmg.manahub.R.drawable.ic_counter),
                contentDescription = "All",
                modifier = Modifier.size(symbolSize),
                tint = mc.goldMtg
            )
        } else {
            ManaSymbolImage(token = color, size = symbolSize)
        }
    }
}
