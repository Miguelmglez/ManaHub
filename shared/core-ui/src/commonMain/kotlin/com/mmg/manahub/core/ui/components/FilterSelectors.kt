package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A generic dropdown selector component following the ManaHub design system.
 * Used for sorting, filtering, and grouping selections.
 *
 * @param icon The leading icon to display.
 * @param label The static label (e.g., "Sort by:").
 * @param valueText The text representing the currently selected value.
 * @param items The list of options to display in the dropdown.
 * @param selectedItem The currently selected item from [items].
 * @param onSelect Callback when an item is selected.
 * @param itemLabel Mapping from item to its display string.
 * @param modifier Optional [Modifier].
 */
@Composable
fun <T> ManaHubSelector(
    icon: ImageVector,
    label: String,
    valueText: String,
    items: List<T>,
    selectedItem: T,
    onSelect: (T) -> Unit,
    itemLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            color = mc.backgroundSecondary,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = ty.labelLarge,
                    color = mc.textSecondary
                )
                Text(
                    text = valueText,
                    style = ty.labelLarge,
                    color = mc.primaryAccent
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = mc.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = mc.backgroundSecondary,
            modifier = Modifier.width(220.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = item == selectedItem
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemLabel(item),
                            style = ty.bodyMedium,
                            color = if (isSelected) mc.primaryAccent else mc.textPrimary
                        )
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp)) }
                    } else null
                )
                if (index < items.size - 1) {
                    HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}
