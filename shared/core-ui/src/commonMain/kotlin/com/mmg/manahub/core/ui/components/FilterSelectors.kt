package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.mmg.manahub.core.ui.theme.spacing

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
    label: String? = null,
    valueText: String,
    items: List<T>,
    selectedItem: T,
    onSelect: (T) -> Unit,
    itemLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            color = mc.backgroundSecondary,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                if (label != null) {
                    Text(
                        text = label,
                        style = ty.labelLarge,
                        color = mc.textSecondary
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                }

                Text(
                    text = valueText,
                    style = ty.labelLarge,
                    color = mc.primaryAccent
                )
                
                if (label == null) {
                    Spacer(modifier = Modifier.weight(1f))
                }
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

/**
 * A bottom sheet selector component following the ManaHub design system.
 * Replaces DropdownMenu with a ModalBottomSheet for better mobile UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ManaHubBottomSheetSelector(
    icon: ImageVector,
    label: String? = null,
    valueText: String,
    items: List<T>,
    selectedItem: T,
    onSelect: (T) -> Unit,
    itemLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var expanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            color = mc.backgroundSecondary,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                if (label != null) {
                    Text(
                        text = label,
                        style = ty.labelLarge,
                        color = mc.textSecondary
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                }

                Text(
                    text = valueText,
                    style = ty.labelLarge,
                    color = mc.primaryAccent
                )
                
                if (label == null) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = mc.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            sheetState = sheetState,
            containerColor = mc.backgroundSecondary,
            contentColor = mc.textPrimary
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.xl)
            ) {
                items(
                    count = items.size,
                    key = { index -> items[index].hashCode() }
                ) { index ->
                    val item = items[index]
                    val isSelected = item == selectedItem
                    
                    Surface(
                        onClick = {
                            onSelect(item)
                            expanded = false
                        },
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.lg, vertical = spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = itemLabel(item),
                                style = ty.titleMedium,
                                color = if (isSelected) mc.primaryAccent else mc.textPrimary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = mc.primaryAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    if (index < items.size - 1) {
                        HorizontalDivider(
                            color = mc.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = spacing.lg)
                        )
                    }
                }
            }
        }
    }
}
