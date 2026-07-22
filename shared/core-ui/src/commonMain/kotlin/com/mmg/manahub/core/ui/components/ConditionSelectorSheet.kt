package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.addcard_condition_sheet_title
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.CardConstants
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionSelectorSheet(
    selectedCondition: String,
    onDismiss: () -> Unit,
    onSelectCondition: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        shape = BottomSheetShape
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.addcard_condition_sheet_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(CardConstants.conditions, key = { it.first }) { (code, displayName) ->
                    val isSelected = code == selectedCondition
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelectCondition(code) }
                            .then(
                                if (isSelected) Modifier.background(mc.primaryAccent.copy(alpha = 0.08f))
                                else Modifier
                            )
                            .padding(horizontal = spacing.xl, vertical = spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Icon(
                            imageVector = getConditionIcon(code),
                            contentDescription = null,
                            tint = if (isSelected) mc.primaryAccent else mc.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = ty.bodyMedium,
                                color = if (isSelected) mc.primaryAccent else mc.textPrimary,
                            )
                            Text(
                                text = code,
                                style = ty.labelSmall,
                                color = if (isSelected) mc.primaryAccent.copy(alpha = 0.7f) else mc.textSecondary,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = mc.primaryAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getConditionIcon(code: String): ImageVector = when (code) {
    "M" -> Icons.Default.AutoAwesome
    "NM" -> Icons.Default.Stars
    "EX" -> Icons.Default.CheckCircle
    "GD" -> Icons.Default.SentimentSatisfied
    "LP" -> Icons.Default.RemoveCircleOutline
    "PL" -> Icons.Default.Warning
    "PO" -> Icons.Default.Dangerous
    else -> Icons.Default.Stars
}
