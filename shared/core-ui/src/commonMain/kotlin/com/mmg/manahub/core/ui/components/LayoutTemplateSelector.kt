package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.LayoutTemplate
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A row of selectable tiles for choosing a player-seating [LayoutTemplate] out of
 * [availableLayouts] (typically all layouts valid for the current player count — see
 * `LayoutTemplates.getLayoutsForCount`). The [activeLayout] tile is highlighted.
 *
 * Shared by the in-game "Manage Players" layout tab and the pre-game setup screen's Settings
 * section so the two pickers can never drift apart — extracted 2026-08-17 from what was
 * previously an inline block in `GamePlayScreen.kt`'s `ManageTab`.
 *
 * @param availableLayouts Layouts to offer as options (already filtered to the relevant player count).
 * @param activeLayout The currently selected/active layout; its tile is highlighted.
 * @param onSelectLayout Callback invoked with the tapped layout.
 * @param modifier Optional modifier.
 */
@Composable
fun LayoutTemplateSelector(
    availableLayouts: List<LayoutTemplate>,
    activeLayout: LayoutTemplate,
    onSelectLayout: (LayoutTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        availableLayouts.forEach { layout ->
            val isSelected = layout == activeLayout
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) mc.primaryAccent.copy(alpha = 0.20f) else mc.surface,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectLayout(layout) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = layout.name,
                        style = MaterialTheme.magicTypography.labelMedium,
                        color = if (isSelected) mc.primaryAccent else mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}
