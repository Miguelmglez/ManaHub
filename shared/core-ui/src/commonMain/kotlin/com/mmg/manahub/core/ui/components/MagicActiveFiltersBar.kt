package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * A full-width active filters indicator bar with filter count and clear action.
 *
 * @param text The text describing active filters (e.g., "2 active filters").
 * @param clearLabel The label for the clear button (e.g., "Clear filters").
 * @param onClear Callback when clear is clicked.
 * @param modifier Optional [Modifier].
 */
@Composable
fun MagicActiveFiltersBar(
    text: String,
    clearLabel: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = mc.primaryAccent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = text,
                    style = ty.labelLarge,
                    color = mc.primaryAccent,
                )
            }
            TextButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = spacing.sm, vertical = 0.dp),
            ) {
                Text(
                    text = clearLabel.uppercase(),
                    color = mc.lifeNegative,
                    style = ty.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                )
            }
        }
    }
}
