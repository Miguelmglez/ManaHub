package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.ic_multicolor
import com.mmg.manahub.core.ui.theme.magicColors
import org.jetbrains.compose.resources.painterResource

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
    isMultiColorExclusive: Boolean = true,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    colors: List<String> = listOf("M", "W", "U", "B", "R", "G", "C")
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ManaColorItem(
                    color = color,
                    isSelected = if (color == "M" && isMultiColorExclusive) selectedColors.isEmpty() else selectedColors.contains(color),
                    onClick = { onToggleColor(color) },
                    itemSize = itemSize,
                    symbolSize = symbolSize,
                )
            }
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
    val manaColor = if (color == "M") mc.goldMtg else manaColorFor(color, mc)
    
    // For Black ("B"), use a light gray selection color to improve visibility on dark backgrounds
    val selectionColor = if (color == "B") Color.LightGray else manaColor
    val selectionAlpha = if (color == "B") 0.4f else 0.2f

    Box(
        modifier = modifier
            .size(itemSize.coerceAtLeast(48.dp))
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
        if (color == "M") {
            Icon(
                painter = painterResource(Res.drawable.ic_multicolor),
                contentDescription = "Multicolor",
                modifier = Modifier.size(symbolSize),
                tint = Color.Unspecified,
            )
        } else {
            ManaSymbolImage(token = color, size = symbolSize)
        }
    }
}
